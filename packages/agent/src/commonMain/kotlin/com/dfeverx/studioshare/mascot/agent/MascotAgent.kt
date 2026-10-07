package com.dfeverx.studioshare.mascot.agent

import com.dfeverx.studioshare.mascot.MascotMomentMoods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Mascot Agent: an event-driven companion controller that monitors background tasks,
 * system warnings, and notifications across Desktop (Tray + Floating Companion), Mobile, and Web.
 */
class MascotAgent(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _tasks = MutableStateFlow<Map<String, AgentTask>>(emptyMap())
    private val _warnings = MutableStateFlow<Map<String, AgentWarning>>(emptyMap())
    private val _state = MutableStateFlow<MascotAgentState>(MascotAgentState.Idle())
    val state: StateFlow<MascotAgentState> = _state.asStateFlow()

    private val notificationListeners = mutableListOf<(AgentAlert) -> Unit>()
    private val warningListeners = mutableListOf<(AgentWarning) -> Unit>()

    /**
     * Reports progress on a background task (e.g., photo uploading, AI face culling, export).
     */
    fun reportProgress(
        taskId: String,
        title: String,
        detail: String = "",
        progress: Float = 0f,
        mood: String = "uploading"
    ) {
        val task = AgentTask(
            id = taskId,
            title = title,
            detail = detail,
            progress = progress,
            mood = mood
        )
        val updated = _tasks.value + (taskId to task)
        _tasks.value = updated
        recomputeState()
    }

    /**
     * Marks a background task as complete, optionally triggering a success alert.
     */
    fun completeTask(
        taskId: String,
        celebrationTitle: String? = null,
        celebrationMessage: String? = null,
        celebrationMood: String = "celebrating"
    ) {
        val updated = _tasks.value - taskId
        _tasks.value = updated
        if (celebrationTitle != null) {
            postAlert(
                id = "done_$taskId",
                title = celebrationTitle,
                message = celebrationMessage ?: "Task completed successfully",
                mood = celebrationMood
            )
        } else {
            recomputeState()
        }
    }

    /**
     * Removes a task without triggering an alert.
     */
    fun cancelTask(taskId: String) {
        _tasks.value = _tasks.value - taskId
        recomputeState()
    }

    /**
     * Posts a warning that persists until dismissed or resolved.
     */
    fun postWarning(
        id: String,
        title: String,
        message: String,
        mood: String = "careful",
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        label: String? = null,
    ) {
        val warning = AgentWarning(
            id = id,
            title = title,
            message = message,
            mood = mood,
            actionLabel = actionLabel,
            onAction = onAction,
            label = label,
        )
        _warnings.value = _warnings.value + (id to warning)
        warningListeners.forEach { it(warning) }
        recomputeState()
    }

    /**
     * Dismisses an active warning.
     */
    fun dismissWarning(id: String) {
        _warnings.value = _warnings.value - id
        recomputeState()
    }

    /**
     * Posts a transient alert / milestone (e.g. "Booking received!").
     * Stays active in the agent for [durationMs] then reverts to previous state.
     */
    fun postAlert(
        id: String,
        title: String,
        message: String,
        mood: String = "celebrating",
        durationMs: Long = 4500L
    ) {
        show(AgentAlert(id = id, title = title, message = message, mood = mood), durationMs)
    }

    private fun show(alert: AgentAlert, durationMs: Long) {
        val id = alert.id
        if (!alert.quiet) notificationListeners.forEach { it(alert) }
        _state.value = MascotAgentState.Alert(alert)
        scope.launch {
            delay(durationMs)
            // Revert back if no newer alert superseded it
            val current = _state.value
            if (current is MascotAgentState.Alert && current.alert.id == id) {
                recomputeState()
            }
        }
    }

    /**
     * Something just happened in the app — a [com.dfeverx.studioshare.mascot.MascotMoments] key. The
     * face takes the moment's mood, and if the moment has something to say (its spec `say`, or
     * [message]) the notch opens with it: e.g. `moment(MascotMoments.AuthSignedIn, "Welcome back, Priya!")`.
     * A moment with nothing to say only changes the face for a moment.
     *
     * @param detail a second, smaller line under the message.
     * @param durationMs how long it shows; by default long enough to read it.
     */
    fun moment(
        key: String,
        message: String? = null,
        detail: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        firstAlreadySeen: Boolean = false,
        durationMs: Long? = null,
    ) {
        val text = message ?: MascotMomentMoods.moment(key)?.say
        val alert = AgentAlert(
            id = "$key@${System.nanoTime()}",
            title = text.orEmpty(),
            message = detail.orEmpty(),
            mood = MascotMomentMoods.moodFor(key, firstAlreadySeen),
            label = MascotMomentMoods.areaFor(key),
            actionLabel = actionLabel,
            onAction = onAction,
            quiet = text == null,
        )
        show(alert, durationMs ?: if (alert.quiet) 1_800L else readingTime(alert))
    }

    /** Closes the alert showing now, if it is [id] (an OK button on its card). */
    fun dismissAlert(id: String) {
        val current = _state.value
        if (current is MascotAgentState.Alert && current.alert.id == id) recomputeState()
    }

    /** Registers a listener for system notifications. */
    fun onAlert(listener: (AgentAlert) -> Unit) {
        notificationListeners.add(listener)
    }

    /** Registers a listener for system warnings. */
    fun onWarning(listener: (AgentWarning) -> Unit) {
        warningListeners.add(listener)
    }

    private fun recomputeState() {
        // Priority order: Warnings > Active Tasks > Idle
        val warningsList = _warnings.value.values.toList()
        if (warningsList.isNotEmpty()) {
            _state.value = MascotAgentState.Warning(
                activeWarning = warningsList.first(),
                allWarnings = warningsList
            )
            return
        }

        val tasksList = _tasks.value.values.toList()
        if (tasksList.isNotEmpty()) {
            _state.value = MascotAgentState.Working(
                activeTask = tasksList.first(),
                queuedTasks = tasksList.drop(1)
            )
            return
        }

        _state.value = MascotAgentState.Idle()
    }

    companion object {
        /** A comfortable time to read [alert]: 2.5 s plus ~45 ms a character, 3–7 s. */
        internal fun readingTime(alert: AgentAlert): Long =
            (2_500L + 45L * (alert.title.length + alert.message.length)).coerceIn(3_000L, 7_000L)

        /** Default global singleton instance for quick access across features. */
        val default: MascotAgent by lazy { MascotAgent() }
    }
}
