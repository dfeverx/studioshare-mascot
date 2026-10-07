package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.dfeverx.studioshare.mascot.agent.AgentTask
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import com.dfeverx.studioshare.mascot.agent.MascotAgentState
import com.dfeverx.studioshare.mascot.face.StudioFace
import com.dfeverx.studioshare.mascot.face.gazeToward
import kotlinx.coroutines.delay
import java.awt.MouseInfo
import kotlin.math.hypot

internal const val NOTCH_WINDOW_TITLE = "StudioShare Notch Companion"

// ---- geometry (laid out after Coucou's island: hidden, compact, open) -------------------------

/** Without a notch the hidden island is a small tab this size at the top centre. */
private val FLAT_W = 80.dp
private val FLAT_BAND = 28.dp
/** Compact grows the hidden island by this much each side: room for the face and a status mark. */
internal val COMPACT_GROW = 80.dp
/** Where the face and the status mark sit in compact, in from the island's left and right edges. */
private val COMPACT_INSET = 40.dp
/** The open island: always this wide, as tall as what it shows. */
internal val OPEN_W = 640.dp
internal val OPEN_H = 160.dp
internal val OPEN_PROGRESS_H = 176.dp

/** The open island's column: a header row under the top edge, then the card. */
private val HEADER_TOP = 8.dp
private val HEADER_H = 34.dp
private val CARD_TOP = HEADER_TOP + HEADER_H
private val CARD_BOTTOM_PAD = 10.dp
private val CARD_SIDE_PAD = 10.dp

/** The face in a message card, centred this far in from the island's left edge. */
private val MESSAGE_FACE_X = 64.dp
private val MESSAGE_FACE = 56.dp
/** Text starts here in the card, clear of the face. */
private val MESSAGE_LEAD = 106.dp

/** The progress card: padding either side of the bar, the bar's top in the card, its height. */
private val BAR_PAD = 26.dp
private val BAR_Y = 86.dp
private val BAR_H = 6.dp
private val FACE_RIDER = 28.dp

/** Bottom corners: the notch's own curve until it opens, then rounder. */
private val REST_CORNER = 14.dp
private val OPEN_CORNER = 22.dp
private val CARD_CORNER = 20.dp

/** Room around the island for the spring to overshoot into. */
private val WINDOW_MARGIN = 16.dp

// ---- the look --------------------------------------------------------------------------------

internal val INK = Color(0xFF000000)
private val CARD = Color(0xFF141518)
private val CARD_EDGE = Color.White.copy(alpha = 0.035f)
private val TEXT = Color(0xFFF5F6F8)
private val MUTED = Color(0xFF8E939C)
private val DIM = Color(0xFF6B7079)
private val BAR_LABEL = Color(0xFFA9ADB5)
private val BAD_TEXT = Color(0xFFFF8D97)
private val WARN_TEXT = Color(0xFFF7C46C)
private val TRACK = Color.White.copy(alpha = 0.09f)
private val FILL_START = Color(0xFF1FA87A)
private val FILL_END = Color(0xFF34D399)
private val FILL_GLOW = Color(0xFF6EE7B7)

// ---- motion ----------------------------------------------------------------------------------

/** Opening: a spring with a little overshoot (SwiftUI's response 0.5 s, damping 0.72). */
private val openSpring: AnimationSpec<Dp> = spring(dampingRatio = 0.72f, stiffness = 158f)
/** Closing: a quick ease that settles without any bounce. */
private val closeTween: AnimationSpec<Dp> = tween(340, easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f))
private val easeIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)

/** How long an open island waits after the mouse leaves before it folds back to compact. */
private const val COLLAPSE_AFTER_LEAVE_MS = 15_000L
/** How long compact stays after the mouse leaves before it hides back into the notch. */
private const val HIDE_AFTER_LEAVE_MS = 60_000L
/** A new task opens with its progress this long, then folds back to a ring in compact. */
private const val TASK_PEEK_MS = 3_500L
/** Resting the mouse on the face this long makes it blush; then not again for a while. */
private const val LOVE_AFTER_MS = 1_900L
private const val LOVE_COOLDOWN_MS = 6_000L
private const val LOVE_HOLD_MS = 2_500L

