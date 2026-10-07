package com.dfeverx.studioshare.mascot.rig

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The mascot, drawn in code: the hooded figure with the cap, the glowing pink→orange face and the
 * white sneakers. Full body always. Everything that changes with the mood — face, arms, the thing it
 * holds, walking legs — is a parameter, so one drawing acts out every mood with no images to load.
 *
 * Coordinates are a 100 × 140 box, feet on the bottom edge; [drawMascot] scales it to the canvas.
 * Shading is gradients (no blur, no layers), so a frame is a few dozen paths — cheap everywhere.
 */
data class RigPose(
    val face: String = "smile",
    val arms: String = "down",
    val prop: String = "none",
    val walking: Boolean = false,
    /** Flip to face left (the walk direction). */
    val mirrored: Boolean = false,
)

data class RigColors(
    val outfit: Color,
    val outfitLight: Color,
    val outfitDark: Color,
    val rim: Color?,
) {
    companion object {
        /** On light UI: the black outfit of the renders. */
        val Dark = RigColors(Color(0xFF26262A), Color(0xFF3A3A40), Color(0xFF141416), rim = null)

        /** On dark UI: the white outfit, so it never sinks into the background. */
        val Light = RigColors(Color(0xFFE9E9EC), Color(0xFFFFFFFF), Color(0xFFBDBDC4), rim = null)
    }
}

const val RIG_W = 100f
const val RIG_H = 140f

private val Visor = Color(0xFF060607)
private val GlowTop = Color(0xFFFF2E93)
private val GlowBottom = Color(0xFFFF8A3D)
private val Accent = Color(0xFFFFC21A)
private val Sneaker = Color(0xFFF4F4F2)
private val Ink = Color(0xFF1A1A1C)

/**
 * Draws the mascot for [pose] at time [t] seconds (blinks, waves and the walk cycle read it; a still
 * frame passes a fixed t). [blink] 0..1 closes the eyes.
 */
fun DrawScope.drawMascot(pose: RigPose, colors: RigColors, t: Float, blink: Float = 0f) {
    val s = min(size.width / RIG_W, size.height / RIG_H)
    val ox = (size.width - RIG_W * s) / 2
    val oy = size.height - RIG_H * s
    translate(ox, oy) {
        scale(s, s, pivot = Offset.Zero) {
            val flip = if (pose.mirrored) -1f else 1f
            scale(flip, 1f, pivot = Offset(RIG_W / 2, 0f)) {
                drawFigure(pose, colors, t, blink)
            }
        }
    }
}

private fun DrawScope.drawFigure(pose: RigPose, c: RigColors, t: Float, blink: Float) {
    // ground shadow
    drawOval(Color.Black.copy(alpha = 0.18f), topLeft = Offset(24f, 133f), size = Size(52f, 7f))

    val swing = if (pose.walking) sin(t * 2f * PI.toFloat() / 0.5f) else 0f
    drawLeg(c, hipX = 41f, swing = swing)
    drawLeg(c, hipX = 59f, swing = -swing)

    val arms = armAngles(pose.arms, t, swing)
    // the arm behind the body is drawn first when it hangs down, in front when it is raised
    drawBody(c)
    drawArm(c, shoulder = Offset(30f, 73f), side = -1f, a = arms.left)
    drawArm(c, shoulder = Offset(70f, 73f), side = 1f, a = arms.right, thumb = pose.arms == "thumbs")
    drawHead(c, pose.face, t, blink)
    drawProp(pose.prop, t)
    // hands over the face (shy, camera) sit above the head
    if (pose.arms == "cover" || pose.arms == "camera" || pose.arms == "chin") {
        drawArm(c, shoulder = Offset(30f, 73f), side = -1f, a = arms.left, handOnly = pose.arms == "chin")
        drawArm(c, shoulder = Offset(70f, 73f), side = 1f, a = arms.right)
        if (pose.prop == "camera") drawCamera(Offset(50f, 47f), 1f)
    }
}

