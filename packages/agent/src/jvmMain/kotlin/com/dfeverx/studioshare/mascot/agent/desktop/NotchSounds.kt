package com.dfeverx.studioshare.mascot.agent.desktop

import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The notch's sounds: a tiny toy orchestra synthesized in code — no audio files, so nothing to ship and
 * nothing borrowed. Everything sits in C-major pentatonic, high and short, so any two sounds that overlap
 * still sound sweet together. Three toy voices (see [Voice]); pitch slides give the mascot a "voice" —
 * a rising "hm?" when it peeks, a falling "uh-oh" when something breaks. Quiet by default: the notch
 * sits by you all day; it should giggle, not ring.
 */
enum class NotchSound(internal val notes: List<Note>) {
    /** The island peeks out under the mouse: a tiny curious "hm?" bubble. */
    Peek(listOf(Note(C5, 0.0, 0.09, Voice.Bubble, glideTo = G5, level = 0.5))),

    /** The island opens to say something: a bouncy "ba-ding!" up a sixth, with a sprinkle on top. */
    Open(
        listOf(
            Note(G5, 0.0, 0.14, Voice.Marimba),
            Note(E6, 0.07, 0.26, Voice.Marimba),
            Note(C7, 0.09, 0.2, Voice.MusicBox, level = 0.22),
        ),
    ),

    /** The island folds back: a soft "byee~" bubble sliding down. */
    Close(listOf(Note(E6, 0.0, 0.16, Voice.Bubble, glideTo = A5, level = 0.55))),

    /** Something needs you: a playful "hey, hey?" — the second knock lifts like a question. */
    Attention(
        listOf(
            Note(A5, 0.0, 0.13, Voice.Marimba),
            Note(A5, 0.15, 0.2, Voice.Marimba, glideTo = D6, glide = 0.06),
        ),
    ),

    /** A task finished: a music-box "ta-da~" running up to a sparkle. */
    Done(
        listOf(
            Note(C6, 0.0, 0.16, Voice.MusicBox, level = 0.8),
            Note(E6, 0.06, 0.16, Voice.MusicBox, level = 0.8),
            Note(G6, 0.12, 0.18, Voice.MusicBox, level = 0.85),
            Note(C7, 0.18, 0.34, Voice.MusicBox),
            Note(C6, 0.18, 0.3, Voice.Marimba, level = 0.45),
        ),
    ),

    /** Something went wrong: a gentle, sheepish "uh-oh", never a buzzer. */
    Error(
        listOf(
            Note(E5, 0.0, 0.13, Voice.Bubble, level = 0.8),
            Note(C5, 0.15, 0.24, Voice.Bubble, glideTo = A4, glide = 0.2, level = 0.8),
        ),
    ),

    /** A button click: a tiny bubble pop. */
    Tap(listOf(Note(G6, 0.0, 0.035, Voice.Bubble, glideTo = C7, level = 0.4))),
    ;

    /**
     * One tone: [freq] Hz starting [at] seconds in, ringing for [length] seconds in [voice]. With [glideTo]
     * the pitch slides there over [glide] seconds (the whole note by default).
     */
    internal data class Note(
        val freq: Double,
        val at: Double,
        val length: Double,
        val voice: Voice = Voice.Marimba,
        val glideTo: Double? = null,
        val glide: Double = length,
        val level: Double = 1.0,
    )

    /** The toy instruments. */
    internal enum class Voice {
        /** Soft wooden knock: a warm fundamental plus a bright 4th partial that dies almost at once. */
        Marimba,
        /** Tinkly tine: octave shimmer plus an inharmonic glint, like a wind-up music box. */
        MusicBox,
        /** Rounded pure "boop" that swells and fades — cartoon bubbles and little vocal slides. */
        Bubble,
    }
}

