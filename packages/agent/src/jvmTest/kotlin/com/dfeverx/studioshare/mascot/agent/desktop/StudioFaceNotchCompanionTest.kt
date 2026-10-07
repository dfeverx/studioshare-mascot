package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.dfeverx.studioshare.mascot.MascotMoments
import com.dfeverx.studioshare.mascot.agent.AgentAlert
import com.dfeverx.studioshare.mascot.agent.AgentTask
import com.dfeverx.studioshare.mascot.agent.AgentWarning
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import com.dfeverx.studioshare.mascot.agent.MascotAgentState
import com.dfeverx.studioshare.mascot.face.HandGesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The notch companion: what it opens to say, how big, where the face sits and what it sounds like —
 * plus a render of the island in each state to `build/studio-face-notch/states.png`, for eyeballing
 * without a window.
 */
class StudioFaceNotchCompanionTest {
    private val notched = NotchGeometry(0, 0, 1512, 185.dp, 38.dp)
    private val flat = NotchGeometry(0, 0, 1920, 0.dp, 0.dp)
    private val upload = MascotAgentState.Working(AgentTask("u", "Uploading Wedding — Ava & Sam", "128 of 300 photos", 0.43f))
    private val warning = MascotAgentState.Warning(
        AgentWarning("w", "Storage almost full", "92% of your studio's space is used.", actionLabel = "Manage", onAction = {}, label = "Storage"),
    )
    private val signedIn = MascotAgentState.Alert(
        AgentAlert("a", "You're signed in. Welcome back!", "", mood = "happy", label = "Sign in"),
    )
    private val booking = MascotAgentState.Alert(
        AgentAlert("b", "New booking!", "Priya booked a portrait session for Sat 14 Nov.", mood = "celebrating", label = "Orbit"),
    )

    private val celebrated = MascotAgentState.Alert(
        AgentAlert("c", "All photos uploaded!", "", mood = "celebrating", label = "Upload", hands = HandGesture.Cheer),
    )

    @Test fun onlySomethingNewWakesASleepingIsland() {
        assertNull(actionKey(MascotAgentState.Idle()))
        assertNull(actionKey(MascotAgentState.Alert(AgentAlert("q", "", "", mood = "glance", quiet = true))))
        assertEquals("alert:a", actionKey(signedIn))
        assertEquals("warning:w", actionKey(warning))
        // progress on the same task is not news
        val later = MascotAgentState.Working(upload.activeTask.copy(progress = 0.9f))
        assertEquals(actionKey(upload), actionKey(later))
    }

    @Test fun restingIslandSaysNothing() {
        assertNull(liveCard(null, MascotAgentState.Idle(), opened = false, peeks = false))
        assertNull(liveCard(null, upload, opened = false, peeks = false))
        val quiet = MascotAgentState.Alert(AgentAlert("q", "", "", mood = "glance", quiet = true))
        assertNull(liveCard(null, quiet, opened = true, peeks = false))
    }

    @Test fun eachStateOpensToTheRightCard() {
        val note = assertIs<NotchCard.Message>(liveCard(null, signedIn, opened = false, peeks = false))
        assertTrue(note.isNote)
        assertEquals("Sign in", note.label)
        val warn = assertIs<NotchCard.Message>(liveCard(null, warning, opened = false, peeks = false))
        // secondary first, primary last, like an approval's Deny · Allow
        assertEquals(listOf("Dismiss", "Manage"), warn.buttons.map { it.label })
        assertEquals(Tone.Warn, warn.detailTone)
        assertIs<NotchCard.Progress>(liveCard(null, upload, opened = false, peeks = true))
        assertIs<NotchCard.Progress>(liveCard(null, upload, opened = true, peeks = false))
        // a click on a quiet island says so, with a wave
        val quiet = assertIs<NotchCard.Message>(liveCard(null, MascotAgentState.Idle(), opened = true, peeks = false))
        assertEquals(HandGesture.Wave, quiet.hands)
    }

    @Test fun cardsOpenTheIslandAndWorkKeepsItCompact() {
        val idle = MascotAgentState.Idle()
        assertEquals(Look.Open, lookOf(liveCard(null, signedIn, false, false), signedIn, peeking = false))
        assertEquals(Look.Open, lookOf(liveCard(null, warning, false, false), warning, peeking = false))
        assertEquals(Look.Compact, lookOf(null, upload, peeking = false))
        assertEquals(Look.Compact, lookOf(null, idle, peeking = true))
        assertEquals(Look.Hidden, lookOf(null, idle, peeking = false))
    }

