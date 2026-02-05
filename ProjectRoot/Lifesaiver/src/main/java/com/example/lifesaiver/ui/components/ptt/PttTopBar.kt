package com.example.lifesaiver.ui.components.ptt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.components.tripleClickable
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

@Composable
internal fun PttTopBar(
    onPanicClear: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "LIFESAIVIOR",
            color = AppColors.Gray500,
            fontSize = scaledSp(12, scale),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = scaledDp(20, scale), top = scaledDp(18, scale))
                .tripleClickable(onTripleClick = onPanicClear)
        )
    }
}