private fun DrawScope.drawLeg(c: RigColors, hipX: Float, swing: Float) {
    rotate(degrees = swing * 22f, pivot = Offset(hipX, 104f)) {
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(c.outfitLight, c.outfit, c.outfitDark), startX = hipX - 7, endX = hipX + 7),
            topLeft = Offset(hipX - 7f, 100f),
            size = Size(14f, 26f),
            cornerRadius = CornerRadius(6f),
        )
        // sneaker: white upper with a soft grey edge, yellow sole
        val toe = if (hipX < 50) -3f else 3f
        val shoe = Offset(hipX - 10.5f + toe, 121f)
        drawRoundRect(Color(0xFF9EA0A6), topLeft = shoe - Offset(0.8f, 0.8f), size = Size(22.6f, 14.6f), cornerRadius = CornerRadius(7f))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White, Sneaker, Color(0xFFD9D9DC)), startY = 121f, endY = 132f),
            topLeft = shoe, size = Size(21f, 12f), cornerRadius = CornerRadius(6f),
        )
        drawRoundRect(Accent, topLeft = Offset(shoe.x, 130.5f), size = Size(21f, 4f), cornerRadius = CornerRadius(2f))
        drawLine(Color(0xFFB9B9BE), Offset(shoe.x + 6f, 125f), Offset(shoe.x + 14f, 125f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawBody(c: RigColors) {
    val body = Path().apply {
        moveTo(33f, 66f)
        cubicTo(26f, 70f, 24f, 88f, 26f, 106f)
        quadraticTo(50f, 112f, 74f, 106f)
        cubicTo(76f, 88f, 74f, 70f, 67f, 66f)
        close()
    }
    drawPath(body, Brush.radialGradient(listOf(c.outfitLight, c.outfit, c.outfitDark), center = Offset(42f, 76f), radius = 46f))
    // pocket and drawstrings
    drawArc(c.outfitDark, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(36f, 90f), size = Size(28f, 14f), style = Stroke(1.4f, cap = StrokeCap.Round))
    drawLine(c.outfitDark, Offset(46f, 68f), Offset(45f, 82f), strokeWidth = 1.3f, cap = StrokeCap.Round)
    drawLine(c.outfitDark, Offset(54f, 68f), Offset(55f, 82f), strokeWidth = 1.3f, cap = StrokeCap.Round)
}

private data class Arm(val shoulder: Float, val elbow: Float)
private data class Arms(val left: Arm, val right: Arm)

/** Shoulder and elbow angles in degrees from hanging straight down; positive swings outward/up. */
private fun armAngles(name: String, t: Float, walkSwing: Float): Arms {
    val wave = 22f * sin(t * 2f * PI.toFloat() / 0.6f)
    return when (name) {
        "wave" -> Arms(Arm(12f, 0f), Arm(150f, 20f + wave))
        "cheer" -> Arms(Arm(155f, 10f + wave * 0.3f), Arm(155f, 10f - wave * 0.3f))
        "thumbs" -> Arms(Arm(10f, 0f), Arm(48f, 105f))
        "carry" -> Arms(Arm(18f, -78f), Arm(18f, -78f))
        "camera" -> Arms(Arm(38f, -140f), Arm(38f, -140f))
        "chin" -> Arms(Arm(10f, 0f), Arm(30f, -150f))
        "shrug" -> Arms(Arm(62f, 70f), Arm(62f, 70f))
        "cover" -> Arms(Arm(34f, -150f), Arm(34f, -150f))
        "point" -> Arms(Arm(10f, 0f), Arm(92f, 0f))
        "hips" -> Arms(Arm(34f, -70f), Arm(34f, -70f))
        "stop" -> Arms(Arm(45f, -95f), Arm(45f, -95f))
        else -> Arms(Arm(10f + walkSwing * 18f, 0f), Arm(10f - walkSwing * 18f, 0f))
    }
}

private fun DrawScope.drawArm(c: RigColors, shoulder: Offset, side: Float, a: Arm, handOnly: Boolean = false, thumb: Boolean = false) {
    val upper = 17f
    val lower = 15f
    val sa = a.shoulder * side
    val elbow = shoulder + polar(upper, sa)
    val hand = elbow + polar(lower, sa + a.elbow * side)
    if (!handOnly) {
        drawLine(c.outfitDark, shoulder, elbow, strokeWidth = 11f, cap = StrokeCap.Round)
        drawLine(c.outfit, shoulder, elbow, strokeWidth = 9f, cap = StrokeCap.Round)
        drawLine(c.outfitDark, elbow, hand, strokeWidth = 10f, cap = StrokeCap.Round)
        drawLine(c.outfit, elbow, hand, strokeWidth = 8f, cap = StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(listOf(c.outfitLight, c.outfit), center = hand + Offset(-1.5f, -1.5f), radius = 7f), radius = 6.5f, center = hand)
    if (thumb) {
        drawLine(c.outfitDark, hand + Offset(-1f, -3f), hand + Offset(-1f, -10f), strokeWidth = 5.5f, cap = StrokeCap.Round)
        drawLine(c.outfit, hand + Offset(-1f, -3f), hand + Offset(-1f, -10f), strokeWidth = 4f, cap = StrokeCap.Round)
    }
}

/** Point at [len] along an angle measured from straight down, positive towards +x. */
private fun polar(len: Float, deg: Float): Offset {
    val r = deg * PI.toFloat() / 180f
    return Offset(sin(r) * len, cos(r) * len)
}

private fun DrawScope.drawHead(c: RigColors, face: String, t: Float, blink: Float) {
    val center = Offset(50f, 40f)
    // hood
    drawCircle(Brush.radialGradient(listOf(c.outfitLight, c.outfit, c.outfitDark), center = center + Offset(-9f, -10f), radius = 40f), radius = 30f, center = center)
    // cap: dome and brim
    val dome = Path().apply {
        addArc(Rect(center = Offset(50f, 30f), radius = 29f), 190f, 160f)
        close()
    }
    drawPath(dome, Brush.verticalGradient(listOf(c.outfitLight, c.outfit), startY = 2f, endY = 30f))
    val brim = Path().apply {
        moveTo(24f, 22f)
        cubicTo(36f, 15f, 70f, 14f, 86f, 22f)
        cubicTo(88f, 25f, 80f, 28f, 72f, 26f)
        cubicTo(58f, 23f, 38f, 24f, 26f, 27f)
        close()
    }
    drawPath(brim, c.outfitDark)
    drawPath(brim, Brush.verticalGradient(listOf(c.outfitLight, c.outfit), startY = 14f, endY = 26f), alpha = 0.85f)
    drawCircle(c.outfitDark, 1.6f, Offset(50f, 3.5f))
    // visor
    drawOval(Visor, topLeft = Offset(28f, 25f), size = Size(44f, 37f))
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), center = Offset(40f, 31f), radius = 16f),
        topLeft = Offset(28f, 25f), size = Size(44f, 37f),
    )
    drawFace(face, t, blink)
}

