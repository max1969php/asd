package com.asd.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.asd.desktop.ui.App
import com.asd.player.ui.theme.AsdTheme

fun main() = application {
    val desktop = remember { Desktop() }
    val icon = remember { Main::class.java.getResourceAsStream("/asd.png")?.use { BitmapPainter(loadImageBitmap(it)) } }
    Window(
        onCloseRequest = {
            desktop.close()
            exitApplication()
        },
        title = Texts.APP,
        icon = icon,
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
    ) {
        AsdTheme { App(desktop) }
    }
}

private object Main
