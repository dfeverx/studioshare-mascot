package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.dfeverx.studioshare.mascot.face.HandGesture
import com.dfeverx.studioshare.mascot.face.StudioFace
import com.dfeverx.studioshare.mascot.face.gazeToward
import kotlinx.coroutines.delay
import java.awt.MouseInfo
import kotlin.math.hypot

internal const val NOTCH_WINDOW_TITLE = "StudioShare Notch Companion"

/**
 * Each side of the island that shows past the notch at rest: the face sits in the left one's outer
 * corner, a status mark in the right. Always there, notch or not, so the face is never behind the camera.
 */
internal val EAR = 88.dp
/**
 * Extra room either side of the measured notch, so nothing tucks under the camera housing's curve
 * (or under a notch measured a little narrow).
 */
internal val NOTCH_SLACK = 8.dp
/**
 * How far in from the island's outer edges the resting marks sit: the face at the far left, the
 * status mark at the far right. The face's box is a little bigger than its drawn body, so the two
 * insets differ to look equal.
 */
private val REST_FACE_LEAD = 6.dp
private val REST_MARK_TRAIL = 12.dp
/** The island's height without a notch to match. */
private val FLAT_BAND = 30.dp
/** The widest the island grows. */
private val ISLAND_MAX_W = 760.dp

// ---- the look (after Coucou: a black island, a lifted card washed with the mood's colour) ----

internal val INK = Color(0xFF000000)
private val CARD = Color(0xFF141518)
private val CARD_EDGE = Color.White.copy(alpha = 0.035f)
private val TEXT = Color(0xFFF5F6F8)
private val MUTED = Color(0xFF8E939C)
private val BAD_TEXT = Color(0xFFFF8D97)
private val WARN_TEXT = Color(0xFFF7C46C)
private val TRACK = Color.White.copy(alpha = 0.08f)
private val FILL_START = Color(0xFF22C55E)
private val FILL_END = Color(0xFF34D399)

/**
 * The island's corners. The notch companion is the one place the mascot breaks the no-radius rule:
 * the island has to read as the notch grown longer, and everything in it follows the notch's curve.
 */
private val REST_CORNER = 14.dp
private val OPEN_CORNER = 24.dp
private val CARD_CORNER = 18.dp

/** Island padding around a card; the band above it is the notch's own height. */
private val PAD = 10.dp
private val CARD_TEXT_MAX = 440.dp
private val PROGRESS_W = 520.dp
private val FACE_NOTE = 32.dp
private val FACE_CARD = 42.dp
private val FACE_RIDER = 24.dp
private val FACE_LEAD = 14.dp
private val BAR_H = 5.dp
private val BAR_BOTTOM = 12.dp
/** The hello: the face dropped out of the notch with room under it, and the hand it waves. */
private val HELLO_SIZE = DpSize(150.dp, 54.dp)
private val FACE_HELLO = 44.dp

/** Room around the island for its spring to overshoot into, and for the face's glow. */
private val WINDOW_MARGIN = 16.dp
private val STAGE_CARD_H = 100.dp

/**
 * Dynamic Island motion: opening is a soft spring with just a breath of overshoot, closing a firmer
 * one that settles without any — both springs, so a change of mind mid-way carries its momentum
 * instead of jumping.
 */
private val openSpring: AnimationSpec<Dp> = spring(dampingRatio = 0.78f, stiffness = 190f)
private val closeSpring: AnimationSpec<Dp> = spring(dampingRatio = 1f, stiffness = 320f)

/** How long a click-opened island waits after the mouse leaves before it folds back. */
private const val COLLAPSE_AFTER_LEAVE_MS = 1_500L
/** A new task peeks out with its progress this long, then tucks back to a ring in the ear. */
private const val TASK_PEEK_MS = 3_500L
/** How long a hello stays out: the hand rises, waves, and the island folds back. */
private const val HELLO_MS = 2_300L
/** Resting the mouse on the face this long makes it blush; then not again for a while. */
private const val LOVE_AFTER_MS = 1_900L
private const val LOVE_COOLDOWN_MS = 6_000L
private const val LOVE_HOLD_MS = 2_500L

/** The companion's window, so a [Face] inside it can work out where it sits on screen. */
private val LocalNotchWindow = compositionLocalOf<java.awt.Window?> { null }

