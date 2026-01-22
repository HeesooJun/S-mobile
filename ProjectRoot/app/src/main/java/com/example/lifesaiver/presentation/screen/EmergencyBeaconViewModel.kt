package com.example.lifesaiver.presentation.screen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmergencyBeaconUiState(
    val headerLabel: String = "긴급 상황",
    val titleLabel: String = "구조 신호 송출",
    val subtitlePrimary: String = "초절전 모드로 신호 전송 중",
    val subtitleSecondary: String = "화면 밝기가 최소화됩니다",
    val prevLabel: String = "이전",
    val nextLabel: String = "다음"
)

class EmergencyBeaconViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EmergencyBeaconUiState())
    val uiState: StateFlow<EmergencyBeaconUiState> = _uiState.asStateFlow()
}