    @Test fun eachChangeOfLookHasItsSound() {
        val warn = liveCard(null, warning, false, false)
        val booked = liveCard(null, booking, false, false)
        val failed = liveCard(null, MascotAgentState.Alert(AgentAlert("f", "Upload failed", "", mood = "oops")), false, false)
        assertEquals(NotchSound.Peek, soundFor(Look.Hidden, Look.Compact, null))
        assertEquals(NotchSound.Attention, soundFor(Look.Compact, Look.Open, warn))
        assertEquals(NotchSound.Done, soundFor(Look.Hidden, Look.Open, booked))
        assertEquals(NotchSound.Error, soundFor(Look.Hidden, Look.Open, failed))
        assertEquals(NotchSound.Open, soundFor(Look.Compact, Look.Open, liveCard(null, upload, opened = true, peeks = false)))
        assertEquals(NotchSound.Close, soundFor(Look.Open, Look.Compact, null))
        assertNull(soundFor(Look.Compact, Look.Hidden, null))
    }

    @Test fun soundsAreShortAndSynthesized() {
        NotchSound.entries.forEach { sound ->
            val pcm = NotchSoundPlayer.render(sound)
            val seconds = pcm.size / 2.0 / NotchSoundPlayer.RATE
            assertTrue(seconds in 0.05..0.6, "$sound lasts $seconds s")
            assertTrue(pcm.any { it != 0.toByte() }, "$sound is silent")
        }
    }

    @Test fun cardButtonsAreTheSameButtonWhateverTheirCallback() {
        // cards are rebuilt every recomposition with fresh lambdas; they must still compare equal
        assertEquals(liveCard(null, warning, false, false), liveCard(null, warning, false, false))
    }

    @Test fun momentsSayTheirSpecLineOrYours() {
        val agent = MascotAgent(CoroutineScope(Dispatchers.Unconfined))
        agent.moment(MascotMoments.AuthSignedIn)
        val said = assertIs<MascotAgentState.Alert>(agent.state.value).alert
        assertEquals("You're signed in. Welcome back!", said.title)
        assertEquals("Sign in", said.label)
        assertEquals("happy", said.mood)

        agent.moment(MascotMoments.AuthSignedIn, "Welcome back, Priya!")
        assertEquals("Welcome back, Priya!", assertIs<MascotAgentState.Alert>(agent.state.value).alert.title)

        // the face gestures where the spec says it fits
        assertEquals(HandGesture.Wave, said.hands)
        agent.moment(MascotMoments.UploadDone)
        assertEquals(HandGesture.Cheer, (liveCard(agent, agent.state.value, false, false) as NotchCard.Message).hands)
        agent.moment(MascotMoments.UploadDone, firstAlreadySeen = true)
        assertNull(assertIs<MascotAgentState.Alert>(agent.state.value).alert.hands)

        // a moment with nothing to say only changes the face
        agent.moment(MascotMoments.AppNavigated)
        assertTrue(assertIs<MascotAgentState.Alert>(agent.state.value).alert.quiet)
    }

    @Test fun moodsWashInTheirColour() {
        assertEquals(Tone.Good, toneOf("celebrating"))
        assertEquals(Tone.Warn, toneOf("careful"))
        assertEquals(Tone.Bad, toneOf("oops"))
        assertEquals(Tone.Busy, toneOf("uploading"))
        assertEquals(Tone.Calm, toneOf("idle"))
    }

    @Test fun islandKeepsTheNotchWidthUntilItOpens() {
        // hidden: wider than the notch, so it shows; compact: the same width, a row taller; open: 640 wide
        assertEquals(DpSize(185.dp + NOTCH_EAR * 2, 38.dp), islandSize(notched, Look.Hidden, null))
        assertTrue(islandSize(notched, Look.Hidden, null).width > notched.notchWidth)
        assertEquals(DpSize(185.dp + NOTCH_EAR * 2, 38.dp + COMPACT_DROP), islandSize(notched, Look.Compact, null))
        assertEquals(islandSize(flat, Look.Hidden, null).width, islandSize(flat, Look.Compact, null).width)
        assertEquals(DpSize(OPEN_W, OPEN_H), islandSize(notched, Look.Open, liveCard(null, booking, false, false)))
        assertEquals(OPEN_PROGRESS_H, islandSize(notched, Look.Open, liveCard(null, upload, true, false)).height)
        // the open island is as tall as what it holds: a line, a message, one with buttons
        val note = islandSize(notched, Look.Open, liveCard(null, signedIn, false, false)).height
        val asks = islandSize(notched, Look.Open, liveCard(null, warning, false, false)).height
        assertTrue(note < OPEN_H && OPEN_H < asks, "note $note, message $OPEN_H, with buttons $asks")
        assertTrue(stageSize(notched).height >= asks)
        // without a notch, hidden is a tab as wide as a notch
        assertEquals(185.dp, islandSize(flat, Look.Hidden, null).width)
        // the whole stage stays a strip under the menu bar
        assertTrue(stageSize(notched).height < 200.dp)
    }