private val glow = Brush.verticalGradient(listOf(GlowTop, GlowBottom), startY = 32f, endY = 56f)

private fun DrawScope.glowStroke(path: Path, width: Float) {
    drawPath(path, glow, alpha = 0.22f, style = Stroke(width * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, glow, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.glowFill(rect: Rect) {
    drawRoundRect(glow, topLeft = rect.topLeft - Offset(1.4f, 1.4f), size = Size(rect.width + 2.8f, rect.height + 2.8f), cornerRadius = CornerRadius(rect.width), alpha = 0.22f)
    drawRoundRect(glow, topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(rect.width / 2))
}

private fun line(a: Offset, b: Offset, c: Offset? = null, d: Offset? = null) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    c?.let { lineTo(it.x, it.y) }
    d?.let { lineTo(it.x, it.y) }
}

private fun arc(from: Offset, to: Offset, depth: Float) = Path().apply {
    moveTo(from.x, from.y)
    quadraticTo((from.x + to.x) / 2, (from.y + to.y) / 2 + depth, to.x, to.y)
}

private fun DrawScope.drawFace(face: String, t: Float, blink: Float) {
    val le = Offset(41f, 40f)
    val re = Offset(59f, 40f)
    fun pill(at: Offset, h: Float = 8f) {
        val hh = max(1.6f, h * (1f - blink))
        glowFill(Rect(at.x - 2.6f, at.y - hh / 2, at.x + 2.6f, at.y + hh / 2))
    }
    fun happyEye(at: Offset) = glowStroke(arc(at + Offset(-4f, 1.5f), at + Offset(4f, 1.5f), -6f), 2.6f)
    fun closedEye(at: Offset) = glowStroke(arc(at + Offset(-4f, -0.5f), at + Offset(4f, -0.5f), 4f), 2.4f)
    fun smile(depth: Float = 7f, w: Float = 11f) = glowStroke(arc(Offset(50f - w, 49f), Offset(50f + w, 49f), depth), 3.4f)

    when (face) {
        "happy" -> { happyEye(le); happyEye(re); smile(9f, 12f) }
        "wink" -> { pill(le); glowStroke(line(re + Offset(-4f, -2f), re + Offset(3f, 0.5f), re + Offset(-4f, 3f)), 2.4f); smile() }
        "closed", "sleepy" -> { closedEye(le); closedEye(re); glowStroke(arc(Offset(45f, 51f), Offset(55f, 51f), 3f), 3f) }
        "surprised" -> {
            pill(le, 9f); pill(re, 9f)
            drawOval(glow, topLeft = Offset(46.5f, 47f), size = Size(7f, 8f))
        }
        "worried" -> {
            glowStroke(line(le + Offset(-4f, -2.5f), le + Offset(3f, -5f)), 2f)
            glowStroke(line(re + Offset(4f, -2.5f), re + Offset(-3f, -5f)), 2f)
            pill(le, 6f); pill(re, 6f)
            glowStroke(line(Offset(44f, 52f), Offset(56f, 51f)), 3f)
        }
        "sad" -> { pill(le, 5f); pill(re, 5f); glowStroke(arc(Offset(42f, 54f), Offset(58f, 54f), -5f), 3.2f) }
        "focused" -> {
            glowStroke(line(le + Offset(-4f, 0f), le + Offset(4f, 0f)), 2.6f)
            glowStroke(line(re + Offset(-4f, 0f), re + Offset(4f, 0f)), 2.6f)
            smile(4f, 7f)
        }
        "thinking" -> { pill(le, 6f); pill(re + Offset(0f, -2f), 9f); glowStroke(arc(Offset(47f, 51f), Offset(58f, 49f), 2f), 3f) }
        "cool" -> {
            drawRoundRect(Ink, topLeft = Offset(31f, 35f), size = Size(17f, 9f), cornerRadius = CornerRadius(3f))
            drawRoundRect(Ink, topLeft = Offset(52f, 35f), size = Size(17f, 9f), cornerRadius = CornerRadius(3f))
            drawLine(Ink, Offset(48f, 38f), Offset(52f, 38f), strokeWidth = 1.6f)
            smile(7f, 10f)
        }
        "scan" -> {
            pill(le); pill(re); smile(5f, 9f)
            // a scan line sweeping the visor top to bottom
            val y = 28f + 32f * ((t % 1.4f) / 1.4f)
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, GlowTop, GlowBottom, Color.Transparent), startX = 30f, endX = 70f), Offset(30f, y), Offset(70f, y), strokeWidth = 1.6f)
            drawLine(GlowTop.copy(alpha = 0.18f), Offset(31f, y), Offset(69f, y), strokeWidth = 5f)
        }
        else -> { pill(le); pill(re); smile() }
    }
}

