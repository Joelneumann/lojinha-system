package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.AdminNote
import kotlinx.coroutines.flow.Flow

interface AdminNoteRepository {
    fun getNotesFlow(): Flow<List<AdminNote>>
    suspend fun getAllNotes(): List<AdminNote>
    suspend fun addNote(note: AdminNote)
    suspend fun deleteNote(id: String)
    suspend fun deleteAllNotes()
}