// ---- tone: the colour a mood washes the card with ------------------------------------------

/** Colour family of a mood, for the card's wash, its header dot and the badge on the face. */
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
    /** What the face does with its hands while this shows; null keeps them away. */
    val hands: HandGesture? get() = null

    /** A message: where it's from, what happened, maybe a line more and a button or two. */
    data class Message(
        override val key: String,
        override val mood: String,
        val label: String?,
        val title: String,
        val detail: String? = null,
        val detailTone: Tone? = null,
        val buttons: List<CardButton> = emptyList(),
        override val hands: HandGesture? = null,
    ) : NotchCard {
        /** Just a line: opens as a single row, the shortest card there is. */
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

    /** No words: the face drops out of the notch and waves. What a click on a quiet island gets. */
    data object Hello : NotchCard {
        override val key = "hello"
        override val mood = "glance"
        override val hands = HandGesture.Wave
    }
}

/** How the island is: resting in the notch, or open with a card under it. */
internal enum class Look { Rest, Card }

internal fun lookOf(card: NotchCard?): Look = if (card == null) Look.Rest else Look.Card

/**
 * What the island should be showing for [state], or null to rest in the notch. A warning or alert
 * speaks for itself; a task shows its progress while it [peeks] (just started), and opens out when
 * [opened] by a click; a click on a quiet island gets a wave hello.
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
                label = (w.label ?: "Heads up") + if (count > 1) "  ·  1 of $count" else "",
                title = w.title,
                detail = w.message.takeIf { it.isNotBlank() },
                detailTone = if (toneOf(w.mood) == Tone.Bad) Tone.Bad else Tone.Warn,
                buttons = listOfNotNull(
                    if (w.actionLabel != null && action != null) {
                        CardButton(w.actionLabel, primary = true) { action(); agent?.dismissWarning(w.id) }
                    } else null,
                    CardButton("Dismiss", primary = false) { agent?.dismissWarning(w.id) },
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
                hands = a.hands,
            )
        }
        is MascotAgentState.Working ->
            if (opened || peeks) {
                NotchCard.Progress("task:$opened", state.currentMood, state.activeTask, state.queuedTasks.size, expanded = opened)
            } else null
        is MascotAgentState.Idle ->
            if (opened) NotchCard.Hello else null
    }

/**
 * A notch companion: the StudioShare face living in the MacBook's notch, after Coucou's Mochi.
 *
 * At rest it is the notch with a wide ear either side: the face in the outer corner of the left one,
 * watching the cursor, and in the right one a progress ring while something runs or an amber pulse
 * while something needs you. No words. When something happens the notch opens downward into a card
 * that is wide and short — one row for a line like "You're signed in", two for a message with a
 * detail and buttons beside it — so it never covers more of the screen than a strip under the menu
 * bar. The card is washed with the mood's colour, the face slides in from the ear, and the motion is
 * the Dynamic Island's: a soft spring open, a firmer one closed. Hovering keeps whatever is showing;
 * clicking the resting island opens it.
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
) {
    if (!visible) return
    val state by agent.state.collectAsState()
    val notch = remember { MacNotch.geometry() }

    var opened by remember { mutableStateOf(false) }
    var inIsland by remember { mutableStateOf(false) }
    var overFace by remember { mutableStateOf(false) }
    var love by remember { mutableStateOf(false) }

    // a task that has just started peeks out with its progress
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
    // (a hello has nothing to read, so it never lingers)
    val live = liveCard(agent, state, opened, peeks = peekTask != null && peekTask == activeTask)
    var shown by remember { mutableStateOf<NotchCard?>(null) }
    LaunchedEffect(live, inIsland) {
        if (live != null) shown = live
        else if (shown != null && (!inIsland || shown == NotchCard.Hello)) shown = null
    }
    // a hello waves and folds back, mouse or no mouse
    LaunchedEffect(live) {
        if (live == NotchCard.Hello) {
            delay(HELLO_MS)
            opened = false
        }
    }
    // a click-opened island folds back once the mouse has been away a moment
    LaunchedEffect(opened, inIsland) {
        if (opened && !inIsland) {
            delay(COLLAPSE_AFTER_LEAVE_MS)
            opened = false
        }
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
        CompositionLocalProvider(LocalNotchWindow provides window) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Island(
                    state = state,
                    card = shown,
                    notch = notch,
                    mood = if (love) "shy" else shown?.mood ?: state.currentMood,
                    // a blush hides behind its hands; a card gestures if its moment does
                    hands = if (love) HandGesture.Shy else shown?.hands,
                    handsId = if (love) "love@$lastLove" else shown?.key,
                    faceScale = faceScale,
                    metrics = metrics,
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
    notch: NotchGeometry,
    mood: String,
    animate: Boolean = true,
    hands: HandGesture? = null,
    handsId: Any? = null,
    faceScale: Float = 1f,
    metrics: IslandMetrics? = null,
    onRestClick: () -> Unit = {},
    onFaceClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val band = bandHeight(notch)
    val rest = restSize(notch)
    val look = lookOf(card)

    // measured size of the card that is showing
    var cardSize by remember { mutableStateOf(DpSize.Zero) }
    var cardKey by remember { mutableStateOf<String?>(null) }
    val measured = when (look) {
        Look.Rest -> true
        Look.Card -> cardSize != DpSize.Zero && cardKey == card?.key
    }
    // until it is measured, the new look waits where it is (a frame)
    var settled by remember { mutableStateOf<Pair<Look, NotchCard?>>(Look.Rest to null) }
    SideEffect { if (measured && settled != (look to card)) settled = look to card }
    val (shownLook, shownCard) = settled

    val target = when (shownLook) {
        Look.Rest -> rest
        Look.Card -> openSize(notch, cardSize)
    }
    val opening = shownLook != Look.Rest
    val spec: AnimationSpec<Dp> = if (!animate) snap() else if (opening) openSpring else closeSpring
    val islandW by animateDpAsState(target.width, spec)
    val islandH by animateDpAsState(target.height, spec)
    val corner by animateDpAsState(
        if (shownLook == Look.Card) OPEN_CORNER else REST_CORNER.coerceAtMost(band / 2), spec,
    )

    val place = facePlace(notch, shownLook, shownCard, target, cardSize)
    val faceDx by animateDpAsState(place.dx, spec)
    val faceY by animateDpAsState(place.y, spec)
    val faceSize by animateDpAsState(place.size, spec)
    val faceX = islandW / 2 + faceDx
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
                if (opening) Modifier
                else Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onRestClick),
            ),
    ) {
        // at rest: a mark in the right ear, no words
        Layer(visible = shownLook == Look.Rest, animate = animate) {
            Box(
                Modifier.align(Alignment.TopEnd).size(EAR, band).padding(end = REST_MARK_TRAIL),
                contentAlignment = Alignment.CenterEnd,
            ) { EarMark(state) }
        }
        // the card, laid out at its own size and centred under the band; the island's clip reveals it
        Layer(visible = shownLook == Look.Card, animate = animate, keepLast = card.takeIf { look == Look.Card }) { c ->
            if (c != null) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = band)
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .onSizeChanged {
                            cardKey = c.key
                            cardSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) }
                        },
                ) { CardView(c, minWidth = rest.width - PAD * 2) }
            }
        }
        // one face for every look, so it glides between the ear and the card
        val tone = toneOf(mood)
        Box(
            Modifier
                .offset(faceX - faceSize / 2, faceY - faceSize / 2)
                .size(faceSize)
                .graphicsLayer { scaleX = faceScale; scaleY = faceScale },
        ) {
            // hands once the face is out of the ear; in it, only hands kept on the cheeks fit the band
            Face(state, mood, animate, hands.takeIf { shownLook == Look.Card || it == HandGesture.Shy }, handsId, onFaceClick)
            // a gesturing face drops its badge: the hands would land on it (the card has its own dot)
            if (tone != Tone.Calm && hands == null) StatusBadge(tone, faceSize)
        }
    }
}

/**
 * A layer of the island that fades in like the Dynamic Island's content — out of a soft blur,
 * growing the last few percent — after the island has started to open, and out quickly before it
 * closes. While fading out it keeps showing [keepLast]'s last value; while [keepLast] is set but
 * not yet [visible], it is laid out unseen so it can be measured.
 */