/** The companion's window, so a [Face] inside it can work out where it sits on screen. */
private val LocalNotchWindow = compositionLocalOf<java.awt.Window?> { null }
/** The companion's sounds, for the buttons; null (the tests' render) is silent. */
private val LocalNotchSounds = compositionLocalOf<NotchSoundPlayer?> { null }

// ---- tone: the colour a mood washes the card with ------------------------------------------

/** Colour family of a mood, for the card's wash, its dot and the badge on the face. */
internal enum class Tone(val color: Color, val wash: Float) {
    Good(Color(0xFF34D399), 0.50f),
    Warn(Color(0xFFF5A524), 0.42f),
    Bad(Color(0xFFF4505E), 0.55f),
    Busy(Color(0xFF6366F1), 0.50f),
    Love(Color(0xFFF472B6), 0.55f),
    Ask(Color(0xFF22D3EE), 0.38f),
    Calm(Color.White, 0.08f),
}

internal fun toneOf(mood: String): Tone = when (mood) {
    "celebrating", "happy", "proud", "connected", "ok", "capturing", "goodbye" -> Tone.Good
    "careful", "hot", "locked" -> Tone.Warn
    "oops", "sad", "disconnected" -> Tone.Bad
    "uploading", "sending", "carrying", "searching", "scanning", "focused", "connecting", "waiting", "patient" -> Tone.Busy
    "shy", "excited", "glance" -> Tone.Love
    "curious", "thinking" -> Tone.Ask
    else -> Tone.Calm
}

// ---- what the open island shows -------------------------------------------------------------

/** A button on a card. Equal by what it shows, so a card rebuilt with fresh callbacks is the same card. */
internal class CardButton(val label: String, val primary: Boolean, val onClick: () -> Unit) {
    override fun equals(other: Any?) = other is CardButton && other.label == label && other.primary == primary
    override fun hashCode() = label.hashCode() * 31 + primary.hashCode()
}

/** One thing the island opens to say. */
internal sealed interface NotchCard {
    val mood: String
    val key: String

    /** A message: who it's from, what happened, maybe a line more and a button or two. */
    data class Message(
        override val key: String,
        override val mood: String,
        val label: String?,
        val title: String,
        val detail: String? = null,
        val detailTone: Tone? = null,
        val buttons: List<CardButton> = emptyList(),
        /** Small and dim after the label, e.g. "1 of 3". */
        val counter: String? = null,
    ) : NotchCard {
        /** Just a line: no detail, no buttons. */
        val isNote get() = detail == null && buttons.isEmpty()
    }

    /** A task underway: its name and progress over a bar the face rides. */
    data class Progress(
        override val key: String,
        override val mood: String,
        val task: AgentTask,
        val queued: Int,
        val expanded: Boolean,
    ) : NotchCard
}

/** How the island is: hidden in the notch, compact around it, or open with a card. */
internal enum class Look { Hidden, Compact, Open }

/**
 * The look for what is showing: a card opens the island; otherwise a hover ([peeking]) or something
 * underway or waiting keeps it compact, so its mark shows; otherwise it hides in the notch.
 */
internal fun lookOf(card: NotchCard?, state: MascotAgentState, peeking: Boolean): Look = when {
    card != null -> Look.Open
    peeking || state is MascotAgentState.Working || state is MascotAgentState.Warning -> Look.Compact
    else -> Look.Hidden
}

/**
 * What the island should be showing for [state], or null to stay shut. A warning or alert speaks
 * for itself; a task shows its progress while it [peeks] (just started), and when [opened] by a
 * click; a click on a quiet island says so.
 */
