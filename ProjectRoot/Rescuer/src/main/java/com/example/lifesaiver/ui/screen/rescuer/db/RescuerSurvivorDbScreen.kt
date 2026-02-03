package com.example.lifesaiver.ui.screen.rescuer.db

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlin.math.pow

@Composable
fun RescuerSurvivorDbScreen(
    survivors: List<SurvivorProfile>,
    peerRssiMap: Map<String, Int> = emptyMap(),
    onBack: () -> Unit,
    onCallClick: (SurvivorProfile) -> Unit,
    onEndCall: () -> Unit,
    onOpenMeshMap: () -> Unit,
    selectedTargetPeerId: String? = null,
    onSelectTarget: (SurvivorProfile) -> Unit = {},
    isLive: Boolean = true,
    activeSurvivor: SurvivorProfile? = null,
    isInCall: Boolean = false,
    callingPeerId: String? = null
) {
    val scale = LocalAppScale.current

    val deepBlack = Color(0xFF0A0A0F)
    val charcoal = Color(0xFF1A1A24)
    val border = Color(0xFF2A2A3A)
    val neonGreen = Color(0xFF00FF88)
    val neonRed = Color(0xFFFF2A5A)

    val liveLabel = if (isLive) "실시간" else "대기"
    val activeCallName = activeSurvivor?.name?.ifBlank { "생존자" }
    val callingSurvivorName = survivors
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

    ScreenScaffold(
        gradient = listOf(deepBlack, deepBlack),
        vignetteColor = deepBlack.copy(alpha = 0.75f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(deepBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = scaledDp(18, scale))
                    .padding(top = scaledDp(14, scale)),
                    horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "뒤로",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f))
                    {
                        Text(
                            text = "생존자 DB",
                            color = Color.White,
                            fontSize = scaledSp(20, scale),
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "생존자 정보 ${survivors.size}건",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = scaledSp(12, scale),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(neonGreen.copy(alpha = 0.10f))
                                    .padding(horizontal = scaledDp(12, scale), vertical = scaledDp(8, scale))
                            ) {
                                Text(
                                    text = liveLabel,
                                    color = neonGreen,
                                    fontSize = scaledSp(12, scale),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(scaledDp(8, scale)))
                            TextButton(
                                onClick = onOpenMeshMap,
                                contentPadding = PaddingValues(horizontal = scaledDp(10, scale), vertical = 0.dp)
                            ) {
                                Text(
                                    text = "메쉬 망",
                                    color = neonGreen,
                                    fontSize = scaledSp(11, scale),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (isInCall && activeCallName != null) {
                            Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(neonRed.copy(alpha = 0.12f))
                                        .padding(horizontal = scaledDp(10, scale), vertical = scaledDp(6, scale))
                                ) {
                                    Text(
                                        text = "통화 중 ${activeCallName.orEmpty()}",
                                        color = neonRed,
                                        fontSize = scaledSp(10, scale),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(
                                    onClick = onEndCall,
                                    contentPadding = PaddingValues(horizontal = scaledDp(8, scale), vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "통화 종료",
                                        color = neonRed,
                                        fontSize = scaledSp(10, scale),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(scaledDp(14, scale)))

                Surface(
                    color = charcoal.copy(alpha = 0.60f),
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
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = scaledSp(14, scale)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "검색",
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = neonGreen,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(scaledDp(14, scale)))

                if (filtered.isEmpty()) {
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
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = scaledSp(14, scale),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                            Text(
                                text = "연결되면 이 화면에 자동으로 표시됩니다",
                                color = Color.White.copy(alpha = 0.50f),
                                fontSize = scaledSp(12, scale),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = scaledDp(24, scale)),
                        verticalArrangement = Arrangement.spacedBy(scaledDp(12, scale))
                    ) {
                        items(filtered) { survivor ->
                            SurvivorCard(
                                survivor = survivor,
                                scale = scale,
                                charcoal = charcoal,
                                border = border,
                                neonGreen = neonGreen,
                                neonRed = neonRed,
                                peerRssiMap = peerRssiMap,
                                isSelected = survivor.peerId.isNotBlank() && survivor.peerId == selectedTargetPeerId,
                                onSelectTarget = onSelectTarget,
                                onCallClick = onCallClick,
                                isCalling = survivor.peerId.isNotBlank() && survivor.peerId == callingPeerId,
                                callButtonEnabled = callingPeerId == null || survivor.peerId == callingPeerId
                            )
                        }
                    }
                }
            }

            if (callingPeerId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
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
                                color = Color.White,
                                fontSize = scaledSp(15, scale),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                            Text(
                                text = "${callingSurvivorName} · 최대 15초 대기",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = scaledSp(12, scale),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
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
    isSelected: Boolean,
    onSelectTarget: (SurvivorProfile) -> Unit,
    onCallClick: (SurvivorProfile) -> Unit,
    isCalling: Boolean,
    callButtonEnabled: Boolean
) {
    Surface(
        color = charcoal.copy(alpha = 0.80f),
        shape = RoundedCornerShape(scaledDp(22, scale)),
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectTarget(survivor) }
                .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(14, scale)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(scaledDp(44, scale))
                    .clip(CircleShape)
                    .background(neonGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = survivor.name.firstOrNull()?.toString() ?: "?",
                    color = neonGreen,
                    fontSize = scaledSp(16, scale),
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(scaledDp(12, scale)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (survivor.name.isBlank()) "이름 미상" else survivor.name,
                    color = Color.White,
                    fontSize = scaledSp(16, scale),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(scaledDp(4, scale)))
                Text(
                    text = buildString {
                        append(if (survivor.gender.isBlank()) "성별 미상" else survivor.gender)
                        append(" · ")
                        append(if (survivor.birthDate.isBlank()) "생년월일 미상" else survivor.birthDate)
                    },
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = scaledSp(12, scale),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                val rssi = peerRssiMap[survivor.peerId]
                val rssiText = rssi?.let { "${it} dBm" } ?: "-"
                val distanceText = rssi?.let { String.format("%.1f", estimateDistanceMeters(it)) } ?: "-"
                Text(
                    text = "RSSI $rssiText · 약 ${distanceText}m",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = scaledSp(11, scale),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(scaledDp(8, scale)))

                Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))) {
                    if (survivor.notes.isNotBlank()) {
                        MiniChip(
                            text = survivor.notes,
                            bg = neonRed.copy(alpha = 0.10f),
                            fg = neonRed
                        )
                    }
                    if (isSelected) {
                        MiniChip(
                            text = "거리 추적",
                            bg = neonGreen.copy(alpha = 0.12f),
                            fg = neonGreen
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (isCalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(scaledDp(30, scale)),
                        color = neonGreen,
                        strokeWidth = scaledDp(2.6f, scale)
                    )
                } else {
                    FilledIconButton(
                        onClick = { onCallClick(survivor) },
                        enabled = callButtonEnabled,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = neonGreen,
                            disabledContainerColor = neonGreen.copy(alpha = 0.32f),
                            disabledContentColor = Color.Black.copy(alpha = 0.45f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "통화",
                            tint = Color.Black
                        )
                    }
                }
                Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background((if (isCalling) neonRed else neonGreen).copy(alpha = 0.10f))
                        .padding(horizontal = scaledDp(10, scale), vertical = scaledDp(7, scale))
                ) {
                    Text(
                        text = if (isCalling) "연결 시도 중" else "수신됨",
                        color = if (isCalling) neonRed else neonGreen,
                        fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.Bold
                    )
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
private fun MiniChip(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