@Composable
private fun BoxScope.Layer(
    visible: Boolean,
    animate: Boolean,
    keepLast: NotchCard? = null,
    content: @Composable BoxScope.(NotchCard?) -> Unit,
) {
    var last by remember { mutableStateOf<NotchCard?>(null) }
    if (keepLast != null) last = keepLast
    val t by animateFloatAsState(
        if (visible) 1f else 0f,
        when {
            !animate -> snap()
            visible -> tween(260, delayMillis = 120)
            else -> tween(120)
        },
    )
    // a layer about to show is still laid out (unseen) so its size is known before the island moves
    if (t < 0.01f && keepLast == null) return
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                alpha = t
                val s = 0.94f + 0.06f * t
                scaleX = s; scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
            .then(if (t < 0.99f) Modifier.blur((6 * (1 - t)).dp) else Modifier),
    ) { content(last) }
}

// ---- geometry -------------------------------------------------------------------------------

internal fun bandHeight(notch: NotchGeometry): Dp = if (notch.hasNotch) notch.bandHeight else FLAT_BAND

/** The gap the island leaves for the camera: the notch and a little slack, or nothing without one. */
internal fun notchGap(notch: NotchGeometry): Dp = if (notch.hasNotch) notch.notchWidth + NOTCH_SLACK * 2 else 0.dp

