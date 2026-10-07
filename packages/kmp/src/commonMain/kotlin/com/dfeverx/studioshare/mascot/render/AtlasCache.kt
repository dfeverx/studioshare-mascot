package com.dfeverx.studioshare.mascot.render

import androidx.compose.ui.graphics.ImageBitmap
import com.dfeverx.studioshare.mascot.pack.MascotFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Decoded atlases, newest first. A 256 px, 24-frame atlas is ~6 MB decoded, so only [capacity] are
 * kept; the mascot rarely shows more than a mood, the walk and the one it is about to return to.
 */
class AtlasCache(private val capacity: Int = 3) {
    private val entries = LinkedHashMap<String, ImageBitmap>()
    private val mutex = Mutex()

    suspend fun get(files: MascotFileReader, path: String, packVersion: Long): ImageBitmap? {
        val key = "$packVersion/$path"
        mutex.withLock {
            entries.remove(key)?.let { entries[key] = it; return it }
        }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { files.read(path)?.let(::decodeImage) }.getOrNull()
        } ?: return null
        mutex.withLock {
            entries[key] = bitmap
            while (entries.size > capacity) entries.remove(entries.keys.first())
        }
        return bitmap
    }

    suspend fun clear() = mutex.withLock { entries.clear() }
}
