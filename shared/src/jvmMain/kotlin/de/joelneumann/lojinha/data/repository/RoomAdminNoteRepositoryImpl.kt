package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.AdminNoteDao
import de.joelneumann.lojinha.data.entity.AdminNoteEntity
import de.joelneumann.lojinha.domain.model.AdminNote
import de.joelneumann.lojinha.domain.repository.AdminNoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAdminNoteRepositoryImpl(
    private val adminNoteDao: AdminNoteDao,
    private val onDataChanged: (() -> Unit)? = null
) : AdminNoteRepository {

    override fun getNotesFlow(): Flow<List<AdminNote>> {
        return adminNoteDao.getAllNotesFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllNotes(): List<AdminNote> {
        return adminNoteDao.getAllNotes().map { it.toDomain() }
    }

    override suspend fun addNote(note: AdminNote) {
        val sanitized = note.copy(
            id = if (note.id.isBlank()) java.util.UUID.randomUUID().toString() else note.id,
            message = note.message.trim().take(1000),
            timestamp = if (note.timestamp <= 0L) System.currentTimeMillis() else note.timestamp
        )
        adminNoteDao.insertNote(AdminNoteEntity.fromDomain(sanitized))
        onDataChanged?.invoke()
    }

    override suspend fun deleteNote(id: String) {
        adminNoteDao.deleteNoteById(id)
        onDataChanged?.invoke()
    }

    override suspend fun deleteAllNotes() {
        adminNoteDao.deleteAllNotes()
        onDataChanged?.invoke()
    }
}
