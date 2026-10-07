package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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
import com.dfeverx.studioshare.mascot.face.StudioFace
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
 * The notch companion: what it opens to say, how big, and where the face sits — plus a render of
 * the island in each state to `build/studio-face-notch/states.png`, for eyeballing without a window.
 */
class StudioFaceNotchCompanionTest {
    private val notched = NotchGeometry(0, 0, 1512, 185.dp, 38.dp)
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
        assertEquals(listOf("Manage", "Dismiss"), warn.buttons.map { it.label })
        assertEquals(Tone.Warn, warn.detailTone)
        assertIs<NotchCard.Progress>(liveCard(null, upload, opened = false, peeks = true))
        assertIs<NotchCard.Progress>(liveCard(null, upload, opened = true, peeks = false))
    }

    @Test fun clickingAQuietIslandWavesHelloWithoutWords() {
        assertEquals(NotchCard.Hello, liveCard(null, MascotAgentState.Idle(), opened = true, peeks = false))
        assertEquals(HandGesture.Wave, NotchCard.Hello.hands)
        // the face drops straight out of the notch, bigger than at rest
        val rest = facePlace(notched, Look.Rest, null, restSize(notched), DpSize.Zero)
        val hello = facePlace(notched, Look.Card, NotchCard.Hello, DpSize.Zero, DpSize(150.dp, 54.dp))
        assertEquals(0.dp, hello.dx)
        assertTrue(hello.y > notched.bandHeight && hello.size > rest.size)
    }

    @Test fun everythingOpensDownIntoACard() {
        assertEquals(Look.Card, lookOf(liveCard(null, signedIn, false, false)))
        assertEquals(Look.Card, lookOf(liveCard(null, upload, opened = false, peeks = true)))
        assertEquals(Look.Card, lookOf(liveCard(null, warning, false, false)))
        assertEquals(Look.Rest, lookOf(null))
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

    @Test fun islandIsWiderThanTheNotchWithRoomForTheFace() {
        val rest = restSize(notched)
        // the notch, a little slack either side of it, and a wide ear each side
        assertEquals(185.dp + NOTCH_SLACK * 2 + EAR * 2, rest.width)
        assertEquals(38.dp, rest.height)
        // the face sits in the outer left corner, entirely clear of the notch
        val face = facePlace(notched, Look.Rest, null, rest, DpSize.Zero)
        val faceRight = rest.width / 2 + face.dx + face.size / 2
        val notchLeft = (rest.width - 185.dp) / 2
        assertTrue(faceRight < notchLeft - 20.dp, "face must sit well left of the notch")
        assertTrue(rest.width / 2 + face.dx - face.size / 2 <= 8.dp, "face hugs the left corner")
        // and fills the band without spilling out of it
        assertTrue(face.size >= 34.dp && face.size <= notched.bandHeight)
        // no notch: just the two ears
        assertEquals(EAR * 2, restSize(notched.copy(notchWidth = 0.dp)).width)
    }

    @Test fun openIslandIsWideAndShort() {
        // a card adds only its own height under the band, and never shrinks the island
        assertEquals(DpSize(560.dp, 38.dp + 48.dp + 10.dp), openSize(notched, DpSize(540.dp, 48.dp)))
        assertEquals(restSize(notched).width, openSize(notched, DpSize(100.dp, 48.dp)).width)
        // the whole stage stays a strip under the menu bar
        assertTrue(stageSize(notched).height < 170.dp)
    }

    @Test fun faceGoesFromTheCornerIntoTheCardAndRidesTheBar() {
        val rest = facePlace(notched, Look.Rest, null, restSize(notched), DpSize.Zero)
        val card = liveCard(null, booking, false, false)
        val inCard = facePlace(notched, Look.Card, card, DpSize(560.dp, 110.dp), DpSize(540.dp, 58.dp))
        assertTrue(inCard.y > notched.bandHeight && inCard.size > rest.size)
        val note = liveCard(null, signedIn, false, false)
        assertTrue(facePlace(notched, Look.Card, note, DpSize.Zero, DpSize(400.dp, 44.dp)).size < inCard.size)
        val progress = liveCard(null, upload, opened = true, peeks = false) as NotchCard.Progress
        val riding = facePlace(notched, Look.Card, progress, DpSize.Zero, DpSize(520.dp, 52.dp))
        val done = facePlace(notched, Look.Card, progress.copy(task = upload.activeTask.copy(progress = 1f)), DpSize.Zero, DpSize(520.dp, 52.dp))
        assertTrue(done.dx > riding.dx)
    }

    @Test fun renderStates() {
        val agent = MascotAgent()
        val islands: List<Pair<MascotAgentState, NotchCard?>> = listOf(
            MascotAgentState.Idle() to null,
            upload to null,
            warning to null,
            signedIn to liveCard(agent, signedIn, false, false),
            upload to liveCard(agent, upload, opened = false, peeks = true),
            booking to liveCard(agent, booking, false, false),
            warning to liveCard(agent, warning, false, false),
            upload to liveCard(agent, upload, opened = true, peeks = false),
            MascotAgentState.Idle() to liveCard(agent, MascotAgentState.Idle(), opened = true, peeks = false),
            celebrated to liveCard(agent, celebrated, false, false),
        )
        val w = stageSize(notched).width.value.toInt()
        val rowH = 110
        val h = islands.size * (rowH + 8) + 8
        ImageComposeScene(w * 2, h * 2, Density(2f)) {
            Column(Modifier.background(Color(0xFF2B2D33)).padding(top = 8.dp)) {
                islands.forEach { (state, card) ->
                    Box(Modifier.size(w.dp, rowH.dp), contentAlignment = Alignment.TopCenter) {
                        // the hardware notch, to see what it hides
                        Island(state, card, notched, card?.mood ?: state.currentMood, animate = false, hands = card?.hands)
                        Box(Modifier.size(notched.notchWidth, notched.bandHeight).background(Color(0xFF3A1A1A).copy(alpha = 0.55f)))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }.use { scene ->
            repeat(4) { scene.render(it * 50_000_000L) } // let the cards and wings measure and settle
            val image = scene.render(400_000_000L)
            val out = File("build/studio-face-notch").apply { mkdirs() }
            File(out, "states.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
