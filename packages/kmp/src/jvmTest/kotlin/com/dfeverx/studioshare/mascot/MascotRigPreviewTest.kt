package com.dfeverx.studioshare.mascot

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.dfeverx.studioshare.mascot.pack.MascotPack
import com.dfeverx.studioshare.mascot.rig.RigColors
import com.dfeverx.studioshare.mascot.rig.RigPose
import com.dfeverx.studioshare.mascot.rig.drawMascot
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Renders every mood of the bundled pack, on a light and a dark background, to
 * `build/mascot-preview/` as PNGs — the way to look at a pack change without running the app.
 */
class MascotRigPreviewTest {
    @Test fun renderEveryMood() {
        val manifestFile = listOf(
            File("../../pipeline/dist/manifest.json"),
            File("../pipeline/dist/manifest.json"),
            File("pipeline/dist/manifest.json")
        ).firstOrNull { it.exists() } ?: File("../../pipeline/dist/manifest.json")
        val pack = MascotPack.parse(manifestFile.readBytes())!!
        val out = File("build/mascot-preview").apply { mkdirs() }
        val cellW = 200
        val cellH = 280
        val moods = pack.moods.keys.sorted()
        for ((name, bg, colors) in listOf(
            Triple("light", Color(0xFFF4F4F6), RigColors.Dark),
            Triple("dark", Color(0xFF151517), RigColors.Light),
        )) {
            val cols = 6
            val rows = (moods.size + cols - 1) / cols
            val bitmap = ImageBitmap(cols * cellW, rows * cellH)
            val canvas = Canvas(bitmap)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(cols * cellW.toFloat(), rows * cellH.toFloat())) {
                drawRect(bg)
            }
            moods.forEachIndexed { i, moodName ->
                val m = pack.moods.getValue(moodName)
                val pose = RigPose(face = m.face, arms = m.arms, prop = m.prop, walking = moodName == "walk")
                canvas.save()
                canvas.translate((i % cols) * cellW.toFloat(), (i / cols) * cellH.toFloat())
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(cellW.toFloat(), cellH - 20f)) {
                    drawMascot(pose, colors, t = 0.4f)
                }
                canvas.restore()
            }
            val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "moods-$name.png").writeBytes(png)
        }
        File(out, "order.txt").writeText(moods.joinToString("\n"))
    }
}
