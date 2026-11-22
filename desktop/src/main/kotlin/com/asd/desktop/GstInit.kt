package com.asd.desktop

import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Version

/** GStreamer si inizializza una volta sola, da chi arriva primo (player o analisi). */
object GstInit {
    private var done = false

    @Synchronized
    fun ensure() {
        if (done) return
        Gst.init(Version.of(1, 16), "asd-desktop")
        done = true
    }
}
