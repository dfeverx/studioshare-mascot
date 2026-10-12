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
import com.dfeverx.studioshare.mascot.agent.TaskPart
import com.dfeverx.studioshare.mascot.face.HandGesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    private val parallel = MascotAgentState.Working(
        AgentTask(
            "u", "Uploading photos", "32 of 50 photos", 0.64f,
            parts = listOf(TaskPart("Ava & Sam · Ceremony", "20 of 30"), TaskPart("Priya's portraits · Studio", "12 of 20")),
        ),
    )
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

    @Test fun islandOpensAndClosesOneLookAtATime() {
        // a notification: the tab, then the face out to the side, then the card
        assertEquals(Look.Compact, nextLookToward(Look.Hidden, Look.Open))
        assertEquals(Look.Open, nextLookToward(Look.Compact, Look.Open))
        // and back: the card folds before the face goes home
        assertEquals(Look.Compact, nextLookToward(Look.Open, Look.Hidden))
        assertEquals(Look.Hidden, nextLookToward(Look.Compact, Look.Hidden))
        assertEquals(Look.Open, nextLookToward(Look.Open, Look.Open))
    }

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
        // a task starting behind another (an upload while the camera is connected) peeks itself
        val camera = MascotAgentState.Working(AgentTask("c", "Camera connected"), queuedTasks = listOf(upload.activeTask))
        val peek = assertIs<NotchCard.Progress>(liveCard(null, camera, opened = false, peeks = true, peekTask = upload.activeTask))
        assertEquals(upload.activeTask, peek.task)
        // a click on a quiet island opens to the face alone, happy, with a wave — nothing to read
        val hello = assertIs<NotchCard.Hello>(liveCard(null, MascotAgentState.Idle(), opened = true, peeks = false))
        assertEquals(NotchCard.Hello, hello)
        assertEquals("happy", hello.mood)
        assertEquals(HandGesture.Wave, hello.hands)
    }

    @Test fun inboxNotificationsStayUntilDismissed() {
        val agent = MascotAgent(CoroutineScope(Dispatchers.Unconfined))
        agent.moment(MascotMoments.OrbitNewBooking, "New booking", "Priya, Sat 14 Nov", actionLabel = "Open", onAction = {}, persistent = true, id = "n1")
        agent.moment(MascotMoments.CrewAssigned, "You're on a shoot", persistent = true, id = "n2")
        val shown = assertIs<MascotAgentState.Alert>(agent.state.value)
        // the newest first, counting what waits
        assertEquals("n2", shown.alert.id)
        assertEquals(2, shown.pending)
        val card = assertIs<NotchCard.Message>(liveCard(agent, shown, false, false))
        assertEquals("1 of 2", card.counter)
        assertEquals(listOf("Dismiss"), card.buttons.map { it.label })
        assertTrue(waits(shown))
        // a transient message over them goes, and they come back
        agent.moment(MascotMoments.StateSaved, "Saved")
        val saved = assertIs<MascotAgentState.Alert>(agent.state.value)
        assertEquals("Saved", saved.alert.title)
        assertEquals(0, saved.pending)
        agent.dismissAlert(assertIs<MascotAgentState.Alert>(agent.state.value).alert.id)
        assertEquals("n2", assertIs<MascotAgentState.Alert>(agent.state.value).alert.id)
        // Dismiss takes one down; the next shows, with Open first-class
        card.buttons.single { it.label == "Dismiss" }.onClick()
        val next = assertIs<MascotAgentState.Alert>(agent.state.value)
        assertEquals("n1", next.alert.id)
        assertEquals(listOf("Dismiss", "Open"), (liveCard(agent, next, false, false) as NotchCard.Message).buttons.map { it.label })
        // read in the app: gone from the notch too
        agent.dismissAlert("n1")
        assertIs<MascotAgentState.Idle>(agent.state.value)
    }

    @Test fun parallelPartsListUnderTheBarAndTheCardGrows() {
        val folders = (1..5).map { TaskPart("Event $it · Folder", "$it of 10") }
        assertEquals(folders.take(2), partLines(folders.take(2)))
        assertEquals(folders.take(3), partLines(folders.take(3)))
        // past the room there is, the last line counts the rest
        assertEquals(listOf("Event 1 · Folder", "Event 2 · Folder", "+3 more"), partLines(folders).map { it.label })

        fun card(parts: List<TaskPart>) =
            NotchCard.Progress("task:true", "uploading", AgentTask("u", "Uploading photos", parts = parts), 0, expanded = true)
        val one = openSize(card(emptyList())).height
        assertEquals(OPEN_PROGRESS_H, one)
        assertTrue(openSize(card(folders.take(2))).height > one)
        assertEquals(openSize(card(folders.take(3))).height, openSize(card(folders)).height)
    }

    @Test fun aClickBringsTheLastMessageBack() {
        val agent = MascotAgent(CoroutineScope(Dispatchers.Unconfined))
        // nothing went by: a click has nothing to bring back (the hello opens instead)
        assertFalse(agent.recall())
        agent.moment(MascotMoments.UploadDone, "All photos uploaded!", "Ava & Sam · Ceremony · 20 photos")
        val first = assertIs<MascotAgentState.Alert>(agent.state.value).alert
        // it folds (or is closed), and a click shows it again — not a fresh notification
        var notified = 0
        agent.onAlert { notified++ }
        agent.dismissAlert(first.id)
        assertIs<MascotAgentState.Idle>(agent.state.value)
        assertTrue(agent.recall())
        assertEquals(first, assertIs<MascotAgentState.Alert>(agent.state.value).alert)
        assertEquals(0, notified)
        // a newer message replaces it
        agent.moment(MascotMoments.CullStarted, "Culling started")
        agent.dismissAlert(assertIs<MascotAgentState.Alert>(agent.state.value).alert.id)
        assertTrue(agent.recall())
        assertEquals("Culling started", assertIs<MascotAgentState.Alert>(agent.state.value).alert.title)
    }

    @Test fun aMessageOutlastsATasksProgress() {
        val agent = MascotAgent(CoroutineScope(Dispatchers.Unconfined))
        agent.reportProgress("up", "Uploading photos", "Ava & Sam · Ceremony · 3 of 20 photos", 0.15f)
        agent.moment(MascotMoments.StateSaved, "Saved")
        // the upload ticks on; the message stays its time
        agent.reportProgress("up", "Uploading photos", "Ava & Sam · Ceremony · 4 of 20 photos", 0.2f)
        assertEquals("Saved", assertIs<MascotAgentState.Alert>(agent.state.value).alert.title)
        // closed, the upload shows again, with where it is
        agent.dismissAlert(assertIs<MascotAgentState.Alert>(agent.state.value).alert.id)
        val working = assertIs<MascotAgentState.Working>(agent.state.value)
        assertEquals("Ava & Sam · Ceremony · 4 of 20 photos", working.activeTask.detail)
    }

    @Test fun aClickOnAnAlertWithAnActionTakesIt() {
        val agent = MascotAgent(CoroutineScope(Dispatchers.Unconfined))
        var opened = 0
        agent.moment(MascotMoments.UploadDone, "All photos uploaded!", "120 photos", actionLabel = "Open", onAction = { opened++ })
        val card = assertIs<NotchCard.Message>(liveCard(agent, agent.state.value, false, false))
        // it goes by itself, so its action is the only button
        assertEquals(listOf("Open"), card.buttons.map { it.label })
        // the whole card is the button: a click opens the app and the card goes
        card.onClick!!()
        assertEquals(1, opened)
        assertIs<MascotAgentState.Idle>(agent.state.value)
        // nothing to do, nothing to click
        agent.moment(MascotMoments.StateSaved, "Saved")
        assertNull(assertIs<NotchCard.Message>(liveCard(agent, agent.state.value, false, false)).onClick)
    }

    @Test fun liveProcessesKeepTheIslandCompact() {
        // a task keeps the island compact even while a notification is what the agent shows
        val pinned = MascotAgentState.Alert(AgentAlert("p", "New booking", "", persistent = true))
        assertEquals(Look.Compact, lookOf(null, pinned, peeking = false))
        assertEquals(Look.Compact, lookOf(null, MascotAgentState.Idle(), peeking = false, busy = true))
    }

    @Test fun theHelloIsJustTheFace() {
        val size = islandSize(notched, Look.Open, NotchCard.Hello)
        assertEquals(DpSize(HELLO_W, HELLO_H), size)
        assertTrue(size.width > notched.notchWidth && stageSize(notched).height >= size.height)
        val face = facePlace(notched, Look.Open, NotchCard.Hello)
        assertEquals(HELLO_W / 2, face.x)
        assertTrue(face.y - face.size / 2 >= notched.bandHeight)
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
        // hidden: wider than the notch, so it shows; compact: the same; open: 640 wide
        assertEquals(DpSize(185.dp + NOTCH_EAR * 2, 38.dp), islandSize(notched, Look.Hidden, null))
        assertTrue(islandSize(notched, Look.Hidden, null).width > notched.notchWidth)
        // compact never grows under the camera: the mark goes in the right ear
        assertEquals(DpSize(185.dp + NOTCH_EAR * 2, 38.dp), islandSize(notched, Look.Compact, null))
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
        // the whole stage stays a strip under the menu bar, with room for a task listing its parts
        assertTrue(stageSize(notched).height < 260.dp)
    }

    @Test fun faceSitsAtTheLeftEndAndMovesIntoTheCard() {
        // hidden and compact alike: the face in the notch's left ear, clear of the camera
        for (look in listOf(Look.Hidden, Look.Compact)) {
            val ear = facePlace(notched, look, null)
            assertEquals(1f, ear.alpha)
            assertTrue(ear.x - ear.size / 2 >= 0.dp && ear.x + ear.size / 2 <= NOTCH_EAR, "face must sit beside the camera")
            assertTrue(ear.y + ear.size / 2 <= notched.bandHeight)
        }
        // in a flat tab it shows too, at the left end
        val tab = facePlace(flat, Look.Hidden, null)
        assertEquals(1f, tab.alpha)
        assertTrue(tab.x < islandSize(flat, Look.Hidden, null).width / 2)
        val face = facePlace(notched, Look.Compact, null)
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
            Triple(MascotAgentState.Idle(), null, notched),
            Triple(MascotAgentState.Alert(AgentAlert("p", "New booking!", "Priya booked a portrait session.", label = "Orbit", persistent = true), pending = 2), null, notched),
            Triple(celebrated, liveCard(agent, celebrated, false, false), notched),
            Triple(parallel, liveCard(agent, parallel, opened = true, peeks = false), notched),
        )
        val w = stageSize(notched).width.value.toInt()
        val rowH = 240
        val h = islands.size * (rowH + 8) + 8
        ImageComposeScene(w * 2, h * 2, Density(2f)) {
            Column(Modifier.background(Color(0xFF2B2D33)).padding(top = 8.dp)) {
                islands.forEach { (state, card, geometry) ->
                    Box(Modifier.size(w.dp, rowH.dp), contentAlignment = Alignment.TopCenter) {
                        val look = if (card == null && state is MascotAgentState.Idle) Look.Hidden else lookOf(card, state, peeking = true)
                        val mood = card?.mood ?: if (state is MascotAgentState.Idle) "sleepy" else state.currentMood
                        Island(state, card, look, geometry, mood, animate = false, hands = card?.hands)
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
