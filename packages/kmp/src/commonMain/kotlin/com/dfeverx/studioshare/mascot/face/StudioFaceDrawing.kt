package com.dfeverx.studioshare.mascot.face

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.dfeverx.studioshare.mascot.render.MascotMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/** The icon's colours. The face is always the black icon, whatever the theme around it. */
object StudioFacePalette {
    val body = Color(0xFF000000)
    val glow = Color(0xFFFF4FD8)
    val eyeTop = Color(0xFFFF9147)
    val eyeBottom = Color(0xFFFF0FA8)
    val mouthTop = Color(0xFFFFC21A)
    val mouthBottom = Color(0xFFFF00AE)
    val mark = Color(0xFFFFFFFF)
}

/**
 * Continuous face parameters: what an [EyeShape] and [MouthShape] become, so a mood change can tween
 * instead of snapping. All lengths are fractions of the body's side.
 */
internal data class FaceParams(
    val open: Float = 1f,
    /** +1 happy ∩ arcs, −1 closed ∪ arcs, 0 the drops. */
    val arc: Float = 0f,
    /** Degrees each eye leans in at the top, on top of the icon's own lean. */
    val tilt: Float = 0f,
    val eyeScale: Float = 1f,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    /** How far the upper lid comes down, 0..1 of the eye. */
    val lid: Float = 0f,
    /** Degrees the lid drops toward the outer corner (sad); 0 is level. */
    val lidTilt: Float = 0f,
    val wink: Float = 0f,
    val scan: Float = 0f,
    /** +1 the icon's U, 0 straight, negative a frown. */
    val curve: Float = 1f,
    val width: Float = 1f,
    val thick: Float = 1f,
    val fill: Float = 0f,
    val oval: Float = 0f,
    val ovalSize: Float = 1f,
    val wavy: Float = 0f,
) {
    fun lerp(to: FaceParams, f: Float) = FaceParams(
        open = l(open, to.open, f), arc = l(arc, to.arc, f), tilt = l(tilt, to.tilt, f),
        eyeScale = l(eyeScale, to.eyeScale, f), lookX = l(lookX, to.lookX, f), lookY = l(lookY, to.lookY, f),
        lid = l(lid, to.lid, f), lidTilt = l(lidTilt, to.lidTilt, f), wink = l(wink, to.wink, f), scan = l(scan, to.scan, f),
        curve = l(curve, to.curve, f), width = l(width, to.width, f), thick = l(thick, to.thick, f),
        fill = l(fill, to.fill, f), oval = l(oval, to.oval, f), ovalSize = l(ovalSize, to.ovalSize, f),
        wavy = l(wavy, to.wavy, f),
    )

    companion object {
        fun of(e: FaceExpression): FaceParams {
            val eyes = when (e.eyes) {
                EyeShape.Teardrop -> FaceParams()
                EyeShape.Happy -> FaceParams(arc = 1f)
                EyeShape.Closed -> FaceParams(arc = -1f)
                EyeShape.Sleepy -> FaceParams(lid = 0.55f)
                EyeShape.Wide -> FaceParams(eyeScale = 1.2f)
                EyeShape.Focused -> FaceParams(lid = 0.36f)
                EyeShape.Worried -> FaceParams(lid = 0.3f, lidTilt = 22f)
                EyeShape.Wink -> FaceParams(wink = 1f)
                EyeShape.Look -> FaceParams(lookX = 0.8f, lookY = -0.6f)
                EyeShape.Scan -> FaceParams(scan = 1f)
            }
            return when (e.mouth) {
                MouthShape.Smile -> eyes
                MouthShape.Small -> eyes.copy(curve = 0.75f, width = 0.5f, thick = 0.8f)
                MouthShape.Grin -> eyes.copy(width = 1.04f, fill = 1f)
                MouthShape.Flat -> eyes.copy(curve = 0.1f, width = 0.5f, thick = 0.75f)
                MouthShape.SmallO -> eyes.copy(curve = 0.4f, width = 0.4f, oval = 1f, ovalSize = 0.75f)
                MouthShape.Open -> eyes.copy(curve = 0.4f, width = 0.4f, oval = 1f, ovalSize = 1.05f)
                MouthShape.Frown -> eyes.copy(curve = -0.7f, width = 0.55f, thick = 0.8f)
                MouthShape.Wavy -> eyes.copy(curve = 0f, width = 0.62f, thick = 0.6f, wavy = 1f)
            }
        }
    }
}

