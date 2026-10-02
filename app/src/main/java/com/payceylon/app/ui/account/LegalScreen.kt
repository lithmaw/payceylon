package com.payceylon.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import com.payceylon.app.ui.wallet.PageHeader

/**
 * In-app legal documents. Prototype copy: replace with the versions approved by PayCeylon's
 * legal team (and served remotely so they can change without an app release).
 */
enum class LegalDocument(val title: String, val updated: String, val sections: List<Pair<String, String>>) {
    Privacy(
        title = "Privacy policy",
        updated = "Last updated 1 October 2026",
        sections = listOf(
            "What we collect" to "Your name, username, mobile number, email and profile photo; the cards and bank accounts you link (only the last four digits and expiry are kept); your transactions; and basic device information used to keep your account secure.",
            "How we use it" to "To run your wallets, process payments, top-ups, swaps and withdrawals, verify your identity, prevent fraud, meet Central Bank of Sri Lanka requirements, and send you transaction and security alerts.",
            "What others see" to "People you pay, or who scan your QR code, see your name, username and profile photo. They never see your phone number, email, balances or linked accounts.",
            "Card and bank details" to "Full card numbers and security codes are handled by our PCI DSS compliant payment partner and never stored on your phone or our servers.",
            "Sharing" to "We share data only with banks, card networks and payment partners needed to complete your transactions, and with authorities where Sri Lankan law requires it. We never sell your data.",
            "Your choices" to "You can update your details at any time in Settings, turn off promotional notifications, and ask us to export or delete your data by writing to privacy@payceylon.lk.",
            "Retention" to "Transaction records are kept for at least six years as required by financial regulations. Other data is deleted within 90 days of closing your account.",
        ),
    ),
    Terms(
        title = "Terms of service",
        updated = "Last updated 1 October 2026",
        sections = listOf(
            "Your account" to "You must be 18 or older and use your own Sri Lankan mobile number. Keep your phone and SMS codes private; you are responsible for activity on your account.",
            "Fiat wallet" to "LKR balances can be topped up from linked cards (1.5% fee) or bank accounts (free) and withdrawn to linked bank accounts for a LKR 25 fee. Top-ups are limited to LKR 500,000 each.",
            "Crypto wallet" to "Tokens are bought and sold by swapping with LKR at the quoted rate, with a 0.5% fee. Crypto can't be topped up or withdrawn to a bank directly. Crypto prices are volatile and you may lose value.",
            "Payments" to "Payments to other PayCeylon users are instant and can't be reversed. Check the username before you send.",
            "Fees and limits" to "Current fees and limits are always shown before you confirm. We'll tell you at least 30 days before any increase.",
            "Closing your account" to "You can close your account at any time once your balances are zero. We may suspend accounts used for fraud or in breach of these terms.",
        ),
    ),
}

@Composable
fun LegalScreen(document: LegalDocument, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWalletColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PageHeader(title = document.title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = document.updated,
                modifier = Modifier,
                color = PayCeylonPrimary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            document.sections.forEachIndexed { index, (heading, body) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.card)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = heading,
                        color = colors.content,
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = body,
                        color = colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }
            }
        }
    }
}
