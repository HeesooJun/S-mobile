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
import androidx.compose.ui.unit.dp
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

    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(profileState) {
        name = profileState.name
        gender = profileState.gender
        birthDate = profileState.birthDate
        notes = profileState.notes
    }

    val birthDateValid = isBirthDateValid(birthDate)
    var showBirthDateError by rememberSaveable { mutableStateOf(false) }
    val isComplete = name.isNotBlank() && gender.isNotBlank() && birthDateValid
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        gradient = listOf(AppColors.Black, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f) // 오류 수정: 파라미터 추가
    ) {
        // Header
        Box(
            modifier = Modifier.fillMaxWidth().height(scaledDp(56, scale)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = scaledDp(16, scale))
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.White)
            }
            Text("사용자 정보", color = AppColors.White, fontSize = scaledSp(18, scale), fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = scaledDp(24, scale))) {
            Spacer(modifier = Modifier.height(scaledDp(30, scale)))

            Text("필수", color = Color(0xFFFF5252), fontSize = scaledSp(14, scale), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(scaledDp(12, scale)))

            Column(verticalArrangement = Arrangement.spacedBy(scaledDp(16, scale))) {
                ProfileInputField("이름", name, "이름 입력") { name = it }
                ProfileExposedSelectField("성별", gender, "성별 선택", listOf("남성", "여성")) { gender = it }
                ProfileDateField("생년월일", birthDate, "YYYY-MM-DD", showBirthDateError) {
                    showBirthDateError = false
                    showDatePicker = true
                }
            }

            Spacer(modifier = Modifier.height(scaledDp(40, scale)))
            Text("선택", color = AppColors.Gray400, fontSize = scaledSp(14, scale), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(scaledDp(12, scale)))
            ProfileInputField("특이사항", notes, "알레르기, 지병 등") { notes = it }

            Spacer(modifier = Modifier.weight(1f))

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
            }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun ProfileInputField(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val scale = LocalAppScale.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = AppColors.Gray400, fontSize = scaledSp(12, scale), modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = AppColors.Gray500) },
            textStyle = TextStyle(color = AppColors.White, fontSize = scaledSp(16, scale)),
            shape = RoundedCornerShape(scaledDp(12, scale)), colors = profileFieldColors()
        )
    }
}

@Composable
private fun ProfileExposedSelectField(label: String, value: String, placeholder: String, options: List<String>, onSelected: (String) -> Unit) {
    val scale = LocalAppScale.current
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = AppColors.Gray400, fontSize = scaledSp(12, scale), modifier = Modifier.padding(bottom = 4.dp))
        ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
            OutlinedTextField(
                value = value, onValueChange = {}, modifier = Modifier.fillMaxWidth().menuAnchor(),
                placeholder = { Text(placeholder, color = AppColors.Gray500) }, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = RoundedCornerShape(scaledDp(12, scale)), colors = profileFieldColors()
            )
            ExposedDropdownMenu(expanded, { expanded = false }, Modifier.background(AppColors.Gray800)) {
                options.forEach { DropdownMenuItem(text = { Text(it, color = AppColors.White) }, onClick = { onSelected(it); expanded = false }) }
            }
        }
    }
}

@Composable
private fun ProfileDateField(label: String, value: String, placeholder: String, isError: Boolean, onClick: () -> Unit) {
    val scale = LocalAppScale.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = AppColors.Gray400, fontSize = scaledSp(12, scale), modifier = Modifier.padding(bottom = 4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value, onValueChange = {}, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, color = AppColors.Gray500) },
                enabled = false, isError = isError, shape = RoundedCornerShape(scaledDp(12, scale)), colors = profileFieldColors()
            )
            Box(modifier = Modifier.matchParentSize().clickable(onClick = onClick))
        }
    }
}

@Composable
private fun profileFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E),
    disabledContainerColor = Color(0xFF1E1E1E), focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
    focusedTextColor = AppColors.White, unfocusedTextColor = AppColors.White, disabledTextColor = AppColors.White
)

private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
private fun parseDateToMillis(value: String): Long? = try { LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (_: Exception) { null }
private fun isBirthDateValid(value: String): Boolean = try { LocalDate.parse(value); true } catch (_: Exception) { false }
