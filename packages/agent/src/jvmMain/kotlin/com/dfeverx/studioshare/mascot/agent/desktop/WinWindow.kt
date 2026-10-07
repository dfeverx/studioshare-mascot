package com.dfeverx.studioshare.mascot.agent.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString

/**
 * Windows' side of [MacNotch.setClickThrough]: a transparent window still takes every click on its
 * rectangle, so the companion's stage is made click-through with `WS_EX_TRANSPARENT` everywhere but
 * the island. A no-op off Windows.
 */
internal object WinWindow {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    private const val GWL_EXSTYLE = -20
    private const val WS_EX_TRANSPARENT = 0x20L

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun FindWindowW(className: WString?, title: WString): Pointer?
        fun GetWindowLongPtrW(hwnd: Pointer, index: Int): Long
        fun SetWindowLongPtrW(hwnd: Pointer, index: Int, value: Long): Long
    }

    private val user32 by lazy { Native.load("user32", User32::class.java) }

    /** Windows already found by title. */
    private val windows = mutableMapOf<String, Pointer>()

    /** Lets clicks through the window titled [title] (true) or takes them (false); false if the window isn't found yet. */
    fun setClickThrough(title: String, through: Boolean): Boolean {
        if (!isWindows) return true
        return runCatching {
            val hwnd = windows[title] ?: user32.FindWindowW(null, WString(title))?.also { windows[title] = it } ?: return false
            val style = user32.GetWindowLongPtrW(hwnd, GWL_EXSTYLE)
            // the window is already layered (it is transparent), which is what lets the style pass clicks on
            val next = if (through) style or WS_EX_TRANSPARENT else style and WS_EX_TRANSPARENT.inv()
            if (next != style) user32.SetWindowLongPtrW(hwnd, GWL_EXSTYLE, next)
            true
        }.getOrDefault(false)
    }
}
