package `in`.voxagent.mobile.ui

import `in`.voxagent.mobile.net.VoxHttpException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.phone.PhoneApi
import `in`.voxagent.mobile.phone.PhoneStatus
import `in`.voxagent.mobile.phone.normalizePhone
import `in`.voxagent.mobile.phone.phoneErrorMessage
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.VoxFunnelDisplayFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CODE_LENGTH = 6
private const val RESEND_SECONDS = 30

@Composable
fun PhoneVerificationFlow(
    token: String,
    status: PhoneStatus,
    onVerified: () -> Unit,
    onSkip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var linked by remember { mutableStateOf(status.has_phone) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var last4 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var cooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    fun sendCode() {
        busy = true
        error = ""
        scope.launch {
            runCatching { PhoneApi.startVerification(token) }
                .onSuccess {
                    last4 = it
                    codeSent = true
                    code = ""
                    cooldown = RESEND_SECONDS
                }
                .onFailure { error = phoneErrorMessage(it) }
            busy = false
        }
    }

    fun linkNumber() {
        val normalized = normalizePhone(phone)
        if (normalized == null) {
            error = "Include the country code with a leading +, e.g. +15550001234"
            return
        }
        busy = true
        error = ""
        scope.launch {
            runCatching { PhoneApi.link(token, normalized) }
                .onSuccess {
                    linked = true
                    busy = false
                    sendCode()
                }
                .onFailure {
                    error = if (it is VoxHttpException && it.statusCode == 409) {
                        phoneErrorMessage(it)
                    } else {
                        "Couldn't save your number. Check it and try again."
                    }
                    busy = false
                }
        }
    }

    fun confirmCode() {
        busy = true
        error = ""
        scope.launch {
            runCatching { PhoneApi.confirm(token, code) }
                .onSuccess { onVerified() }
                .onFailure {
                    error = phoneErrorMessage(it)
                    busy = false
                }
        }
    }

    VoxDarkScreen {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.fillMaxHeight(0.25f))
        VoxLogo(modifier = Modifier.offset(y = (-20).dp), size = 80.dp, animated = true)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (linked) "Verify your number" else "Your number",
                color = Mist,
                fontFamily = VoxFunnelDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    codeSent -> "We sent a $CODE_LENGTH-digit code on WhatsApp to the number ending in ${last4.ifEmpty { "your phone" }}. It expires in 10 minutes."
                    linked -> "Confirm the number you linked so Vox can safely reach you. We'll send a code on WhatsApp."
                    else -> "Add the phone number you call Vox from, with its country code. Only use a number that's yours."
                },
                color = GraphiteDark,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }

        when {
            !linked -> {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    placeholder = { Text("+1 555 000 1234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = voxFieldColors(),
                )
                VoxPrimaryButton(
                    text = if (busy) "Saving…" else "Continue",
                    onClick = { if (!busy) linkNumber() },
                )
            }
            !codeSent -> {
                VoxPrimaryButton(
                    text = if (busy) "Sending…" else "Send code",
                    onClick = { if (!busy) sendCode() },
                )
            }
            else -> {
                OutlinedTextField(
                    value = code,
                    onValueChange = { value -> code = value.filter { it.isDigit() }.take(CODE_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    placeholder = { Text("123456") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = voxFieldColors(),
                )
                VoxPrimaryButton(
                    text = if (busy) "Verifying…" else "Verify",
                    onClick = { if (!busy && code.length == CODE_LENGTH) confirmCode() },
                )
                VoxTextButton(
                    text = if (cooldown > 0) "Resend code in ${cooldown}s" else "Resend code",
                    onClick = { if (!busy && cooldown == 0) sendCode() },
                )
            }
        }

        if (error.isNotEmpty()) {
            Text(text = error, color = CoralPulse, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        VoxTextButton(text = "Verify later", onClick = { if (!busy) onSkip() })
    }
    }
}
