package com.example.lifesaivior.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp
import kotlinx.coroutines.delay

@Composable
fun EmergencySignalDropdown(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isBeepRepeating: Boolean,
    onBeepTap: () -> Unit,
    isBeepSticky: Boolean,
    onBeepStickyChange: (Boolean) -> Unit,
    onBeepRepeatingChange: (Boolean) -> Unit,
    isVibrateRepeating: Boolean,
    onVibrateTap: () -> Unit,
    isVibrateSticky: Boolean,
    onVibrateStickyChange: (Boolean) -> Unit,
    onVibrateRepeatingChange: (Boolean) -> Unit,
    isHighToneRepeating: Boolean,
    onHighToneTap: () -> Unit,
    isHighToneSticky: Boolean,
    onHighToneStickyChange: (Boolean) -> Unit,
    onHighToneRepeatingChange: (Boolean) -> Unit,
    isStopHighlighted: Boolean,
    onStopAll: () -> Unit,
    torchSosEnabled: Boolean,
    onToggleTorchSos: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    val cornerSize = scaledDp(14, scale)
    val handlePadding = scaledDp(8, scale)
    val contentPadding = PaddingValues(horizontal = scaledDp(10, scale), vertical = scaledDp(6, scale))
    val buttonHeight = scaledDp(40, scale)
    val iconSize = scaledDp(18, scale)
    val titleSize = scaledSp(12, scale)
    val buttonTextSize = scaledSp(11, scale)
    val handleShape = RoundedCornerShape(bottomStart = cornerSize, bottomEnd = cornerSize)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = scaledDp(140, scale))
                .clip(handleShape)
                .background(AppColors.Gray800.copy(alpha = 0.9f))
                .clickable { onExpandedChange(!isExpanded) }
                .padding(horizontal = handlePadding, vertical = scaledDp(6, scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "아날로그 신호 접기" else "아날로그 신호 펼치기",
                tint = AppColors.White,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.size(scaledDp(4, scale)))
            Text(
                text = "아날로그 신호",
                color = AppColors.White,
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(initialOffsetY = { -it / 3 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it / 3 }) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(8, scale))
                    .background(AppColors.Gray900.copy(alpha = 0.95f), RoundedCornerShape(cornerSize))
                    .padding(scaledDp(12, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "아날로그 신호",
                    color = AppColors.White,
                    fontSize = scaledSp(13, scale),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(scaledDp(6, scale))
                ) {
                    RepeatActionButton(
                        label = "비프음",
                        enabled = true,
                        onTap = onBeepTap,
                        isStickyActive = isBeepSticky,
                        onStickyStateChanged = onBeepStickyChange,
                        isRepeating = isBeepRepeating,
                        onActiveStateChanged = onBeepRepeatingChange,
                        fontSize = buttonTextSize,
                        contentPadding = contentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                    )
                    RepeatActionButton(
                        label = "저주파",
                        enabled = true,
                        onTap = onVibrateTap,
                        isStickyActive = isVibrateSticky,
                        onStickyStateChanged = onVibrateStickyChange,
                        isRepeating = isVibrateRepeating,
                        onActiveStateChanged = onVibrateRepeatingChange,
                        fontSize = buttonTextSize,
                        contentPadding = contentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                    )
                    RepeatActionButton(
                        label = "고주파",
                        enabled = true,
                        onTap = onHighToneTap,
                        isStickyActive = isHighToneSticky,
                        onStickyStateChanged = onHighToneStickyChange,
                        isRepeating = isHighToneRepeating,
                        onActiveStateChanged = onHighToneRepeatingChange,
                        fontSize = buttonTextSize,
                        contentPadding = contentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                    )
                }
                Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(scaledDp(6, scale))
                ) {
                    Button(
                        onClick = onToggleTorchSos,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (torchSosEnabled) AppColors.Green else AppColors.Gray800,
                            contentColor = AppColors.White
                        ),
                        contentPadding = contentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                    ) {
                        Text(
                            text = if (torchSosEnabled) "손전등 SOS 끄기" else "손전등 SOS 켜기",
                            fontSize = buttonTextSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = onStopAll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStopHighlighted) AppColors.Red else AppColors.Gray800,
                            contentColor = AppColors.White
                        ),
                        contentPadding = contentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight)
                    ) {
                        Text(
                            text = "송출 중지",
                            fontSize = buttonTextSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatActionButton(
    label: String,
    enabled: Boolean,
    onTap: () -> Unit,
    isStickyActive: Boolean,
    onStickyStateChanged: (Boolean) -> Unit,
    isRepeating: Boolean,
    onActiveStateChanged: (Boolean) -> Unit,
    fontSize: TextUnit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentTap by rememberUpdatedState(onTap)
    val currentStickyStateChanged by rememberUpdatedState(onStickyStateChanged)
    val currentActiveStateChanged by rememberUpdatedState(onActiveStateChanged)
    var suppressTap by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed, enabled, isStickyActive) {
        if (!enabled) {
            if (isStickyActive) {
                currentStickyStateChanged(false)
            }
            currentActiveStateChanged(false)
            return@LaunchedEffect
        }
        if (isStickyActive) {
            currentActiveStateChanged(true)
            return@LaunchedEffect
        }
        if (!isPressed) {
            currentActiveStateChanged(false)
            return@LaunchedEffect
        }
        delay(1_000L)
        if (!isPressed || isStickyActive) return@LaunchedEffect
        suppressTap = true
        currentStickyStateChanged(true)
        currentActiveStateChanged(true)
    }
    TextButton(
        enabled = enabled,
        onClick = {
            if (suppressTap) {
                suppressTap = false
                return@TextButton
            }
            if (isStickyActive) {
                currentStickyStateChanged(false)
                currentActiveStateChanged(false)
                return@TextButton
            }
            currentTap()
        },
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isRepeating) {
                AppColors.Gray700.copy(alpha = 0.9f)
            } else {
                AppColors.Gray800.copy(alpha = 0.7f)
            },
            contentColor = AppColors.White,
            disabledContainerColor = AppColors.Gray800.copy(alpha = 0.35f),
            disabledContentColor = AppColors.Gray500
        ),
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        Text(
            text = if (isRepeating) "$label · 반복중" else label,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}
