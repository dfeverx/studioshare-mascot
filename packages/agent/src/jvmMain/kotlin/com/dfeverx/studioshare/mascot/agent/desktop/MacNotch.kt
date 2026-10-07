package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Where the notch is on the main screen, in AWT points. [notchWidth] is 0 on a screen without one
 * (an external display, an Intel Mac, Windows), and the companion then sits as a small tab at the
 * top centre instead.
 */
internal data class NotchGeometry(
    val screenX: Int,
    val screenY: Int,
    val screenWidth: Int,
    val notchWidth: Dp,
    /** The menu bar's height, which on a notched Mac is the notch's. */
    val bandHeight: Dp,
) {
    val hasNotch get() = notchWidth > 0.dp
}

/**
 * The few AppKit calls the notch companion needs, made through the Objective-C runtime so the agent
 * stays plain JVM: reading the notch out of `NSScreen`, and lifting the window above the menu bar.
 * AWT can only make a window "always on top" (the floating level), which still sits *under* the
 * menu bar — and so under the notch. Everything here is a no-op off macOS or if a call fails.
 */
internal object MacNotch {
    private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac")

    fun geometry(): NotchGeometry {
        val gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val b = gc.bounds
        val menuBar = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(gc).top }.getOrDefault(0)
        val notch = if (isMac) runCatching { notchOfMainScreen() }.getOrNull() else null
        val band = when {
            notch != null && notch.second > 0 -> notch.second
            menuBar > 0 -> menuBar.toDouble()
            else -> 32.0
        }
        return NotchGeometry(b.x, b.y, b.width, (notch?.first ?: 0.0).dp, band.dp)
    }

    /**
     * Raises the window titled [title] above the menu bar, on every Space and over full-screen apps,
     * with no shadow, and starts it click-through. AppKit wants this on its main thread, so it is
     * queued there.
     */
    fun raiseAboveMenuBar(title: String) {
        if (!isMac) return
        runCatching {
            onMainThread {
                val window = findWindow(title) ?: return@onMainThread
                windows[title] = window
                send(window, "setLevel:", NS_MAIN_MENU_WINDOW_LEVEL + 3)
                send(window, "setCollectionBehavior:", CAN_JOIN_ALL_SPACES or STATIONARY or FULL_SCREEN_AUXILIARY or IGNORES_CYCLE)
                send(window, "setHasShadow:", 0L)
                send(window, "setIgnoresMouseEvents:", 1L)
            }
        }
    }

    /**
     * Lets clicks through the window titled [title] (true) or takes them (false). The companion's
     * window is bigger than the bar it draws, so it passes clicks through everywhere but the bar.
     */
    fun setClickThrough(title: String, through: Boolean) {
        if (!isMac) return
        runCatching {
            onMainThread {
                val window = windows[title] ?: return@onMainThread
                send(window, "setIgnoresMouseEvents:", if (through) 1L else 0L)
            }
        }
    }

    /** Windows already found by title; only touched on the main thread. */
    private val windows = mutableMapOf<String, Pointer>()

    // ---- NSScreen ------------------------------------------------------------------------------

    /** (notch width, notch height) of `NSScreen.mainScreen`, or null on a screen without one. */
    private fun notchOfMainScreen(): Pair<Double, Double>? {
        val screen = objc.objc_msgSend(cls("NSScreen"), sel("mainScreen")) ?: return null
        if (!respondsTo(screen, "auxiliaryTopLeftArea")) return null // before macOS 12
        val frame = rect.objc_msgSend(screen, sel("frame"))
        val left = rect.objc_msgSend(screen, sel("auxiliaryTopLeftArea"))
        val right = rect.objc_msgSend(screen, sel("auxiliaryTopRightArea"))
        if (left.width <= 0 || right.width <= 0) return null
        val width = frame.width - left.width - right.width
        return if (width > 0) width to left.height else null
    }

    // ---- NSWindow ------------------------------------------------------------------------------

    private fun findWindow(title: String): Pointer? {
        val app = objc.objc_msgSend(cls("NSApplication"), sel("sharedApplication")) ?: return null
        val windows = objc.objc_msgSend(app, sel("windows")) ?: return null
        val count = Pointer.nativeValue(objc.objc_msgSend(windows, sel("count")))
        for (i in 0 until count) {
            val w = objc.objc_msgSend(windows, sel("objectAtIndex:"), i) ?: continue
            val t = objc.objc_msgSend(w, sel("title")) ?: continue
            val utf8 = objc.objc_msgSend(t, sel("UTF8String")) ?: continue
            if (utf8.getString(0, "UTF-8") == title) return w
        }
        return null
    }

    private fun respondsTo(obj: Pointer, selector: String) =
        Pointer.nativeValue(objc.objc_msgSend(obj, sel("respondsToSelector:"), sel(selector))) and 0xFF != 0L

    private fun send(obj: Pointer, selector: String, arg: Long) {
        objc.objc_msgSend(obj, sel(selector), arg)
    }

    // ---- runtime -------------------------------------------------------------------------------

    private const val NS_MAIN_MENU_WINDOW_LEVEL = 24L
    private const val CAN_JOIN_ALL_SPACES = 1L shl 0
    private const val STATIONARY = 1L shl 4
    private const val IGNORES_CYCLE = 1L shl 6
    private const val FULL_SCREEN_AUXILIARY = 1L shl 8

    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer
        fun objc_msgSend(receiver: Pointer?, selector: Pointer): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer, arg: Long): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer, arg: Pointer?): Pointer?
    }

    /** The same `objc_msgSend`, declared to return an `NSRect` (arm64 returns it through x8). */
    @Suppress("FunctionName")
    private interface ObjCRect : Library {
        fun objc_msgSend(receiver: Pointer?, selector: Pointer): NSRect.ByValue
    }

    @Suppress("FunctionName")
    private interface Dispatch : Library {
        fun dispatch_async_f(queue: Pointer, context: Pointer?, work: Work)
    }

    fun interface Work : Callback {
        fun invoke(context: Pointer?)
    }

    @Structure.FieldOrder("x", "y", "width", "height")
    open class NSRect : Structure() {
        @JvmField var x = 0.0
        @JvmField var y = 0.0
        @JvmField var width = 0.0
        @JvmField var height = 0.0

        class ByValue : NSRect(), Structure.ByValue
    }

    private val objc by lazy { Native.load("objc", ObjC::class.java) }
    private val rect by lazy { Native.load("objc", ObjCRect::class.java) }
    private val dispatch by lazy { Native.load("System", Dispatch::class.java) }
    private val mainQueue by lazy { NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q") }

    /** Callbacks the main queue hasn't run yet; JNA frees a callback once it is unreachable. */
    private val pending = mutableSetOf<Work>()

    private fun onMainThread(block: () -> Unit) {
        lateinit var work: Work
        work = Work {
            try {
                runCatching(block)
            } finally {
                synchronized(pending) { pending -= work }
            }
        }
        synchronized(pending) { pending += work }
        dispatch.dispatch_async_f(mainQueue, null, work)
    }

    private fun cls(name: String) = objc.objc_getClass(name)
    private fun sel(name: String) = objc.sel_registerName(name)
}
