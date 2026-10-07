package com.dfeverx.studioshare.mascot.agent.desktop

import com.dfeverx.studioshare.mascot.agent.AgentAlert
import com.dfeverx.studioshare.mascot.agent.AgentWarning
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import java.awt.SystemTray
import java.awt.TrayIcon

/**
 * Desktop system notifier that hooks into [MascotAgent] to deliver native OS notifications
 * (Notification Center on macOS, Action Center on Windows) whenever warnings or milestone alerts occur.
 */
class DesktopMascotNotifier(
    private val agent: MascotAgent = MascotAgent.default
) {
    private var trayIcon: TrayIcon? = null

    init {
        agent.onAlert { alert ->
            sendAlertNotification(alert)
        }
        agent.onWarning { warning ->
            sendWarningNotification(warning)
        }
    }

    fun bindTrayIcon(icon: TrayIcon) {
        this.trayIcon = icon
    }

    private fun sendAlertNotification(alert: AgentAlert) {
        val icon = trayIcon ?: getSystemTrayIcon()
        if (icon != null) {
            icon.displayMessage(
                "StudioShare • ${alert.title}",
                alert.message,
                TrayIcon.MessageType.INFO
            )
        }
    }

    private fun sendWarningNotification(warning: AgentWarning) {
        val icon = trayIcon ?: getSystemTrayIcon()
        if (icon != null) {
            icon.displayMessage(
                "⚠️ StudioShare • ${warning.title}",
                warning.message,
                TrayIcon.MessageType.WARNING
            )
        }
    }

    private fun getSystemTrayIcon(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        val tray = SystemTray.getSystemTray()
        return tray.trayIcons.firstOrNull()
    }
}
