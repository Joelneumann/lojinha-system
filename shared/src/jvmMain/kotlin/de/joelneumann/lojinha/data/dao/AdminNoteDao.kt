package de.joelneumann.lojinha.data.dao

import androidx.room.*
import de.joelneumann.lojinha.data.entity.AdminNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminNoteDao {
    @Query("SELECT * FROM admin_notes ORDER BY timestamp DESC")
    fun getAllNotesFlow(): Flow<List<AdminNoteEntity>>

    @Query("SELECT * FROM admin_notes ORDER BY timestamp DESC")
    suspend fun getAllNotes(): List<AdminNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: AdminNoteEntity)

    @Query("DELETE FROM admin_notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("DELETE FROM admin_notes")
    suspend fun deleteAllNotes()
}
