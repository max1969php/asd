package com.asd.desktop

import java.io.File
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/** org.mpris.MediaPlayer2: l'applicazione. */
@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MediaPlayer2 : DBusInterface {
    fun Raise()
    fun Quit()
}

/** org.mpris.MediaPlayer2.Player: i comandi del player (tasti multimediali, barra di GNOME). */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MediaPlayer2Player : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)
}

/**
 * ASD Desktop su D-Bus come lettore MPRIS: GNOME lo mostra tra i controlli multimediali e gli
 * manda i tasti play/pausa, avanti e indietro delle tastiere e delle cuffie. Se il bus di sessione
 * non c'è (es. avvio senza desktop) ASD funziona lo stesso, solo senza questi controlli.
 */
class Mpris(
    private val player: Player,
    private val onRaise: () -> Unit,
    private val onQuit: () -> Unit,
) : MediaPlayer2, MediaPlayer2Player, Properties {

    private var connection: DBusConnection? = null
    private var last: Map<String, Variant<*>> = emptyMap()

    fun start(): Boolean = runCatching {
        val c = DBusConnectionBuilder.forSessionBus().build()
        c.requestBusName(BUS_NAME)
        c.exportObject(PATH, this)
        connection = c
    }.isSuccess

    /** Avvisa GNOME di quello che è cambiato (stato, pezzo, modi). */
    fun publish() {
        val c = connection ?: return
        val now = playerProps()
        val changed = now.filter { (k, v) -> k != "Position" && last[k]?.value != v.value }
        last = now
        if (changed.isEmpty()) return
        runCatching { c.sendMessage(Properties.PropertiesChanged(PATH, PLAYER, changed, emptyList())) }
    }

    fun close() {
        runCatching { connection?.releaseBusName(BUS_NAME) }
        runCatching { connection?.close() }
        connection = null
    }

    override fun getObjectPath(): String = PATH
    override fun isRemote(): Boolean = false

    // org.mpris.MediaPlayer2
    override fun Raise() = onRaise()
    override fun Quit() = onQuit()

    // org.mpris.MediaPlayer2.Player
    override fun Next() {
        player.next()
    }
    override fun Previous() {
        player.previous()
    }
    override fun Pause() {
        if (player.state.value.playing) player.togglePlayPause()
    }
    override fun PlayPause() {
        player.togglePlayPause()
    }
    override fun Stop() = Pause()
    override fun Play() {
        if (!player.state.value.playing) player.togglePlayPause()
    }
    override fun Seek(offset: Long) {
        player.seekTo(player.positionMs + offset / 1000)
    }
    override fun SetPosition(trackId: DBusPath, position: Long) {
        if (trackId.path == trackPath()) player.seekTo(position / 1000)
    }
    override fun OpenUri(uri: String) = Unit

    // org.freedesktop.DBus.Properties
    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        (if (interfaceName == ROOT) rootProps() else playerProps())[propertyName] as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        // Volume, Rate, Shuffle e LoopStatus da fuori: ASD li cambia solo dai suoi comandi.
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
        if (interfaceName == ROOT) rootProps() else playerProps()

    private fun rootProps(): Map<String, Variant<*>> = mapOf(
        "CanQuit" to Variant(true),
        "CanRaise" to Variant(true),
        "HasTrackList" to Variant(false),
        "Identity" to Variant(Texts.APP),
        "DesktopEntry" to Variant("asd-desktop"),
        "SupportedUriSchemes" to Variant(listOf("file"), "as"),
        "SupportedMimeTypes" to Variant(listOf("audio/mpeg", "audio/flac", "audio/ogg", "audio/mp4", "audio/wav", "audio/opus"), "as"),
    )

    private fun playerProps(): Map<String, Variant<*>> {
        val s = player.state.value
        val song = s.current
        val status = when {
            song == null -> "Stopped"
            s.playing -> "Playing"
            else -> "Paused"
        }
        val metadata = HashMap<String, Variant<*>>()
        metadata["mpris:trackid"] = Variant(DBusPath(trackPath()))
        if (song != null) {
            metadata["xesam:title"] = Variant(song.title)
            metadata["xesam:artist"] = Variant(listOf(song.displayArtist), "as")
            song.album?.let { metadata["xesam:album"] = Variant(it) }
            metadata["xesam:url"] = Variant(song.uri)
            if (s.durationMs > 0) metadata["mpris:length"] = Variant(s.durationMs * 1000)
            metadata["mpris:artUrl"] = Variant(ICON_URL)
        }
        return mapOf(
            "PlaybackStatus" to Variant(status),
            "LoopStatus" to Variant(
                when (s.repeat) {
                    Repeat.OFF -> "None"
                    Repeat.ALL -> "Playlist"
                    Repeat.ONE -> "Track"
                }
            ),
            "Rate" to Variant(1.0),
            "Shuffle" to Variant(s.shuffle),
            "Metadata" to Variant(metadata, "a{sv}"),
            "Volume" to Variant(1.0),
            "Position" to Variant(player.positionMs * 1000),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "CanGoNext" to Variant(song != null),
            "CanGoPrevious" to Variant(song != null),
            "CanPlay" to Variant(song != null),
            "CanPause" to Variant(song != null),
            "CanSeek" to Variant(song != null),
            "CanControl" to Variant(true),
        )
    }

    /** Un id D-Bus per il pezzo corrente (valido come object path). */
    private fun trackPath(): String = player.state.value.current?.let { "/org/asd/track/${it.id}" } ?: "/org/mpris/MediaPlayer2/TrackList/NoTrack"

    companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.asd_desktop"
        const val PATH = "/org/mpris/MediaPlayer2"
        private const val ROOT = "org.mpris.MediaPlayer2"
        private const val PLAYER = "org.mpris.MediaPlayer2.Player"

        /** L'icona installata dal .deb, mostrata da GNOME quando il pezzo non ha copertina. */
        private val ICON_URL = File("/usr/share/icons/hicolor/256x256/apps/asd-desktop.png").takeIf { it.exists() }?.toURI()?.toString() ?: ""
    }
}
