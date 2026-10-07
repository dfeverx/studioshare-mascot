package com.dfeverx.studioshare.mascot.agent.desktop

import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The notch's sounds: short, soft chimes synthesized in code — no audio files, so nothing to ship and
 * nothing borrowed. Each is a few sine partials with a quick attack and an exponential tail, quiet by
 * default (the notch sits by you all day; it should murmur, not ring).
 */
enum class NotchSound(internal val notes: List<Note>) {
    /** The island grows out of the notch under the mouse: one faint, short blip. */
    Peek(listOf(Note(1174.7, 0.0, 0.07, level = 0.45))),
    /** The island opens to say something: two notes rising a fifth. */
    Open(listOf(Note(880.0, 0.0, 0.16), Note(1318.5, 0.07, 0.22))),
    /** The island folds back: one soft note, a little lower. */
    Close(listOf(Note(660.0, 0.0, 0.12, level = 0.6))),
    /** Something needs you: two equal notes, a gentle knock. */
    Attention(listOf(Note(987.8, 0.0, 0.14), Note(987.8, 0.16, 0.2))),
    /** A task finished: a rising major triad. */
    Done(listOf(Note(784.0, 0.0, 0.18), Note(987.8, 0.08, 0.2), Note(1174.7, 0.16, 0.3))),
    /** Something went wrong: a falling minor third. */
    Error(listOf(Note(740.0, 0.0, 0.16), Note(622.3, 0.1, 0.26))),
    /** A button click: a tiny tick. */
    Tap(listOf(Note(1567.98, 0.0, 0.05, level = 0.5))),
    ;

    /** One tone: [freq] Hz starting [at] seconds in, ringing for [length] seconds. */
    internal data class Note(val freq: Double, val at: Double, val length: Double, val level: Double = 1.0)
}

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
                for (i in 0 until min(len, n - start)) {
                    val t = i.toDouble() / RATE
                    // 4 ms attack, then a tail that has all but died by the note's end
                    val env = min(1.0, t / 0.004) * exp(-5.0 * t / note.length)
                    val w = 2 * PI * note.freq * t
                    // a touch of the octave and twelfth makes a sine sound like a soft bell
                    val tone = sin(w) + 0.28 * sin(2 * w) + 0.1 * sin(3 * w)
                    mix[start + i] += tone * env * note.level / 1.38
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
