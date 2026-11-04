package com.segawa.os.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.segawa.os.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SegawaViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = SegawaPreferences(app)
    private val dao = FocusDatabase.get(app).focusDao()

    val accent = prefs.accent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Crimson")
    val mode = prefs.mode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Dark")
    val sessions = dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setAccent(value: String) = viewModelScope.launch { prefs.setAccent(value) }
    fun setMode(value: String) = viewModelScope.launch { prefs.setMode(value) }
    fun saveSession(minutes: Int, completed: Boolean) = viewModelScope.launch {
        dao.insert(FocusSessionEntity(startedAt = System.currentTimeMillis(), durationMinutes = minutes, completed = completed))
    }
}
