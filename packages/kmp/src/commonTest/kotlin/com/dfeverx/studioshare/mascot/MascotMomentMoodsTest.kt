package com.dfeverx.studioshare.mascot

import com.dfeverx.studioshare.mascot.face.HandGesture
import com.dfeverx.studioshare.mascot.face.StudioFaceExpressions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MascotMomentMoodsTest {
    @Test fun everyMoodHasAFaceExpressionWithTheSpecsMotion() {
        for (mood in MascotMomentMoods.moods) {
            val e = StudioFaceExpressions.byMood[mood]
            assertTrue(e != null, "mood without a face expression: $mood")
            assertEquals(MascotMomentMoods.motionOf(mood), e.motion, "motion of $mood")
        }
        assertEquals(MascotMomentMoods.moods, StudioFaceExpressions.byMood.keys, "face expressions for moods not in the spec")
    }

    @Test fun everyMomentConstantResolvesToAKnownMood() {
        for (key in MascotMoments.all) {
            assertTrue(MascotMomentMoods.moment(key) != null, "constant without a table row: $key")
            assertTrue(MascotMomentMoods.moodFor(key) in MascotMomentMoods.moods, "moment $key")
        }
    }

    @Test fun unknownMomentAndMoodReadAsIdle() {
        assertEquals("idle", MascotMomentMoods.moodFor("no.such.moment"))
        assertEquals("idle", MascotMomentMoods.moodFor(null))
        assertEquals(StudioFaceExpressions.idle, StudioFaceExpressions.forMood("no-such-mood"))
    }

    @Test fun everyMomentGestureIsAHandGestureTheFaceKnows() {
        val withHands = MascotMoments.all.filter { MascotMomentMoods.moment(it)?.hands != null }
        assertTrue(withHands.isNotEmpty())
        for (key in withHands) {
            assertTrue(MascotMomentMoods.handsFor(key) != null, "moment $key: unknown gesture ${MascotMomentMoods.moment(key)?.hands}")
            assertTrue(MascotMomentMoods.moment(key)?.hold != true, "held moment $key can't gesture")
        }
        assertEquals(HandGesture.Wave, MascotMomentMoods.handsFor(MascotMoments.AuthSignedIn))
        assertEquals(null, MascotMomentMoods.handsFor(MascotMoments.AppNavigated))
    }

    @Test fun aRepeatedFirstCelebrationShowsOk() {
        val key = MascotMoments.AuthStudioLaunched
        assertEquals("celebrating", MascotMomentMoods.moodFor(key))
        assertEquals("ok", MascotMomentMoods.moodFor(key, firstAlreadySeen = true))
        // and doesn't cheer again
        assertEquals(HandGesture.Cheer, MascotMomentMoods.handsFor(key))
        assertEquals(null, MascotMomentMoods.handsFor(key, firstAlreadySeen = true))
    }
}
