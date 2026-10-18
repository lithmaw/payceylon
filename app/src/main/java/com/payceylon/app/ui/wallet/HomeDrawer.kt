package com.payceylon.app.ui.wallet

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.account.ProfileAvatar
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary

/** Where the sidebar can take the user; Home decides what each one opens. */
enum class DrawerItem { Profile, History, Receive, Bills, Nearby, PaymentMethods, Settings, Help, LogOut }

/**
 * The left sidebar on Home: the user, the two wallets, then shortcuts and account options.
 * Log out asks first.
 */
@Composable
internal fun HomeDrawer(
    photo: ImageBitmap?,
    name: String,
    handle: String,
    selectedWallet: WalletType,
    onWalletChange: (WalletType) -> Unit,
    onItem: (DrawerItem) -> Unit,
) {
    val colors = LocalWalletColors.current
    var confirmLogOut by rememberSaveable { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = colors.page,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(pressedScale = 0.98f) { onItem(DrawerItem.Profile) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileAvatar(photo = photo, size = 48.dp, name = name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = name,
                        color = colors.content,
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@$handle",
                        color = colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            DrawerHeading("Wallets")
            WalletType.entries.forEach { wallet ->
                val selected = wallet == selectedWallet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(pressedScale = 0.98f) { onWalletChange(wallet) }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.card else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WalletBadge(wallet)
                    Text(
                        text = wallet.label,
                        modifier = Modifier.weight(1f),
                        color = colors.content,
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (selected) {
                        Image(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = "Selected",
                            modifier = Modifier.size(18.dp),
                            colorFilter = ColorFilter.tint(PayCeylonPrimary),
                        )
                    }
                }
            }

            DrawerHeading("Money")
            DrawerRow(R.drawable.ic_doc, "Transaction history") { onItem(DrawerItem.History) }
            DrawerRow(R.drawable.ic_scan, "My QR code") { onItem(DrawerItem.Receive) }
            DrawerRow(R.drawable.ic_bills, "Pay bills") { onItem(DrawerItem.Bills) }
            DrawerRow(R.drawable.ic_location, "ATMs & agents nearby") { onItem(DrawerItem.Nearby) }

            DrawerHeading("Account")
            DrawerRow(R.drawable.ic_top_up, "Cards & bank accounts") { onItem(DrawerItem.PaymentMethods) }
            DrawerRow(R.drawable.ic_user, "Account settings") { onItem(DrawerItem.Settings) }
            DrawerRow(R.drawable.ic_help, "Help & support") { onItem(DrawerItem.Help) }

            Spacer(Modifier.height(16.dp))
            DrawerRow(R.drawable.ic_logout, "Log out", tint = PayCeylonPrimary) { confirmLogOut = true }
        }
    }

    if (confirmLogOut) {
        AlertDialog(
            onDismissRequest = { confirmLogOut = false },
            containerColor = colors.card,
            title = { Text("Log out of PayCeylon?", color = colors.content, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Your wallet stays safe. Sign in again with your phone number.",
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogOut = false
                    onItem(DrawerItem.LogOut)
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
private fun DrawerHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 10.dp, top = 24.dp, bottom = 6.dp),
        color = LocalWalletColors.current.contentTertiary,
        fontFamily = InterFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DrawerRow(
    @DrawableRes icon: Int,
    title: String,
    tint: Color = LocalWalletColors.current.content,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(tint),
        )
        Text(
            text = title,
            color = tint,
            fontFamily = InterFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}