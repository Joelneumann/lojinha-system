package de.joelneumann.lojinha.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.domain.model.AdminNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RoomAdminNoteRepositoryImplTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var db: AppDatabase
    private var dataChangedCallCount = 0
    private lateinit var repository: RoomAdminNoteRepositoryImpl

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("admin_note_repo_test").toFile()
        dbFile = File(tempDir, "test_notes.db")

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        db = builder.build()

        dataChangedCallCount = 0
        repository = RoomAdminNoteRepositoryImpl(
            adminNoteDao = db.adminNoteDao(),
            onDataChanged = { dataChangedCallCount++ }
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testAddNoteAndGetNotesFlow() = runTest {
        val initialNotes = repository.getNotesFlow().first()
        assertTrue(initialNotes.isEmpty(), "Initial notes must be empty")

        val note = AdminNote(
            id = "note-1",
            message = "We need more Club Mate!",
            userId = "u-123",
            userNameSnapshot = "Alice",
            timestamp = 1700000000000L
        )
        repository.addNote(note)

        assertEquals(1, dataChangedCallCount)

        val updatedNotes = repository.getNotesFlow().first()
        assertEquals(1, updatedNotes.size)
        assertEquals(note.id, updatedNotes.first().id)
        assertEquals("We need more Club Mate!", updatedNotes.first().message)
        assertEquals("Alice", updatedNotes.first().userNameSnapshot)
        assertEquals("u-123", updatedNotes.first().userId)

        val allNotes = repository.getAllNotes()
        assertEquals(1, allNotes.size)
        assertEquals(note.id, allNotes.first().id)
    }

    @Test
    fun testDeleteNote() = runTest {
        val note1 = AdminNote("n1", "Note 1", "u1", "Bob", 1700000001000L)
        val note2 = AdminNote("n2", "Note 2", "u2", "Charlie", 1700000002000L)
        repository.addNote(note1)
        repository.addNote(note2)

        assertEquals(2, repository.getNotesFlow().first().size)
        val countBeforeDelete = dataChangedCallCount

        repository.deleteNote(note1.id)

        assertEquals(countBeforeDelete + 1, dataChangedCallCount)
        val remaining = repository.getNotesFlow().first()
        assertEquals(1, remaining.size)
        assertEquals(note2.id, remaining.first().id)
    }

    @Test
    fun testDeleteAllNotes() = runTest {
        repository.addNote(AdminNote("nA", "Note A", "u1", "Bob", 1700000001000L))
        repository.addNote(AdminNote("nB", "Note B", "u2", "Charlie", 1700000002000L))

        assertEquals(2, repository.getNotesFlow().first().size)

        repository.deleteAllNotes()

        val emptyList = repository.getNotesFlow().first()
        assertTrue(emptyList.isEmpty())
        assertTrue(repository.getAllNotes().isEmpty())
    }
}
