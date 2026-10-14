package com.payceylon.app.ui.account

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.PhoneAuthCredential
import com.payceylon.app.R
import com.payceylon.app.data.ApiException
import com.payceylon.app.data.CodeRequest
import com.payceylon.app.data.PhoneAuth
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.auth.PayCeylonButtonStyle
import com.payceylon.app.ui.auth.SriLankanPhoneVisualTransformation
import com.payceylon.app.ui.auth.maskSriLankanMobileNumber
import com.payceylon.app.ui.auth.normalizeSriLankanMobileNumber
import com.payceylon.app.ui.components.PayCeylonTextField
import com.payceylon.app.ui.components.findActivity
import com.payceylon.app.ui.components.rememberSubmitState
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import com.payceylon.app.ui.wallet.PageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    account: AccountState,
    onBack: () -> Unit,
    onEditField: (ProfileField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val context = LocalContext.current
    val submit = rememberSubmitState()
    val profile = account.profile
    // The system photo picker needs no storage permission.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            submit.run {
                val bitmap = loadProfilePhoto(context, uri)
                if (bitmap != null) {
                    account.updatePhoto(bitmap)
                } else {
                    Toast.makeText(context, "Couldn't open that photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    fun choosePhoto() = pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PageHeader(title = "Personal details", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier) {
                ProfileAvatar(
                    account.photo,
                    name = account.profile.name,
                    size = 108.dp,
                    modifier = Modifier.tap { choosePhoto() },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .border(3.dp, colors.page, CircleShape)
                        .padding(3.dp)
                        .background(PayCeylonPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = "Change photo",
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "Change photo",
                    modifier = Modifier.tap { choosePhoto() },
                    color = PayCeylonPrimary,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (account.photo != null) {
                    Text(
                        text = "Remove",
                        modifier = Modifier.tap { submit.run { account.updatePhoto(null) } },
                        color = colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            SettingsSection(null, Modifier) {
                SettingsRow(R.drawable.ic_user, ProfileField.Name.label, value = profile.name, onClick = { onEditField(ProfileField.Name) })
                SettingsRow(R.drawable.ic_at, ProfileField.Username.label, value = "@${profile.handle}", onClick = { onEditField(ProfileField.Username) })
                SettingsRow(R.drawable.ic_phone, ProfileField.Phone.label, value = profile.displayPhone, onClick = { onEditField(ProfileField.Phone) })
                SettingsRow(R.drawable.ic_mail, ProfileField.Email.label, value = profile.email, onClick = { onEditField(ProfileField.Email) }, showDivider = false)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Your name, username and photo are shown to people you pay and on your QR code.",
                modifier = Modifier
                    .padding(horizontal = 8.dp),
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Decodes a picked photo, downsampled to roughly avatar size. */
internal suspend fun loadProfilePhoto(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }.getOrNull()
}

/** Edits one profile field. A new phone number must be confirmed with an SMS code first. */
@Composable
fun EditProfileFieldScreen(
    account: AccountState,
    field: ProfileField,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val context = LocalContext.current
    val submit = rememberSubmitState()
    val original = account.profile.valueOf(field)
    var value by rememberSaveable { mutableStateOf(original) }
    var code by rememberSaveable { mutableStateOf("") }
    var verifyingPhone by rememberSaveable { mutableStateOf(false) }
    var codeError by rememberSaveable { mutableStateOf<String?>(null) }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val normalized = normalizeProfileValue(field, value)
    val changed = normalized != original
    val error = serverError ?: if (changed && value.isNotBlank()) validateProfileField(field, value) else null
    val canSave = changed && error == null

    fun saved() {
        Toast.makeText(context, "${field.label} updated", Toast.LENGTH_SHORT).show()
        onSaved()
    }

    fun save() = submit.run {
        val problem = account.updateProfile(field, value)
        if (problem == null) saved() else serverError = problem
    }

    /** Moves the account to the new number once Firebase has verified it. */
    suspend fun changePhone(credential: PhoneAuthCredential) {
        PhoneAuth.changePhone(credential)
        account.savePhoneChange()
        saved()
    }

    fun sendCode() = submit.run {
        val host = context.findActivity() ?: return@run
        when (val request = PhoneAuth.requestCode(host, "+94$normalized")) {
            is CodeRequest.Sent -> {
                verificationId = request.verificationId
                code = ""
                codeError = null
                verifyingPhone = true
            }
            is CodeRequest.Verified -> changePhone(request.credential)
        }
    }

    fun verifyCode() = submit.run {
        val id = verificationId ?: return@run
        try {
            changePhone(PhoneAuth.credential(id, code))
        } catch (e: ApiException) {
            if (e.code == "INVALID_OTP") codeError = e.message else throw e
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
    ) {
        PageHeader(
            title = if (verifyingPhone) "Verify number" else field.label,
            onBack = { if (verifyingPhone) verifyingPhone = false else onBack() },
        )
        Spacer(Modifier.height(28.dp))
        AnimatedContent(
            targetState = verifyingPhone,
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            label = "edit field step",
            modifier = Modifier.weight(1f),
        ) { verifying ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (verifying) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    Text(
                        text = "Enter the 6-digit code we sent to ${maskSriLankanMobileNumber(normalized)}.",
                        color = colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                    )
                    PayCeylonTextField(
                        value = code,
                        onValueChange = {
                            code = it.filter(Char::isDigit).take(6)
                            codeError = null
                        },
                        label = "Verification code",
                        placeholder = "000000",
                        error = codeError,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.focusRequester(focus),
                    )
                } else {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    PayCeylonTextField(
                        value = if (field == ProfileField.Phone) normalizeSriLankanMobileNumber(value) else value,
                        onValueChange = {
                            serverError = null
                            value = when (field) {
                                ProfileField.Phone -> normalizeSriLankanMobileNumber(it)
                                ProfileField.Username -> it.lowercase().replace(" ", "").take(20)
                                else -> it.take(60)
                            }
                        },
                        label = field.label,
                        error = error,
                        helper = when {
                            field == ProfileField.Username -> "3–20 characters: letters, numbers, dots, underscores"
                            field == ProfileField.Phone -> "We'll text a code to confirm the new number"
                            field == ProfileField.Email -> "Receipts and security alerts go here"
                            else -> null
                        },
                        prefix = when (field) {
                            ProfileField.Phone -> "+94"
                            ProfileField.Username -> "@"
                            else -> null
                        },
                        keyboardType = when (field) {
                            ProfileField.Phone -> KeyboardType.Phone
                            ProfileField.Email -> KeyboardType.Email
                            else -> KeyboardType.Text
                        },
                        capitalization = if (field == ProfileField.Name) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                        visualTransformation = if (field == ProfileField.Phone) {
                            SriLankanPhoneVisualTransformation
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        imeAction = ImeAction.Done,
                        modifier = Modifier.focusRequester(focus),
                    )
                }
            }
        }
        PayCeylonButton(
            label = when {
                submit.busy -> "Please wait…"
                verifyingPhone -> "Verify and save"
                field == ProfileField.Phone -> "Send code"
                else -> "Save"
            },
            onClick = {
                when {
                    verifyingPhone -> verifyCode()
                    field == ProfileField.Phone -> sendCode()
                    else -> save()
                }
            },
            enabled = !submit.busy && if (verifyingPhone) code.length == 6 else canSave,
            style = PayCeylonButtonStyle.Accent,
        )
    }
}
