package com.example.lifesaiver.ui.components.ptt

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import com.example.lifesaiver.R
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.ui.components.MeshEdge
import com.example.lifesaiver.ui.components.MeshMap
import com.example.lifesaiver.ui.components.MeshNode
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.flow.SharedFlow

@Composable
internal fun PttMeshOverlay(
    nodes: List<MeshNode>,
    edges: List<MeshEdge>,
    visualEvents: SharedFlow<MeshVisualEvent>,
    connectedCount: Int,
    meshDisplayCount: Int,
    bleDebugStats: BleDebugStats,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Black.copy(alpha = 0.9f))
    ) {
        MeshMap(
            nodes = nodes,
            edges = edges,
            visualEvents = visualEvents,
            modifier = Modifier.fillMaxSize()
        )
        PttTopIconButton(
            iconRes = R.drawable.ic_common_back,
            contentDescription = "닫기",
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = scaledDp(16, scale), top = scaledDp(16, scale))
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = scaledDp(18, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "메쉬 현황",
                color = AppColors.White,
                fontSize = scaledSp(16, scale),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "직접 ${connectedCount}명 · 메쉬 ${meshDisplayCount}명",
                color = AppColors.Gray400,
                fontSize = scaledSp(12, scale)
            )
            val scanAvg = bleDebugStats.scanRssiAvg?.let { "$it dBm" } ?: "-"
            val connAvg = bleDebugStats.connectionRssiAvg?.let { "$it dBm" } ?: "-"
            Text(
                text = "RSSI scan $scanAvg (${bleDebugStats.scanRssiCount}) · conn $connAvg (${bleDebugStats.connectionRssiCount})",
                color = AppColors.Gray500,
                fontSize = scaledSp(11, scale),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "pending ${bleDebugStats.pendingCount} · attempts ${bleDebugStats.attemptTracked}/${bleDebugStats.maxAttempts}",
                color = AppColors.Gray500,
                fontSize = scaledSp(10, scale)
            )
        }
    }
}

@Composable
private fun PttTopIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        modifier = modifier
            .size(scaledDp(36, scale))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(scaledDp(18, scale)),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(AppColors.White)
            )
        }
    }
}