// pitches (Hz) — C-major pentatonic, so everything plays nicely together
private const val A4 = 440.0
private const val C5 = 523.25
private const val E5 = 659.26
private const val G5 = 783.99
private const val A5 = 880.0
private const val C6 = 1046.5
private const val D6 = 1174.66
private const val E6 = 1318.51
private const val G6 = 1567.98
private const val C7 = 2093.0

/** Plays [NotchSound]s off the UI thread. Muted or failing audio is silently skipped. */
class NotchSoundPlayer(
    /** 0..1; Coucou-quiet by default. */
    var volume: Float = DEFAULT_VOLUME,
    var enabled: Boolean = true,
) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "notch-sound").apply { isDaemon = true } }
    private val cache = HashMap<NotchSound, ByteArray>()

    fun play(sound: NotchSound) {
        if (!enabled || volume <= 0f) return
        val gain = volume
        worker.execute {
            runCatching {
                val pcm = synchronized(cache) { cache.getOrPut(sound) { render(sound) } }
                val format = AudioFormat(RATE.toFloat(), 16, 1, true, false)
                AudioSystem.getSourceDataLine(format).use { line ->
                    line.open(format)
                    line.start()
                    line.write(scaled(pcm, gain), 0, pcm.size)
                    line.drain()
                }
            }
        }
    }

    companion object {
        const val DEFAULT_VOLUME = 0.12f
        internal const val RATE = 44_100

        /** [sound] at full scale as 16-bit little-endian mono PCM. */
        internal fun render(sound: NotchSound): ByteArray {
            val end = sound.notes.maxOf { it.at + it.length } + 0.02
            val n = (end * RATE).toInt()
            val mix = DoubleArray(n)
            for (note in sound.notes) {
                val start = (note.at * RATE).toInt()
                val len = (note.length * RATE).toInt()
                var phase = 0.0
                for (i in 0 until min(len, n - start)) {
                    val t = i.toDouble() / RATE
                    // integrate the (possibly sliding) pitch so a glide stays click-free
                    val target = note.glideTo
                    val freq = if (target == null) note.freq else note.freq * (target / note.freq).pow(min(1.0, t / note.glide))
                    phase += 2 * PI * freq / RATE
                    mix[start + i] += voice(note.voice, phase, t, note.length) * note.level
                }
            }
            // a fixed scale (not peak-normalised) so a note's level is its loudness; overlaps are clamped
            val out = ByteArray(n * 2)
            for (i in 0 until n) {
                val s = (mix[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.9).toInt()
                out[i * 2] = s.toByte()
                out[i * 2 + 1] = (s shr 8).toByte()
            }
            return out
        }

        /** One sample of [voice] at [phase] radians, [t] seconds into a note [length] seconds long; peaks near ±1. */
        private fun voice(voice: NotchSound.Voice, phase: Double, t: Double, length: Double): Double = when (voice) {
            NotchSound.Voice.Marimba -> {
                // 2 ms mallet strike, then a tail that has all but died by the note's end
                val env = min(1.0, t / 0.002) * exp(-5.0 * t / length)
                (sin(phase) + 0.3 * sin(4 * phase) * exp(-t * 70)) * env / 1.3
            }
            NotchSound.Voice.MusicBox -> {
                val env = min(1.0, t / 0.002) * exp(-6.0 * t / length)
                (sin(phase) + 0.25 * sin(2 * phase) + 0.15 * sin(5.4 * phase) * exp(-t * 40)) * env / 1.4
            }
            NotchSound.Voice.Bubble -> {
                // swells up and back down to silence: round, with no strike at all
                val env = sin(PI * min(1.0, t / length)).pow(1.5)
                sin(phase) * env
            }
        }

        private fun scaled(pcm: ByteArray, gain: Float): ByteArray {
            val g = gain.coerceIn(0f, 1f)
            val out = ByteArray(pcm.size)
            for (i in pcm.indices step 2) {
                val s = ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort()
                val v = (s * g).toInt()
                out[i] = v.toByte()
                out[i + 1] = (v shr 8).toByte()
            }
            return out
        }
    }
}
