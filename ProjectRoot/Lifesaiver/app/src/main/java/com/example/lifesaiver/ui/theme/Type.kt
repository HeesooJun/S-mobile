package com.example.lifesaiver.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.R

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
    Font(R.font.pretendard_black, FontWeight.Black)
)

private val BaseTypography = Typography()

val Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = Pretendard),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = Pretendard),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = Pretendard),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = Pretendard),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = Pretendard),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = Pretendard),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = Pretendard),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = Pretendard),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = Pretendard),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = Pretendard),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = Pretendard),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = Pretendard),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = Pretendard),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = Pretendard),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = Pretendard)
)
