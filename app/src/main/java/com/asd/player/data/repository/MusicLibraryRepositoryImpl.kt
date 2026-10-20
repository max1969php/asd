package com.asd.player.data.repository

import android.net.Uri
import com.asd.player.data.database.SongDao
import com.asd.player.data.database.SongEntity
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.data.scanner.MetadataReader
import com.asd.player.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MusicLibraryRepositoryImpl(
    private val songDao: SongDao,
    private val scanner: AudioScanner,
    private val metadataReader: MetadataReader,
) : MusicLibraryRepository {

    private val scanMutex = Mutex()
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    override val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    override val songs: Flow<List<Song>> = songDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun rescan(treeUri: Uri) {
        if (scanMutex.isLocked) return
        scanMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    _scanState.value = ScanState.Scanning(0, 0)
                    val files = scanner.scan(treeUri)
                    val existing = songDao.getAll().associateBy { it.uri }
                    val toInsert = mutableListOf<SongEntity>()
                    val toUpdate = mutableListOf<SongEntity>()

                    files.forEachIndexed { index, file ->
                        val old = existing[file.uri]
                        // Metadati riletti solo per file nuovi o modificati: le riscansioni restano veloci.
                        val unchanged = old != null && old.fileSize == file.size && old.modifiedAt == file.lastModified
                        if (!unchanged) {
                            val meta = metadataReader.read(Uri.parse(file.uri))
                            val entity = SongEntity(
                                id = old?.id ?: 0,
                                uri = file.uri,
                                fileName = file.fileName,
                                title = meta.title ?: file.fileName.substringBeforeLast('.'),
                                artist = meta.artist,
                                album = meta.album,
                                durationMs = meta.durationMs,
                                mimeType = file.mimeType,
                                fileSize = file.size,
                                modifiedAt = file.lastModified,
                            )
                            if (old == null) toInsert += entity else toUpdate += entity
                        }
                        if (index % 25 == 0) _scanState.value = ScanState.Scanning(index, files.size)
                    }

                    val foundUris = files.mapTo(HashSet()) { it.uri }
                    val toDelete = existing.values.filter { it.uri !in foundUris }.map { it.id }
                    songDao.applyScan(toInsert, toUpdate, toDelete)
                    _scanState.value = ScanState.Done(toInsert.size, toUpdate.size, toDelete.size)
                } catch (e: SecurityException) {
                    _scanState.value = ScanState.Failed("Accesso alla cartella revocato. Sceglila di nuovo.")
                } catch (e: Exception) {
                    _scanState.value = ScanState.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }
}
