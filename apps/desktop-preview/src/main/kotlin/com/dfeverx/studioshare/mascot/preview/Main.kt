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
import com.dfeverx.studioshare.mascot.pack.LoadedPack
import com.dfeverx.studioshare.mascot.pack.MascotPack
import com.dfeverx.studioshare.mascot.render.AtlasCache
import com.dfeverx.studioshare.mascot.render.MascotSprite
import com.dfeverx.studioshare.mascot.rig.MascotFigure
import com.dfeverx.studioshare.mascot.rig.RigColors
import com.dfeverx.studioshare.mascot.rig.RigPose
import com.dfeverx.studioshare.mascot.rig.drawMascot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

fun main() = application {
    val agent = remember { MascotAgent() }
    val notifier = remember { DesktopMascotNotifier(agent) }
    var showCompanionWindow by remember { mutableStateOf(false) }

    val manifestFile = remember {
        listOf(
            File("pipeline/dist/manifest.json"),
            File("../../pipeline/dist/manifest.json")
        ).firstOrNull { it.exists() }
    }
    val distDir = remember(manifestFile) { manifestFile?.parentFile }
    val pack = remember(manifestFile) {
        manifestFile?.let { runCatching { MascotPack.parse(it.readBytes()) }.getOrNull() }
    }
    val loadedPack = remember(pack, distDir) {
        if (pack != null && distDir != null) {
            LoadedPack(pack) { path ->
                File(distDir, path).takeIf { it.exists() }?.readBytes()
            }
        } else null
    }
    val atlasCache = remember { AtlasCache(capacity = 32) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "StudioShare Mascot Studio — Standalone Preview & Agent Testbench"
    ) {
        PreviewScreen(
            agent = agent,
            loadedPack = loadedPack,
            atlasCache = atlasCache,
            showCompanionWindow = showCompanionWindow,
            onToggleCompanionWindow = { showCompanionWindow = !showCompanionWindow }
        )
    }

    MascotMiniCompanionWindow(
        agent = agent,
        visible = showCompanionWindow,
        onClose = { showCompanionWindow = false },
        onOpenMainApp = { /* Focus main window */ },
        loadedPack = loadedPack,
        cache = atlasCache
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewScreen(
    agent: MascotAgent,
    loadedPack: LoadedPack?,
    atlasCache: AtlasCache,
    showCompanionWindow: Boolean,
    onToggleCompanionWindow: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val agentState by agent.state.collectAsState()
    val pack = loadedPack?.pack

    var selectedMood by remember { mutableStateOf("idle") }
    var selectedMoment by remember { mutableStateOf("upload.done") }
    var isMomentMode by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(true) }
    var isAnimating by remember { mutableStateOf(true) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var simulatedProgress by remember { mutableFloatStateOf(0f) }
    var isSimulatingUpload by remember { mutableStateOf(false) }

    val allMoods = remember(pack) {
        pack?.moods?.keys?.sorted() ?: listOf(
            "idle", "walk", "happy", "uploading", "careful", "celebrating", "proud", "thinking",
            "searching", "scanning", "sleepy", "excited", "curious", "focused", "locked",
            "oops", "sad", "shy", "patient", "waiting", "hot", "goodbye"
        )
    }
    val allMoments = remember(pack) { pack?.moments?.keys?.sorted() ?: emptyList() }

    val resolved = remember(pack, isMomentMode, selectedMood, selectedMoment) {
        if (isMomentMode) {
            pack?.resolve(selectedMoment) ?: MascotPack.Resolved(
                momentKey = selectedMoment,
                moodName = "idle",
                mood = MascotPack.Mood(),
                artName = "idle",
                art = null,
                first = null
            )
        } else {
            pack?.resolveMood(selectedMood) ?: MascotPack.Resolved(
                momentKey = null,
                moodName = selectedMood,
                mood = MascotPack.Mood(),
                artName = selectedMood,
                art = null,
                first = null
            )
        }
    }

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
                        if (loadedPack != null && resolved.art != null) {
                            MascotSprite(
                                resolved = resolved,
                                loaded = loadedPack,
                                dark = isDarkTheme,
                                cache = atlasCache,
                                loop = isLooping,
                                animate = isAnimating,
                                walking = !isMomentMode && selectedMood == "walk",
                                modifier = Modifier.size(240.dp)
                            )
                        } else {
                            MascotFigure(
                                resolved = resolved,
                                colors = if (isDarkTheme) RigColors.Light else RigColors.Dark,
                                oneShot = !isLooping,
                                walking = !isMomentMode && selectedMood == "walk",
                                mirrored = false,
                                animate = isAnimating,
                                modifier = Modifier.size(240.dp)
                            )
                        }

                        // Badge showing current mood / moment & frame animation info
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF282830).copy(alpha = 0.85f))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            val framesCount = resolved.art?.frames ?: 0
                            val fps = resolved.art?.fps ?: resolved.mood.fps
                            val label = if (isMomentMode) "Moment: $selectedMoment" else "Mood: $selectedMood"
                            val modeStr = if (isAnimating) {
                                if (isLooping) "Looping" else "One-shot"
                            } else "Paused"
                            Text(
                                text = "$label • $framesCount frames @ ${fps}fps ($modeStr)",
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
                            Button(
                                onClick = { isLooping = !isLooping },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLooping) Color(0xFF33333D) else Color(0xFF4CAF50)
                                )
                            ) {
                                Text(if (isLooping) "Mode: Loop" else "Mode: One-Shot", fontSize = 11.sp)
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
                                            isAnimating = true
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
                            agent.postAlert(
                                id = "alert_booking",
                                title = "New Shoot Booked!",
                                message = "Sarah requested Sunset Beach Session.",
                                mood = "celebrating"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Trigger Booking Alert")
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

                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.compose.material3.HorizontalDivider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Pack Stats
                    Text("Pack Metadata", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    if (pack != null) {
                        Text("Version: v${pack.version}", fontSize = 11.sp, color = Color.Gray)
                        Text("Hash: ${pack.hash}", fontSize = 11.sp, color = Color.Gray)
                        Text("Moods: ${pack.moods.size}", fontSize = 11.sp, color = Color.Gray)
                        Text("Atlases: ${pack.assets.size}", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        Text("No dist pack found (run npm run pack)", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
