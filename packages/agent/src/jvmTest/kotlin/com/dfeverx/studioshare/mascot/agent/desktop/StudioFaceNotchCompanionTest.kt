package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.dfeverx.studioshare.mascot.agent.AgentAlert
import com.dfeverx.studioshare.mascot.agent.AgentTask
import com.dfeverx.studioshare.mascot.agent.AgentWarning
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import com.dfeverx.studioshare.mascot.agent.MascotAgentState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.unit.DpSize

/**
 * The notch companion's status line, plus a render of each bar state to
 * `build/studio-face-notch/states.png` for eyeballing without opening a window.
 */
class StudioFaceNotchCompanionTest {
    private val upload = MascotAgentState.Working(AgentTask("u", "Uploading Wedding — Ava & Sam", "128 of 300 photos", 0.43f))
    private val warning = MascotAgentState.Warning(
        AgentWarning("w", "Storage almost full", "92% of your studio's space is used.", actionLabel = "Manage", onAction = {}),
    )
    private val alert = MascotAgentState.Alert(AgentAlert("a", "New booking!", "Priya booked a portrait session for Sat 14 Nov."))

    @Test fun compactLineSummarisesEachState() {
        assertEquals("StudioShare", compactLine(MascotAgentState.Idle()))
        assertEquals("Uploading Wedding — Ava & Sam", compactLine(upload))
        assertEquals("Storage almost full", compactLine(warning))
        val two = warning.copy(allWarnings = listOf(warning.activeWarning, warning.activeWarning.copy(id = "w2")))
        assertEquals("2 things need you", compactLine(two))
        assertEquals("New booking!", compactLine(alert))
    }

    @Test fun earBadgeIsShort() {
        assertEquals(null, earBadge(MascotAgentState.Idle()))
        assertEquals("43%", earBadge(upload))
        assertEquals("!", earBadge(warning))
    }

    @Test fun islandSpansTheNotchWithAnEarEachSide() {
        val notched = NotchGeometry(0, 0, 1512, 185.dp, 38.dp)
        assertEquals(DpSize(185.dp + EAR * 2, 38.dp), compactSize(notched))
        assertEquals(38.dp + EXPANDED.height, expandedSize(notched).height)
        // the window is fixed and holds the open island, so opening never resizes it
        assertTrue(stageSize(notched).width > expandedSize(notched).width)
        assertTrue(stageSize(notched).height > expandedSize(notched).height)
        val flat = notched.copy(notchWidth = 0.dp, bandHeight = 30.dp)
        assertEquals(DpSize(COMPACT.width, 30.dp), compactSize(flat))
    }

    @Test fun faceRestsInTheLeftEarAndMovesToTheCard() {
        val notched = NotchGeometry(0, 0, 1512, 185.dp, 38.dp)
        val rest = facePlacement(notched, expanded = false)
        assertEquals(EAR / 2, rest.centre.x)
        assertTrue(rest.size < notched.bandHeight)
        val open = facePlacement(notched, expanded = true)
        assertTrue(open.centre.y > notched.bandHeight && open.size > rest.size)
    }

    @Test fun renderStates() {
        val agent = MascotAgent()
        val w = EXPANDED.width.value.toInt()
        val bars: List<Pair<Int, @Composable () -> Unit>> = listOf(
            COMPACT.height.value.toInt() to { Compact(MascotAgentState.Idle(), onOpenMainApp = {}) },
            COMPACT.height.value.toInt() to { Compact(upload, onOpenMainApp = {}) },
            38 to { NotchBand(upload, 185.dp, onOpenMainApp = {}) },
            EXPANDED.height.value.toInt() to { Expanded(agent, upload, onOpenMainApp = {}) },
            EXPANDED.height.value.toInt() to { Expanded(agent, warning, onOpenMainApp = {}) },
            EXPANDED.height.value.toInt() to { Expanded(agent, alert, onOpenMainApp = {}) },
        )
        val gap = 12
        val h = bars.sumOf { it.first + gap }
        ImageComposeScene(w * 2, h * 2, Density(2f)) {
            Column(Modifier.background(Color(0xFF2C2C30))) {
                bars.forEach { (bh, content) ->
                    val bw = when (bh) {
                        COMPACT.height.value.toInt() -> COMPACT.width
                        38 -> 185.dp + EAR * 2
                        else -> EXPANDED.width
                    }
                    Box(Modifier.size(bw, bh.dp).background(INK)) { content() }
                    Spacer(Modifier.height(gap.dp))
                }
            }
        }.use { scene ->
            val image = scene.render(400_000_000L)
            val out = File("build/studio-face-notch").apply { mkdirs() }
            File(out, "states.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
