package com.dfeverx.studioshare.mascot.agent.desktop

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Whether a mouse button is down anywhere on screen. The companion's window lets every click
 * outside the island through to what is under it, so it never hears them; reading the button's
 * state is how it knows the user clicked elsewhere, to fold an open card away. No permission is
 * needed for this on either system (macOS asks only for keyboard state). False where it can't tell.
 */
internal object GlobalMouse {
    private val os = System.getProperty("os.name").orEmpty()

    @Suppress("FunctionName")
    private interface CoreGraphics : Library {
        fun CGEventSourceButtonState(stateId: Int, button: Int): Boolean
    }

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun GetAsyncKeyState(key: Int): Short
    }

    private val coreGraphics by lazy {
        runCatching { Native.load("CoreGraphics", CoreGraphics::class.java) }.getOrNull()
    }
    private val user32 by lazy { runCatching { Native.load("user32", User32::class.java) }.getOrNull() }

    /** The left or right button is down. */
    fun pressed(): Boolean = runCatching {
        when {
            os.startsWith("Mac") -> coreGraphics?.let {
                // kCGEventSourceStateCombinedSessionState; kCGMouseButtonLeft, kCGMouseButtonRight
                it.CGEventSourceButtonState(0, 0) || it.CGEventSourceButtonState(0, 1)
            } ?: false
            os.startsWith("Windows") -> user32?.let {
                // VK_LBUTTON, VK_RBUTTON; the high bit is "down now"
                (it.GetAsyncKeyState(0x01).toInt() and 0x8000) != 0 || (it.GetAsyncKeyState(0x02).toInt() and 0x8000) != 0
            } ?: false
            else -> false
        }
    }.getOrDefault(false)
}