internal fun liveCard(agent: MascotAgent?, state: MascotAgentState, opened: Boolean, peeks: Boolean): NotchCard? =
    when (state) {
        is MascotAgentState.Warning -> {
            val w = state.activeWarning
            val count = state.allWarnings.size
            val action = w.onAction
            NotchCard.Message(
                key = "warning:${w.id}",
                mood = w.mood,
                label = w.label ?: "Heads up",
                counter = "1 of $count".takeIf { count > 1 },
                title = w.title,
                detail = w.message.takeIf { it.isNotBlank() },
                detailTone = if (toneOf(w.mood) == Tone.Bad) Tone.Bad else Tone.Warn,
                buttons = listOfNotNull(
                    CardButton("Dismiss", primary = false) { agent?.dismissWarning(w.id) },
                    if (w.actionLabel != null && action != null) {
                        CardButton(w.actionLabel, primary = true) { action(); agent?.dismissWarning(w.id) }
                    } else null,
                ),
            )
        }
        is MascotAgentState.Alert -> {
            val a = state.alert
            val action = a.onAction
            if (a.quiet) null
            else NotchCard.Message(
                key = "alert:${a.id}",
                mood = a.mood,
                label = a.label,
                title = a.title,
                detail = a.message.takeIf { it.isNotBlank() },
                detailTone = Tone.Bad.takeIf { toneOf(a.mood) == Tone.Bad },
                buttons = if (a.actionLabel != null && action != null) listOf(
                    CardButton(a.actionLabel, primary = true) { action(); agent?.dismissAlert(a.id) },
                    CardButton("OK", primary = false) { agent?.dismissAlert(a.id) },
                ) else emptyList(),
            )
        }
        is MascotAgentState.Working ->
            if (opened || peeks) {
                NotchCard.Progress("task:$opened", state.currentMood, state.activeTask, state.queuedTasks.size, expanded = opened)
            } else null
        is MascotAgentState.Idle ->
            if (opened) NotchCard.Message("idle", "idle", "StudioShare", "All quiet. I'll tell you when something happens.")
            else null
    }

/** The sound for the island turning [from] one look [to] another while showing [card]; null for none. */
internal fun soundFor(from: Look, to: Look, card: NotchCard?): NotchSound? = when {
    to == Look.Open && from != Look.Open -> when {
        card is NotchCard.Message && card.key.startsWith("warning:") -> NotchSound.Attention
        card is NotchCard.Message && card.key.startsWith("alert:") -> when (toneOf(card.mood)) {
            Tone.Good -> NotchSound.Done
            Tone.Bad -> NotchSound.Error
            Tone.Warn -> NotchSound.Attention
            else -> NotchSound.Open
        }
        else -> NotchSound.Open
    }
    from == Look.Open && to != Look.Open -> NotchSound.Close
    from == Look.Hidden && to == Look.Compact -> NotchSound.Peek
    else -> null
}

/**
 * A notch companion: the StudioShare face living in the notch, laid out after Coucou's island.
 *
 * Hidden, it is exactly the notch (a small tab at the top centre on a screen without one). Hovering
 * grows it into compact — 80 pt more each side, the face in the left one watching the cursor and a
 * progress ring or an amber pulse in the right — and clicking opens it: 640 wide, a header row with
 * the mute toggle, and one card washed with the mood's colour. Alerts and warnings open it on their
 * own, each with its own sound; it folds back to compact 15 s after the mouse leaves, and hides 60 s
 * after that.
 *
 * It reads [MascotAgent.state] and nothing else: [MascotAgent.moment] for what happens in the app
 * (with the spec's default line, or your own), [MascotAgent.reportProgress] for tasks,
 * [MascotAgent.postWarning] for what needs the user.
 *
 * The window is a fixed transparent stage above the menu bar and the island animates inside it,
 * so opening never resizes a native window; everything but the island lets clicks through.
 */