private fun l(a: Float, b: Float, f: Float) = a + (b - a) * f

/**
 * Which way to look at something [dx], [dy] points away (right and down positive): each axis −1..1,
 * turning hard for a nearby offset and settling as it grows. The axes are independent, so a cursor
 * far below and a little to the side still gets a real sideways look.
 */
fun gazeToward(dx: Float, dy: Float): Offset = Offset(tanh(dx / 260f), tanh(dy / 200f))

private fun hash(n: Float, seed: Float) = (sin(n * seed) * 43758.547f).let { it - floor(it) }

/** One blink, 0 open … 1 shut: down in 70 ms, back up in 130 ms. */
private fun blinkShape(dt: Float): Float = when {
    dt < 0f || dt > 0.2f -> 0f
    dt < 0.07f -> (dt / 0.07f).let { it * it * (3 - 2 * it) }
    else -> (1f - (dt - 0.07f) / 0.13f).let { it * it }
}

/**
 * Blink, 0 open … 1 shut: 2.2–5.4 s apart, and about one in five a quick double. Pure in [t] so a
 * render is repeatable.
 */
internal fun blinkAt(t: Float): Float {
    val slot = 3.8f
    val n = floor(t / slot)
    var b = 0f
    for (k in listOf(n - 1, n)) {
        val start = k * slot + hash(k, 12.9898f) * 1.6f
        b = maxOf(b, blinkShape(t - start))
        if (hash(k, 78.233f) < 0.22f) b = maxOf(b, blinkShape(t - start - 0.23f))
    }
    return b
}

/**
 * Draws the face filling this scope. [t] is seconds of life (blinks, drift, accent animation);
 * [motionT] seconds into the mood's body motion.
 */
internal fun DrawScope.drawStudioFace(
    p: FaceParams,
    motion: String,
    t: Float,
    motionT: Float,
    accent: Accent?,
    accentAlpha: Float = 1f,
    previousAccent: Accent? = null,
    previousAlpha: Float = 0f,
    progress: Float? = null,
    glow: Boolean = true,
    gaze: Offset? = null,
) {
    val s = min(size.width, size.height) * 0.78f
    val pose = MascotMotion.pose(motion, motionT)
    val turn = gaze ?: Offset.Zero
    val c = Offset(
        size.width / 2 + pose.dx * s + turn.x * s * 0.03f,
        size.height / 2 + s * 0.03f + pose.dy * s + turn.y * s * 0.02f,
    )
    translate(c.x, c.y) {
        rotate(pose.rotation, pivot = Offset.Zero) {
            scale(pose.scaleX, pose.scaleY, pivot = Offset(0f, s / 2)) {
                drawBody(s, glow)
                // the face turns toward what it looks at: eyes and mouth slide across the body and
                // narrow a little, like features on a head turning — big enough to read even at
                // menu-bar size, where moving the eyes alone is under a pixel
                translate(turn.x * s * 0.10f, turn.y * s * 0.07f) {
                    scale(1f - 0.10f * abs(turn.x), 1f - 0.06f * abs(turn.y), pivot = Offset.Zero) {
                        drawEyes(s, p, t, gaze)
                        drawMouth(s, p)
                    }
                }
                val anchor = Offset(s * 0.43f, -s * 0.45f)
                if (previousAccent != null && previousAlpha > 0.01f) drawAccent(previousAccent, anchor, s, t, previousAlpha, progress)
                if (accent != null && accentAlpha > 0.01f) drawAccent(accent, anchor, s, t, accentAlpha, progress)
            }
        }
    }
}

