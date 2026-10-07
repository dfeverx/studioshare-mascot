package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import com.dfeverx.studioshare.mascot.agent.MascotAgentState
import com.dfeverx.studioshare.mascot.face.StudioFace
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

internal val COMPACT = DpSize(240.dp, 40.dp)
internal val EXPANDED = DpSize(380.dp, 128.dp)
internal val INK = Color(0xFF000000)
private val LINE = Color.White.copy(alpha = 0.14f)
private val TEXT = Color.White
private val MUTED = Color.White.copy(alpha = 0.62f)

/**
 * A notch-style companion: the StudioShare face in a small black bar at the top centre of the
 * screen, just under the menu bar (under the notch on a MacBook). It reads [MascotAgent.state] and
 * nothing else — the same feed the mini HUD uses — so every upload, warning and milestone the app
 * already reports shows up here.
 *
 * Compact, it is the face and one line of status. It opens into a card on hover, for a warning
 * (which waits for the user) and for an alert (which closes itself when the agent drops it).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StudioFaceNotchCompanion(
    agent: MascotAgent,
    visible: Boolean,
    onClose: () -> Unit,
    onOpenMainApp: () -> Unit,
) {
    if (!visible) return
    val state by agent.state.collectAsState()
    var hovered by remember { mutableStateOf(false) }
    val expanded = hovered || state is MascotAgentState.Warning || state is MascotAgentState.Alert
    val target = if (expanded) EXPANDED else COMPACT

    val windowState = rememberWindowState(size = COMPACT, position = topCentre(COMPACT.width))
    LaunchedEffect(target) { resizeAroundCentre(windowState, target) }

    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = "StudioShare",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        focusable = false,
    ) {
        WindowDraggableArea {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(INK, RectangleShape)
                    .border(1.dp, LINE, RectangleShape)
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false },
            ) {
                if (expanded) Expanded(agent, state, onOpenMainApp) else Compact(state, onOpenMainApp)
            }
        }
    }
}

@Composable
internal fun Compact(state: MascotAgentState, onOpenMainApp: () -> Unit) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Face(state, 30.dp, onOpenMainApp)
        Spacer(Modifier.width(8.dp))
        Text(
            compactLine(state), color = TEXT, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        val w = state as? MascotAgentState.Working
        if (w != null) {
            Spacer(Modifier.width(8.dp))
            Text(w.activeTask.percentText, color = MUTED, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun Expanded(agent: MascotAgent, state: MascotAgentState, onOpenMainApp: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Face(state, 76.dp, onOpenMainApp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            when (state) {
                is MascotAgentState.Working -> {
                    val task = state.activeTask
                    Title(task.title)
                    if (task.detail.isNotBlank()) Body(task.detail, lines = 1)
                    Spacer(Modifier.height(8.dp))
                    if (task.isIndeterminate) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().height(3.dp), color = TEXT, trackColor = LINE,
                            strokeCap = StrokeCap.Butt, gapSize = 0.dp,
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(LINE)) {
                            Box(
                                Modifier.fillMaxWidth(task.progress.coerceIn(0f, 1f)).fillMaxHeight()
                                    .background(TEXT),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val queued = state.queuedTasks.size
                    Body(task.percentText + if (queued > 0) "  ·  $queued more queued" else "", lines = 1)
                }
                is MascotAgentState.Warning -> {
                    val warning = state.activeWarning
                    Title(warning.title)
                    Body(warning.message, lines = 2)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        val action = warning.onAction
                        if (warning.actionLabel != null && action != null) {
                            SquareButton(warning.actionLabel, filled = true) {
                                action()
                                agent.dismissWarning(warning.id)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        SquareButton("Dismiss", filled = false) { agent.dismissWarning(warning.id) }
                    }
                }
                is MascotAgentState.Alert -> {
                    Title(state.alert.title)
                    Body(state.alert.message, lines = 2)
                }
                is MascotAgentState.Idle -> {
                    Title("StudioShare")
                    Body("All quiet. I'll tell you when something happens.", lines = 2)
                }
            }
        }
    }
}

@Composable
private fun Face(state: MascotAgentState, size: Dp, onOpenMainApp: () -> Unit) {
    val progress = (state as? MascotAgentState.Working)?.activeTask
        ?.takeUnless { it.isIndeterminate }?.progress
    StudioFace(
        mood = state.currentMood,
        progress = progress,
        modifier = Modifier.size(size).clickable(onClick = onOpenMainApp),
    )
}

@Composable
private fun Title(text: String) = Text(
    text, color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Body(text: String, lines: Int) = Text(
    text, color = MUTED, fontSize = 11.sp, maxLines = lines, overflow = TextOverflow.Ellipsis,
)

@Composable
private fun SquareButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (filled) TEXT else INK, RectangleShape)
            .border(1.dp, if (filled) TEXT else LINE, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = if (filled) INK else TEXT, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

internal fun compactLine(state: MascotAgentState): String = when (state) {
    is MascotAgentState.Working -> state.activeTask.title
    is MascotAgentState.Warning ->
        if (state.allWarnings.size > 1) "${state.allWarnings.size} things need you" else state.activeWarning.title
    is MascotAgentState.Alert -> state.alert.title
    is MascotAgentState.Idle -> "StudioShare"
}

/** Top centre of the main screen, just under the menu bar (which on a MacBook includes the notch). */
private fun topCentre(width: Dp): WindowPosition {
    val gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val bounds = gc.bounds
    val top = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(gc).top }.getOrDefault(0)
    return WindowPosition(
        x = (bounds.x + (bounds.width - width.value) / 2).dp,
        y = (bounds.y + top + 4).dp,
    )
}

/** Resizes in place about the window's horizontal centre, so it grows out of where it sits. */
private fun resizeAroundCentre(state: WindowState, size: DpSize) {
    val pos = state.position
    if (pos is WindowPosition.Absolute) {
        val centre = pos.x + state.size.width / 2
        state.position = WindowPosition(centre - size.width / 2, pos.y)
    }
    state.size = size
}