@Composable
fun StudioFaceNotchCompanion(
    agent: MascotAgent,
    visible: Boolean,
    onClose: () -> Unit,
    onOpenMainApp: () -> Unit,
    sounds: NotchSoundPlayer = remember { NotchSoundPlayer() },
) {
    if (!visible) return
    val state by agent.state.collectAsState()
    val notch = remember { MacNotch.geometry() }

    var opened by remember { mutableStateOf(false) }
    var peeking by remember { mutableStateOf(false) }
    var inIsland by remember { mutableStateOf(false) }
    var overFace by remember { mutableStateOf(false) }
    var love by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(!sounds.enabled) }

    // a task that has just started opens with its progress
    val activeTask = (state as? MascotAgentState.Working)?.activeTask?.id
    var peekTask by remember { mutableStateOf<String?>(null) }
    var seenTasks by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(activeTask) {
        if (activeTask != null && activeTask !in seenTasks) {
            seenTasks = seenTasks + activeTask
            peekTask = activeTask
            delay(TASK_PEEK_MS)
            if (peekTask == activeTask) peekTask = null
        }
    }

    // what is showing: the live card, or — while the mouse is on it — the last one, so it can be read
    val live = liveCard(agent, state, opened, peeks = peekTask != null && peekTask == activeTask)
    var shown by remember { mutableStateOf<NotchCard?>(null) }
    LaunchedEffect(live, inIsland) {
        if (live != null) shown = live
        else if (shown != null && !inIsland) shown = null
    }
    // hovering peeks; leaving folds an open island back after a while, and hides compact after longer
    LaunchedEffect(inIsland) {
        if (inIsland) {
            peeking = true
            return@LaunchedEffect
        }
        if (opened) {
            delay(COLLAPSE_AFTER_LEAVE_MS)
            opened = false
        }
        delay(HIDE_AFTER_LEAVE_MS)
        peeking = false
    }
    // a face the mouse rests on blushes, now and then
    var lastLove by remember { mutableStateOf(0L) }
    LaunchedEffect(overFace) {
        if (!overFace) return@LaunchedEffect
        delay(LOVE_AFTER_MS)
        val now = System.currentTimeMillis()
        if (state is MascotAgentState.Idle && now - lastLove > LOVE_COOLDOWN_MS) {
            lastLove = now
            love = true
            delay(LOVE_HOLD_MS)
            love = false
        }
    }

    val look = lookOf(shown, state, peeking)
    var lastLook by remember { mutableStateOf(Look.Hidden) }
    LaunchedEffect(look) {
        soundFor(lastLook, look, shown)?.let(sounds::play)
        lastLook = look
    }

    val stage = stageSize(notch)
    val windowState = rememberWindowState(size = stage, position = topCentre(notch, stage.width))

    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = NOTCH_WINDOW_TITLE,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(Unit) {
            // AWT can only float the window, which leaves it under the menu bar (and the notch);
            // lift it above, then pin it back to the very top in case AWT kept it below the bar
            MacNotch.raiseAboveMenuBar(NOTCH_WINDOW_TITLE)
            windowState.position = topCentre(notch, stage.width)
        }
        val metrics = remember { IslandMetrics() }

        // 60 Hz: where the mouse is against the island and the face. Pointer events can't do this:
        // the window ignores the mouse everywhere but the island.
        LaunchedEffect(Unit) {
            var clickable = false
            while (true) {
                val m = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                if (m != null) {
                    val x = m.x - window.x.toFloat()
                    val y = m.y - window.y.toFloat()
                    val left = (stage.width.value - metrics.width) / 2
                    val slack = 6f
                    val now = x >= left - slack && x <= left + metrics.width + slack && y <= metrics.height + slack
                    if (now != inIsland) inIsland = now
                    if (now != clickable) {
                        clickable = now
                        MacNotch.setClickThrough(NOTCH_WINDOW_TITLE, !now)
                    }
                    val over = hypot(x - (left + metrics.faceX), y - metrics.faceY) <= metrics.faceSize / 2 + 4
                    if (over != overFace) overFace = over
                }
                delay(16)
            }
        }

        val faceScale by animateFloatAsState(if (overFace) 1.08f else 1f, spring(0.6f, Spring.StiffnessMedium))
        CompositionLocalProvider(LocalNotchWindow provides window, LocalNotchSounds provides sounds) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Island(
                    state = state,
                    card = shown,
                    look = look,
                    notch = notch,
                    mood = if (love) "shy" else shown?.mood ?: state.currentMood,
                    faceScale = faceScale,
                    metrics = metrics,
                    muted = muted,
                    onToggleMute = {
                        muted = !muted
                        sounds.enabled = !muted
                        if (!muted) sounds.play(NotchSound.Tap)
                    },
                    onRestClick = { opened = true },
                    onFaceClick = if (shown != null) onOpenMainApp else ({ opened = true }),
                )
            }
        }
    }
}

/** Where the island and its face are right now, in dp from the island's top-left; read by the mouse poll. */
internal class IslandMetrics {
    var width = 0f
    var height = 0f
    var faceX = 0f
    var faceY = 0f
    var faceSize = 0f
}

/**
 * The island itself: black, flush with the top, the notch in its middle. Shared by the companion's
 * window and the tests' render, so the picture is what ships. [animate] false snaps every change.
 */
