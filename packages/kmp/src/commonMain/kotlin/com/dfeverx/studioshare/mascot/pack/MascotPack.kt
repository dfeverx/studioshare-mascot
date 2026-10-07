package com.dfeverx.studioshare.mascot.pack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The mascot pack's `manifest.json`, built by `tools/mascot/pack.mjs` from `Brand/mascot/mascot.json`.
 *
 * Three layers, so each can change without the others: **moments** (what is happening in the app,
 * named by code) map to **moods** (how the mascot feels and moves), and each mood points at a frame
 * **atlas** per theme. Unknown keys are ignored, so a newer pack never breaks an older app.
 */
@Serializable
data class MascotPack(
    val schema: Int,
    /** UTC yyyymmddHHMM of the build; a newer pack always compares greater. */
    val version: Long,
    val hash: String = "",
    val frame: Frame,
    val moods: Map<String, Mood>,
    /** Moment key → that moment's own rendered clip (the doc's action), drawn instead of its mood's art. */
    val clips: Map<String, Mood> = emptyMap(),
    val moments: Map<String, Moment>,
    /** Atlas path → sha256 hex. */
    val assets: Map<String, String> = emptyMap(),
) {
    @Serializable
    data class Frame(val w: Int, val h: Int)

    @Serializable
    data class Mood(
        val code: String = "",
        val fps: Int = 12,
        /** One of [MascotMotion]'s ids; an unknown one plays as `none`. */
        val motion: String = "none",
        /** Mood whose art is borrowed while this one has none of its own. */
        val fallback: String? = null,
        val frames: Int = 0,
        val cols: Int = 1,
        val loopFrom: Int = 0,
        /** Frame shown when motion is reduced. */
        val still: Int = 0,
        /** Theme (`light` | `dark`) → atlas path. Optional: without one the mood is drawn by the rig. */
        val atlas: Map<String, String> = emptyMap(),
        /** Rig face (see `rig/MascotRig.kt`): smile, happy, wink, closed, sleepy, surprised, worried, sad, focused, thinking, cool, scan. */
        val face: String = "smile",
        /** Rig arms: down, wave, cheer, thumbs, carry, camera, chin, shrug, cover, point, hips, stop. */
        val arms: String = "down",
        /** What it holds or shows: none, box, cloud, laptop, magnifier, question, exclaim, zzz, sparks, confetti, heart, plug, unplug, key, sweat, hourglass, check, phone, envelope, star, camera. */
        val prop: String = "none",
    ) {
        val hasArt: Boolean get() = frames > 0 && atlas.isNotEmpty()
    }

    @Serializable
    data class Moment(
        val mood: String,
        /** Documents that the moment lasts while its state lasts (code uses `hold`, not `play`). */
        val hold: Boolean = false,
        /** Once-per-account celebration key; repeats play [MOOD_OK] instead. */
        val first: String? = null,
    )

    /**
     * What the mascot shows for [momentKey]: the moment's mood (for motion and timing) and the mood
     * whose art is drawn — itself, or the first mood down its fallback chain that has art.
     * Never fails: an unknown moment or mood resolves to [MOOD_IDLE].
     */
    fun resolve(momentKey: String, firstAlreadySeen: Boolean = false): Resolved {
        val moment = moments[momentKey]
        val moodName = when {
            moment == null -> MOOD_IDLE
            moment.first != null && firstAlreadySeen && moods.containsKey(MOOD_OK) -> MOOD_OK
            moods.containsKey(moment.mood) -> moment.mood
            else -> MOOD_IDLE
        }
        val clip = clips[momentKey]?.takeIf { it.hasArt && moodName == moment?.mood }
        val resolved = resolveMood(moodName).copy(momentKey = momentKey, first = moment?.first)
        return if (clip == null) resolved else resolved.copy(artName = "$CLIP_PREFIX$momentKey", art = clip, artIsClip = true)
    }

    fun resolveMood(moodName: String): Resolved {
        val name = if (moods.containsKey(moodName)) moodName else MOOD_IDLE
        val mood = moods[name] ?: Mood()
        var artName = name
        val seen = mutableSetOf(name)
        while (moods[artName]?.hasArt != true) {
            val next = moods[artName]?.fallback
            if (next == null || !seen.add(next)) {
                artName = MOOD_IDLE
                break
            }
            artName = next
        }
        return Resolved(
            momentKey = null,
            moodName = name,
            mood = mood,
            artName = artName,
            art = moods[artName]?.takeIf { it.hasArt },
            first = null,
        )
    }

    data class Resolved(
        val momentKey: String?,
        val moodName: String,
        val mood: Mood,
        val artName: String,
        /** Null only for a pack with no idle art at all; draw nothing then. */
        val art: Mood?,
        val first: String?,
        /** The art is the moment's own clip, whose motion is baked into its frames. */
        val artIsClip: Boolean = false,
    ) {
        fun atlasPath(dark: Boolean): String? =
            art?.atlas?.get(if (dark) THEME_DARK else THEME_LIGHT) ?: art?.atlas?.values?.firstOrNull()
    }

    companion object {
        const val SUPPORTED_SCHEMA = 1
        const val MOOD_IDLE = "idle"
        const val MOOD_OK = "ok"
        const val MOOD_WALK = "walk"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val CLIP_PREFIX = "@"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Null for anything unusable: bad JSON, a schema this build doesn't know, or no idle mood. */
        fun parse(bytes: ByteArray): MascotPack? = runCatching {
            json.decodeFromString(serializer(), bytes.decodeToString())
        }.getOrNull()?.takeIf { it.schema == SUPPORTED_SCHEMA && it.moods.containsKey(MOOD_IDLE) }
    }
}
