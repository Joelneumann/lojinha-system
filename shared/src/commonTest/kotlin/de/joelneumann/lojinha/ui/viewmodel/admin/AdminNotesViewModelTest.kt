package de.joelneumann.lojinha.ui.viewmodel.admin

import de.joelneumann.lojinha.domain.model.AdminNote
import de.joelneumann.lojinha.domain.repository.AdminNoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AdminNotesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAdminNoteRepository(
        initialNotes: List<AdminNote> = emptyList()
    ) : AdminNoteRepository {
        val notesFlow = MutableStateFlow(initialNotes)
        var shouldThrowOnDelete = false
        val deletedIds = mutableListOf<String>()

        override fun getNotesFlow(): Flow<List<AdminNote>> = notesFlow
        override suspend fun getAllNotes(): List<AdminNote> = notesFlow.value

        override suspend fun addNote(note: AdminNote) {
            notesFlow.value = notesFlow.value + note
        }

        override suspend fun deleteNote(id: String) {
            if (shouldThrowOnDelete) {
                throw IllegalStateException("Database write failed")
            }
            deletedIds.add(id)
            notesFlow.value = notesFlow.value.filter { it.id != id }
        }

        override suspend fun deleteAllNotes() {
            notesFlow.value = emptyList()
        }
    }

    @Test
    fun testNotesLoadedAndSortedByTimestampDescending() = runTest {
        val note1 = AdminNote("n1", "Old note", "u1", "Alice", 1000L)
        val note2 = AdminNote("n2", "New note", "u2", "Bob", 5000L)
        val note3 = AdminNote("n3", "Mid note", "u3", "Charlie", 3000L)

        val repo = FakeAdminNoteRepository(listOf(note1, note2, note3))
        val viewModel = AdminNotesViewModel(repo)

        val loadedNotes = viewModel.notes.value
        assertEquals(3, loadedNotes.size)
        assertEquals("n2", loadedNotes[0].id)
        assertEquals("n3", loadedNotes[1].id)
        assertEquals("n1", loadedNotes[2].id)
    }

    @Test
    fun testUpdateSearchQuery() = runTest {
        val repo = FakeAdminNoteRepository()
        val viewModel = AdminNotesViewModel(repo)

        assertEquals("", viewModel.searchQuery.value)
        viewModel.updateSearchQuery("Chocolate")
        assertEquals("Chocolate", viewModel.searchQuery.value)
    }

    @Test
    fun testDeleteNoteSuccess() = runTest {
        val note = AdminNote("n1", "Suggestion", "u1", "Alice", 1000L)
        val repo = FakeAdminNoteRepository(listOf(note))
        val viewModel = AdminNotesViewModel(repo)

        assertEquals(1, viewModel.notes.value.size)

        viewModel.deleteNote("n1")

        assertTrue(repo.deletedIds.contains("n1"))
        assertTrue(viewModel.notes.value.isEmpty())
        assertNull(viewModel.errorMessage.value)
        assertFalse(viewModel.isDeletingNote.value)
    }

    @Test
    fun testDeleteNoteFailureSetsErrorMessage() = runTest {
        val note = AdminNote("n1", "Suggestion", "u1", "Alice", 1000L)
        val repo = FakeAdminNoteRepository(listOf(note)).apply {
            shouldThrowOnDelete = true
        }
        val viewModel = AdminNotesViewModel(repo)

        viewModel.deleteNote("n1")

        assertEquals("Database write failed", viewModel.errorMessage.value)
        assertFalse(viewModel.isDeletingNote.value)

        viewModel.clearError()
        assertNull(viewModel.errorMessage.value)
    }
}