@Composable
internal fun Island(
    state: MascotAgentState,
    card: NotchCard?,
    look: Look,
    notch: NotchGeometry,
    mood: String,
    animate: Boolean = true,
    faceScale: Float = 1f,
    metrics: IslandMetrics? = null,
    muted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onRestClick: () -> Unit = {},
    onFaceClick: () -> Unit = {},
) {
    val target = islandSize(notch, look, card)
    val open = look == Look.Open
    val spec: AnimationSpec<Dp> = if (!animate) snap() else if (open) openSpring else closeTween
    val islandW by animateDpAsState(target.width, spec)
    val islandH by animateDpAsState(target.height, spec)
    val corner by animateDpAsState(if (open) OPEN_CORNER else REST_CORNER.coerceAtMost(target.height / 2), spec)

    val place = facePlace(notch, look, card)
    val faceX by animateDpAsState(place.x, spec)
    val faceY by animateDpAsState(place.y, spec)
    val faceSize by animateDpAsState(place.size, spec)
    val faceAlpha by animateFloatAsState(place.alpha, if (animate) tween(250) else snap())
    metrics?.let {
        it.width = islandW.value
        it.height = islandH.value
        it.faceX = faceX.value
        it.faceY = faceY.value
        it.faceSize = faceSize.value * faceScale
    }

    Box(
        Modifier
            .size(islandW, islandH)
            .clip(RoundedCornerShape(bottomStart = corner, bottomEnd = corner))
            .background(INK)
            .then(
                if (open) Modifier
                else Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onRestClick),
            ),
    ) {
        // compact: a mark on the right, no words
        Layer(visible = look == Look.Compact, animate = animate, keep = Unit) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = -COMPACT_INSET + 14.dp).size(28.dp, bandHeight(notch)),
                contentAlignment = Alignment.Center,
            ) { CompactMark(state) }
        }
        // open: the header row and the card under it, laid out at the open size; the island's clip reveals them
        Layer(visible = open, animate = animate, keep = card.takeIf { open }) { c ->
            if (c != null) {
                val size = islandSize(notch, Look.Open, c)
                Box(Modifier.align(Alignment.TopCenter).size(size)) {
                    Header(c, muted, onToggleMute)
                    CardView(
                        c,
                        Modifier
                            .padding(start = CARD_SIDE_PAD, end = CARD_SIDE_PAD, top = CARD_TOP, bottom = CARD_BOTTOM_PAD)
                            .fillMaxSize(),
                    )
                }
            }
        }
        // one face for every look, so it glides between compact and the card
        val tone = toneOf(mood)
        Box(
            Modifier
                .offset(faceX - faceSize / 2, faceY - faceSize / 2)
                .size(faceSize)
                .graphicsLayer { scaleX = faceScale; scaleY = faceScale; alpha = faceAlpha },
        ) {
            Face(state, mood, animate, onFaceClick)
            if (tone != Tone.Calm && look != Look.Hidden) StatusBadge(tone, faceSize)
        }
    }
}

/**
 * A layer of the island that comes in like Coucou's views — after the island has started to open,
 * a spring from 97 % scale and transparent — and goes out quickly before it closes. While going out
 * it keeps showing [keep]'s last non-null value.
 */
@Composable
private fun <T> BoxScope.Layer(
    visible: Boolean,
    animate: Boolean,
    keep: T?,
    content: @Composable BoxScope.(T?) -> Unit,
) {
    var last by remember { mutableStateOf<T?>(null) }
    if (keep != null) last = keep
    val t = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible, animate) {
        when {
            !animate -> t.snapTo(if (visible) 1f else 0f)
            visible -> {
                delay(160)
                t.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 247f))
            }
            else -> t.animateTo(0f, tween(160, easing = easeIn))
        }
    }
    if (t.value < 0.01f) return
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                alpha = t.value.coerceIn(0f, 1f)
                val s = 0.97f + 0.03f * t.value
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
    ) { content(last) }
}

// ---- geometry -------------------------------------------------------------------------------

internal fun bandHeight(notch: NotchGeometry): Dp = if (notch.hasNotch) notch.bandHeight else FLAT_BAND

