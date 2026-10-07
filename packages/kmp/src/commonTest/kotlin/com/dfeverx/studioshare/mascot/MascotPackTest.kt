package com.dfeverx.studioshare.mascot

import com.dfeverx.studioshare.mascot.pack.MascotPack
import com.dfeverx.studioshare.mascot.pack.Sha256
import com.dfeverx.studioshare.mascot.render.frameAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MascotPackTest {
    private val manifest = """
        {"schema":1,"version":202610050900,"frame":{"w":256,"h":256},
         "moods":{
           "idle":{"code":"M01","frames":1,"cols":1,"atlas":{"light":"atlas/idle-light.webp","dark":"atlas/idle-dark.webp"}},
           "thinking":{"code":"M11","motion":"sway","frames":1,"cols":1,"atlas":{"light":"atlas/thinking-light.webp"}},
           "ok":{"code":"M24","frames":1,"cols":1,"atlas":{"light":"atlas/ok-light.webp"}},
           "oops":{"code":"M19","motion":"shake","fallback":"thinking"},
           "lost":{"code":"X","fallback":"nowhere"},
           "loopA":{"fallback":"loopB"},"loopB":{"fallback":"loopA"},
           "celebrating":{"code":"M05","frames":1,"atlas":{"light":"atlas/c.webp"}}},
         "clips":{"state.error":{"fps":10,"frames":4,"cols":4,"atlas":{"light":"atlas/moment-state.error-light.webp"}},
           "upload.done":{"frames":2,"atlas":{"light":"atlas/moment-upload.done-light.webp"}},
           "x.loop":{"frames":0}},
         "moments":{"state.error":{"mood":"oops"},"upload.done":{"mood":"celebrating","first":"first-upload"},
           "x.loop":{"mood":"loopA"},"x.missing":{"mood":"notAMood"}},
         "futureField":true}
    """.trimIndent().encodeToByteArray()

    private val pack = assertNotNull(MascotPack.parse(manifest))

    @Test fun aMoodWithoutArtBorrowsItsFallbacksArtButKeepsItsOwnMotion() {
        val r = pack.resolveMood("oops")
        assertEquals("oops", r.moodName)
        assertEquals("shake", r.mood.motion)
        assertEquals("thinking", r.artName)
        assertEquals("atlas/thinking-light.webp", r.atlasPath(dark = false))
    }

    @Test fun aMomentsOwnClipWinsOverItsMoodsArt() {
        val r = pack.resolve("state.error")
        assertEquals("oops", r.moodName)
        assertEquals(true, r.artIsClip)
        assertEquals("atlas/moment-state.error-light.webp", r.atlasPath(dark = false))
        // a clip with no frames is ignored
        assertEquals(false, pack.resolve("x.loop").artIsClip)
        // a repeat of a first plays "ok", so the celebration's own clip is not used
        assertEquals(true, pack.resolve("upload.done", firstAlreadySeen = false).artIsClip)
        assertEquals(false, pack.resolve("upload.done", firstAlreadySeen = true).artIsClip)
    }

    @Test fun aMissingThemeFallsBackToTheOtherOne() {
        assertEquals("atlas/thinking-light.webp", pack.resolveMood("oops").atlasPath(dark = true))
    }

    @Test fun unknownMomentsMoodsAndBrokenChainsEndAtIdle() {
        assertEquals("idle", pack.resolve("no.suchMoment").artName)
        assertEquals("idle", pack.resolve("x.missing").moodName)
        assertEquals("idle", pack.resolveMood("lost").artName)
        assertEquals("idle", pack.resolve("x.loop").artName)
    }

    @Test fun aCelebrationAlreadySeenPlaysOk() {
        assertEquals("celebrating", pack.resolve("upload.done", firstAlreadySeen = false).moodName)
        assertEquals("ok", pack.resolve("upload.done", firstAlreadySeen = true).moodName)
    }

    @Test fun unusablePacksAreRejected() {
        assertNull(MascotPack.parse("not json".encodeToByteArray()))
        assertNull(MascotPack.parse(manifest.decodeToString().replace("\"schema\":1", "\"schema\":2").encodeToByteArray()))
        assertNull(MascotPack.parse("""{"schema":1,"version":1,"frame":{"w":1,"h":1},"moods":{},"moments":{}}""".encodeToByteArray()))
    }

    @Test fun framesPlayTheIntroOnceThenLoop() {
        assertEquals(0, frameAt(0f, 10, 8, 3, loop = true))
        assertEquals(7, frameAt(0.79f, 10, 8, 3, loop = true))
        assertEquals(3, frameAt(0.8f, 10, 8, 3, loop = true))
        assertEquals(4, frameAt(0.9f, 10, 8, 3, loop = true))
        assertEquals(7, frameAt(5f, 10, 8, 3, loop = false))
        assertEquals(0, frameAt(5f, 10, 1, 0, loop = true))
    }

    @Test fun sha256MatchesTheStandardVectors() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".encodeToByteArray()))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(ByteArray(0)))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
    }
}
