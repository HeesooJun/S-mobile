package com.example.lifesaiver.presentation.screen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModeGateUiState(
    val appName: String = "Saivior",
    val tagline: String = "오프라인 구조 시스템",
    val rescuerLabel: String = "구조자 모드",
    val questionLabel: String = "위급상황이신가요?",
    val yesLabel: String = "YES",
    val noLabel: String = "NO"
)

class ModeGateViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ModeGateUiState())
    val uiState: StateFlow<ModeGateUiState> = _uiState.asStateFlow()
}
