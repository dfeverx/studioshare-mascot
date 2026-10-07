package com.dfeverx.studioshare.mascot

/** One row of `spec/mascot.json`'s moments: the mood a moment shows. */
data class MascotMomentDef(
    val mood: String,
    /** Documents that the moment lasts while its state lasts (code uses `hold`, not `play`). */
    val hold: Boolean = false,
    /** Once-per-account celebration key; repeats show [MascotMomentMoods.MOOD_OK] instead. */
    val first: String? = null,
    /** The default line a one-off moment says in the notch; null keeps it to a change of face. */
    val say: String? = null,
)

/**
 * Moments → moods, baked in at build time from `spec/mascot.json` (see `tools/moments.mjs`).
 * There is no pack to download: the face draws every mood in code.
 */
object MascotMomentMoods {
    const val MOOD_IDLE = "idle"
    const val MOOD_OK = "ok"
    const val MOOD_WALK = "walk"

    val moods: Set<String> get() = MASCOT_MOOD_MOTIONS.keys

    fun moment(momentKey: String?): MascotMomentDef? = momentKey?.let { MASCOT_MOMENTS[it] }

    /** The mood [momentKey] shows. Never fails: an unknown moment reads as [MOOD_IDLE]. */
    fun moodFor(momentKey: String?, firstAlreadySeen: Boolean = false): String {
        val m = moment(momentKey) ?: return MOOD_IDLE
        return when {
            m.first != null && firstAlreadySeen -> MOOD_OK
            m.mood in MASCOT_MOOD_MOTIONS -> m.mood
            else -> MOOD_IDLE
        }
    }

    /** The label a notification header shows for [momentKey]'s area, e.g. `upload.done` → "Upload". */
    fun areaFor(momentKey: String): String = MASCOT_AREAS[momentKey.substringBefore('.')] ?: "StudioShare"

    /** A mood's body motion (a `MascotMotion` id); `none` for an unknown mood. */
    fun motionOf(mood: String): String = MASCOT_MOOD_MOTIONS[mood] ?: "none"
}
