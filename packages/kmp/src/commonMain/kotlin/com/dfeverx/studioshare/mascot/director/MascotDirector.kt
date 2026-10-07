package com.dfeverx.studioshare.mascot.director

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the mascot is doing right now. [cueId] changes whenever a new cue takes over. */
data class MascotScene(
    val momentKey: String,
    /** Key of a `mascotAnchor` to walk to, or null to stay docked. */
    val anchor: String? = null,
    /** True for a one-shot that plays once and hands back; false for a held state. */
    val oneShot: Boolean = false,
    val cueId: Long = 0,
)

/** How much a cue matters when several want the mascot at once. */
enum class MascotPriority { Ambient, Normal, High, Blocking }

/**
 * Decides what the mascot does. Screens never draw the mascot; they *cue* it:
 * - [hold] while a state lasts (culling, uploading, offline) and [release] when it ends;
 * - [play] for a moment that happened (saved, published, failed) — it plays once, then the
 *   strongest held state (or idle) comes back.
 *
 * Held cues: the highest [MascotPriority] wins, the newest breaks a tie. A one-shot wins over held
 * cues below [MascotPriority.High], so "Saved" can interrupt "uploading" but not "session expired".
 * While disabled it keeps nothing, so turning the mascot back on starts from idle.
 */
class MascotDirector(private val idleMoment: String = "app.idle") {
    private data class Cue(val moment: String, val anchor: String?, val priority: MascotPriority, val seq: Long)

    private val holds = LinkedHashMap<Any, Cue>()
    private var oneShot: Cue? = null
    private var seq = 0L
    private var enabled = true
    private val _scene = MutableStateFlow(MascotScene(idleMoment))
    val scene: StateFlow<MascotScene> = _scene.asStateFlow()

    fun setEnabled(value: Boolean) = update {
        enabled = value
        if (!value) {
            holds.clear()
            oneShot = null
        }
    }

    fun hold(owner: Any, moment: String, anchor: String? = null, priority: MascotPriority = MascotPriority.Normal) =
        update {
            if (!enabled) return@update
            val existing = holds[owner]
            if (existing != null && existing.moment == moment && existing.anchor == anchor && existing.priority == priority) {
                return@update
            }
            holds.remove(owner)
            holds[owner] = Cue(moment, anchor, priority, ++seq)
        }

    fun release(owner: Any) = update { holds.remove(owner) }

    fun play(moment: String, anchor: String? = null, priority: MascotPriority = MascotPriority.Normal) =
        update {
            if (!enabled) return@update
            oneShot = Cue(moment, anchor, priority, ++seq)
        }

    /** The stage reports a one-shot finished playing; anything newer is left alone. */
    fun finished(cueId: Long) = update {
        if (oneShot?.seq == cueId) oneShot = null
    }

    private inline fun update(block: () -> Unit) {
        // Cues come from composition (main thread) and from signal collectors on Main as well; the
        // director is not touched off the main thread, so a plain section is enough.
        block()
        _scene.value = compute()
    }

    private fun compute(): MascotScene {
        val held = holds.values.maxWithOrNull(compareBy<Cue> { it.priority }.thenBy { it.seq })
        val shot = oneShot
        val winner = when {
            shot == null -> held
            held == null -> shot
            held.priority >= MascotPriority.High && held.priority > shot.priority -> held
            else -> shot
        }
        return if (winner == null) {
            MascotScene(idleMoment, cueId = 0)
        } else {
            MascotScene(winner.moment, winner.anchor, oneShot = winner === shot, cueId = winner.seq)
        }
    }
}
