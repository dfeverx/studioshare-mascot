package com.dfeverx.studioshare.mascot.pack

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads a file of the pack by its manifest-relative path (`manifest.json`, `atlas/idle-light.webp`). */
fun interface MascotFileReader {
    suspend fun read(path: String): ByteArray?
}

/** A small writable directory for the downloaded pack (the app points it at its cache dir). */
interface MascotCacheStore : MascotFileReader {
    suspend fun write(path: String, bytes: ByteArray)

    /** Deletes every cached version except [keepVersionDir]. */
    suspend fun prune(keepVersionDir: String)
}

/** A loaded pack plus where its atlases are read from. */
class LoadedPack(val pack: MascotPack, val files: MascotFileReader)

/**
 * The pack in use. The bundled pack ships with the app; a newer one published at
 * `https://<host>/mascot/manifest.json` replaces it once every changed atlas has been downloaded and
 * matched against the manifest's sha256 — a partial or tampered download is never used.
 * Every failure is silent: the mascot is decoration, not a feature anyone waits on.
 */
class MascotPackRepository(
    private val bundled: MascotFileReader,
    private val cache: MascotCacheStore? = null,
    private val fetch: (suspend (url: String) -> ByteArray?)? = null,
    private val remoteBaseUrl: String? = null,
) {
    private val _pack = MutableStateFlow<LoadedPack?>(null)
    val pack: StateFlow<LoadedPack?> = _pack.asStateFlow()
    private val mutex = Mutex()

    /** Loads the newest usable pack (bundled or previously downloaded). Safe to call repeatedly. */
    suspend fun load(): LoadedPack? = mutex.withLock {
        _pack.value?.let { return@withLock it }
        val bundledPack = runCatching { bundled.read(MANIFEST)?.let(MascotPack::parse) }.getOrNull()
        val cachedPack = cachedPack()
        val chosen = when {
            cachedPack != null && (bundledPack == null || cachedPack.pack.version > bundledPack.version) -> cachedPack
            bundledPack != null -> LoadedPack(bundledPack, bundled)
            else -> null
        }
        _pack.value = chosen
        chosen
    }

    /**
     * Fetches the published pack and switches to it when it is newer. Returns true when it switched.
     * Atlases whose sha256 is unchanged are reused from the current pack instead of re-downloaded.
     */
    suspend fun refreshRemote(): Boolean {
        val fetch = fetch ?: return false
        val cache = cache ?: return false
        val base = remoteBaseUrl?.trimEnd('/') ?: return false
        val current = load()
        val manifestBytes = runCatching { fetch("$base/$MANIFEST") }.getOrNull() ?: return false
        val remote = MascotPack.parse(manifestBytes) ?: return false
        if (current != null && remote.version <= current.pack.version) return false

        val dir = versionDir(remote.version)
        for ((path, sha) in remote.assets) {
            val reused = current?.takeIf { it.pack.assets[path] == sha }?.files?.read(path)
            val bytes = reused?.takeIf { Sha256.hex(it) == sha }
                ?: runCatching { fetch("$base/$path") }.getOrNull()?.takeIf { Sha256.hex(it) == sha }
                ?: return false
            cache.write("$dir/$path", bytes)
        }
        // The manifest goes last and the pointer after it: a crash mid-download leaves the old pack.
        cache.write("$dir/$MANIFEST", manifestBytes)
        cache.write(POINTER, remote.version.toString().encodeToByteArray())
        mutex.withLock { _pack.value = LoadedPack(remote, prefixed(cache, dir)) }
        runCatching { cache.prune(dir) }
        return true
    }

    private suspend fun cachedPack(): LoadedPack? {
        val cache = cache ?: return null
        return runCatching {
            val version = cache.read(POINTER)?.decodeToString()?.trim()?.toLongOrNull() ?: return null
            val dir = versionDir(version)
            val files = prefixed(cache, dir)
            val pack = files.read(MANIFEST)?.let(MascotPack::parse) ?: return null
            // An atlas the OS purged from the cache reads as the bundled one of the same path, else
            // nothing — the mascot skips a frame rather than the app reading every atlas at start.
            LoadedPack(pack) { path -> files.read(path) ?: bundled.read(path) }
        }.getOrNull()
    }

    private fun prefixed(cache: MascotCacheStore, dir: String) = MascotFileReader { cache.read("$dir/$it") }

    private fun versionDir(version: Long) = "v$version"

    private companion object {
        const val MANIFEST = "manifest.json"
        const val POINTER = "current"
    }
}
