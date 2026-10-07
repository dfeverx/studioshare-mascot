package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import com.dfeverx.studioshare.mascot.agent.MascotAgentState

/**
 * Compose Desktop Menu Bar / System Tray companion for the mascot agent.
 * Reflects live task progress, warning states, and quick actions directly from the tray.
 */
@Composable
fun ApplicationScope.MascotTrayCompanion(
    agent: MascotAgent,
    icon: Painter,
    onOpenApp: () -> Unit,
    onToggleCompanionWindow: (() -> Unit)? = null,
    onExitApp: () -> Unit
) {
    val trayState = rememberTrayState()
    val state by agent.state.collectAsState()

    val tooltipText = when (val s = state) {
        is MascotAgentState.Idle -> "StudioShare • Ready"
        is MascotAgentState.Working -> "StudioShare • ${s.activeTask.title}: ${s.activeTask.percentText}"
        is MascotAgentState.Warning -> "⚠️ StudioShare • ${s.activeWarning.title}"
        is MascotAgentState.Alert -> "✨ StudioShare • ${s.alert.title}"
    }

    Tray(
        icon = icon,
        state = trayState,
        tooltip = tooltipText,
        onAction = onOpenApp,
        menu = {
            Item("Open StudioShare", onClick = onOpenApp)

            when (val s = state) {
                is MascotAgentState.Working -> {
                    Separator()
                    Item("${s.activeTask.title} (${s.activeTask.percentText})", enabled = false, onClick = {})
                    if (s.activeTask.detail.isNotBlank()) {
                        Item(s.activeTask.detail, enabled = false, onClick = {})
                    }
                    Item("Cancel Task", onClick = { agent.cancelTask(s.activeTask.id) })
                }
                is MascotAgentState.Warning -> {
                    Separator()
                    Item("⚠️ ${s.activeWarning.title}", enabled = false, onClick = {})
                    Item(s.activeWarning.message, enabled = false, onClick = {})
                    if (s.activeWarning.actionLabel != null && s.activeWarning.onAction != null) {
                        Item(s.activeWarning.actionLabel, onClick = {
                            s.activeWarning.onAction.invoke()
                            agent.dismissWarning(s.activeWarning.id)
                        })
                    }
                    Item("Dismiss Warning", onClick = { agent.dismissWarning(s.activeWarning.id) })
                }
                is MascotAgentState.Alert -> {
                    Separator()
                    Item("✨ ${s.alert.title}", enabled = false, onClick = {})
                    Item(s.alert.message, enabled = false, onClick = {})
                }
                is MascotAgentState.Idle -> {
                    // Nothing extra needed
                }
            }

            if (onToggleCompanionWindow != null) {
                Separator()
                Item("Toggle Floating Mascot", onClick = onToggleCompanionWindow)
            }

            Separator()
            Item("Quit", onClick = onExitApp)
        }
    )
}