private fun DrawScope.drawProp(prop: String, t: Float) {
    val bob = sin(t * 2f * PI.toFloat() / 1.6f) * 1.5f
    when (prop) {
        "box" -> {
            drawRoundRect(Color(0xFFD9A35B), topLeft = Offset(37f, 82f), size = Size(26f, 18f), cornerRadius = CornerRadius(2f))
            drawLine(Color(0xFFB07C3A), Offset(37f, 88f), Offset(63f, 88f), strokeWidth = 1.4f)
            drawRoundRect(Color.White, topLeft = Offset(44f, 77f), size = Size(12f, 9f), cornerRadius = CornerRadius(1f))
        }
        "cloud" -> drawCloud(Offset(78f, 14f + bob))
        "laptop" -> {
            drawRoundRect(Color(0xFFB8BCC4), topLeft = Offset(33f, 80f), size = Size(34f, 20f), cornerRadius = CornerRadius(2f))
            drawRoundRect(Color(0xFFD5D8DE), topLeft = Offset(29f, 99f), size = Size(42f, 4f), cornerRadius = CornerRadius(2f))
        }
        "magnifier" -> {
            drawCircle(Accent, radius = 7f, center = Offset(84f, 60f + bob), style = Stroke(2.6f))
            drawLine(Accent, Offset(79f, 65f + bob), Offset(74f, 71f + bob), strokeWidth = 3f, cap = StrokeCap.Round)
        }
        "question" -> drawGlyph("?", Offset(82f, 10f + bob))
        "exclaim" -> { drawGlyph("!", Offset(78f, 8f + bob)); drawGlyph("!", Offset(86f, 6f - bob)) }
        "zzz" -> {
            val p = (t % 2.4f) / 2.4f
            drawZ(Offset(74f, 16f - p * 6f), 7f, 1f - p * 0.5f)
            drawZ(Offset(84f, 7f - p * 6f), 5f, 0.8f - p * 0.5f)
        }
        "sparks" -> drawSparks(t)
        "confetti" -> drawConfetti(t)
        "heart" -> drawHeart(Offset(80f, 16f + bob), 7f)
        "plug" -> drawPlug(Offset(82f, 82f + bob), connected = true)
        "unplug" -> drawPlug(Offset(82f, 82f + bob), connected = false)
        "key" -> {
            drawCircle(Accent, radius = 5f, center = Offset(82f, 80f), style = Stroke(2.4f))
            drawLine(Accent, Offset(82f, 85f), Offset(82f, 99f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            drawLine(Accent, Offset(82f, 94f), Offset(86f, 94f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
        "sweat" -> {
            val drop = Path().apply { moveTo(74f, 26f); quadraticTo(70f, 33f, 74f, 35f); quadraticTo(78f, 33f, 74f, 26f); close() }
            drawPath(drop, Color(0xFF8FD3FF))
        }
        "hourglass" -> {
            val p = Path().apply { moveTo(78f, 70f); lineTo(88f, 70f); lineTo(78f, 86f); lineTo(88f, 86f); close() }
            drawPath(p, Accent, style = Stroke(2.2f, join = StrokeJoin.Round))
        }
        "check" -> drawCheck(Offset(82f, 14f + bob))
        "phone" -> {
            drawRoundRect(Ink, topLeft = Offset(74f, 64f), size = Size(13f, 22f), cornerRadius = CornerRadius(2.5f))
            drawRoundRect(Color(0xFF8FC7FF), topLeft = Offset(75.5f, 66f), size = Size(10f, 16f), cornerRadius = CornerRadius(1f))
        }
        "envelope" -> {
            drawRect(Color.White, topLeft = Offset(73f, 10f + bob), size = Size(18f, 12f))
            drawPath(line(Offset(73f, 10f + bob), Offset(82f, 17f + bob), Offset(91f, 10f + bob)), Ink, style = Stroke(1.3f))
        }
        "star" -> drawStar(Offset(82f, 14f + bob), 7f)
        "faceframe" -> {
            // a face being read: a small face with corner brackets locking on
            val at = Offset(84f, 64f + bob)
            drawCircle(Color(0xFF9AA0A8), 6f, at)
            drawCircle(Color.White, 1.1f, at + Offset(-2.2f, -1f))
            drawCircle(Color.White, 1.1f, at + Offset(2.2f, -1f))
            val k = 10f
            for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
                val corner = at + Offset(dx * k, dy * k)
                drawLine(Accent, corner, corner - Offset(dx * 4f, 0f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(Accent, corner, corner - Offset(0f, dy * 4f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
        }
        "camera" -> Unit // drawn over the face with the hands
    }
}

private fun DrawScope.drawGlyph(glyph: String, at: Offset) {
    when (glyph) {
        "?" -> {
            drawArc(Accent, 180f, 260f, useCenter = false, topLeft = at + Offset(-4f, -6f), size = Size(8f, 8f), style = Stroke(2.6f, cap = StrokeCap.Round))
            drawLine(Accent, at + Offset(0f, 2f), at + Offset(0f, 4f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            drawCircle(Accent, 1.5f, at + Offset(0f, 8f))
        }
        else -> {
            drawLine(Accent, at + Offset(0f, -6f), at + Offset(0f, 3f), strokeWidth = 3f, cap = StrokeCap.Round)
            drawCircle(Accent, 1.6f, at + Offset(0f, 7.5f))
        }
    }
}

private fun DrawScope.drawZ(at: Offset, s: Float, alpha: Float) {
    drawPath(line(at, at + Offset(s, 0f), at + Offset(0f, s), at + Offset(s, s)), Accent, alpha = alpha.coerceIn(0f, 1f), style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSparks(t: Float) {
    val p = 0.6f + 0.4f * abs(sin(t * 3f))
    for ((at, ang) in listOf(Offset(16f, 22f) to -30f, Offset(84f, 20f) to 30f, Offset(14f, 54f) to -70f, Offset(86f, 52f) to 70f)) {
        rotate(ang, pivot = at) {
            drawLine(Accent, at, at + Offset(0f, -6f * p), strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawConfetti(t: Float) {
    val colors = listOf(Accent, GlowTop, Color(0xFF7FD6FF), GlowBottom, Color.White)
    for (i in 0 until 10) {
        val x = 10f + (i * 37 % 80)
        val y = ((t * 40f + i * 23f) % 70f)
        rotate(t * 200f + i * 40f, pivot = Offset(x, y)) {
            drawRect(colors[i % colors.size], topLeft = Offset(x - 1.5f, y - 2.5f), size = Size(3f, 5f))
        }
    }
}

private fun DrawScope.drawHeart(at: Offset, r: Float) {
    val p = Path().apply {
        moveTo(at.x, at.y + r)
        cubicTo(at.x - r * 1.6f, at.y - r * 0.2f, at.x - r * 0.6f, at.y - r * 1.4f, at.x, at.y - r * 0.4f)
        cubicTo(at.x + r * 0.6f, at.y - r * 1.4f, at.x + r * 1.6f, at.y - r * 0.2f, at.x, at.y + r)
        close()
    }
    drawPath(p, GlowTop)
}

private fun DrawScope.drawStar(at: Offset, r: Float) {
    val p = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
        val pt = at + Offset(cos(a) * rr, sin(a) * rr)
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, Accent)
}

private fun DrawScope.drawCheck(at: Offset) {
    drawCircle(Color(0xFF2BB673), 7f, at)
    drawPath(line(at + Offset(-3.5f, 0f), at + Offset(-1f, 3f), at + Offset(4f, -3f)), Color.White, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawCloud(at: Offset) {
    drawCircle(Accent, 6f, at + Offset(-5f, 2f))
    drawCircle(Accent, 8f, at + Offset(2f, -1f))
    drawCircle(Accent, 5.5f, at + Offset(9f, 3f))
    drawRoundRect(Accent, topLeft = at + Offset(-11f, 2f), size = Size(25f, 7f), cornerRadius = CornerRadius(3.5f))
    drawPath(line(at + Offset(1f, 7f), at + Offset(1f, -1f)), Color.White, style = Stroke(1.8f, cap = StrokeCap.Round))
    drawPath(line(at + Offset(-2f, 2f), at + Offset(1f, -1f), at + Offset(4f, 2f)), Color.White, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawPlug(at: Offset, connected: Boolean) {
    val gap = if (connected) 0f else 7f
    drawRoundRect(Accent, topLeft = at + Offset(-4f, -gap - 10f), size = Size(8f, 9f), cornerRadius = CornerRadius(2f))
    drawLine(Accent, at + Offset(-2f, -gap - 1f), at + Offset(-2f, -gap + 2f), strokeWidth = 1.6f)
    drawLine(Accent, at + Offset(2f, -gap - 1f), at + Offset(2f, -gap + 2f), strokeWidth = 1.6f)
    drawRoundRect(Color(0xFF9AA0A8), topLeft = at + Offset(-5f, 3f), size = Size(10f, 8f), cornerRadius = CornerRadius(2f))
    drawLine(Color(0xFF9AA0A8), at + Offset(0f, 11f), at + Offset(0f, 18f), strokeWidth = 2f)
}

private fun DrawScope.drawCamera(at: Offset, alpha: Float) {
    drawRoundRect(Ink, topLeft = at + Offset(-13f, -7f), size = Size(26f, 16f), cornerRadius = CornerRadius(3f), alpha = alpha)
    drawRoundRect(Ink, topLeft = at + Offset(-5f, -10f), size = Size(9f, 4f), cornerRadius = CornerRadius(1f), alpha = alpha)
    drawCircle(Color(0xFF2E3440), 6.5f, at + Offset(0f, 1f), alpha = alpha)
    drawCircle(Color(0xFF7FA6D6), 3.2f, at + Offset(-1f, 0f), alpha = alpha)
}
