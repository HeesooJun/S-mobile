package com.example.lifesaiver.presentation.screen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionUiState(
    val title: String = "권한이 필요합니다",
    val description: String = "BLE 및 마이크 권한을 허용해주세요.",
    val actionLabel: String = "권한 요청"
)

class PermissionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()
}
