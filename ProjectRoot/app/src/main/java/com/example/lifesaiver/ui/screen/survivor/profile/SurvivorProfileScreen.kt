@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.lifesaiver.ui.screen.survivor.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.ui.components.PrimaryButton
import com.example.lifesaiver.ui.components.PrimaryButtonVariant
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SurvivorProfileScreen(
    profileStore: ProfileStore,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileState by profileStore.profileFlow.collectAsState(initial = SurvivorProfile())

    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var emergencyContact by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var bloodType by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(profileState) {
        name = profileState.name
        gender = profileState.gender
        birthDate = profileState.birthDate
        emergencyContact = profileState.emergencyContact
        notes = profileState.notes
        bloodType = profileState.bloodType
    }

    val birthDateValid = isBirthDateValid(birthDate)
    var showBirthDateError by rememberSaveable { mutableStateOf(false) }
    val isComplete = name.isNotBlank() && gender.isNotBlank() && birthDateValid
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val formModifier = Modifier
        .fillMaxWidth(0.85f)
        .widthIn(max = scaledDp(360, scale))

    if (showDatePicker) {
        val initialMillis = parseDateToMillis(birthDate)
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Text(
                    text = "확인",
                    color = AppColors.Green,
                    modifier = Modifier
                        .padding(horizontal = scaledDp(12, scale), vertical = scaledDp(8, scale))
                        .clickable {
                            val millis = datePickerState.selectedDateMillis
                            if (millis != null) {
                                val formatted = formatDate(millis)
                                birthDate = formatted
                                showBirthDateError = false
                                showDatePicker = false
                            } else {
                                showBirthDateError = true
                            }
                        }
                )
            },
            dismissButton = {
                Text(
                    text = "취소",
                    color = AppColors.Gray400,
                    modifier = Modifier
                        .padding(horizontal = scaledDp(12, scale), vertical = scaledDp(8, scale))
                        .clickable { showDatePicker = false }
                )
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(scaledDp(56, scale))
                .padding(horizontal = scaledDp(24, scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(
                label = "뒤로",
                variant = SecondaryButtonVariant.Gray,
                onClick = onBack
            )
            Text(
                text = "생존자 정보",
                color = AppColors.White,
                fontSize = scaledSp(16, scale),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(scaledDp(56, scale)))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = scaledDp(24, scale)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = formModifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(scaledDp(16, scale))
            ) {
                Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                Text(
                    text = "필수",
                    color = AppColors.Red,
                    fontSize = scaledSp(12, scale),
                    fontWeight = FontWeight.SemiBold
                )
                ProfileInputField(
                    label = "이름",
                    value = name,
                    placeholder = "이름 입력",
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { name = it }
                )
                ProfileExposedSelectField(
                    label = "성별",
                    value = gender,
                    placeholder = "선택",
                    options = listOf("남성", "여성"),
                    modifier = Modifier.fillMaxWidth(),
                    onSelected = { gender = it }
                )
                ProfileDateField(
                    label = "생년월일",
                    value = birthDate,
                    placeholder = "YYYY-MM-DD",
                    isError = showBirthDateError,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showBirthDateError = false
                        showDatePicker = true
                    }
                )

                Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                Text(
                    text = "권장 / 선택",
                    color = AppColors.Gray400,
                    fontSize = scaledSp(12, scale),
                    fontWeight = FontWeight.Medium
                )
                ProfileInputField(
                    label = "긴급연락처(권장)",
                    value = emergencyContact,
                    placeholder = "연락처 입력",
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { emergencyContact = it }
                )
                ProfileExposedSelectField(
                    label = "혈액형(선택)",
                    value = bloodType,
                    placeholder = "선택",
                    options = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"),
                    modifier = Modifier.fillMaxWidth(),
                    onSelected = { bloodType = it }
                )
                ProfileInputField(
                    label = "특이사항 (선택)",
                    value = notes,
                    placeholder = "특이사항 입력",
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { notes = it }
                )
                Spacer(modifier = Modifier.height(scaledDp(12, scale)))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = scaledDp(24, scale),
                    end = scaledDp(24, scale),
                    bottom = scaledDp(24, scale)
                )
        ) {
            PrimaryButton(
                label = "저장",
                variant = if (isComplete) PrimaryButtonVariant.Red else PrimaryButtonVariant.Gray,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (isComplete) {
                        scope.launch {
                            profileStore.saveProfile(
                                SurvivorProfile(
                                    name = name,
                                    gender = gender,
                                    birthDate = birthDate,
                                    emergencyContact = emergencyContact,
                                    notes = notes,
                                    bloodType = bloodType
                                )
                            )
                            Toast.makeText(context, "저장 완료", Toast.LENGTH_SHORT).show()
                            delay(300)
                            onSaved()
                        }
                    } else if (!birthDateValid) {
                        showBirthDateError = true
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    val scale = LocalAppScale.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(text = label, color = AppColors.Gray500) },
        placeholder = { Text(text = placeholder, color = AppColors.Gray500) },
        textStyle = TextStyle(
            color = AppColors.White,
            fontSize = scaledSp(12, scale)
        ),
        singleLine = true,
        colors = profileFieldColors()
    )
}

@Composable
private fun ProfileExposedSelectField(
    label: String,
    value: String,
    placeholder: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    val scale = LocalAppScale.current
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = modifier.menuAnchor(),
            label = { Text(text = label, color = AppColors.Gray500) },
            placeholder = { Text(text = placeholder, color = AppColors.Gray500) },
            textStyle = TextStyle(
                color = AppColors.White,
                fontSize = scaledSp(12, scale)
            ),
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = profileFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = scaledDp(180, scale))
                .background(
                    color = AppColors.Gray800,
                    shape = RoundedCornerShape(scaledDp(14, scale))
                )
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = AppColors.White,
                            fontSize = scaledSp(12, scale)
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content
    )
}

@Composable
private fun profileFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = AppColors.Gray800,
    unfocusedContainerColor = AppColors.Gray800,
    disabledContainerColor = AppColors.Gray800,
    focusedIndicatorColor = AppColors.Gray700,
    unfocusedIndicatorColor = AppColors.Gray700,
    disabledIndicatorColor = AppColors.Gray700,
    cursorColor = AppColors.Green,
    focusedTextColor = AppColors.White,
    unfocusedTextColor = AppColors.White,
    disabledTextColor = AppColors.White,
    focusedLabelColor = AppColors.Gray400,
    unfocusedLabelColor = AppColors.Gray500,
    disabledLabelColor = AppColors.Gray500,
    disabledPlaceholderColor = AppColors.Gray500
)

@Composable
private fun ProfileDateField(
    label: String,
    value: String,
    placeholder: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label, color = AppColors.Gray500) },
            placeholder = { Text(text = placeholder, color = AppColors.Gray500) },
            textStyle = TextStyle(
                color = AppColors.White,
                fontSize = scaledSp(12, scale)
            ),
            enabled = false,
            isError = isError,
            readOnly = true,
            singleLine = true,
            colors = profileFieldColors()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() }
        )
    }
}

private fun formatDate(millis: Long?): String {
    if (millis == null) return ""
    val date = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return DateTimeFormatter.ISO_LOCAL_DATE.format(date)
}

private fun parseDateToMillis(value: String): Long? {
    return try {
        if (value.isBlank()) return null
        val localDate = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

private fun isBirthDateValid(value: String): Boolean {
    if (value.isBlank()) return false
    return try {
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        true
    } catch (_: Exception) {
        false
    }
}