/** Hidden: exactly the notch, or a small tab without one. */
internal fun hiddenSize(notch: NotchGeometry): DpSize =
    if (notch.hasNotch) DpSize(notch.notchWidth, notch.bandHeight) else DpSize(FLAT_W, FLAT_BAND)

/** Compact: the hidden island with room for the face on the left and a mark on the right. */
internal fun compactSize(notch: NotchGeometry): DpSize =
    hiddenSize(notch).let { DpSize(it.width + COMPACT_GROW * 2, it.height) }

/** Open: 640 wide, and tall enough for the header and the card. */
internal fun openSize(card: NotchCard?): DpSize = DpSize(OPEN_W, if (card is NotchCard.Progress) OPEN_PROGRESS_H else OPEN_H)

internal fun islandSize(notch: NotchGeometry, look: Look, card: NotchCard?): DpSize = when (look) {
    Look.Hidden -> hiddenSize(notch)
    Look.Compact -> compactSize(notch)
    Look.Open -> openSize(card)
}

/** The fixed window the island lives in: room for the widest, tallest island. */
internal fun stageSize(notch: NotchGeometry): DpSize = DpSize(
    maxOf(OPEN_W, compactSize(notch).width) + WINDOW_MARGIN * 2,
    OPEN_PROGRESS_H + WINDOW_MARGIN,
)

/** Where the face sits, its centre [x] and [y] from the island's top-left. */
internal data class FacePlace(val x: Dp, val y: Dp, val size: Dp, val alpha: Float = 1f)

/** The face's box in compact (and in the flat tab): as tall as the band allows. */
private fun bandFace(notch: NotchGeometry) = (bandHeight(notch) - 2.dp).coerceAtMost(34.dp)

internal fun facePlace(notch: NotchGeometry, look: Look, card: NotchCard?): FacePlace {
    val band = bandHeight(notch)
    return when {
        // behind the camera there's nothing to see: shrink it away where compact will grow it from
        look == Look.Hidden && notch.hasNotch -> FacePlace(COMPACT_INSET + 6.dp, band / 2, 6.dp, alpha = 0f)
        look == Look.Hidden -> FacePlace(FLAT_W / 2, band / 2, bandFace(notch))
        look == Look.Compact || card == null -> FacePlace(COMPACT_INSET, band / 2, bandFace(notch))
        card is NotchCard.Progress -> {
            // riding the bar's leading edge
            val barW = OPEN_W - CARD_SIDE_PAD * 2 - BAR_PAD * 2
            val p = if (card.task.isIndeterminate) 0.5f else easeOutProgress(card.task.progress.coerceIn(0f, 1f))
            FacePlace(CARD_SIDE_PAD + BAR_PAD + barW * p, CARD_TOP + BAR_Y + BAR_H / 2, FACE_RIDER)
        }
        else -> {
            val cardH = openSize(card).height - CARD_TOP - CARD_BOTTOM_PAD
            FacePlace(MESSAGE_FACE_X, CARD_TOP + cardH / 2, MESSAGE_FACE)
        }
    }
}

/** How full the bar draws for [p]: eased out, so the start of an upload reads as progress. */
internal fun easeOutProgress(p: Float) = p * (2 - p)

/** Top centre of the screen, flush with its top edge: over the menu bar, and around the notch. */
private fun topCentre(notch: NotchGeometry, width: Dp) = WindowPosition(
    x = (notch.screenX + (notch.screenWidth - width.value) / 2).dp,
    y = notch.screenY.dp,
)

// ---- header ---------------------------------------------------------------------------------

/** The open island's top row: where it's from on the left, the mute toggle on the right. */
@Composable
private fun BoxScope.Header(card: NotchCard, muted: Boolean, onToggleMute: () -> Unit) {
    Row(
        Modifier.align(Alignment.TopStart).padding(top = HEADER_TOP).height(HEADER_H).fillMaxWidth()
            .padding(start = 18.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tone = toneOf(card.mood)
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (tone == Tone.Calm) MUTED else tone.color))
        Spacer(Modifier.width(7.dp))
        Text("StudioShare", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        SpeakerToggle(muted, onToggleMute)
    }
}

