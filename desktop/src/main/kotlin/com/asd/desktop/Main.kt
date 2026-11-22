package com.asd.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.asd.desktop.ui.App
import com.asd.player.ui.theme.AsdTheme
import javax.swing.SwingUtilities

fun main() = application {
    val desktop = remember { Desktop() }
    val icon = remember { Main::class.java.getResourceAsStream("/asd.png")?.use { BitmapPainter(loadImageBitmap(it)) } }
    val windowState = rememberWindowState(width = 1100.dp, height = 760.dp)
    fun quit() {
        desktop.close()
        exitApplication()
    }
    // "Esci" dai controlli multimediali di GNOME arriva da D-Bus: si chiude dal thread della UI.
    desktop.onQuit = { SwingUtilities.invokeLater { quit() } }
    Window(onCloseRequest = ::quit, title = Texts.APP, icon = icon, state = windowState) {
        LaunchedEffect(Unit) {
            desktop.raise.collect {
                windowState.isMinimized = false
                window.toFront()
                window.requestFocus()
            }
        }
        AsdTheme { App(desktop) }
    }
}

private object Main
