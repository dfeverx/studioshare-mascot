package com.dfeverx.studioshare.mascot

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.dfeverx.studioshare.mascot.face.FaceParams
import com.dfeverx.studioshare.mascot.face.StudioFaceExpressions
import com.dfeverx.studioshare.mascot.face.drawStudioFace
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Renders every mood of the StudioShare face to `build/studio-face-preview/moods.png` — the way to
 * look at the face without running the app.
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
}