@Composable
private fun SpeakerToggle(muted: Boolean, onClick: () -> Unit) {
    Canvas(
        Modifier.size(22.dp).clip(CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(4.dp),
    ) {
        val s = size.minDimension
        val body = Path().apply {
            moveTo(s * 0.08f, s * 0.36f); lineTo(s * 0.28f, s * 0.36f); lineTo(s * 0.52f, s * 0.14f)
            lineTo(s * 0.52f, s * 0.86f); lineTo(s * 0.28f, s * 0.64f); lineTo(s * 0.08f, s * 0.64f); close()
        }
        drawPath(body, MUTED)
        val w = 1.4.dp.toPx()
        if (muted) {
            drawLine(MUTED, Offset(s * 0.66f, s * 0.36f), Offset(s * 0.94f, s * 0.64f), w, StrokeCap.Round)
            drawLine(MUTED, Offset(s * 0.94f, s * 0.36f), Offset(s * 0.66f, s * 0.64f), w, StrokeCap.Round)
        } else {
            for (r in listOf(0.2f, 0.36f)) {
                drawArc(
                    MUTED, -45f, 90f, false,
                    topLeft = Offset(s * 0.5f - s * r, s * 0.5f - s * r), size = Size(s * r * 2, s * r * 2),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
    }
}

// ---- card -----------------------------------------------------------------------------------

/** The card: a lifted panel washed from below with its mood's colour. */
@Composable
internal fun CardView(card: NotchCard, modifier: Modifier = Modifier) {
    val tone = if (card is NotchCard.Progress) Tone.Good else toneOf(card.mood)
    val wash = if (card is NotchCard.Progress) {
        if (!card.task.isIndeterminate && card.task.progress >= 1f) 0.28f else 0.14f
    } else tone.wash
    val shape = RoundedCornerShape(CARD_CORNER)
    Box(
        modifier
            .clip(shape)
            .background(CARD)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to tone.color.copy(alpha = wash), 0.7f to Color.Transparent,
                        center = Offset(size.width / 2, size.height * 1.3f),
                        radius = 280.dp.toPx(),
                    ),
                )
            }
            .border(1.dp, CARD_EDGE, shape),
    ) {
        when (card) {
            is NotchCard.Message -> MessageCard(card)
            is NotchCard.Progress -> ProgressCard(card)
        }
    }
}

/**
 * The face on the left (drawn by the island, over this card), then a column: who it's from, the
 * title, its detail, and the buttons in a row under them.
 */
@Composable
private fun BoxScope.MessageCard(card: NotchCard.Message) {
    Column(
        Modifier.align(Alignment.CenterStart).padding(start = MESSAGE_LEAD, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (card.label != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tone = toneOf(card.mood)
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (tone == Tone.Calm) MUTED else tone.color))
                Spacer(Modifier.width(7.dp))
                Text(card.label, color = TEXT, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (card.counter != null) {
                    Spacer(Modifier.width(7.dp))
                    Text(card.counter, color = MUTED, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Text(
            card.title, color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (card.detail != null) {
            Text(
                card.detail,
                color = when (card.detailTone) { Tone.Bad -> BAD_TEXT; Tone.Warn -> WARN_TEXT; else -> MUTED },
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.buttons.isNotEmpty()) {
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.buttons.forEach { PillButton(it) }
            }
        }
    }
}

@Composable
private fun BoxScope.ProgressCard(card: NotchCard.Progress) {
    val task = card.task
    val done = !task.isIndeterminate && task.progress >= 1f
    Column(Modifier.fillMaxWidth().padding(start = BAR_PAD, end = BAR_PAD, top = 18.dp)) {
        Text(
            task.title, color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val left = listOfNotNull(
                task.detail.takeIf { it.isNotBlank() },
                "+${card.queued} more".takeIf { card.queued > 0 },
            ).joinToString("  ·  ")
            Text(
                left, color = BAR_LABEL, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!task.isIndeterminate) {
                Spacer(Modifier.width(12.dp))
                Text(
                    if (done) "✓ Done" else task.percentText,
                    color = if (done) FILL_END else BAR_LABEL, fontSize = 12.5.sp,
                    fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = if (done) FontFamily.Default else FontFamily.Monospace, maxLines = 1,
                )
            }
        }
    }
    // the bar, at a fixed height so the face riding it knows where it is
    val fill by animateFloatAsState(
        task.progress.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow),
    )
    Box(
        Modifier.align(Alignment.TopStart).offset(y = BAR_Y).padding(horizontal = BAR_PAD)
            .fillMaxWidth().height(BAR_H).clip(CircleShape).background(TRACK),
    ) {
        if (task.isIndeterminate) {
            val t by rememberInfiniteTransition(label = "bar").animateFloat(
                0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Reverse), label = "sweep",
            )
            Box(
                Modifier.fillMaxWidth(0.3f + 0.4f * t).fillMaxHeight().clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(FILL_START.copy(alpha = 0f), FILL_END))),
            )
        } else {
            val shown = easeOutProgress(fill)
            Box(
                Modifier.fillMaxWidth(shown).fillMaxHeight().clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(FILL_START, FILL_END)))
                    .drawBehind {
                        drawOval(
                            FILL_GLOW.copy(alpha = 0.45f),
                            topLeft = Offset(size.width - 14.dp.toPx(), size.height / 2 - 6.dp.toPx()),
                            size = Size(28.dp.toPx(), 12.dp.toPx()),
                        )
                    },
            )
        }
    }
}

