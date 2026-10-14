package com.payceylon.app.ui.auth

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.data.ApiException
import com.payceylon.app.ui.account.ProfileField
import com.payceylon.app.ui.account.loadProfilePhoto
import com.payceylon.app.ui.account.normalizeProfileValue
import com.payceylon.app.ui.account.validateProfileField
import com.payceylon.app.ui.components.rememberSubmitState
import com.payceylon.app.ui.theme.Ink
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.MutedText
import com.payceylon.app.ui.theme.PayCeylonRed
import com.payceylon.app.ui.theme.SoftGrey
import kotlinx.coroutines.delay

/**
 * Shown once after the first sign-in. Name and @username are required (others pay and scan
 * you by them); photo and email are optional and can be added later in Settings.
 */
@Composable
fun ProfileSetupScreen(
    isHandleAvailable: suspend (String) -> Boolean,
    /** Saves everything; returns the problem to show, or null. */
    onSave: suspend (name: String, handle: String, email: String, photo: ImageBitmap?) -> String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var handleTaken by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val submit = rememberSubmitState()

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            submit.run {
                photo = loadProfilePhoto(context, uri)
                if (photo == null) Toast.makeText(context, "Couldn't open that photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val nameError = name.takeIf { it.isNotBlank() }?.let { validateProfileField(ProfileField.Name, it) }
    val normalizedHandle = normalizeProfileValue(ProfileField.Username, handle)
    val handleError = when {
        handle.isBlank() -> null
        else -> validateProfileField(ProfileField.Username, handle)
            ?: "@$normalizedHandle is already taken.".takeIf { handleTaken }
    }
    val emailError = email.takeIf { it.isNotBlank() }?.let { validateProfileField(ProfileField.Email, it) }

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

    val handleReady = handle.isNotBlank() && handleError == null
    val canSave = name.isNotBlank() && nameError == null && handleReady && emailError == null

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
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                text = buildAnnotatedString {
                    append("Set up your\n")
                    withStyle(SpanStyle(color = PayCeylonRed)) { append("profile") }
                    append(".")
                },
                color = Ink,
                fontFamily = InterFontFamily,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.4).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "People find you by your username. It's also on your QR code.",
                color = MutedText,
                fontFamily = InterFontFamily,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(24.dp))

            // Optional photo; without one, the avatar is the name's first letter.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.clickable(interactionSource = null, indication = null) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (photo == null) SoftGrey else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    val picked = photo
                    when {
                        picked != null -> Image(picked, "Profile photo", Modifier.size(64.dp), contentScale = ContentScale.Crop)
                        name.isNotBlank() -> Text(
                            text = name.trim().first().uppercase(),
                            color = PayCeylonRed,
                            fontFamily = InterFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        else -> Image(
                            painter = painterResource(R.drawable.ic_camera),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(MutedText),
                        )
                    }
                }
                Column {
                    Text(
                        text = if (photo == null) "Add a photo" else "Change photo",
                        color = PayCeylonRed,
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Optional", color = MutedText, fontFamily = InterFontFamily, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(24.dp))

            PillField(
                value = name,
                onValueChange = {
                    name = it.take(40)
                    serverError = null
                },
                label = "Full name",
                placeholder = "Kasun Perera",
                error = nameError,
                capitalization = KeyboardCapitalization.Words,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )
            Spacer(Modifier.height(16.dp))
            PillField(
                value = handle,
                onValueChange = {
                    handle = it.lowercase().replace(" ", "").removePrefix("@").take(20)
                    serverError = null
                },
                label = "Username",
                placeholder = "kasun.p",
                prefix = "@",
                error = handleError ?: serverError,
                helper = if (handleReady) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = PayCeylonRed, fontWeight = FontWeight.SemiBold)) { append("@$normalizedHandle") }
                        append(" is available")
                    }
                } else {
                    AnnotatedString("3–20 characters: letters, numbers, dots, underscores")
                },
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )
            Spacer(Modifier.height(16.dp))
            PillField(
                value = email,
                onValueChange = { email = it.trim().take(254) },
                label = "Email (optional)",
                placeholder = "kasun@example.com",
                error = emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                helper = AnnotatedString("For receipts and security alerts"),
                onNext = { focusManager.clearFocus() },
            )
            Spacer(Modifier.height(20.dp))
        }
        Box(Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            PayCeylonButton(
                label = if (submit.busy) "Saving…" else "Continue",
                onClick = { submit.run { serverError = onSave(name, handle, email, photo) } },
                style = PayCeylonButtonStyle.Accent,
                enabled = canSave && !submit.busy,
            )
        }
    }
}

/** The grey pill input used for the phone number, with a label above and a helper or error below. */
@Composable
private fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    error: String? = null,
    helper: AnnotatedString? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = TextStyle(color = Ink, fontFamily = InterFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MutedText, fontFamily = InterFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(SoftGrey)
                .border(
                    width = 1.5.dp,
                    color = when {
                        error != null -> PayCeylonRed
                        focused -> Ink.copy(alpha = 0.6f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(32.dp),
                )
                .padding(horizontal = 22.dp),
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(PayCeylonRed),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization, imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    if (prefix != null) {
                        Text(prefix, style = textStyle.copy(color = MutedText))
                        Spacer(Modifier.width(2.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = MutedText.copy(alpha = 0.7f)))
                        inner()
                    }
                }
            },
        )
        AnimatedVisibility(visible = error != null || helper != null) {
            Text(
                text = error?.let(::AnnotatedString) ?: helper ?: AnnotatedString(""),
                modifier = Modifier.padding(horizontal = 8.dp),
                color = if (error != null) PayCeylonRed else MutedText,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
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