private fun DrawScope.drawBody(s: Float, glow: Boolean) {
    val r = s * 0.22f
    if (glow) {
        for (i in 6 downTo 1) {
            val g = s * 0.011f * i
            drawRoundRect(
                StudioFacePalette.glow.copy(alpha = 0.07f * (1f - i / 7f)),
                topLeft = Offset(-s / 2 - g, -s / 2 - g), size = Size(s + 2 * g, s + 2 * g),
                cornerRadius = CornerRadius(r + g), style = Stroke(s * 0.012f),
            )
        }
    }
    drawRoundRect(StudioFacePalette.body, Offset(-s / 2, -s / 2), Size(s, s), CornerRadius(r))
    if (glow) {
        drawRoundRect(
            StudioFacePalette.glow.copy(alpha = 0.35f), Offset(-s / 2, -s / 2), Size(s, s),
            CornerRadius(r), style = Stroke(s * 0.008f),
        )
    }
}

// ---- eyes ----------------------------------------------------------------------------------

/** The icon's drop: a rounded point at the top, full at the bottom; centred on the origin. */
private fun teardrop(w: Float, h: Float) = Path().apply {
    moveTo(0f, -h / 2)
    cubicTo(w * 0.30f, -h / 2, w / 2, -h * 0.16f, w / 2, h * 0.12f)
    cubicTo(w / 2, h * 0.36f, w * 0.28f, h / 2, 0f, h / 2)
    cubicTo(-w * 0.28f, h / 2, -w / 2, h * 0.36f, -w / 2, h * 0.12f)
    cubicTo(-w / 2, -h * 0.16f, -w * 0.30f, -h / 2, 0f, -h / 2)
    close()
}