@Composable
private fun PillButton(button: CardButton) {
    val sounds = LocalNotchSounds.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (button.primary) TEXT else Color.White.copy(alpha = 0.09f))
            .clickable { sounds?.play(NotchSound.Tap); button.onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            button.label, color = if (button.primary) Color(0xFF0B0C0E) else Color(0xFFF1F2F4),
            fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        )
    }
}

// ---- small marks ----------------------------------------------------------------------------

/** What compact shows on the right: a progress ring while working, a soft amber pulse for a warning. */
@Composable
internal fun CompactMark(state: MascotAgentState) {
    when (state) {
        is MascotAgentState.Working -> Ring(state.activeTask)
        is MascotAgentState.Warning -> {
            val pulse by rememberInfiniteTransition(label = "warn").animateFloat(
                0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
            )
            Box(Modifier.size(9.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Tone.Warn.color))
        }
        else -> Box(Modifier.size(6.dp).clip(CircleShape).background(DIM))
    }
}

@Composable
private fun Ring(task: AgentTask) {
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "spin",
    )
    val p by animateFloatAsState(task.progress.coerceIn(0f, 1f), tween(400))
    Canvas(Modifier.size(17.dp)) {
        val w = 2.6.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.14f), style = Stroke(w))
        val brush = Brush.sweepGradient(listOf(FILL_START, FILL_END, FILL_START))
        if (task.isIndeterminate) drawArc(brush, spin, 90f, false, style = Stroke(w, cap = StrokeCap.Round))
        else drawArc(brush, -90f, 360f * p, false, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** A dot at the face's top-left in its mood's colour, ringed in black. */
@Composable
private fun StatusBadge(tone: Tone, face: Dp) {
    val d = maxOf(6.dp, face * 0.2f)
    Box(
        Modifier
            .offset(face * 0.04f, face * 0.06f)
            .size(d)
            .clip(CircleShape)
            .background(INK)
            .padding(d * 0.2f)
            .clip(CircleShape)
            .background(tone.color),
    )
}

@Composable
private fun Face(state: MascotAgentState, mood: String, animate: Boolean, onClick: () -> Unit) {
    val progress = (state as? MascotAgentState.Working)?.activeTask
        ?.takeUnless { it.isIndeterminate }?.progress
    val window = LocalNotchWindow.current
    val density = LocalDensity.current.density
    // the face's centre in the window, in px; written on layout, read every frame by the gaze
    val centre = remember { floatArrayOf(Float.NaN, Float.NaN) }
    StudioFace(
        mood = mood,
        progress = progress,
        animate = animate,
        gaze = window?.let { w -> { cursorGaze(w, centre[0] / density, centre[1] / density) } },
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { c -> c.boundsInWindow().center.let { centre[0] = it.x; centre[1] = it.y } }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
    )
}

/** Which way a face at ([x], [y]) points inside [window] has to look to see the cursor. */
private fun cursorGaze(window: java.awt.Window, x: Float, y: Float) =
    if (x.isNaN()) null
    else runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
        ?.let { gazeToward(it.x - (window.x + x), it.y - (window.y + y)) }
