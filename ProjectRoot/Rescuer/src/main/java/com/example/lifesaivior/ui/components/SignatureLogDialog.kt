package com.example.lifesaivior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.example.lifesaivior.protocol.security.SignatureLogEntry
import com.example.lifesaivior.protocol.security.SignatureLogResult
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignatureLogDialog(
    entries: List<SignatureLogEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    val scale = LocalAppScale.current
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.KOREA) }
    val ordered = remember(entries) { entries.asReversed() }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Gray900, RoundedCornerShape(scaledDp(18, scale)))
                .padding(scaledDp(18, scale))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(scaledDp(12, scale))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "서명 검증 로그",
                        color = AppColors.White,
                        fontSize = scaledSp(14, scale),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))) {
                        SecondaryButton(
                            label = "비우기",
                            variant = SecondaryButtonVariant.Gray,
                            onClick = onClear
                        )
                        SecondaryButton(
                            label = "닫기",
                            variant = SecondaryButtonVariant.Gray,
                            onClick = onDismiss
                        )
                    }
                }

                if (ordered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.Gray800, RoundedCornerShape(scaledDp(12, scale)))
                            .padding(scaledDp(12, scale))
                    ) {
                        Text(
                            text = "표시할 로그가 없습니다.",
                            color = AppColors.Gray400,
                            fontSize = scaledSp(12, scale)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scaledDp(320, scale))
                    ) {
                        items(
                            items = ordered,
                            key = { entry ->
                                "${entry.timestamp}-${entry.peerId}-${entry.packetType}-${entry.result}"
                            }
                        ) { entry ->
                            LogRow(entry = entry, formatter = formatter)
                            Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: SignatureLogEntry, formatter: SimpleDateFormat) {
    val scale = LocalAppScale.current
    val time = formatter.format(Date(entry.timestamp))
    val resultLabel = entry.result.toLabel()
    val resultColor = when (entry.result) {
        SignatureLogResult.VERIFIED -> AppColors.Green
        SignatureLogResult.INVALID,
        SignatureLogResult.NO_SIGNATURE,
        SignatureLogResult.NO_SIGNING_KEY,
        SignatureLogResult.ANNOUNCE_DECODE_FAILED,
        SignatureLogResult.ENCODING_ERROR -> AppColors.Red
        SignatureLogResult.SKIPPED -> AppColors.Gray400
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Gray800, RoundedCornerShape(scaledDp(12, scale)))
            .padding(scaledDp(12, scale))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
        ) {
            Text(
                text = time,
                color = AppColors.Gray400,
                fontSize = scaledSp(10, scale)
            )
            Text(
                text = resultLabel,
                color = resultColor,
                fontSize = scaledSp(11, scale),
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = entry.packetType.name,
                color = AppColors.White,
                fontSize = scaledSp(10, scale)
            )
        }
        Spacer(modifier = Modifier.height(scaledDp(4, scale)))
        Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(6, scale))) {
            Text(
                text = "peer ${entry.peerId.take(12)}",
                color = AppColors.Gray400,
                fontSize = scaledSp(10, scale)
            )
            Spacer(modifier = Modifier.width(scaledDp(6, scale)))
            Text(
                text = entry.detail,
                color = AppColors.Gray500,
                fontSize = scaledSp(10, scale)
            )
        }
    }
}

private fun SignatureLogResult.toLabel(): String {
    return when (this) {
        SignatureLogResult.VERIFIED -> "검증 OK"
        SignatureLogResult.INVALID -> "검증 실패"
        SignatureLogResult.NO_SIGNATURE -> "서명 없음"
        SignatureLogResult.NO_SIGNING_KEY -> "키 없음"
        SignatureLogResult.ENCODING_ERROR -> "인코딩 오류"
        SignatureLogResult.ANNOUNCE_DECODE_FAILED -> "ANNOUNCE 파싱 실패"
        SignatureLogResult.SKIPPED -> "스킵"
    }
}
