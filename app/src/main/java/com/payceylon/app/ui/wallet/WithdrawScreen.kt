package com.payceylon.app.ui.wallet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.ui.account.PaymentMethod
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.auth.PayCeylonButtonStyle
import com.payceylon.app.ui.components.rememberSubmitState
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import java.math.BigDecimal
import java.util.UUID

/**
 * Pays LKR out of the fiat wallet to a linked bank account, with the bank fee added on top.
 * Crypto has no withdrawal; tokens leave the wallet directly through Send.
 */
@Composable
fun WithdrawScreen(
    balance: BigDecimal,
    /** Linked bank accounts. */
    banks: List<PaymentMethod>,
    /** Pays out; throws ApiException when rejected (e.g. insufficient funds). */
    onWithdraw: suspend (amount: BigDecimal, bank: PaymentMethod, requestKey: String) -> Unit,
    onAddBank: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val asset = Asset.LKR
    val requestKey = rememberSaveable { UUID.randomUUID().toString() }
    val submit = rememberSubmitState()
    var amountInput by rememberSaveable { mutableStateOf("") }
    var bankId by rememberSaveable { mutableStateOf(banks.firstOrNull()?.id) }
    var withdrawnAmount by rememberSaveable { mutableStateOf<String?>(null) }

    // Falls back to the first linked account if the chosen one was removed.
    val destination = banks.firstOrNull { it.id == bankId } ?: banks.firstOrNull()
    val amount = amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val fee = WalletFees.withdrawalFee
    val maxAmount = (balance - fee).max(BigDecimal.ZERO)
    val exceedsBalance = amount > maxAmount
    val canWithdraw = destination != null && amount > BigDecimal.ZERO && !exceedsBalance

    AnimatedContent(
        targetState = withdrawnAmount,
        transitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None
        },
        label = "withdraw result",
        modifier = modifier,
    ) { withdrawn ->
        if (withdrawn != null && destination != null) {
            FundsSuccess(
                headline = "-LKR ${BigDecimal(withdrawn).toAmountString(asset)}",
                prefix = "sent to",
                account = destination.title,
                eta = destination.eta,
                onDone = onDone,
            )
            return@AnimatedContent
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.page)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageHeader(title = "Withdraw", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                AmountDisplay(
                    currency = asset.code,
                    input = amountInput,
                    isOverBalance = exceedsBalance,
                    modifier = Modifier,
                )
                Row(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (exceedsBalance) {
                            "Insufficient balance"
                        } else {
                            "Available: LKR ${balance.toAmountString(asset)}"
                        },
                        color = if (exceedsBalance) colors.priceDown else colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Max",
                        modifier = Modifier.tap {
                            amountInput = maxAmount.toKeypadInput(asset.decimals)
                        },
                        color = PayCeylonPrimary,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "Bank fee LKR ${fee.toAmountString(asset)}" +
                        if (canWithdraw) " · Total LKR ${(amount + fee).toAmountString(asset)}" else "",
                    modifier = Modifier,
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            if (destination == null) {
                AddMethodPrompt(
                    caption = "Withdraw to",
                    label = "Link a bank account",
                    onClick = onAddBank,
                    modifier = Modifier,
                )
            } else {
                MethodSelector(
                    caption = "Withdraw to",
                    options = banks,
                    selected = destination,
                    onSelect = { bankId = it.id },
                    title = { it.title },
                    icon = { it.icon },
                    menuLabel = { it.name },
                    detail = { "${it.masked} · ${it.detail} · ETA ${it.eta.lowercase()}" },
                    modifier = Modifier,
                )
            }
            Spacer(Modifier.height(12.dp))
            AmountKeypad(
                onKey = { key -> amountInput = appendAmountKey(amountInput, key, asset.decimals) },
                onDelete = { amountInput = amountInput.dropLast(1) },
                modifier = Modifier,
            )
            Spacer(Modifier.height(16.dp))
            PayCeylonButton(
                label = when {
                    submit.busy -> "Withdrawing…"
                    canWithdraw -> "Withdraw LKR ${amount.toAmountString(asset)}"
                    else -> "Withdraw"
                },
                onClick = {
                    if (destination != null) {
                        submit.run {
                            onWithdraw(amount, destination, requestKey)
                            withdrawnAmount = amount.toPlainString()
                        }
                    }
                },
                enabled = canWithdraw && !submit.busy,
                style = PayCeylonButtonStyle.Accent,
                modifier = Modifier,
            )
        }
    }
}
