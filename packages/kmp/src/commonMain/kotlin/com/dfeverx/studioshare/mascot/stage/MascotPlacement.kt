package com.dfeverx.studioshare.mascot.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.dfeverx.studioshare.mascot.director.MascotDock

/**
 * Where the mascot may stand. Pure geometry in the stage's own pixel space, so the rules that keep it
 * off the UI are testable: it never overlaps an avoid rect, never leaves the stage, and never stands
 * on the thing it walked to.
 */
object MascotPlacement {

    /** Top-left of the mascot at [dock], moved up past anything it would cover. */
    fun dockOffset(
        stage: Size,
        mascot: Size,
        dock: MascotDock,
        margin: Float,
        bottomInset: Float,
        avoid: List<Rect>,
    ): Offset {
        val x = if (dock.end) stage.width - mascot.width - margin else margin
        val top = margin
        val bottom = stage.height - bottomInset - mascot.height - margin
        val y = if (bottom <= top) top else top + (bottom - top) * dock.yFraction
        return freeVertically(Offset(x, y), stage, mascot, margin, bottomInset, avoid)
    }

    /**
     * A spot beside [anchor] (right, left, below, above — in that order), or null when none fits
     * without covering [avoid] or [anchor] itself; the stage then stays docked.
     */
    fun perchBeside(
        anchor: Rect,
        stage: Size,
        mascot: Size,
        margin: Float,
        bottomInset: Float,
        avoid: List<Rect>,
    ): Offset? {
        val gap = margin / 2
        val candidates = listOf(
            Offset(anchor.right + gap, anchor.bottom - mascot.height),
            Offset(anchor.left - gap - mascot.width, anchor.bottom - mascot.height),
            Offset(anchor.center.x - mascot.width / 2, anchor.bottom + gap),
            Offset(anchor.center.x - mascot.width / 2, anchor.top - gap - mascot.height),
            // inside a large anchor (an empty pane), stand in its bottom-end corner
            Offset(anchor.right - margin - mascot.width, anchor.bottom - margin - mascot.height),
        )
        val bounds = Rect(0f, 0f, stage.width, stage.height - bottomInset)
        return candidates.firstOrNull { o ->
            val r = Rect(o, mascot)
            r.left >= bounds.left && r.top >= bounds.top && r.right <= bounds.right && r.bottom <= bounds.bottom &&
                avoid.none { it.overlaps(r) } &&
                (!anchor.overlaps(r) || anchor.isLargeFor(mascot))
        }
    }

    /** Dock for a drop at [offset]: the nearer edge, and how far down it. */
    fun dockFor(offset: Offset, stage: Size, mascot: Size, margin: Float, bottomInset: Float): MascotDock {
        val end = offset.x + mascot.width / 2 > stage.width / 2
        val top = margin
        val bottom = stage.height - bottomInset - mascot.height - margin
        val y = if (bottom <= top) 1f else ((offset.y - top) / (bottom - top)).coerceIn(0f, 1f)
        return MascotDock(end = end, yFraction = y)
    }

    fun clamp(offset: Offset, stage: Size, mascot: Size): Offset = Offset(
        offset.x.coerceIn(0f, (stage.width - mascot.width).coerceAtLeast(0f)),
        offset.y.coerceIn(0f, (stage.height - mascot.height).coerceAtLeast(0f)),
    )

    private fun freeVertically(
        start: Offset,
        stage: Size,
        mascot: Size,
        margin: Float,
        bottomInset: Float,
        avoid: List<Rect>,
    ): Offset {
        var y = start.y
        repeat(avoid.size + 1) {
            val hit = avoid.firstOrNull { it.overlaps(Rect(Offset(start.x, y), mascot)) } ?: return Offset(start.x, y)
            // step above whatever is in the way; if that leaves the stage, try below it instead
            val above = hit.top - margin - mascot.height
            y = if (above >= margin) above else hit.bottom + margin
        }
        val maxY = stage.height - bottomInset - mascot.height - margin
        return Offset(start.x, y.coerceIn(margin.coerceAtMost(maxY), maxY.coerceAtLeast(margin)))
    }

    private fun Rect.isLargeFor(mascot: Size) = width >= mascot.width * 3 && height >= mascot.height * 3
}
