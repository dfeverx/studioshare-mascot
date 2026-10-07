package com.dfeverx.studioshare.mascot.director

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small string key-value storage for the mascot's preferences (the app backs it with kv_table). */
interface MascotPrefsStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String?)
}

/** Where the mascot rests: the left or right edge, and how far down it (0 = top, 1 = bottom). */
data class MascotDock(val end: Boolean = true, val yFraction: Float = 1f) {
    fun encode(): String = "${if (end) "e" else "s"}:$yFraction"

    companion object {
        fun decode(raw: String?): MascotDock? {
            val parts = raw?.split(':')?.takeIf { it.size == 2 } ?: return null
            val y = parts[1].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
            return MascotDock(end = parts[0] != "s", yFraction = y)
        }
    }
}

/**
 * The mascot's preferences: on/off (Profile → Appearance), its dock, "hide for this session", and
 * which once-per-account celebrations have played. Process-wide state, loaded once and written
 * through [MascotPrefsStore]; reads are synchronous so the stage never waits on storage.
 */
class MascotSettings(private val store: MascotPrefsStore) {
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _dock = MutableStateFlow(MascotDock())
    val dock: StateFlow<MascotDock> = _dock.asStateFlow()

    /** "Hide for now" from the mascot's own menu: until the app restarts, not persisted. */
    private val _hiddenForSession = MutableStateFlow(false)
    val hiddenForSession: StateFlow<Boolean> = _hiddenForSession.asStateFlow()

    /** False until [load] has read storage, so a mascot switched off never flashes on at launch. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val firsts = mutableSetOf<String>()
    private var loaded = false

    suspend fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            _enabled.value = store.get(KEY_OFF) != "1"
            MascotDock.decode(store.get(KEY_DOCK))?.let { _dock.value = it }
            store.get(KEY_FIRSTS)?.split(',')?.filter { it.isNotBlank() }?.let(firsts::addAll)
        }
        _ready.value = true
    }

    suspend fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (value) _hiddenForSession.value = false
        runCatching { store.put(KEY_OFF, if (value) null else "1") }
    }

    suspend fun setDock(dock: MascotDock) {
        _dock.value = dock
        runCatching { store.put(KEY_DOCK, dock.encode()) }
    }

    fun hideForSession() {
        _hiddenForSession.value = true
    }

    fun hasCelebrated(key: String): Boolean = key in firsts

    suspend fun markCelebrated(key: String) {
        if (!firsts.add(key)) return
        runCatching { store.put(KEY_FIRSTS, firsts.joinToString(",")) }
    }

    companion object {
        const val KEY_OFF = "mascot_off"
        const val KEY_DOCK = "mascot_dock"
        const val KEY_FIRSTS = "mascot_firsts"
        const val KEY_CHECKED_AT = "mascot_checked_at"
    }
}