private fun DrawScope.drawEyes(s: Float, p: FaceParams, t: Float, gaze: Offset? = null) {
    val blink = blinkAt(t)
    val scan = p.scan * sin(t * 2.4f) * 0.9f
    // a gaze replaces the mood's look and the idle drift; the eyes lead the turn a little (barely
    // when looking down, where the mouth sits right below them)
    val lookX = if (gaze != null) scan + gaze.x * 0.8f * (1f - p.scan) else p.lookX + scan + 0.12f * sin(t * 0.7f) * (1f - p.scan)
    val lookY = if (gaze != null) gaze.y * (if (gaze.y > 0) 0.2f else 0.9f) else p.lookY + 0.08f * sin(t * 0.53f)
    val w = s * 0.16f * p.eyeScale
    val h = s * 0.23f * p.eyeScale
    for (side in listOf(-1f, 1f)) {
        val cx = side * s * 0.19f + lookX * s * 0.045f
        val cy = -s * 0.186f + lookY * s * 0.035f
        val arc = if (side > 0) l(p.arc, 1f, p.wink) else p.arc
        // the icon's drops lean in at the top by ~15°; worried adds to it
        val lean = -side * (15f - p.tilt)
        translate(cx, cy) {
            rotate(lean, pivot = Offset.Zero) {
                val brush = Brush.verticalGradient(
                    listOf(StudioFacePalette.eyeTop, StudioFacePalette.eyeBottom), startY = -h / 2, endY = h / 2,
                )
                val dropAlpha = 1f - abs(arc)
                if (dropAlpha > 0.01f) {
                    val open = (p.open * (1f - blink)).coerceAtLeast(0.08f)
                    scale(1f, open, pivot = Offset(0f, h * 0.15f)) {
                        val drop = teardrop(w, h)
                        if (p.lid > 0.01f) {
                            // keep only what is below the lid; the lid stays level in the face
                            // rather than leaning with the drop, and drops to the outside when sad
                            val edge = Offset(0f, -h / 2 + p.lid * h)
                            val ang = (-lean + side * p.lidTilt) * PI.toFloat() / 180f
                            val along = Offset(cos(ang), sin(ang)) * (4 * w)
                            val down = Offset(-sin(ang), cos(ang)) * (4 * h)
                            val below = Path().apply {
                                moveTo((edge - along).x, (edge - along).y)
                                lineTo((edge + along).x, (edge + along).y)
                                lineTo((edge + along + down).x, (edge + along + down).y)
                                lineTo((edge - along + down).x, (edge - along + down).y)
                                close()
                            }
                            clipPath(below) { drawPath(drop, brush, alpha = dropAlpha) }
                        } else {
                            drawPath(drop, brush, alpha = dropAlpha)
                        }
                    }
                }
                if (abs(arc) > 0.01f) {
                    rotate(-lean, pivot = Offset.Zero) {
                        val a = Path().apply {
                            if (arc > 0) {
                                moveTo(-w * 0.62f, h * 0.12f); quadraticTo(0f, -h * 0.42f, w * 0.62f, h * 0.12f)
                            } else {
                                moveTo(-w * 0.62f, -h * 0.05f); quadraticTo(0f, h * 0.40f, w * 0.62f, -h * 0.05f)
                            }
                        }
                        drawPath(a, brush, alpha = abs(arc), style = Stroke(w * 0.42f, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

// ---- mouth ---------------------------------------------------------------------------------

/** A variable-width ribbon with round ends along [at], as one simple polygon (alpha-safe). */
private fun ribbon(at: (Float) -> Offset, radius: (Float) -> Float, n: Int = 40): Path {
    val left = ArrayList<Offset>(n + 1)
    val right = ArrayList<Offset>(n + 1)
    val normals = ArrayList<Offset>(n + 1)
    for (i in 0..n) {
        val u = i / n.toFloat()
        val a = at((u - 0.01f).coerceAtLeast(0f))
        val b = at((u + 0.01f).coerceAtMost(1f))
        val d = b - a
        val len = hypot(d.x, d.y).takeIf { it > 1e-4f } ?: 1f
        val nrm = Offset(-d.y / len, d.x / len)
        val p = at(u)
        val r = radius(u)
        left += p + nrm * r
        right += p - nrm * r
        normals += nrm
    }
    return Path().apply {
        moveTo(left[0].x, left[0].y)
        for (i in 1..n) lineTo(left[i].x, left[i].y)
        cap(this, at(1f), normals[n], radius(1f), forward = true)
        for (i in n downTo 0) lineTo(right[i].x, right[i].y)
        cap(this, at(0f), normals[0], radius(0f), forward = false)
        close()
    }
}

/** A semicircle from the +normal side round the end to the −normal side. */
private fun cap(path: Path, c: Offset, nrm: Offset, r: Float, forward: Boolean) {
    val start = atan2(nrm.y, nrm.x) + if (forward) 0f else PI.toFloat()
    for (i in 1..10) {
        val a = start - PI.toFloat() * i / 10f
        path.lineTo(c.x + cos(a) * r, c.y + sin(a) * r)
    }
}

private fun cubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, u: Float): Offset {
    val v = 1 - u
    return p0 * (v * v * v) + p1 * (3 * v * v * u) + p2 * (3 * v * u * u) + p3 * (u * u * u)
}

private fun DrawScope.drawMouth(s: Float, p: FaceParams) {
    val mid = s * 0.13f
    val brush = Brush.verticalGradient(
        listOf(StudioFacePalette.mouthTop, StudioFacePalette.mouthBottom), startY = 0f, endY = s * 0.30f,
    )
    val half = s * 0.228f * p.width
    val depth = s * 0.16f * p.curve
    val y0 = mid - depth / 2
    val p0 = Offset(-half, y0)
    val p3 = Offset(half, y0)
    val c1 = Offset(-half, y0 + depth * 1.33f)
    val c2 = Offset(half, y0 + depth * 1.33f)
    val rEnd = s * 0.075f * p.thick
    // the icon's smile swells at the bottom; a near-straight mouth stays even (or it creases)
    val rMid = l(rEnd, s * 0.092f * p.thick, abs(p.curve).coerceAtMost(1f))

    val lineAlpha = (1f - p.oval) * (1f - p.wavy)
    if (p.fill > 0.01f && lineAlpha > 0.01f) {
        val inside = Path().apply {
            moveTo(p0.x, p0.y); cubicTo(c1.x, c1.y, c2.x, c2.y, p3.x, p3.y); close()
        }
        drawPath(inside, brush, alpha = p.fill * lineAlpha)
    }
    if (lineAlpha > 0.01f) {
        val curve = ribbon({ cubic(p0, c1, c2, p3, it) }, { l(rEnd, rMid, sin(PI.toFloat() * it)) })
        drawPath(curve, brush, alpha = lineAlpha)
    }
    if (p.wavy > 0.01f) {
        val wave = ribbon({ Offset(-half + 2 * half * it, mid + s * 0.035f * sin(it * 4 * PI.toFloat())) }, { rEnd })
        drawPath(wave, brush, alpha = p.wavy)
    }
    if (p.oval > 0.01f) {
        val ow = s * 0.17f * p.ovalSize
        val oh = s * 0.21f * p.ovalSize
        drawOval(brush, Offset(-ow / 2, mid - oh / 2), Size(ow, oh), alpha = p.oval)
    }
}

// ---- accents -------------------------------------------------------------------------------

private fun DrawScope.drawAccent(a: Accent, at: Offset, s: Float, t: Float, alpha: Float, progress: Float?) {
    val u = s * 0.13f
    val brush = Brush.linearGradient(
        listOf(StudioFacePalette.mouthTop, StudioFacePalette.eyeBottom),
        start = at + Offset(-u, -u), end = at + Offset(u, u),
    )
    val mark = StudioFacePalette.mark
    val line = Stroke(u * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (a) {
        Accent.Sparkles -> {
            for ((i, o) in listOf(Offset(0f, 0f), Offset(u * 0.95f, u * 0.75f)).withIndex()) {
                val k = (if (i == 0) 1f else 0.55f) * (0.8f + 0.2f * sin(t * 5f + i * 2f))
                drawPath(star(at + o, u * 0.8f * k, u * 0.22f * k), brush, alpha = alpha)
            }
        }
        Accent.Question -> {
            val q = Path().apply {
                moveTo(at.x - u * 0.45f, at.y - u * 0.35f)
                cubicTo(at.x - u * 0.45f, at.y - u * 1.0f, at.x + u * 0.55f, at.y - u * 1.0f, at.x + u * 0.5f, at.y - u * 0.35f)
                cubicTo(at.x + u * 0.48f, at.y + u * 0.05f, at.x, at.y + u * 0.0f, at.x, at.y + u * 0.4f)
            }
            drawPath(q, brush, alpha = alpha, style = line)
            drawCircle(brush, u * 0.16f, Offset(at.x, at.y + u * 0.85f), alpha = alpha)
        }
        Accent.Exclaim -> {
            val bob = sin(t * 6f) * u * 0.06f
            drawLine(brush, Offset(at.x, at.y - u * 0.9f + bob), Offset(at.x, at.y + u * 0.3f + bob), u * 0.3f, StrokeCap.Round, alpha = alpha)
            drawCircle(brush, u * 0.17f, Offset(at.x, at.y + u * 0.85f + bob), alpha = alpha)
        }
        Accent.Sweat -> {
            val drip = (t * 0.6f) % 1f
            translate(at.x - u * 0.2f, at.y + u * 0.2f + drip * u * 0.6f) {
                drawPath(teardrop(u * 0.75f, u * 1.1f), mark, alpha = alpha * (1f - drip * 0.7f))
            }
        }
        Accent.Zzz -> {
            for (i in 0..2) {
                val ph = ((t * 0.5f + i / 3f) % 1f)
                val z = u * (0.35f + 0.25f * i)
                val c = at + Offset(u * 0.6f * i - u * 0.4f, -ph * u * 1.2f + u * 0.4f)
                val zp = Path().apply {
                    moveTo(c.x - z / 2, c.y - z / 2); lineTo(c.x + z / 2, c.y - z / 2)
                    lineTo(c.x - z / 2, c.y + z / 2); lineTo(c.x + z / 2, c.y + z / 2)
                }
                drawPath(zp, mark, alpha = alpha * (1f - ph), style = Stroke(u * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Accent.Check -> {
            val ck = Path().apply {
                moveTo(at.x - u * 0.7f, at.y); lineTo(at.x - u * 0.15f, at.y + u * 0.55f); lineTo(at.x + u * 0.8f, at.y - u * 0.6f)
            }
            drawPath(ck, brush, alpha = alpha, style = Stroke(u * 0.32f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Accent.Heart -> {
            val k = 1f + 0.08f * sin(t * 7f)
            val r = u * 0.9f * k
            val h = Path().apply {
                moveTo(at.x, at.y + r * 0.75f)
                cubicTo(at.x - r * 1.2f, at.y - r * 0.1f, at.x - r * 0.6f, at.y - r * 1.0f, at.x, at.y - r * 0.4f)
                cubicTo(at.x + r * 0.6f, at.y - r * 1.0f, at.x + r * 1.2f, at.y - r * 0.1f, at.x, at.y + r * 0.75f)
                close()
            }
            drawPath(h, brush, alpha = alpha)
        }
        Accent.Dots -> {
            for (i in 0..2) {
                val ph = sin((t * 4f - i * 0.7f)).coerceAtLeast(0f)
                drawCircle(mark, u * 0.2f, at + Offset((i - 1) * u * 0.6f, -ph * u * 0.35f), alpha = alpha * (0.4f + 0.6f * ph))
            }
        }
        Accent.Lock -> {
            drawRect(mark, at + Offset(-u * 0.6f, -u * 0.1f), Size(u * 1.2f, u * 0.95f), alpha = alpha)
            val shackle = Path().apply {
                moveTo(at.x - u * 0.38f, at.y - u * 0.1f)
                lineTo(at.x - u * 0.38f, at.y - u * 0.45f)
                cubicTo(at.x - u * 0.38f, at.y - u * 1.0f, at.x + u * 0.38f, at.y - u * 1.0f, at.x + u * 0.38f, at.y - u * 0.45f)
                lineTo(at.x + u * 0.38f, at.y - u * 0.1f)
            }
            drawPath(shackle, mark, alpha = alpha, style = Stroke(u * 0.2f))
        }
        Accent.Progress -> {
            val r = u * 0.85f
            val tl = at - Offset(r, r)
            drawCircle(StudioFacePalette.body, r * 1.25f, at, alpha = alpha)
            drawCircle(mark.copy(alpha = 0.2f), r, at, alpha = alpha, style = Stroke(u * 0.24f))
            val ring = Stroke(u * 0.24f, cap = StrokeCap.Round)
            if (progress != null) {
                drawArc(brush, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, Size(2 * r, 2 * r), alpha = alpha, style = ring)
            } else {
                drawArc(brush, (t * 300f) % 360f, 90f, false, tl, Size(2 * r, 2 * r), alpha = alpha, style = ring)
            }
        }
    }
}

private fun star(c: Offset, outer: Float, inner: Float) = Path().apply {
    for (i in 0 until 8) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI.toFloat() / 2 + i * PI.toFloat() / 4
        val p = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}
