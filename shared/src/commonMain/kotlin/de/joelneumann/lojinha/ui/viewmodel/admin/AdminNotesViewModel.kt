package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.AdminNote
import de.joelneumann.lojinha.domain.repository.AdminNoteRepository
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminNotesViewModel(
    private val adminNoteRepository: AdminNoteRepository
) : ViewModel() {

    private val _notes = MutableStateFlow<List<AdminNote>>(emptyList())
    val notes: StateFlow<List<AdminNote>> = _notes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDeletingNote = MutableStateFlow(false)
    val isDeletingNote: StateFlow<Boolean> = _isDeletingNote.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var notesJob: Job? = null

    init {
        loadNotes()
    }

    fun loadNotes() {
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            adminNoteRepository.getNotesFlow().collect { list ->
                _notes.value = list.sortedByDescending { it.timestamp }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            _isDeletingNote.value = true
            try {
                adminNoteRepository.deleteNote(id)
            } catch (e: Exception) {
                AppLogger.error("AdminNotesViewModel", "Failed to delete note: ${e.message}", e)
                _errorMessage.value = e.message
            } finally {
                _isDeletingNote.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
