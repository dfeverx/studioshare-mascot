package com.dfeverx.studioshare.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.dfeverx.studioshare.mascot.director.MascotDock
import com.dfeverx.studioshare.mascot.stage.MascotPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MascotPlacementTest {
    private val stage = Size(1000f, 800f)
    private val mascot = Size(80f, 80f)
    private val m = 10f

    @Test fun theDefaultDockIsBottomEndAboveTheBottomChrome() {
        val o = MascotPlacement.dockOffset(stage, mascot, MascotDock(), m, bottomInset = 100f, avoid = emptyList())
        assertEquals(Offset(1000f - 80f - 10f, 800f - 100f - 80f - 10f), o)
    }

    @Test fun theDockStepsAboveAnythingItWouldCover() {
        val button = Rect(850f, 650f, 1000f, 760f)
        val o = MascotPlacement.dockOffset(stage, mascot, MascotDock(), m, 0f, listOf(button))
        assertFalse(Rect(o, mascot).overlaps(button))
        assertTrue(o.y + mascot.height <= button.top)
    }

    @Test fun aPerchSitsBesideTheAnchorWithoutCoveringIt() {
        val anchor = Rect(400f, 300f, 600f, 400f)
        val p = assertNotNull(MascotPlacement.perchBeside(anchor, stage, mascot, m, 0f, emptyList()))
        assertFalse(Rect(p, mascot).overlaps(anchor))
        assertTrue(p.x >= anchor.right)
    }

    @Test fun aPerchAvoidsTheUiAndTriesTheOtherSide() {
        val anchor = Rect(400f, 300f, 600f, 400f)
        val toolbar = Rect(600f, 0f, 1000f, 800f)
        val p = assertNotNull(MascotPlacement.perchBeside(anchor, stage, mascot, m, 0f, listOf(toolbar)))
        assertFalse(Rect(p, mascot).overlaps(toolbar))
        assertTrue(p.x + mascot.width <= anchor.left)
    }

    @Test fun noPerchWhenNothingFits() {
        val anchor = Rect(0f, 0f, 1000f, 60f)
        val everything = Rect(0f, 60f, 1000f, 800f)
        assertNull(MascotPlacement.perchBeside(anchor, stage, mascot, m, 0f, listOf(everything)))
    }

    @Test fun aLargeEmptyPaneIsStoodInNotBeside() {
        val pane = Rect(0f, 0f, 1000f, 800f)
        val p = assertNotNull(MascotPlacement.perchBeside(pane, stage, mascot, m, 0f, emptyList()))
        assertTrue(Rect(p, mascot).overlaps(pane))
    }

    @Test fun aDropSnapsToTheNearerEdge() {
        assertFalse(MascotPlacement.dockFor(Offset(100f, 300f), stage, mascot, m, 0f).end)
        val d = MascotPlacement.dockFor(Offset(800f, 10f), stage, mascot, m, 0f)
        assertTrue(d.end)
        assertEquals(0f, d.yFraction)
    }

    @Test fun docksRoundTripThroughStorage() {
        val d = MascotDock(end = false, yFraction = 0.25f)
        assertEquals(d, MascotDock.decode(d.encode()))
        assertNull(MascotDock.decode("garbage"))
    }
}