/** The island at rest: the notch with an ear each side (just the two ears without one). */
internal fun restSize(notch: NotchGeometry): DpSize = DpSize(notchGap(notch) + EAR * 2, bandHeight(notch))

/** The island open around a card of [card] size: the notch band, the card, padding. */
internal fun openSize(notch: NotchGeometry, card: DpSize): DpSize =
    DpSize(maxOf(card.width + PAD * 2, restSize(notch).width), bandHeight(notch) + card.height + PAD)

/** The fixed window the island lives in: room for the widest, tallest island. */
internal fun stageSize(notch: NotchGeometry): DpSize = DpSize(
    maxOf(ISLAND_MAX_W, restSize(notch).width) + WINDOW_MARGIN * 2,
    bandHeight(notch) + STAGE_CARD_H + PAD + WINDOW_MARGIN,
)

/** Where the face sits: [dx] from the island's centre line, [y] from its top. */
internal data class FacePlace(val dx: Dp, val y: Dp, val size: Dp)

internal fun facePlace(notch: NotchGeometry, look: Look, card: NotchCard?, island: DpSize, cardSize: DpSize): FacePlace {
    val band = bandHeight(notch)
    return when {
        look == Look.Rest || card == null -> {
            // in the island's outer left corner, well clear of the camera, as tall as the band allows
            val size = (band - 2.dp).coerceAtMost(40.dp)
            FacePlace(-restSize(notch).width / 2 + REST_FACE_LEAD + size / 2, band / 2, size)
        }
        card == NotchCard.Hello ->
            // dropped straight down out of the notch, in the middle of the room it opened
            FacePlace(0.dp, band + cardSize.height / 2, FACE_HELLO)
        card is NotchCard.Progress -> {
            // riding the bar's leading edge, like Coucou's Mochi on an upload
            val barW = cardSize.width - FACE_LEAD * 2
            val p = if (card.task.isIndeterminate) 0.5f else card.task.progress.coerceIn(0f, 1f)
            FacePlace(-cardSize.width / 2 + FACE_LEAD + barW * p, band + cardSize.height - BAR_BOTTOM - BAR_H / 2, FACE_RIDER)
        }
        else -> {
            val size = if ((card as NotchCard.Message).isNote) FACE_NOTE else FACE_CARD
            FacePlace(-cardSize.width / 2 + FACE_LEAD + size / 2, band + cardSize.height / 2, size)
        }
    }
}

/** Top centre of the screen, flush with its top edge: over the menu bar, and around the notch. */
private fun topCentre(notch: NotchGeometry, width: Dp) = WindowPosition(
    x = (notch.screenX + (notch.screenWidth - width.value) / 2).dp,
    y = notch.screenY.dp,
)

// ---- card -----------------------------------------------------------------------------------

