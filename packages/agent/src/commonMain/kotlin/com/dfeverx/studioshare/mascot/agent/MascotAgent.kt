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
    /** Alerts that stay until dismissed ([moment] with `persistent`), oldest first. */
    private val _pinned = MutableStateFlow<Map<String, AgentAlert>>(emptyMap())
    private val _state = MutableStateFlow<MascotAgentState>(MascotAgentState.Idle())
    val state: StateFlow<MascotAgentState> = _state.asStateFlow()

    /**
     * Every task underway, whatever [state] is showing: a notification can be up while an upload
     * runs, and the notch still shows the upload's status beside it.
     */
    val tasks: StateFlow<List<AgentTask>> get() = _taskList.asStateFlow()
    private val _taskList = MutableStateFlow<List<AgentTask>>(emptyList())

    private val notificationListeners = mutableListOf<(AgentAlert) -> Unit>()
    private val warningListeners = mutableListOf<(AgentWarning) -> Unit>()

    /**
     * Reports progress on a background task (e.g., photo uploading, AI face culling, export).
     *
     * @param parts what it is doing at once (an upload's folders), listed under the bar when opened.
     */
    fun reportProgress(
        taskId: String,
        title: String,
        detail: String = "",
        progress: Float = 0f,
        mood: String = "uploading",
        parts: List<TaskPart> = emptyList(),
    ) {
        val task = AgentTask(
            id = taskId,
            title = title,
            detail = detail,
            progress = progress,
            mood = mood,
            parts = parts,
        )
        val updated = _tasks.value + (taskId to task)
        _tasks.value = updated
        _taskList.value = updated.values.toList()
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
        _taskList.value = updated.values.toList()
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
        _taskList.value = _tasks.value.values.toList()
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
     * Stays active in the agent for [durationMs] (by default, long enough to read it) then reverts to previous state.
     */
    fun postAlert(
        id: String,
        title: String,
        message: String,
        mood: String = "celebrating",
        durationMs: Long? = null,
    ) {
        val alert = AgentAlert(id = id, title = title, message = message, mood = mood)
        show(alert, durationMs ?: readingTime(alert))
    }

    /** The last message shown, and until when a click can bring it back ([recall]). */
    private var recent: AgentAlert? = null
    private var recentUntil = 0L

    private fun show(alert: AgentAlert, durationMs: Long, notify: Boolean = true) {
        val id = alert.id
        if (notify && !alert.quiet) notificationListeners.forEach { it(alert) }
        if (!alert.quiet) {
            recent = alert
            recentUntil = System.currentTimeMillis() + durationMs + RECALL_MS
        }
        // held for its time over everything, so a task's next progress tick doesn't take it down
        showing = alert
        recomputeState()
        scope.launch {
            delay(durationMs)
            // Revert back if no newer alert superseded it
            if (showing?.id == id) {
                showing = null
                recomputeState()
            }
        }
    }

    /** The transient alert up now, until its time is out or it is closed. */
    private var showing: AgentAlert? = null

    /**
     * Something just happened in the app — a [com.dfeverx.studioshare.mascot.MascotMoments] key. The
     * face takes the moment's mood, and if the moment has something to say (its spec `say`, or
     * [message]) the notch opens with it: e.g. `moment(MascotMoments.AuthSignedIn, "Welcome back, Priya!")`.
     * A moment with nothing to say only changes the face for a moment.
     *
     * @param detail a second, smaller line under the message.
     * @param durationMs how long it shows; by default long enough to read it. Ignored when [persistent].
     * @param persistent stays until the user dismisses it (or [dismissAlert] with its [id]), so it is
     *   never missed — an inbox notification. The notch keeps a mark for it while its card is closed.
     * @param id the alert's id, to take a persistent one down later; by default a fresh one.
     */
    fun moment(
        key: String,
        message: String? = null,
        detail: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        firstAlreadySeen: Boolean = false,
        durationMs: Long? = null,
        persistent: Boolean = false,
        id: String? = null,
    ) {
        val text = message ?: MascotMomentMoods.moment(key)?.say
        val alert = AgentAlert(
            id = id ?: "$key@${System.nanoTime()}",
            title = text.orEmpty(),
            message = detail.orEmpty(),
            mood = MascotMomentMoods.moodFor(key, firstAlreadySeen),
            label = MascotMomentMoods.areaFor(key),
            actionLabel = actionLabel,
            onAction = onAction,
            quiet = text == null,
            hands = MascotMomentMoods.handsFor(key, firstAlreadySeen),
            persistent = persistent && text != null,
        )
        if (alert.persistent) pin(alert)
        else show(alert, durationMs ?: if (alert.quiet) 1_800L else readingTime(alert))
    }

    private fun pin(alert: AgentAlert) {
        // a re-sent notification moves to the end, so it is the one showing
        _pinned.value = _pinned.value - alert.id + (alert.id to alert)
        notificationListeners.forEach { it(alert) }
        recomputeState()
    }

    /**
     * Closes the alert [id]: the one showing now (an OK button on its card), or a persistent one
     * wherever it is in the queue (its Dismiss button, or the app saying it has been read).
     */
    fun dismissAlert(id: String) {
        if (id in _pinned.value) {
            _pinned.value = _pinned.value - id
            val current = _state.value
            // a transient alert showing over it keeps its time; it falls back to what is left
            if (current !is MascotAgentState.Alert || current.alert.persistent) recomputeState()
            return
        }
        if (showing?.id == id) {
            showing = null
            recomputeState()
        }
    }

    /**
     * Shows the last message again, if it went less than [RECALL_MS] ago — a click on the notch to
     * see what just happened ("which upload finished?"). A newer message replaces it. False when
     * there is none to bring back.
     */
    fun recall(): Boolean {
        val alert = recent ?: return false
        if (System.currentTimeMillis() > recentUntil) {
            recent = null
            return false
        }
        val current = _state.value
        if (current is MascotAgentState.Alert && current.alert.id == alert.id) return true
        show(alert, readingTime(alert), notify = false)
        return true
    }

    /** Takes down every persistent alert at once (signing out: they were the last user's). */
    fun dismissAllAlerts() {
        if (_pinned.value.isEmpty()) return
        _pinned.value = emptyMap()
        val current = _state.value
        if (current !is MascotAgentState.Alert || current.alert.persistent) recomputeState()
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
        // Priority order: the transient alert up now > Warnings > Persistent alerts (newest first) > Active Tasks > Idle
        showing?.let {
            _state.value = MascotAgentState.Alert(it)
            return
        }
        val warningsList = _warnings.value.values.toList()
        if (warningsList.isNotEmpty()) {
            _state.value = MascotAgentState.Warning(
                activeWarning = warningsList.first(),
                allWarnings = warningsList
            )
            return
        }

        val pinned = _pinned.value.values.toList()
        if (pinned.isNotEmpty()) {
            _state.value = MascotAgentState.Alert(pinned.last(), pending = pinned.size)
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

        /** After a message goes, a click on the notch brings it back for this long ([recall]). */
        const val RECALL_MS = 15_000L

        /** Default global singleton instance for quick access across features. */
        val default: MascotAgent by lazy { MascotAgent() }
    }
}
