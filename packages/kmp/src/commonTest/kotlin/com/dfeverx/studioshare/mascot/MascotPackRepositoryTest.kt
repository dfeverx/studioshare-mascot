package com.dfeverx.studioshare.mascot

import com.dfeverx.studioshare.mascot.pack.MascotCacheStore
import com.dfeverx.studioshare.mascot.pack.MascotFileReader
import com.dfeverx.studioshare.mascot.pack.MascotPackRepository
import com.dfeverx.studioshare.mascot.pack.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MascotPackRepositoryTest {
    private fun manifest(version: Long, atlas: ByteArray) = """
        {"schema":1,"version":$version,"frame":{"w":4,"h":4},
         "moods":{"idle":{"frames":1,"atlas":{"light":"atlas/idle-light.webp"}}},
         "moments":{},"assets":{"atlas/idle-light.webp":"${Sha256.hex(atlas)}"}}
    """.trimIndent().encodeToByteArray()

    private class MemoryCache : MascotCacheStore {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun read(path: String) = files[path]
        override suspend fun write(path: String, bytes: ByteArray) { files[path] = bytes }
        override suspend fun prune(keepVersionDir: String) {
            files.keys.removeAll { it.startsWith("v") && !it.startsWith("$keepVersionDir/") }
        }
    }

    private val oldAtlas = byteArrayOf(1, 2, 3)
    private val newAtlas = byteArrayOf(4, 5, 6)
    private val bundled = MascotFileReader { path ->
        when (path) {
            "manifest.json" -> manifest(100, oldAtlas)
            "atlas/idle-light.webp" -> oldAtlas
            else -> null
        }
    }

    @Test fun theBundledPackLoadsWhenNothingIsCached() = runTest {
        val repo = MascotPackRepository(bundled, MemoryCache())
        assertEquals(100, repo.load()?.pack?.version)
    }

    @Test fun aNewerVerifiedRemotePackReplacesItAndSurvivesARestart() = runTest {
        val cache = MemoryCache()
        val remote = mapOf("https://x/mascot/manifest.json" to manifest(200, newAtlas), "https://x/mascot/atlas/idle-light.webp" to newAtlas)
        val repo = MascotPackRepository(bundled, cache, { remote[it] }, "https://x/mascot/")
        assertTrue(repo.refreshRemote())
        assertEquals(200, repo.pack.value?.pack?.version)
        assertEquals(newAtlas.toList(), repo.pack.value?.files?.read("atlas/idle-light.webp")?.toList())

        val restarted = MascotPackRepository(bundled, cache)
        assertEquals(200, restarted.load()?.pack?.version)
    }

    @Test fun aTamperedOrMissingAtlasKeepsTheOldPack() = runTest {
        val cache = MemoryCache()
        val tampered = mapOf("https://x/mascot/manifest.json" to manifest(200, newAtlas), "https://x/mascot/atlas/idle-light.webp" to byteArrayOf(9))
        val repo = MascotPackRepository(bundled, cache, { tampered[it] }, "https://x/mascot")
        assertFalse(repo.refreshRemote())
        assertEquals(100, repo.pack.value?.pack?.version)
        assertEquals(100, MascotPackRepository(bundled, cache).load()?.pack?.version)
    }

    @Test fun anOlderOrEqualRemotePackIsIgnored() = runTest {
        val remote = mapOf("https://x/mascot/manifest.json" to manifest(100, newAtlas))
        val repo = MascotPackRepository(bundled, MemoryCache(), { remote[it] }, "https://x/mascot")
        assertFalse(repo.refreshRemote())
    }

    @Test fun unchangedAtlasesAreReusedNotDownloaded() = runTest {
        val fetched = mutableListOf<String>()
        val remote = mapOf("https://x/mascot/manifest.json" to manifest(200, oldAtlas))
        val repo = MascotPackRepository(bundled, MemoryCache(), { fetched += it; remote[it] }, "https://x/mascot")
        assertTrue(repo.refreshRemote())
        assertEquals(listOf("https://x/mascot/manifest.json"), fetched)
    }
}
