package com.payceylon.app.ui.components

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.payceylon.app.R
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.auth.PayCeylonButtonStyle
import com.payceylon.app.ui.theme.Ink
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.MutedText
import com.payceylon.app.ui.theme.PayCeylonPrimary

/** Fingerprint/face, falling back to the screen-lock PIN, pattern or password. */
private const val Authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** "Unlock with biometrics" is a per-device choice, so it lives on the device, not the server. */
class AppLockSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("payceylon_device", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("biometric_unlock", false)
        set(value) = prefs.edit().putBoolean("biometric_unlock", value).apply()
}

/** False when the device has no biometrics or screen lock set up. */
fun canLockApp(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(Authenticators) == BiometricManager.BIOMETRIC_SUCCESS

/** Shows the system biometric prompt; [onResult] gets true when the user passed it. */
fun promptForUnlock(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

            // Cancelled, locked out or no hardware. A single failed scan isn't an error; the prompt stays up.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(Authenticators)
            .build(),
    )
}

/** Covers the app until the user unlocks; asks straight away when shown. */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun unlock() {
        val activity = context.findActivity() as? FragmentActivity ?: return
        promptForUnlock(activity, "Unlock PayCeylon") { passed -> if (passed) onUnlocked() }
    }
    LaunchedEffect(Unit) { unlock() }

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
        Image(
            painter = painterResource(R.drawable.ic_fingerprint),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            colorFilter = ColorFilter.tint(PayCeylonPrimary),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "PayCeylon is locked",
            color = Ink,
            fontFamily = InterFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Unlock with your fingerprint, face or screen lock.",
            color = MutedText,
            fontFamily = InterFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PayCeylonButton(label = "Unlock", onClick = ::unlock, style = PayCeylonButtonStyle.Accent)
        Spacer(Modifier.height(12.dp))
        PayCeylonButton(label = "Sign out", onClick = onSignOut, style = PayCeylonButtonStyle.Secondary, height = 54.dp)
    }
}
