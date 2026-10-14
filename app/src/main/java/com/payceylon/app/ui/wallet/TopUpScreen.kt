package com.payceylon.app.ui.wallet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
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
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import java.math.BigDecimal
import java.util.UUID

/**
 * Adds LKR to the fiat wallet from a linked card or bank account. Crypto can't be topped up
 * directly; it is bought by swapping LKR.
 */
@Composable
fun TopUpScreen(
    /** Linked cards and bank accounts. */
    methods: List<PaymentMethod>,
    /** Adds the money; throws ApiException when rejected (e.g. over the limit). */
    onTopUp: suspend (amount: BigDecimal, method: PaymentMethod, requestKey: String) -> Unit,
    onAddMethod: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val asset = Asset.LKR
    val requestKey = rememberSaveable { UUID.randomUUID().toString() }
    val submit = rememberSubmitState()
    var amountInput by rememberSaveable { mutableStateOf("") }
    var methodId by rememberSaveable { mutableStateOf(methods.firstOrNull()?.id) }
    var addedAmount by rememberSaveable { mutableStateOf<String?>(null) }

    // Falls back to the first linked method if the chosen one was removed.
    val method = methods.firstOrNull { it.id == methodId } ?: methods.firstOrNull()
    val amount = amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val fee = method?.let { WalletFees.topUpFee(amount, it) } ?: BigDecimal.ZERO
    val overLimit = amount > WalletFees.maxTopUpLkr
    val canTopUp = method != null && amount > BigDecimal.ZERO && !overLimit

    AnimatedContent(
        targetState = addedAmount,
        transitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None
        },
        label = "top up result",
        modifier = modifier,
    ) { added ->
        if (added != null && method != null) {
            FundsSuccess(
                headline = "+LKR ${BigDecimal(added).toAmountString(asset)}",
                prefix = "added from",
                account = method.title,
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
            PageHeader(title = "Top Up", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                AmountDisplay(
                    currency = asset.code,
                    input = amountInput,
                    isOverBalance = overLimit,
                    modifier = Modifier,
                )
                QuickAmountRow(
                    amounts = QuickTopUpAmounts,
                    current = amount,
                    onPick = { amountInput = it.toKeypadInput(asset.decimals) },
                    modifier = Modifier,
                )
                Text(
                    text = when {
                        overLimit -> "Top-ups are limited to LKR ${WalletFees.maxTopUpLkr.toAmountString(asset)} at a time"
                        method == null -> "Link a card or bank account to top up"
                        amount <= BigDecimal.ZERO -> "Added to your Fiat Wallet · " +
                            if (method.topUpFeeRate.signum() == 0) "No fee" else "1.5% card fee"
                        fee.signum() == 0 -> "No fee · You pay LKR ${amount.toAmountString(asset)}"
                        else -> "You pay LKR ${(amount + fee).toAmountString(asset)} · incl. LKR ${fee.toAmountString(asset)} fee"
                    },
                    modifier = Modifier,
                    color = if (overLimit) colors.priceDown else colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            if (method == null) {
                AddMethodPrompt(
                    caption = "Pay with",
                    label = "Add a card or bank account",
                    onClick = onAddMethod,
                    modifier = Modifier,
                )
            } else {
                MethodSelector(
                    caption = "Pay with",
                    options = methods,
                    selected = method,
                    onSelect = { methodId = it.id },
                    title = { it.title },
                    icon = { it.icon },
                    menuLabel = { it.name },
                    detail = { "${it.masked} · ${it.eta} · ${if (it.topUpFeeRate.signum() == 0) "No fee" else "1.5% fee"}" },
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
                    submit.busy -> "Topping up…"
                    canTopUp -> "Top up LKR ${amount.toAmountString(asset)}"
                    else -> "Top up"
                },
                onClick = {
                    if (method != null) {
                        submit.run {
                            onTopUp(amount, method, requestKey)
                            addedAmount = amount.toPlainString()
                        }
                    }
                },
                enabled = canTopUp && !submit.busy,
                style = PayCeylonButtonStyle.Accent,
                modifier = Modifier,
            )
        }
    }
}
