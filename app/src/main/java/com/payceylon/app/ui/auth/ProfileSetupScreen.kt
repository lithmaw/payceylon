package com.payceylon.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.data.ApiException
import com.payceylon.app.ui.account.ProfileField
import com.payceylon.app.ui.account.normalizeProfileValue
import com.payceylon.app.ui.account.validateProfileField
import com.payceylon.app.ui.components.PayCeylonTextField
import com.payceylon.app.ui.components.rememberSubmitState
import com.payceylon.app.ui.theme.Ink
import com.payceylon.app.ui.theme.MutedText
import com.payceylon.app.ui.theme.PayCeylonRed
import kotlinx.coroutines.delay

/** Shown once after the first sign-in: the name and @username others pay and scan. */
@Composable
fun ProfileSetupScreen(
    isHandleAvailable: suspend (String) -> Boolean,
    /** Saves both; returns the problem to show, or null. */
    onSave: suspend (name: String, handle: String) -> String?,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }
    var handleTaken by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val submit = rememberSubmitState()

    val nameError = name.takeIf { it.isNotBlank() }?.let { validateProfileField(ProfileField.Name, it) }
    val normalizedHandle = normalizeProfileValue(ProfileField.Username, handle)
    val handleError = when {
        handle.isBlank() -> null
        else -> validateProfileField(ProfileField.Username, handle)
            ?: "@$normalizedHandle is already taken.".takeIf { handleTaken }
    }

    LaunchedEffect(normalizedHandle) {
        handleTaken = false
        if (validateProfileField(ProfileField.Username, normalizedHandle) != null) return@LaunchedEffect
        delay(300)
        handleTaken = try {
            !isHandleAvailable(normalizedHandle)
        } catch (_: ApiException) {
            false // The save will report it if it really is taken.
        }
    }

    val canSave = name.isNotBlank() && handle.isNotBlank() && nameError == null && handleError == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(54.dp))
            Text(
                text = buildAnnotatedString {
                    append("Set up your\n")
                    withStyle(SpanStyle(color = PayCeylonRed)) { append("profile") }
                    append(".")
                },
                color = Ink,
                fontSize = 42.sp,
                lineHeight = 43.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.5).sp,
            )
            Text(
                text = "People see your name and find you by your username. It's also on your QR code.",
                color = MutedText,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            PayCeylonTextField(
                value = name,
                onValueChange = {
                    name = it.take(40)
                    serverError = null
                },
                label = "Full name",
                placeholder = "Kasun Perera",
                error = nameError,
                capitalization = KeyboardCapitalization.Words,
            )
            PayCeylonTextField(
                value = handle,
                onValueChange = {
                    handle = it.lowercase().replace(" ", "").removePrefix("@").take(20)
                    serverError = null
                },
                label = "Username",
                placeholder = "kasun.p",
                prefix = "@",
                error = handleError ?: serverError,
                helper = if (canSave) "@$normalizedHandle is available" else "3–20 characters: letters, numbers, dots, underscores",
                imeAction = ImeAction.Done,
            )
        }
        Box(Modifier.padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            PayCeylonButton(
                label = if (submit.busy) "Saving…" else "Continue",
                onClick = { submit.run { serverError = onSave(name, handle) } },
                style = PayCeylonButtonStyle.Accent,
                enabled = canSave && !submit.busy,
            )
        }
    }
}

/** Shown when signed in but the PayCeylon server can't be reached. */
@Composable
fun ConnectionProblemScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Can't reach PayCeylon",
            color = Ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            color = MutedText,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PayCeylonButton(label = "Try again", onClick = onRetry, style = PayCeylonButtonStyle.Accent)
        Spacer(Modifier.height(12.dp))
        PayCeylonButton(label = "Sign out", onClick = onSignOut, style = PayCeylonButtonStyle.Secondary, height = 54.dp)
    }
}
