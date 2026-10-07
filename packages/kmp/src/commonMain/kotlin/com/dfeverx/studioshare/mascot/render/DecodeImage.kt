package com.dfeverx.studioshare.mascot.render

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes an encoded image (the pack's WebP atlases). Null when the bytes are not an image. */
internal expect fun decodeImage(bytes: ByteArray): ImageBitmap?
