package com.payceylon.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import com.payceylon.app.ui.wallet.PageHeader

@Composable
fun SettingsScreen(
    account: AccountState,
    appVersion: String,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onPaymentMethods: () -> Unit,
    onLegal: (LegalDocument) -> Unit,
    onHelp: () -> Unit,
    onLogOut: () -> Unit,
    /** Device-only "unlock with biometrics" switch. */
    appLockEnabled: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val profile = account.profile
    var confirmLogOut by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PageHeader(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Profile summary; the whole card opens the profile editor.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tap(onClick = onEditProfile)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProfileAvatar(account.photo, size = 44.dp, name = profile.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = profile.name,
                        color = colors.content,
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "@${profile.handle}",
                        color = PayCeylonPrimary,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_chevron_up_small),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .rotate(90f)
                        .size(width = 9.5.dp, height = 5.5.dp),
                    colorFilter = ColorFilter.tint(colors.contentTertiary),
                )
            }

            SettingsSection("Account", Modifier) {
                SettingsRow(
                    icon = R.drawable.ic_user,
                    title = "Personal details",
                    value = "Name, photo, phone, email",
                    onClick = onEditProfile,
                )
                val cardCount = account.cards.size
                val bankCount = account.banks.size
                SettingsRow(
                    icon = R.drawable.ic_top_up,
                    title = "Cards & bank accounts",
                    value = "$cardCount ${if (cardCount == 1) "card" else "cards"} · " +
                        "$bankCount ${if (bankCount == 1) "bank account" else "bank accounts"}",
                    onClick = onPaymentMethods,
                    showDivider = false,
                )
            }

            SettingsSection("Security", Modifier) {
                SettingsRow(
                    icon = R.drawable.ic_fingerprint,
                    title = "Biometric unlock",
                    value = "Unlock the app with biometrics",
                    onClick = { onAppLockChange(!appLockEnabled) },
                    trailing = { PayCeylonSwitch(checked = appLockEnabled, onCheckedChange = onAppLockChange) },
                )
                SettingToggleRow(account, AccountSetting.BiometricApproval)
                SettingToggleRow(account, AccountSetting.HideBalance, showDivider = false)
            }

            SettingsSection("Notifications", Modifier) {
                SettingToggleRow(account, AccountSetting.TransactionAlerts)
                SettingToggleRow(account, AccountSetting.Promotions, showDivider = false)
            }

            SettingsSection("About", Modifier) {
                SettingsRow(
                    icon = R.drawable.ic_shield,
                    title = "Privacy policy",
                    onClick = { onLegal(LegalDocument.Privacy) },
                )
                SettingsRow(
                    icon = R.drawable.ic_doc,
                    title = "Terms of service",
                    onClick = { onLegal(LegalDocument.Terms) },
                )
                SettingsRow(
                    icon = R.drawable.ic_help,
                    title = "Help & support",
                    value = "support@payceylon.lk",
                    onClick = onHelp,
                )
                SettingsRow(
                    icon = R.drawable.ic_info,
                    title = "App version",
                    value = appVersion,
                    showDivider = false,
                )
            }

            SettingsSection(null, Modifier) {
                SettingsRow(
                    icon = R.drawable.ic_logout,
                    title = "Log out",
                    titleColor = PayCeylonPrimary,
                    iconTint = PayCeylonPrimary,
                    onClick = { confirmLogOut = true },
                    showDivider = false,
                    trailing = {},
                )
            }
        }
    }

    if (confirmLogOut) {
        AlertDialog(
            onDismissRequest = { confirmLogOut = false },
            containerColor = colors.card,
            title = {
                Text("Log out of PayCeylon?", color = colors.content, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "You'll need your phone number and an SMS code to sign back in.",
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogOut = false
                    onLogOut()
                }) {
                    Text("Log out", color = PayCeylonPrimary, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogOut = false }) {
                    Text("Cancel", color = colors.content, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium)
                }
            },
        )
    }
}

@Composable
private fun SettingToggleRow(account: AccountState, setting: AccountSetting, showDivider: Boolean = true) {
    val enabled = account.isEnabled(setting)
    SettingsRow(
        icon = setting.icon,
        title = setting.title,
        value = setting.description,
        onClick = { account.setEnabled(setting, !enabled) },
        showDivider = showDivider,
        trailing = { PayCeylonSwitch(checked = enabled, onCheckedChange = { account.setEnabled(setting, it) }) },
    )
}
