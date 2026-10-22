package com.asd.player.data.repository

import com.asd.player.L10n
import com.asd.player.R
import com.asd.player.data.database.SongDao
import com.asd.player.data.database.SongEntity
import com.asd.player.data.drive.DriveAuthException
import com.asd.player.data.scanner.AudioMetadata
import com.asd.player.data.scanner.ScannedFile
import com.asd.player.data.scanner.SourceScanner
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class MusicLibraryRepositoryImpl(private val songDao: SongDao) : MusicLibraryRepository {

    // Una scansione alla volta: se ne arriva un'altra (es. l'altra fonte) aspetta il suo turno.
    private val scanMutex = Mutex()
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    override val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    override val songs: Flow<List<Song>> = songDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun rescan(scanner: SourceScanner): Boolean = scanMutex.withLock {
        val source = scanner.source
        withContext(Dispatchers.IO) {
            try {
                _scanState.value = ScanState.Scanning(source, 0, 0)
                val files = scanner.listFiles()
                val existing = songDao.getBySource(source.name).associateBy { it.uri }

                // Metadati riletti solo per file nuovi o modificati: le riscansioni restano veloci.
                val changed = files.filter { file ->
                    val old = existing[file.uri]
                    old == null || old.fileSize != file.size || old.modifiedAt != file.lastModified
                }
                val processed = AtomicInteger(files.size - changed.size)
                _scanState.value = ScanState.Scanning(source, processed.get(), files.size)

                val semaphore = Semaphore(scanner.metadataParallelism)
                val entities = coroutineScope {
                    changed.map { file ->
                        async {
                            semaphore.withPermit { toEntity(scanner, file, existing[file.uri]) }.also {
                                val done = processed.incrementAndGet()
                                if (done % 10 == 0) _scanState.value = ScanState.Scanning(source, done, files.size)
                            }
                        }
                    }.awaitAll()
                }
                val (toUpdate, toInsert) = entities.partition { it.id != 0L }

                val foundUris = files.mapTo(HashSet()) { it.uri }
                val toDelete = existing.values.filter { it.uri !in foundUris }.map { it.id }
                songDao.applyScan(toInsert, toUpdate, toDelete)
                _scanState.value = ScanState.Done(source, toInsert.size, toUpdate.size, toDelete.size)
                true
            } catch (e: SecurityException) {
                _scanState.value = ScanState.Failed(source, L10n.str(R.string.folder_access_revoked))
                false
            } catch (e: DriveAuthException) {
                _scanState.value = ScanState.Failed(source, e.message ?: L10n.str(R.string.drive_access_denied))
                false
            } catch (e: Exception) {
                _scanState.value = ScanState.Failed(source, e.message ?: e.javaClass.simpleName)
                false
            }
        }
    }

    override suspend fun add(file: ScannedFile, metadata: AudioMetadata, source: SongSource) =
        withContext(Dispatchers.IO) { songDao.upsert(entityOf(file, metadata, source)) }

    override suspend fun unmeasured(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getUnmeasured().map { it.toDomain() }
    }

    override suspend fun setLoudness(songId: Long, lufs: Double?, peakDb: Double?) = withContext(Dispatchers.IO) {
        songDao.setLoudness(songId, lufs ?: SongEntity.LOUDNESS_FAILED, peakDb)
    }

    override suspend fun clearSource(source: SongSource) = withContext(Dispatchers.IO) {
        songDao.deleteBySource(source.name)
    }

    private fun toEntity(scanner: SourceScanner, file: ScannedFile, old: SongEntity?): SongEntity =
        entityOf(file, scanner.readMetadata(file), scanner.source, old?.id ?: 0)

    private fun entityOf(file: ScannedFile, meta: AudioMetadata, source: SongSource, id: Long = 0): SongEntity {
        // Senza tag (es. gli m4a del TUBO dopo una riscansione completa) il nome "Artista - Titolo" fa da tag.
        val base = file.fileName.substringBeforeLast('.')
        val parts = base.split(" - ", limit = 2).map { it.trim() }.takeIf { it.size == 2 && it.all(String::isNotEmpty) }
        return SongEntity(
            id = id,
            uri = file.uri,
            fileName = file.fileName,
            title = meta.title ?: parts?.get(1) ?: base,
            artist = meta.artist ?: parts?.takeIf { meta.title == null }?.get(0),
            album = meta.album,
            durationMs = meta.durationMs,
            mimeType = file.mimeType,
            fileSize = file.size,
            modifiedAt = file.lastModified,
            source = source.name,
        )
    }
}
