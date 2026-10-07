package com.dfeverx.studioshare.mascot.agent

import com.dfeverx.studioshare.mascot.face.HandGesture
import kotlinx.serialization.Serializable

/**
 * An ongoing background task reported to the mascot agent (e.g. uploading photos, culling, exporting).
 */
data class AgentTask(
    val id: String,
    val title: String,
    val detail: String = "",
    val progress: Float = 0f, // 0.0 to 1.0 (or negative for indeterminate)
    val mood: String = "uploading",
    val timestamp: Long = System.currentTimeMillis()
) {
    val isIndeterminate: Boolean get() = progress < 0f
    val percentText: String get() = if (isIndeterminate) "..." else "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
}

/**
 * A system warning or attention item that requires user awareness.
 */
data class AgentWarning(
    val id: String,
    val title: String,
    val message: String,
    val mood: String = "careful", // e.g. careful, disconnected, hot, oops
    val timestamp: Long = System.currentTimeMillis(),
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    /** Where it comes from, shown small above the message (e.g. "Storage"); null shows none. */
    val label: String? = null,
)

/**
 * A one-shot notification or milestone alert (e.g. booking received, cull finished, review posted).
 */
data class AgentAlert(
    val id: String,
    val title: String,
    val message: String,
    val mood: String = "celebrating", // e.g. celebrating, proud, excited, ok
    val timestamp: Long = System.currentTimeMillis(),
    /** Where it comes from, shown small above the title (e.g. "Upload"); null shows none. */
    val label: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    /** Only changes the face's mood; the notch stays shut (a moment with nothing to say). */
    val quiet: Boolean = false,
    /** A gesture the face makes while it shows (a moment's spec `hands`); null keeps its hands away. */
    val hands: HandGesture? = null,
)

/**
 * Reactive state of the Mascot Background Agent.
 */
sealed interface MascotAgentState {
    val currentMood: String

    /** The mascot is idle/resting; no active background jobs or urgent warnings. */
    data class Idle(
        override val currentMood: String = "idle"
    ) : MascotAgentState

    /** The mascot is actively monitoring one or more background tasks. */
    data class Working(
        val activeTask: AgentTask,
        val queuedTasks: List<AgentTask> = emptyList(),
        override val currentMood: String = activeTask.mood
    ) : MascotAgentState

    /** The mascot is sounding a warning or issue that needs photographer attention. */
    data class Warning(
        val activeWarning: AgentWarning,
        val allWarnings: List<AgentWarning> = listOf(activeWarning),
        override val currentMood: String = activeWarning.mood
    ) : MascotAgentState

    /** A transient milestone or incoming alert being surfaced to the user. */
    data class Alert(
        val alert: AgentAlert,
        override val currentMood: String = alert.mood
    ) : MascotAgentState
}
