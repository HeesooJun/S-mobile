package com.example.rescuer.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rescuer.core.ble.BleRSSILocating
import com.example.rescuer.ui.components.DistanceTrend
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. UI 상태 정의: DistanceTrack 컴포넌트와 1:1 매핑
data class DistanceUiState(
    val distanceMeters: Float? = null,
    val trend: DistanceTrend = DistanceTrend.Unknown,
    val isSearching: Boolean = true,
    val statusLabel: String = "신호 탐색 중...",
    val guideLabel: String = "주변 환경 및 장애물에 따라\n거리가 정확하지 않을 수 있습니다."
)

class DistanceViewModel(
    private val locating: BleRSSILocating
) : ViewModel() {

    private val _uiState = MutableStateFlow(DistanceUiState())
    val uiState: StateFlow<DistanceUiState> = _uiState.asStateFlow()

    private var collectionJob: Job? = null

    // Screen의 DisposableEffect에서 호출됨
    fun start() {
        locating.startTracking() // 비즈니스 로직 시작
        startCollectingData()
    }

    fun stop() {
        locating.stopTracking() // 비즈니스 로직 중지
        stopCollectingData()
        resetState()
    }

    private fun startCollectingData() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            // BleRSSILocating에서 계산된 m 단위 거리 스트림 구독
            locating.distance.collect { newDistance ->
                updateDistanceState(newDistance)
            }
        }
    }

    private fun stopCollectingData() {
        collectionJob?.cancel()
    }

    private fun updateDistanceState(newDist: Float?) {
        val currentState = _uiState.value
        val prevDist = currentState.distanceMeters

        // 거리 변화량 계산 (노이즈 필터링: 0.5m 기준)
        val newTrend = when {
            newDist == null || prevDist == null -> DistanceTrend.Unknown
            newDist < prevDist - 0.5f -> DistanceTrend.Approaching
            newDist > prevDist + 0.5f -> DistanceTrend.Receding
            else -> currentState.trend // 변화폭이 작으면 이전 트렌드 유지
        }

        _uiState.value = currentState.copy(
            distanceMeters = newDist,
            trend = newTrend,
            isSearching = (newDist == null), // null이면 자동으로 탐색 중 애니메이션 트리거
            statusLabel = if (newDist == null) "신호 탐색 중..." else "상대 기기 감지됨"
        )
    }

    private fun resetState() {
        _uiState.value = DistanceUiState()
    }
}
