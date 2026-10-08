package com.dfeverx.studioshare.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.dfeverx.studioshare.mascot.face.FaceParams
import com.dfeverx.studioshare.mascot.face.HandGesture
import com.dfeverx.studioshare.mascot.face.handPose
import com.dfeverx.studioshare.mascot.face.handsOut
import com.dfeverx.studioshare.mascot.face.StudioFaceExpressions
import com.dfeverx.studioshare.mascot.face.drawStudioFace
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.dfeverx.studioshare.mascot.face.gazeToward

/**
 * Renders every mood of the StudioShare face to `build/studio-face-preview/moods.png`, and the idle
 * face looking at each point of the compass to `gaze.png` — the way to look at the face without
 * running the app. `hands.png` is each hand gesture (a row) through its life (columns, left to right).
 */
class StudioFacePreviewTest {

    @Test fun renderEveryMood() {
        val out = File("build/studio-face-preview").apply { mkdirs() }
        val cell = 220
        val moods = MascotMomentMoods.moods.sorted()
        val cols = 6
        val rows = (moods.size + cols - 1) / cols
        val bitmap = ImageBitmap(cols * cell, rows * cell)
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(cols * cell.toFloat(), rows * cell.toFloat())) {
            drawRect(Color(0xFF1C1C1E))
        }
        moods.forEachIndexed { i, mood ->
            val e = StudioFaceExpressions.forMood(mood)
            canvas.save()
            canvas.translate((i % cols) * cell.toFloat(), (i / cols) * cell.toFloat())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(cell.toFloat(), cell.toFloat())) {
                drawStudioFace(FaceParams.of(e), "none", t = 1.2f, motionT = 0f, accent = e.accent, progress = 0.45f)
            }
            canvas.restore()
        }
        val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, "moods.png").writeBytes(png)
        File(out, "order.txt").writeText(moods.joinToString("\n"))
    }

    @Test fun renderGaze() {
        val out = File("build/studio-face-preview").apply { mkdirs() }
        val cell = 220
        // a 3×3 grid: each face looks toward its own cell, the centre one straight ahead
        val bitmap = ImageBitmap(3 * cell, 3 * cell)
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(3f * cell, 3f * cell)) {
            drawRect(Color(0xFF1C1C1E))
        }
        val e = StudioFaceExpressions.idle
        for (row in 0..2) for (col in 0..2) {
            canvas.save()
            canvas.translate(col * cell.toFloat(), row * cell.toFloat())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(cell.toFloat(), cell.toFloat())) {
                drawStudioFace(
                    FaceParams.of(e), "none", t = 1.2f, motionT = 0f, accent = null,
                    gaze = Offset(col - 1f, row - 1f),
                )
            }
            canvas.restore()
        }
        val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, "gaze.png").writeBytes(png)
    }

    @Test fun renderHands() {
        val out = File("build/studio-face-preview").apply { mkdirs() }
        val cell = 200
        val steps = 6
        // the mood each gesture is used with most
        val gestures = listOf(
            HandGesture.Wave to "happy", HandGesture.Cheer to "celebrating", HandGesture.TaDa to "proud",
            HandGesture.Shrug to "thinking", HandGesture.Shy to "shy",
        )
        val bitmap = ImageBitmap(steps * cell, gestures.size * cell)
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(steps * cell.toFloat(), gestures.size * cell.toFloat())) {
            drawRect(Color(0xFF000000))
        }
        gestures.forEachIndexed { row, (g, mood) ->
            val e = StudioFaceExpressions.forMood(mood)
            for (col in 0 until steps) {
                val t = 0.12f + col * (g.seconds - 0.24f) / (steps - 1)
                canvas.save()
                // the face at half the cell, so its hands have room and stay out of the next one
                canvas.translate(col * cell + cell / 4f, row * cell + cell / 4f)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(cell / 2f, cell / 2f)) {
                    drawStudioFace(FaceParams.of(e), "none", t = 1.2f, motionT = 0f, accent = null, hands = g, handsT = t)
                }
                canvas.restore()
            }
        }
        val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, "hands.png").writeBytes(png)
    }

    @Test fun handsComeOutForTheGestureOnly() {
        for (g in HandGesture.entries) {
            assertEquals(0f, handsOut(g, -0.01f), "$g before")
            assertEquals(0f, handsOut(g, g.seconds + 0.01f), "$g after")
            assertEquals(1f, handsOut(g, g.seconds / 2), "$g midway")
            assertEquals(g, HandGesture.of(g.id))
            // the hands stay near the body: never more than a hand's width past its sides or above it
            var t = 0f
            while (t < g.seconds) {
                for (side in intArrayOf(-1, 1)) {
                    val p = handPose(g, side, t)
                    assertTrue(kotlin.math.abs(p.x) <= 0.8f && p.y >= -0.5f && p.y <= 0.45f, "$g $side at $t: $p")
                }
                t += 0.05f
            }
        }
        assertEquals(null, HandGesture.of("clap"))
    }

    @Test fun gazeTurnsEachAxisOnItsOwn() {
        val g = gazeToward(300f, 600f)
        // far below and to the side still turns hard sideways (the axes don't share a distance)
        assertTrue(g.x > 0.8f && g.y > 0.99f)
        assertEquals(0f, gazeToward(0f, 0f).x)
        assertTrue(gazeToward(-5000f, -5000f).let { it.x >= -1f && it.y >= -1f })
    }
}
