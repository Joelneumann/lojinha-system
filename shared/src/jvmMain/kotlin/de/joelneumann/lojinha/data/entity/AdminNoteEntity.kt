package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.AdminNote

@Entity(
    tableName = "admin_notes",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class AdminNoteEntity(
    @PrimaryKey val id: String,
    val message: String,
    val userId: String,
    val userNameSnapshot: String,
    val timestamp: Long
) {
    fun toDomain(): AdminNote = AdminNote(
        id = id,
        message = message,
        userId = userId,
        userNameSnapshot = userNameSnapshot,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(note: AdminNote): AdminNoteEntity = AdminNoteEntity(
            id = note.id,
            message = note.message,
            userId = note.userId,
            userNameSnapshot = note.userNameSnapshot,
            timestamp = note.timestamp
        )
    }
}
