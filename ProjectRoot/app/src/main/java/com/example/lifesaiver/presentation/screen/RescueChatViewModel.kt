package com.example.lifesaiver.presentation.screen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RescueChatUiState(
    val inputValue: String = ""
)

class RescueChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RescueChatUiState())
    val uiState: StateFlow<RescueChatUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _uiState.value = _uiState.value.copy(inputValue = value)
    }

    fun consumeSend(): String? {
        val text = _uiState.value.inputValue.trim()
        if (text.isBlank()) return null
        _uiState.value = _uiState.value.copy(inputValue = "")
        return text
    }
}