    @Test fun faceSitsUnderTheNotchAndMovesIntoTheCard() {
        val compact = islandSize(notched, Look.Compact, null)
        val face = facePlace(notched, Look.Compact, null)
        assertTrue(face.y - face.size / 2 >= notched.bandHeight, "face must sit under the camera")
        assertTrue(face.y + face.size / 2 <= compact.height && face.x - face.size / 2 >= 0.dp)
        assertTrue(face.size <= COMPACT_DROP)
        // hidden: the face shows in the notch's left ear, clear of the camera; in a flat tab it shows too
        val ear = facePlace(notched, Look.Hidden, null)
        assertEquals(1f, ear.alpha)
        assertTrue(ear.x - ear.size / 2 >= 0.dp && ear.x + ear.size / 2 <= NOTCH_EAR, "face must sit beside the camera")
        assertEquals(1f, facePlace(flat, Look.Hidden, null).alpha)
        // open: bigger, under the header
        val inCard = facePlace(notched, Look.Open, liveCard(null, booking, false, false))
        assertTrue(inCard.y > notched.bandHeight && inCard.size > face.size)
        // a task: rides the bar
        val progress = liveCard(null, upload, opened = true, peeks = false) as NotchCard.Progress
        val riding = facePlace(notched, Look.Open, progress)
        val done = facePlace(notched, Look.Open, progress.copy(task = upload.activeTask.copy(progress = 1f)))
        assertTrue(done.x > riding.x)
    }

    @Test fun renderStates() {
        val agent = MascotAgent()
        val islands: List<Triple<MascotAgentState, NotchCard?, NotchGeometry>> = listOf(
            Triple(MascotAgentState.Idle(), null, flat),
            Triple(MascotAgentState.Idle(), null, notched),
            Triple(upload, null, notched),
            Triple(warning, null, notched),
            Triple(signedIn, liveCard(agent, signedIn, false, false), notched),
            Triple(upload, liveCard(agent, upload, opened = false, peeks = true), notched),
            Triple(booking, liveCard(agent, booking, false, false), notched),
            Triple(warning, liveCard(agent, warning, false, false), notched),
            Triple(upload, liveCard(agent, upload, opened = true, peeks = false), flat),
            Triple(MascotAgentState.Idle(), liveCard(agent, MascotAgentState.Idle(), opened = true, peeks = false), notched),
            Triple(celebrated, liveCard(agent, celebrated, false, false), notched),
        )
        val w = stageSize(notched).width.value.toInt()
        val rowH = 184
        val h = islands.size * (rowH + 8) + 8
        ImageComposeScene(w * 2, h * 2, Density(2f)) {
            Column(Modifier.background(Color(0xFF2B2D33)).padding(top = 8.dp)) {
                islands.forEach { (state, card, geometry) ->
                    Box(Modifier.size(w.dp, rowH.dp), contentAlignment = Alignment.TopCenter) {
                        val look = if (card == null && state is MascotAgentState.Idle) Look.Hidden else lookOf(card, state, peeking = true)
                        Island(state, card, look, geometry, card?.mood ?: state.currentMood, animate = false, hands = card?.hands)
                        // the hardware notch, to see what it hides
                        if (geometry.hasNotch) {
                            Box(Modifier.size(geometry.notchWidth, geometry.bandHeight).background(Color(0xFF3A1A1A).copy(alpha = 0.55f)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }.use { scene ->
            repeat(4) { scene.render(it * 50_000_000L) } // let everything settle
            val image = scene.render(400_000_000L)
            val out = File("build/studio-face-notch").apply { mkdirs() }
            File(out, "states.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
