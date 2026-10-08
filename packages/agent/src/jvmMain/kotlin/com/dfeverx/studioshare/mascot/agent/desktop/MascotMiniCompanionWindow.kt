package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import com.dfeverx.studioshare.mascot.agent.MascotAgentState
import com.dfeverx.studioshare.mascot.face.StudioFace

/**
 * A floating, always-on-top desktop mini companion (HUD widget).
 * Floats near the corner of the user's screen when the main StudioShare app is minimized,
 * showing live progress, warnings, and milestone alerts.
 */
@Composable
fun MascotMiniCompanionWindow(
    agent: MascotAgent,
    visible: Boolean,
    onClose: () -> Unit,
    onOpenMainApp: () -> Unit
) {
    if (!visible) return

    val windowState = rememberWindowState(
        size = DpSize(320.dp, 160.dp),
        position = WindowPosition(Alignment.BottomEnd)
    )

    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = "StudioShare Mascot Companion",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false
    ) {
        val state by agent.state.collectAsState()

        Surface(
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
            color = Color(0xFF1E1E24).copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The StudioShare face
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141418)),
                    contentAlignment = Alignment.Center
                ) {
                    StudioFace(
                        mood = state.currentMood,
                        progress = (state as? MascotAgentState.Working)?.activeTask
                            ?.takeUnless { it.isIndeterminate }?.progress,
                        modifier = Modifier.size(72.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Speech Bubble / Status Content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    when (val s = state) {
                        is MascotAgentState.Working -> {
                            Text(
                                text = s.activeTask.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (s.activeTask.detail.isNotBlank()) {
                                Text(
                                    text = s.activeTask.detail,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (s.activeTask.isIndeterminate) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Color(0xFFFF5288),
                                    trackColor = Color.DarkGray
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { s.activeTask.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Color(0xFFFF5288),
                                    trackColor = Color.DarkGray
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = s.activeTask.percentText,
                                color = Color(0xFFFF85AA),
                                fontSize = 10.sp
                            )
                        }

                        is MascotAgentState.Warning -> {
                            Text(
                                text = "⚠️ " + s.activeWarning.title,
                                color = Color(0xFFFFB74D),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = s.activeWarning.message,
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row {
                                if (s.activeWarning.actionLabel != null && s.activeWarning.onAction != null) {
                                    Button(
                                        onClick = {
                                            s.activeWarning.onAction.invoke()
                                            agent.dismissWarning(s.activeWarning.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text(s.activeWarning.actionLabel, color = Color.Black, fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Button(
                                    onClick = { agent.dismissWarning(s.activeWarning.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Dismiss", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }

                        is MascotAgentState.Alert -> {
                            Text(
                                text = "✨ " + s.alert.title,
                                color = Color(0xFF69F0AE),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = s.alert.message,
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        is MascotAgentState.Idle -> {
                            Text(
                                text = "StudioShare Agent",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Standing by. All systems running smoothly.",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
