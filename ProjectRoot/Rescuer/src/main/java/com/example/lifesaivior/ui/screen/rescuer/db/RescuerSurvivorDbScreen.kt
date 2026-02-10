package com.example.lifesaivior.ui.screen.rescuer.db

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifesaivior.core.location.DistanceMeasurementSource
import com.example.lifesaivior.core.profile.SurvivorProfile
import com.example.lifesaivior.ui.components.ScreenScaffold
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp
import kotlin.math.pow

@Composable
fun RescuerSurvivorDbScreen(
    survivors: List<SurvivorProfile>,
    meshSurvivors: List<SurvivorProfile> = emptyList(),
    peerRssiMap: Map<String, Int> = emptyMap(),
    peerBatteryMap: Map<String, Int> = emptyMap(),
    onDisconnectClick: () -> Unit,
    onOpenMeshMap: () -> Unit,
    isDisconnecting: Boolean = false,
    selectedTargetPeerId: String? = null,
    onSelectTarget: (SurvivorProfile) -> Unit = {},
    isLive: Boolean = true,
    activeSurvivor: SurvivorProfile? = null,
    isInCall: Boolean = false,
    callingPeerId: String? = null,
    callPeerId: String? = null,
    callPeerRttCm: Int? = null,
    activeDistancePeerId: String? = null,
    activeDistanceMeters: Float? = null,
    activeDistanceSource: DistanceMeasurementSource = DistanceMeasurementSource.NONE
) {
    val scale = LocalAppScale.current

    val deepBlack = AppColors.Black
    val charcoal = AppColors.Gray800
    val border = AppColors.Gray700.copy(alpha = 0.9f)
    val neonGreen = AppColors.Green
    val neonRed = AppColors.Red

    val callingSurvivorName = (survivors + meshSurvivors)
        .firstOrNull { it.peerId == callingPeerId }
        ?.name
        ?.ifBlank { "생존자" }
        ?: "생존자"

    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(query, survivors) {
        val q = query.trim()
        if (q.isEmpty()) survivors
        else survivors.filter { survivor -> survivor.name.contains(q, ignoreCase = true) }
    }
    val filteredMesh = remember(query, meshSurvivors) {
        val q = query.trim()
        if (q.isEmpty()) meshSurvivors
        else meshSurvivors.filter { survivor -> survivor.name.contains(q, ignoreCase = true) }
    }
    var isMeshExpanded by rememberSaveable { mutableStateOf(false) }
    val hasAnySurvivor = filtered.isNotEmpty() || filteredMesh.isNotEmpty()

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(deepBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = scaledDp(20, scale))
                    .padding(top = scaledDp(16, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIFESAIVIOR",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ActionPill(
                        label = "메쉬 망",
                        tint = neonGreen,
                        scale = scale,
                        onClick = onOpenMeshMap
                    )
                }

                Spacer(modifier = Modifier.height(scaledDp(12, scale)))

                Surface(
                    color = charcoal.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(scaledDp(18, scale)),
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(14, scale))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "직접 연결 생존자",
                                    color = AppColors.White,
                                    fontSize = scaledSp(18, scale),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "직접 연결 ${survivors.size}명",
                                    color = AppColors.Gray500,
                                    fontSize = scaledSp(12, scale),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            ActionPill(
                                label = if (isDisconnecting) "연결 해제 중" else "연결 해제",
                                tint = neonRed,
                                scale = scale,
                                enabled = !isDisconnecting,
                                onClick = onDisconnectClick
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(scaledDp(12, scale)))

                Surface(
                    color = charcoal.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(scaledDp(18, scale)),
                    border = BorderStroke(1.dp, border),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = query,
                        onValueChange = { value -> query = value },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "이름으로 검색",
                                color = AppColors.White.copy(alpha = 0.45f),
                                fontSize = scaledSp(14, scale)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "검색",
                                tint = AppColors.White.copy(alpha = 0.6f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = neonGreen,
                            focusedTextColor = AppColors.White,
                            unfocusedTextColor = AppColors.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(scaledDp(14, scale)))

                if (!hasAnySurvivor) {
                    Spacer(modifier = Modifier.height(scaledDp(22, scale)))
                    Surface(
                        color = charcoal.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(scaledDp(22, scale)),
                        border = BorderStroke(1.dp, border),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = scaledDp(28, scale)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "수신된 생존자 정보가 없습니다",
                                color = AppColors.White.copy(alpha = 0.85f),
                                fontSize = scaledSp(14, scale),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                            Text(
                                text = "연결되면 이 화면에 자동으로 표시됩니다",
                                color = AppColors.White.copy(alpha = 0.5f),
                                fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = scaledDp(24, scale)),
                        verticalArrangement = Arrangement.spacedBy(scaledDp(12, scale))
                    ) {
                        if (filtered.isEmpty()) {
                            item {
                                Surface(
                                    color = charcoal.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(scaledDp(18, scale)),
                                    border = BorderStroke(1.dp, border),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = scaledDp(20, scale)),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "직접 연결된 생존자가 없습니다",
                                            color = AppColors.White.copy(alpha = 0.82f),
                                            fontSize = scaledSp(13, scale),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                                        Text(
                                            text = "연결되면 바로 목록에 표시됩니다",
                                            color = AppColors.Gray400,
                                            fontSize = scaledSp(11, scale),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filtered) { survivor ->
                                SurvivorCard(
                                    survivor = survivor,
                                    scale = scale,
                                    charcoal = charcoal,
                                    border = border,
                                    neonGreen = neonGreen,
                                    neonRed = neonRed,
                                    peerRssiMap = peerRssiMap,
                                    peerBatteryMap = peerBatteryMap,
                                    isSelected = survivor.peerId.isNotBlank() && survivor.peerId == selectedTargetPeerId,
                                    onSelectTarget = onSelectTarget,
                                    isCalling = survivor.peerId.isNotBlank() && survivor.peerId == callingPeerId,
                                    isInCallWithPeer = isInCall &&
                                        survivor.peerId.isNotBlank() &&
                                        survivor.peerId == (callPeerId ?: activeSurvivor?.peerId),
                                    liveDistanceMeters = if (
                                        survivor.peerId.isNotBlank() &&
                                        survivor.peerId == activeDistancePeerId
                                    ) {
                                        activeDistanceMeters
                                    } else {
                                        null
                                    },
                                    liveDistanceSource = if (
                                        survivor.peerId.isNotBlank() &&
                                        survivor.peerId == activeDistancePeerId
                                    ) {
                                        activeDistanceSource
                                    } else {
                                        DistanceMeasurementSource.NONE
                                    },
                                    rttDistanceMeters = if (
                                        survivor.peerId.isNotBlank() &&
                                        survivor.peerId == callPeerId
                                    ) {
                                        callPeerRttCm?.toFloat()?.div(100f)
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                        if (meshSurvivors.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                            }
                            item {
                                Surface(
                                    color = charcoal.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(scaledDp(18, scale)),
                                    border = BorderStroke(1.dp, border),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isMeshExpanded = !isMeshExpanded }
                                            .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(12, scale)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "메쉬 정보",
                                                color = AppColors.White,
                                                fontSize = scaledSp(14, scale),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "메쉬 수신 ${filteredMesh.size}명",
                                                color = AppColors.Gray500,
                                                fontSize = scaledSp(11, scale),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isMeshExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = if (isMeshExpanded) "접기" else "펼치기",
                                            tint = AppColors.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            if (isMeshExpanded) {
                                if (filteredMesh.isEmpty()) {
                                    item {
                                        Text(
                                            text = "검색 결과 없음",
                                            color = AppColors.Gray400,
                                            fontSize = scaledSp(12, scale),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(start = scaledDp(6, scale))
                                        )
                                    }
                                } else {
                                    items(filteredMesh) { survivor ->
                                        SurvivorCard(
                                            survivor = survivor,
                                            scale = scale,
                                            charcoal = charcoal,
                                            border = border,
                                            neonGreen = neonGreen,
                                            neonRed = neonRed,
                                            peerRssiMap = peerRssiMap,
                                            peerBatteryMap = peerBatteryMap,
                                            isSelected = false,
                                            onSelectTarget = {},
                                            isCalling = false,
                                            isInCallWithPeer = false,
                                            liveDistanceMeters = null,
                                            liveDistanceSource = DistanceMeasurementSource.NONE,
                                            rttDistanceMeters = null,
                                            isSelectable = false,
                                            statusLabel = "메쉬",
                                            statusColor = AppColors.Gray400
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (callingPeerId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Shadow.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = charcoal.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(scaledDp(20, scale)),
                        border = BorderStroke(1.dp, border),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = scaledDp(28, scale))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = scaledDp(22, scale),
                                    vertical = scaledDp(24, scale)
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = neonGreen,
                                strokeWidth = scaledDp(3, scale)
                            )
                            Spacer(modifier = Modifier.height(scaledDp(14, scale)))
                            Text(
                                text = "통화 연결 시도 중",
                                color = AppColors.White,
                                fontSize = scaledSp(15, scale),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                            Text(
                                text = "$callingSurvivorName · 최대 15초 대기",
                                color = AppColors.White.copy(alpha = 0.72f),
                                fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    tint: Color,
    scale: Float,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val resolvedAlpha = if (enabled) 1f else 0.45f
    Surface(
        color = tint.copy(alpha = 0.12f * resolvedAlpha),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f * resolvedAlpha)),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = label,
            color = tint.copy(alpha = resolvedAlpha),
            fontSize = scaledSp(11, scale),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = scaledDp(12, scale), vertical = scaledDp(7, scale))
        )
    }
}

@Composable
private fun SurvivorCard(
    survivor: SurvivorProfile,
    scale: Float,
    charcoal: Color,
    border: Color,
    neonGreen: Color,
    neonRed: Color,
    peerRssiMap: Map<String, Int>,
    peerBatteryMap: Map<String, Int>,
    isSelected: Boolean,
    onSelectTarget: (SurvivorProfile) -> Unit,
    isCalling: Boolean,
    isInCallWithPeer: Boolean,
    liveDistanceMeters: Float?,
    liveDistanceSource: DistanceMeasurementSource,
    rttDistanceMeters: Float?,
    isSelectable: Boolean = true,
    statusLabel: String? = null,
    statusColor: Color? = null
) {
    val rssi = peerRssiMap[survivor.peerId]
    val battery = peerBatteryMap[survivor.peerId]?.coerceIn(0, 100)
    val estimatedDistanceMeters = rssi?.let { estimateDistanceMeters(it) }
    val resolvedDistance = when {
        liveDistanceMeters != null && liveDistanceSource == DistanceMeasurementSource.UWB ->
            liveDistanceMeters to DistanceMeasurementSource.UWB

        liveDistanceMeters != null && liveDistanceSource == DistanceMeasurementSource.RTT ->
            liveDistanceMeters to DistanceMeasurementSource.RTT

        rttDistanceMeters != null ->
            rttDistanceMeters to DistanceMeasurementSource.RTT

        liveDistanceMeters != null && liveDistanceSource == DistanceMeasurementSource.RSSI ->
            liveDistanceMeters to DistanceMeasurementSource.RSSI

        estimatedDistanceMeters != null ->
            estimatedDistanceMeters to DistanceMeasurementSource.RSSI

        else -> null
    }
    val distanceText = resolvedDistance?.first?.let { String.format("%.1f", it) }
    val distancePrefix = when (resolvedDistance?.second) {
        DistanceMeasurementSource.UWB -> "UWB 약 "
        DistanceMeasurementSource.RTT -> "RTT 약 "
        DistanceMeasurementSource.RSSI -> "약 "
        else -> "약 "
    }
    val batteryChipText = battery?.let { "배터리 $it%" } ?: "배터리 미수신"
    val batteryChipBg = when {
        battery == null -> AppColors.Gray700.copy(alpha = 0.35f)
        battery >= 70 -> AppColors.Green.copy(alpha = 0.14f)
        battery >= 35 -> Color(0xFFFFB74D).copy(alpha = 0.18f)
        else -> AppColors.Red.copy(alpha = 0.16f)
    }
    val batteryChipFg = when {
        battery == null -> AppColors.Gray400
        battery >= 70 -> AppColors.Green
        battery >= 35 -> Color(0xFFFFB74D)
        else -> AppColors.Red
    }
    val stateColor = statusColor ?: when {
        isInCallWithPeer -> neonRed
        isCalling -> neonRed
        isSelected -> neonGreen
        else -> AppColors.Gray500
    }
    val stateText = statusLabel ?: when {
        isInCallWithPeer -> "통화 중"
        isCalling -> "연결 중"
        isSelected -> "선택됨"
        else -> "수신됨"
    }
    var isExpanded by rememberSaveable(survivor.peerId) { mutableStateOf(false) }

    Surface(
        color = if (isSelected) charcoal.copy(alpha = 0.95f) else charcoal.copy(alpha = 0.82f),
        shape = RoundedCornerShape(scaledDp(22, scale)),
        border = BorderStroke(1.dp, if (isSelected) neonGreen.copy(alpha = 0.7f) else border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelectable) {
                            Modifier.clickable { onSelectTarget(survivor) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(12, scale)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = scaledDp(6, scale))
                ) {
                    Text(
                        text = if (survivor.name.isBlank()) "이름 미상" else survivor.name,
                        color = AppColors.White,
                        fontSize = scaledSp(16, scale),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isCalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(scaledDp(20, scale)),
                        color = neonGreen,
                        strokeWidth = scaledDp(2, scale)
                    )
                    Spacer(modifier = Modifier.width(scaledDp(8, scale)))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(stateColor.copy(alpha = 0.12f))
                        .padding(horizontal = scaledDp(10, scale), vertical = scaledDp(7, scale))
                ) {
                    Text(
                        text = stateText,
                        color = stateColor,
                        fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(scaledDp(8, scale)))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppColors.Gray700.copy(alpha = 0.45f))
                        .clickable {
                            isExpanded = !isExpanded
                        }
                        .padding(horizontal = scaledDp(6, scale), vertical = scaledDp(6, scale)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "접기" else "펼치기",
                        tint = AppColors.White
                    )
                }
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = scaledDp(24, scale), end = scaledDp(18, scale), bottom = scaledDp(12, scale))
                ) {
                    Text(
                        text = buildString {
                            append(if (survivor.gender.isBlank()) "성별 미상" else survivor.gender)
                            append(" · ")
                            append(if (survivor.birthDate.isBlank()) "생년월일 미상" else survivor.birthDate)
                        },
                        color = AppColors.White.copy(alpha = 0.55f),
                        fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                    MiniChip(
                        text = batteryChipText,
                        bg = batteryChipBg,
                        fg = batteryChipFg
                    )
                    Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                    if (distanceText != null) {
                        MiniChip(
                            text = "$distancePrefix${distanceText}m",
                            bg = neonGreen.copy(alpha = 0.12f),
                            fg = neonGreen
                        )
                    } else {
                        MiniChip(
                            text = "거리 탐색 중",
                            bg = AppColors.Gray700.copy(alpha = 0.35f),
                            fg = AppColors.Gray400
                        )
                    }
                    if (survivor.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                        MiniChip(
                            text = "특이사항 ${survivor.notes}",
                            bg = neonRed.copy(alpha = 0.1f),
                            fg = neonRed
                        )
                    }
                }
            }
        }
    }
}

private fun estimateDistanceMeters(rssi: Int): Float {
    val txPower = -59
    val pathLossExponent = 2.0
    return 10.0.pow((txPower - rssi) / (10 * pathLossExponent)).toFloat()
}

@Composable
private fun MiniChip(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