/** The card: wide and short, washed from below with its mood's colour. */
@Composable
internal fun CardView(card: NotchCard, minWidth: Dp) {
    // a hello is no card at all: just black room under the notch for the face to wave in
    if (card == NotchCard.Hello) return Spacer(Modifier.size(HELLO_SIZE))
    val tone = toneOf(card.mood)
    val shape = RoundedCornerShape(CARD_CORNER)
    Box(
        Modifier
            .widthIn(min = minWidth, max = ISLAND_MAX_W - PAD * 2)
            .clip(shape)
            .background(CARD)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to tone.color.copy(alpha = tone.wash), 0.7f to Color.Transparent,
                        center = Offset(size.width / 2, size.height * 1.5f),
                        radius = 300.dp.toPx(),
                    ),
                )
            }
            .border(1.dp, CARD_EDGE, shape),
    ) {
        when (card) {
            is NotchCard.Message -> MessageCard(card)
            is NotchCard.Progress -> ProgressCard(card)
            NotchCard.Hello -> Unit
        }
    }
}

/**
 * Face, then the words, then the buttons, side by side so the card grows wide and stays short: a
 * note is one row ("● Sign in  You're signed in."), a message two (that, then its detail).
 */
@Composable
private fun MessageCard(card: NotchCard.Message) {
    val face = if (card.isNote) FACE_NOTE else FACE_CARD
    val vertical = if (card.isNote) 8.dp else 10.dp
    Row(Modifier.padding(end = 14.dp, top = vertical, bottom = vertical), verticalAlignment = Alignment.CenterVertically) {
        // the face floats over this slot (one face is drawn for every look)
        Spacer(Modifier.width(FACE_LEAD + face + 12.dp).height(face))
        Column(Modifier.widthIn(max = CARD_TEXT_MAX), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (card.label != null) {
                    val tone = toneOf(card.mood)
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (tone == Tone.Calm) MUTED else tone.color))
                    Spacer(Modifier.width(7.dp))
                    Text(card.label, color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    card.title, color = TEXT, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.detail != null) {
                Text(
                    card.detail,
                    color = when (card.detailTone) { Tone.Bad -> BAD_TEXT; Tone.Warn -> WARN_TEXT; else -> MUTED },
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (card.buttons.isNotEmpty()) {
            Spacer(Modifier.width(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { card.buttons.forEach { PillButton(it) } }
        }
    }
}

@Composable
private fun ProgressCard(card: NotchCard.Progress) {
    val task = card.task
    Column(Modifier.width(PROGRESS_W).padding(start = FACE_LEAD, end = FACE_LEAD, top = 10.dp, bottom = BAR_BOTTOM)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                task.title, color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            val right = listOfNotNull(
                task.detail.takeIf { it.isNotBlank() },
                task.percentText.takeUnless { task.isIndeterminate },
                "+${card.queued} more".takeIf { card.queued > 0 },
            ).joinToString("  ·  ")
            Text(right, color = MUTED, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        val fill by animateFloatAsState(
            task.progress.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow),
        )
        Box(Modifier.fillMaxWidth().height(BAR_H).clip(CircleShape).background(TRACK)) {
            if (task.isIndeterminate) {
                val t by rememberInfiniteTransition(label = "bar").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Reverse), label = "sweep",
                )
                Box(
                    Modifier.fillMaxWidth(0.3f + 0.4f * t).fillMaxHeight().clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(FILL_START.copy(alpha = 0f), FILL_END))),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(FILL_START, FILL_END))),
                )
            }
        }
    }
}

@Composable
private fun PillButton(button: CardButton) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (button.primary) TEXT else Color.White.copy(alpha = 0.09f))
            .clickable(onClick = button.onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            button.label, color = if (button.primary) Color(0xFF0B0C0E) else Color(0xFFF1F2F4),
            fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        )
    }
}

// ---- small marks ----------------------------------------------------------------------------

/** What the right ear shows at rest: a progress ring while working, a soft amber pulse for a warning. */
@Composable
internal fun EarMark(state: MascotAgentState) {
    when (state) {
        is MascotAgentState.Working -> Ring(state.activeTask)
        is MascotAgentState.Warning -> {
            val pulse by rememberInfiniteTransition(label = "warn").animateFloat(
                0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
            )
            Box(Modifier.size(9.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Tone.Warn.color))
        }
        else -> Unit
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

/** A dot at the face's top-left in its mood's colour, ringed in black (Coucou's status badge). */
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
private fun Face(
    state: MascotAgentState,
    mood: String,
    animate: Boolean,
    hands: HandGesture?,
    handsId: Any?,
    onClick: () -> Unit,
) {
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
        hands = hands,
        handsId = handsId,
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
