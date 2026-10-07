package com.dfeverx.studioshare.mascot

import com.dfeverx.studioshare.mascot.director.MascotDirector
import com.dfeverx.studioshare.mascot.director.MascotPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MascotDirectorTest {
    private val d = MascotDirector()
    private val upload = Any()
    private val cull = Any()
    private val session = Any()

    @Test fun idleWhenNothingIsCued() = assertEquals("app.idle", d.scene.value.momentKey)

    @Test fun theNewestHoldOfEqualPriorityWinsAndReleasingRestoresTheOther() {
        d.hold(upload, "upload.uploading")
        d.hold(cull, "cull.running", anchor = "cull.pane")
        assertEquals("cull.running", d.scene.value.momentKey)
        assertEquals("cull.pane", d.scene.value.anchor)
        d.release(cull)
        assertEquals("upload.uploading", d.scene.value.momentKey)
        d.release(upload)
        assertEquals("app.idle", d.scene.value.momentKey)
    }

    @Test fun aOneShotInterruptsThenHandsBack() {
        d.hold(upload, "upload.uploading")
        d.play("state.saved")
        val shot = d.scene.value
        assertTrue(shot.oneShot)
        assertEquals("state.saved", shot.momentKey)
        d.finished(shot.cueId)
        assertEquals("upload.uploading", d.scene.value.momentKey)
        assertFalse(d.scene.value.oneShot)
    }

    @Test fun aStaleFinishDoesNotEndANewerOneShot() {
        d.play("state.saved")
        val first = d.scene.value.cueId
        d.play("state.sent")
        d.finished(first)
        assertEquals("state.sent", d.scene.value.momentKey)
    }

    @Test fun aBlockingHoldIsNotInterruptedByANormalOneShot() {
        d.hold(session, "state.sessionExpired", priority = MascotPriority.Blocking)
        d.play("state.saved")
        assertEquals("state.sessionExpired", d.scene.value.momentKey)
        d.hold(upload, "upload.uploading", priority = MascotPriority.Normal)
        assertEquals("state.sessionExpired", d.scene.value.momentKey)
    }

    @Test fun disablingDropsEveryCueAndIgnoresNewOnes() {
        d.hold(upload, "upload.uploading")
        d.setEnabled(false)
        assertEquals("app.idle", d.scene.value.momentKey)
        d.hold(cull, "cull.running")
        d.play("state.saved")
        assertEquals("app.idle", d.scene.value.momentKey)
        d.setEnabled(true)
        assertEquals("app.idle", d.scene.value.momentKey)
    }

    @Test fun reHoldingTheSameCueKeepsItsPlace() {
        d.hold(upload, "upload.uploading")
        d.hold(cull, "cull.running")
        d.hold(upload, "upload.uploading")
        assertEquals("cull.running", d.scene.value.momentKey)
    }
}
