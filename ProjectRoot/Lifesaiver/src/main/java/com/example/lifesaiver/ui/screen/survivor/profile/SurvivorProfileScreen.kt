@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.lifesaiver.ui.screen.survivor.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.ui.components.PrimaryButton
import com.example.lifesaiver.ui.components.PrimaryButtonVariant
import com.example.lifesaiver.ui.components.ScreenScaffold
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
    onSendProfileUpdate: (SurvivorProfile) -> Unit,
    onBack: () -> Unit
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileState by profileStore.profileFlow.collectAsState(initial = SurvivorProfile())

    // 기본값 "홍길동" 로직 삭제됨 (사용자 피드백 반영)
    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    // 데이터 로드 시점의 동기화만 유지
    LaunchedEffect(profileState) {
        name = profileState.name
        gender = profileState.gender
        birthDate = profileState.birthDate
        notes = profileState.notes
    }

    val isComplete = name.isNotBlank() && gender.isNotBlank() && isBirthDateValid(birthDate)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        gradient = listOf(AppColors.Black, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        // 1. 헤더 영역 (아이콘 + 사용자 정보)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(scaledDp(56, scale)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = scaledDp(16, scale))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = AppColors.White)
            }
            Text(
                text = "사용자 정보",
                color = AppColors.White,
                fontSize = scaledSp(18, scale),
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = scaledDp(24, scale))
        ) {
            Spacer(modifier = Modifier.height(scaledDp(20, scale)))

            // 2. 필수 입력 섹션
            Text("필수", color = Color(0xFFFF5252), fontSize = scaledSp(14, scale), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(scaledDp(16, scale)))

            Column(verticalArrangement = Arrangement.spacedBy(scaledDp(20, scale))) {
                ProfileFieldWrapper("이름") {
                    ProfileInputField(name, "이름 입력") { name = it }
                }
                ProfileFieldWrapper("성별") {
                    ProfileExposedSelectField(gender, "성별 선택", listOf("남성", "여성")) { gender = it }
                }
                ProfileFieldWrapper("생년월일") {
                    ProfileDateField(birthDate, "YYYY-MM-DD") { showDatePicker = true }
                }
            }

            Spacer(modifier = Modifier.height(scaledDp(32, scale)))

            // 3. 선택 입력 섹션
            Text("선택", color = AppColors.Gray400, fontSize = scaledSp(14, scale), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(scaledDp(16, scale)))

            ProfileFieldWrapper("특이사항") {
                ProfileInputField(notes, "알레르기, 지병 등 (선택사항)") { notes = it }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. 저장 버튼
            PrimaryButton(
                label = "저장",
                variant = if (isComplete) PrimaryButtonVariant.Red else PrimaryButtonVariant.Gray,
                modifier = Modifier.fillMaxWidth().padding(bottom = scaledDp(24, scale)),
                onClick = {
                    if (isComplete) {
                        scope.launch {
                            val profile = SurvivorProfile(name, gender, birthDate, notes)
                            profileStore.saveProfile(profile)
                            onSendProfileUpdate(profile)
                            Toast.makeText(context, "저장 완료", Toast.LENGTH_SHORT).show()
                            delay(300)
                            onSaved()
                        }
                    }
                }
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = parseDateToMillis(birthDate))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { birthDate = formatDate(it) }
                    showDatePicker = false
                }) { Text("확인", color = AppColors.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소", color = AppColors.Gray400) }
            }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun ProfileFieldWrapper(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val scale = LocalAppScale.current
        Text(
            text = label,
            color = AppColors.Gray400,
            fontSize = scaledSp(12, scale),
            modifier = Modifier.padding(bottom = scaledDp(8, scale))
        )
        content()
    }
}

@Composable
private fun ProfileInputField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val scale = LocalAppScale.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = AppColors.Gray500) },
        textStyle = TextStyle(color = AppColors.White, fontSize = scaledSp(16, scale)),
        shape = RoundedCornerShape(scaledDp(12, scale)),
        colors = profileFieldColors()
    )
}

@Composable
private fun ProfileExposedSelectField(value: String, placeholder: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        val scale = LocalAppScale.current
        OutlinedTextField(
            value = value, onValueChange = {}, modifier = Modifier.fillMaxWidth().menuAnchor(),
            placeholder = { Text(placeholder, color = AppColors.Gray500) }, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(scaledDp(12, scale)),
            colors = profileFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF2A2A2A))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = AppColors.White) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ProfileDateField(value: String, placeholder: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val scale = LocalAppScale.current
        OutlinedTextField(
            value = value, onValueChange = {}, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = AppColors.Gray500) },
            enabled = false,
            shape = RoundedCornerShape(scaledDp(12, scale)),
            colors = profileFieldColors()
        )
        Box(modifier = Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun profileFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF1E1E1E),
    unfocusedContainerColor = Color(0xFF1E1E1E),
    disabledContainerColor = Color(0xFF1E1E1E),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = AppColors.White,
    unfocusedTextColor = AppColors.White,
    disabledTextColor = AppColors.White
)

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun parseDateToMillis(value: String): Long? =
    try { LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (_: Exception) { null }

private fun isBirthDateValid(value: String): Boolean =
    try { LocalDate.parse(value); true } catch (_: Exception) { false }
