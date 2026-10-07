package com.dfeverx.studioshare.mascot

import com.dfeverx.studioshare.mascot.pack.MascotPack
import com.dfeverx.studioshare.mascot.pack.Sha256
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The pack the app ships with: parses, carries every moment code names, and every atlas matches its hash. */
class BundledPackTest {
    private val dir = listOf(
        File("../../pipeline/dist"),
        File("../pipeline/dist"),
        File("pipeline/dist")
    ).firstOrNull { it.exists() } ?: File("../../pipeline/dist")
    private val pack = assertNotNull(
        if (File(dir, "manifest.json").exists()) MascotPack.parse(File(dir, "manifest.json").readBytes()) else null,
        "bundled manifest must parse at ${dir.absolutePath}"
    )

    @Test fun everyMomentInCodeExistsInThePack() {
        val missing = MascotMoments.all.filterNot { it in pack.moments }
        assertTrue(missing.isEmpty(), "moments missing from the bundled pack: $missing — run tools/mascot/pack.mjs --sync")
    }

    @Test fun everyMomentNamesARealMood() {
        for ((key, moment) in pack.moments) assertTrue(moment.mood in pack.moods, "$key names unknown mood ${moment.mood}")
        assertTrue(MascotPack.MOOD_WALK in pack.moods)
    }

    @Test fun everyAtlasIsPresentAndMatchesItsHash() {
        for ((path, sha) in pack.assets) {
            val file = File(dir, path)
            assertTrue(file.exists(), "missing $path")
            assertTrue(Sha256.hex(file.readBytes()) == sha, "hash mismatch for $path")
        }
    }
}
