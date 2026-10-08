package com.dfeverx.studioshare.mascot.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import com.dfeverx.studioshare.mascot.agent.MascotAgentState
import com.dfeverx.studioshare.mascot.agent.desktop.DesktopMascotNotifier
import com.dfeverx.studioshare.mascot.agent.desktop.MascotMiniCompanionWindow
import com.dfeverx.studioshare.mascot.agent.desktop.StudioFaceNotchCompanion
import com.dfeverx.studioshare.mascot.face.StudioFace
import com.dfeverx.studioshare.mascot.MascotMomentMoods
import com.dfeverx.studioshare.mascot.MascotMoments
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main() = application {
    val agent = remember { MascotAgent() }
    val notifier = remember { DesktopMascotNotifier(agent) }
    var showCompanionWindow by remember { mutableStateOf(false) }
    var showNotchCompanion by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "StudioShare Mascot Studio — Standalone Preview & Agent Testbench"
    ) {
        PreviewScreen(
            agent = agent,
            showCompanionWindow = showCompanionWindow,
            onToggleCompanionWindow = { showCompanionWindow = !showCompanionWindow },
            showNotchCompanion = showNotchCompanion,
            onToggleNotchCompanion = { showNotchCompanion = !showNotchCompanion }
        )
    }

    MascotMiniCompanionWindow(
        agent = agent,
        visible = showCompanionWindow,
        onClose = { showCompanionWindow = false },
        onOpenMainApp = { /* Focus main window */ }
    )

    StudioFaceNotchCompanion(
        agent = agent,
        visible = showNotchCompanion,
        onClose = { showNotchCompanion = false },
        onOpenMainApp = { /* Focus main window */ }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewScreen(
    agent: MascotAgent,
    showCompanionWindow: Boolean,
    onToggleCompanionWindow: () -> Unit,
    showNotchCompanion: Boolean = false,
    onToggleNotchCompanion: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val agentState by agent.state.collectAsState()

    var selectedMood by remember { mutableStateOf("idle") }
    var selectedMoment by remember { mutableStateOf("upload.done") }
    /** Bumped on every moment click, so clicking one again replays its gesture. */
    var momentPlays by remember { mutableStateOf(0) }
    var isMomentMode by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(true) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var simulatedProgress by remember { mutableFloatStateOf(0f) }
    var isSimulatingUpload by remember { mutableStateOf(false) }

    val allMoods = remember { MascotMomentMoods.moods.sorted() }
    val allMoments = remember { MascotMoments.all }
    val shownMood = if (isMomentMode) MascotMomentMoods.moodFor(selectedMoment) else selectedMood

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkTheme) Color(0xFF121214) else Color(0xFFF7F7F8)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Left Panel: Interactive Character Preview Canvas
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1A1A1E) else Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Character Stage",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isDarkTheme) "Dark UI" else "Light UI",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Button(
                                onClick = { isDarkTheme = !isDarkTheme },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) Color(0xFF33333D) else Color(0xFFE5E5EA)
                                )
                            ) {
                                Text(
                                    if (isDarkTheme) "Switch to Light" else "Switch to Dark",
                                    color = if (isDarkTheme) Color.White else Color.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Center Mascot Viewport
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDarkTheme) Color(0xFF141417) else Color(0xFFEBEBF0)),
                        contentAlignment = Alignment.Center
                    ) {
                        StudioFace(
                            mood = shownMood,
                            animate = isAnimating,
                            progress = if (isSimulatingUpload) simulatedProgress else null,
                            hands = if (isMomentMode) MascotMomentMoods.handsFor(selectedMoment) else null,
                            handsId = momentPlays,
                            modifier = Modifier.size(240.dp)
                        )

                        // Badge showing current mood / moment & frame animation info
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF282830).copy(alpha = 0.85f))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            val gesture = MascotMomentMoods.handsFor(selectedMoment)?.let { " + ${it.id}" }.orEmpty()
                            val label = if (isMomentMode) "Moment: $selectedMoment → $shownMood$gesture" else "Mood: $selectedMood"
                            val motion = MascotMomentMoods.motionOf(shownMood)
                            Text(
                                text = "$label • motion: $motion" + if (isAnimating) "" else " (paused)",
                                color = Color(0xFFFF5288),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animation Controls Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !isMomentMode,
                                onClick = { isMomentMode = false },
                                label = { Text("Moods (${allMoods.size})", fontSize = 12.sp) }
                            )
                            if (allMoments.isNotEmpty()) {
                                FilterChip(
                                    selected = isMomentMode,
                                    onClick = { isMomentMode = true },
                                    label = { Text("Moments (${allMoments.size})", fontSize = 12.sp) }
                                )
                            }

                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { isAnimating = !isAnimating },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAnimating) Color(0xFF33333D) else Color(0xFFFF5288)
                                )
                            ) {
                                Text(if (isAnimating) "Pause" else "Play", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset chips (Scrollable FlowRow)
                    Box(modifier = Modifier.weight(0.7f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!isMomentMode) {
                                allMoods.forEach { mood ->
                                    FilterChip(
                                        selected = selectedMood == mood,
                                        onClick = {
                                            selectedMood = mood
                                            isAnimating = true
                                        },
                                        label = { Text(mood, fontSize = 11.sp) }
                                    )
                                }
                            } else {
                                allMoments.forEach { moment ->
                                    FilterChip(
                                        selected = selectedMoment == moment,
                                        onClick = {
                                            selectedMoment = moment
                                            momentPlays++
                                            isAnimating = true
                                            // the notch companion says it, if the moment has a line
                                            agent.moment(moment)
                                        },
                                        label = { Text(moment, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right Panel: Mascot Agent Simulator & Testbench
            Card(
                modifier = Modifier.width(360.dp).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1A1A1E) else Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Background Agent",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                    Text(
                        text = "Simulate events running in the background",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Agent Status Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkTheme) Color(0xFF222228) else Color(0xFFF0F0F5))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Agent State", fontSize = 11.sp, color = Color.Gray)
                            val stateName = when (agentState) {
                                is MascotAgentState.Idle -> "Idle (Standing by)"
                                is MascotAgentState.Working -> "Working: ${(agentState as MascotAgentState.Working).activeTask.title}"
                                is MascotAgentState.Warning -> "Warning: ${(agentState as MascotAgentState.Warning).activeWarning.title}"
                                is MascotAgentState.Alert -> "Alert: ${(agentState as MascotAgentState.Alert).alert.title}"
                            }
                            Text(
                                text = stateName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Test Action 1: Background Upload Simulation
                    Text("1. Background Tasks", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (!isSimulatingUpload) {
                                isSimulatingUpload = true
                                isMomentMode = false
                                selectedMood = "uploading"
                                isAnimating = true
                                scope.launch {
                                    for (i in 0..100 step 10) {
                                        simulatedProgress = i / 100f
                                        agent.reportProgress(
                                            taskId = "sim_upload",
                                            title = "Uploading Wedding Gallery",
                                            detail = "$i of 100 photos synced",
                                            progress = simulatedProgress,
                                            mood = "uploading"
                                        )
                                        delay(400)
                                    }
                                    agent.completeTask(
                                        taskId = "sim_upload",
                                        celebrationTitle = "Upload Completed!",
                                        celebrationMessage = "100 photos safely backed up"
                                    )
                                    selectedMood = "celebrating"
                                    isSimulatingUpload = false
                                }
                            }
                        },
                        enabled = !isSimulatingUpload,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5288))
                    ) {
                        Text(if (isSimulatingUpload) "Simulating Upload..." else "Simulate Background Upload")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Test Action 2: System Warning
                    Text("2. System Warnings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isMomentMode = false
                            selectedMood = "careful"
                            isAnimating = true
                            agent.postWarning(
                                id = "warn_disk",
                                label = "Storage",
                                title = "Low Storage Space",
                                message = "Less than 1.5 GB remaining on local disk.",
                                mood = "careful",
                                actionLabel = "Clean Cache",
                                onAction = { println("Clean cache triggered!") }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Post Storage Warning")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { agent.dismissWarning("warn_disk") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dismiss Storage Warning")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Test Action 3: Notification Milestone
                    Text("3. Notifications", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isMomentMode = false
                            selectedMood = "celebrating"
                            isAnimating = true
                            agent.moment(
                                MascotMoments.OrbitNewBooking,
                                detail = "Sarah requested a Sunset Beach session.",
                                actionLabel = "Open",
                                onAction = { println("Open booking") }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Trigger Booking Alert")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { agent.moment(MascotMoments.AuthSignedIn, "Welcome back, Nithin!") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Signed In")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { agent.moment(MascotMoments.UploadDone) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload Done")
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Test Action 4: Floating Mini Companion HUD
                    Text("4. Desktop Surfaces", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onToggleCompanionWindow,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                    ) {
                        Text(if (showCompanionWindow) "Hide Floating HUD" else "Show Floating HUD")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onToggleNotchCompanion,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF000000))
                    ) {
                        Text(if (showNotchCompanion) "Hide Notch Companion" else "Show Notch Companion")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.compose.material3.HorizontalDivider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Character stats
                    Text("Character", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("Moods: ${allMoods.size}", fontSize = 11.sp, color = Color.Gray)
                    Text("Moments: ${allMoments.size}", fontSize = 11.sp, color = Color.Gray)
                    Text("Drawn in code — no art pack", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}
