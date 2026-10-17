package com.payceylon.app.ui.bills

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.data.ApiException
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.auth.PayCeylonButtonStyle
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.PayCeylonTextField
import com.payceylon.app.ui.components.rememberSubmitState
import com.payceylon.app.ui.components.showError
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import com.payceylon.app.ui.wallet.AmountDisplay
import com.payceylon.app.ui.wallet.AmountKeypad
import com.payceylon.app.ui.wallet.Asset
import com.payceylon.app.ui.wallet.FundsSuccess
import com.payceylon.app.ui.wallet.MethodBadge
import com.payceylon.app.ui.wallet.PageHeader
import com.payceylon.app.ui.wallet.QuickAmountRow
import com.payceylon.app.ui.wallet.appendAmountKey
import com.payceylon.app.ui.wallet.toAmountString
import com.payceylon.app.ui.wallet.toKeypadInput
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Steps of the bill flow, in order, so transitions slide forward or back. */
private enum class BillStep { Billers, Account, Amount, Done }

private val QuickReloads: List<BigDecimal> = listOf("100", "200", "500", "1000").map(::BigDecimal)
private val DueDateFormat = SimpleDateFormat("d MMM", Locale.US)

/**
 * Pay a utility bill or reload a phone from the fiat wallet: pick a biller (or a recent one),
 * enter the account, then the amount (prefilled with the amount due for bills).
 */
@Composable
fun BillsScreen(
    balance: BigDecimal,
    loadBillers: suspend () -> List<Biller>,
    loadSaved: suspend () -> List<SavedBiller>,
    lookupBill: suspend (biller: Biller, account: String) -> Bill,
    removeSaved: suspend (SavedBiller) -> Unit,
    /** Pays; throws ApiException when rejected (e.g. insufficient funds). */
    onPay: suspend (biller: Biller, account: String, amount: BigDecimal, requestKey: String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val submit = rememberSubmitState()

    var billers by remember { mutableStateOf<List<Biller>?>(null) }
    var saved by remember { mutableStateOf(emptyList<SavedBiller>()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var step by rememberSaveable { mutableStateOf(BillStep.Billers) }
    var billerId by rememberSaveable { mutableStateOf<String?>(null) }
    var accountInput by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var requestKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var paidAmount by rememberSaveable { mutableStateOf("") }
    var bill by remember { mutableStateOf<Bill?>(null) }

    val biller = billers?.firstOrNull { it.id == billerId }

    suspend fun load() {
        loadError = null
        try {
            billers = loadBillers()
            saved = loadSaved()
        } catch (e: ApiException) {
            loadError = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    fun openAmount(prefill: BigDecimal?) {
        amountInput = prefill?.toKeypadInput(Asset.LKR.decimals).orEmpty()
        requestKey = UUID.randomUUID().toString()
        step = BillStep.Amount
    }

    /** Looks the bill up (bill billers) and moves on to the amount. */
    fun continueWith(target: Biller, number: String, reloadAmount: BigDecimal? = null) {
        billerId = target.id
        account = number
        if (target.isReload) {
            bill = null
            openAmount(reloadAmount)
        } else {
            submit.run {
                val found = lookupBill(target, number)
                bill = found
                account = found.account
                openAmount(found.amountDue)
            }
        }
    }

    fun goBack() {
        when (step) {
            BillStep.Billers, BillStep.Done -> onBack()
            BillStep.Account -> step = BillStep.Billers
            // Recent billers skip the account step, so back from the amount goes to the list.
            BillStep.Amount -> step = if (accountInput.isEmpty()) BillStep.Billers else BillStep.Account
        }
    }
    BackHandler(enabled = step == BillStep.Account || step == BillStep.Amount) { goBack() }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState > initialState
            val slide = tween<IntOffset>(260, easing = PayCeylonEasing)
            (slideInHorizontally(slide) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(slide) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(120)))
        },
        label = "bill step",
        modifier = modifier,
    ) { current ->
        when (current) {
            BillStep.Billers -> BillersStep(
                billers = billers,
                saved = saved,
                error = loadError,
                onRetry = { scope.launch { load() } },
                onBack = onBack,
                onBiller = { chosen ->
                    billerId = chosen.id
                    accountInput = ""
                    step = BillStep.Account
                },
                onSaved = { recent ->
                    val target = billers?.firstOrNull { it.id == recent.billerId } ?: return@BillersStep
                    accountInput = ""
                    continueWith(target, recent.account, reloadAmount = recent.lastAmount)
                },
                onRemoveSaved = { recent ->
                    scope.launch {
                        try {
                            removeSaved(recent)
                            saved = saved - recent
                        } catch (e: ApiException) {
                            showError(context, e.message)
                        }
                    }
                },
                busy = submit.busy,
            )

            BillStep.Account -> if (biller != null) {
                AccountStep(
                    biller = biller,
                    input = accountInput,
                    onInput = { accountInput = it.filter { c -> c.isDigit() || c == ' ' || c == '+' }.take(16) },
                    busy = submit.busy,
                    onContinue = { continueWith(biller, normalizeAccount(accountInput)) },
                    onBack = ::goBack,
                )
            }

            BillStep.Amount -> if (biller != null) {
                AmountStep(
                    biller = biller,
                    account = account,
                    bill = bill,
                    balance = balance,
                    input = amountInput,
                    onInput = { amountInput = it },
                    busy = submit.busy,
                    onPay = { amount ->
                        submit.run {
                            onPay(biller, account, amount, requestKey)
                            paidAmount = amount.toPlainString()
                            step = BillStep.Done
                        }
                    },
                    onBack = ::goBack,
                )
            }

            BillStep.Done -> FundsSuccess(
                headline = "-LKR ${BigDecimal(paidAmount.ifEmpty { "0" }).toAmountString(Asset.LKR)}",
                prefix = if (biller?.isReload == true) "reloaded to" else "paid to",
                account = biller?.let { "${it.shortName} · ${formatAccount(it, account)}" }.orEmpty(),
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun BillersStep(
    billers: List<Biller>?,
    saved: List<SavedBiller>,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onBiller: (Biller) -> Unit,
    onSaved: (SavedBiller) -> Unit,
    onRemoveSaved: (SavedBiller) -> Unit,
    busy: Boolean,
) {
    val colors = LocalWalletColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp),
    ) {
        PageHeader(title = "Pay bills", onBack = onBack)
        Spacer(Modifier.height(20.dp))
        when {
            billers == null && error != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error, color = colors.contentSecondary, fontFamily = InterFontFamily, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Try again",
                    modifier = Modifier.tap(onClick = onRetry),
                    color = PayCeylonPrimary,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            billers == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PayCeylonPrimary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            }
            else -> Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val recent = saved.mapNotNull { s -> billers.firstOrNull { it.id == s.billerId }?.let { it to s } }
                if (recent.isNotEmpty()) {
                    Section("Recent") {
                        recent.forEachIndexed { index, (recentBiller, entry) ->
                            if (index > 0) RowDivider()
                            BillerRow(
                                biller = recentBiller,
                                title = recentBiller.shortName,
                                subtitle = "${formatAccount(recentBiller, entry.account)} · last LKR ${entry.lastAmount.toAmountString(Asset.LKR)}",
                                onClick = { if (!busy) onSaved(entry) },
                                onRemove = { onRemoveSaved(entry) },
                            )
                        }
                    }
                }
                BillCategory.entries.forEach { category ->
                    val inCategory = billers.filter { it.category == category }
                    if (inCategory.isNotEmpty()) {
                        Section(category.label) {
                            inCategory.forEachIndexed { index, item ->
                                if (index > 0) RowDivider()
                                BillerRow(
                                    biller = item,
                                    title = item.shortName,
                                    subtitle = item.name.takeIf { it != item.shortName }
                                        ?: if (item.isReload) "Prepaid reload" else item.category.label,
                                    onClick = { onBiller(item) },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Demo: bills are simulated and nothing is paid to the real companies.",
                    color = colors.contentTertiary,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = LocalWalletColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = colors.contentSecondary,
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card),
        ) {
            content()
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        thickness = 1.dp,
        color = LocalWalletColors.current.subtle,
    )
}

@Composable
private fun BillerRow(
    biller: Biller,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val colors = LocalWalletColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MethodBadge(biller.category.icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.content, fontFamily = InterFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            Image(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = "Remove",
                modifier = Modifier
                    .tap(onClick = onRemove)
                    .padding(6.dp)
                    .size(14.dp),
                colorFilter = ColorFilter.tint(colors.contentTertiary),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_chevron_up_small),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(width = 9.5.dp, height = 5.5.dp)
                    .rotate(90f),
                colorFilter = ColorFilter.tint(colors.contentTertiary),
            )
        }
    }
}

@Composable
private fun AccountStep(
    biller: Biller,
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWalletColors.current
    val digits = normalizeAccount(input)
    val valid = if (biller.isReload) {
        Regex("(94)?0?7\\d{8}").matches(digits)
    } else {
        digits.length in 8..12
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
    ) {
        PageHeader(title = biller.shortName, onBack = onBack)
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MethodBadge(biller.category.icon)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(biller.name, color = colors.content, fontFamily = InterFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (biller.isReload) "Prepaid reload" else biller.category.label,
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PayCeylonTextField(
            value = input,
            onValueChange = onInput,
            label = biller.accountLabel,
            placeholder = biller.accountHint,
            helper = if (biller.isReload) "The number to reload" else "Printed on your ${biller.shortName} bill",
            keyboardType = if (biller.isReload) KeyboardType.Phone else KeyboardType.Number,
            imeAction = ImeAction.Done,
            onImeAction = { if (valid && !busy) onContinue() },
        )
        Spacer(Modifier.weight(1f))
        PayCeylonButton(
            label = when {
                busy -> "Finding your bill…"
                biller.isReload -> "Continue"
                else -> "Find bill"
            },
            onClick = onContinue,
            enabled = valid && !busy,
        )
    }
}

@Composable
private fun AmountStep(
    biller: Biller,
    account: String,
    bill: Bill?,
    balance: BigDecimal,
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    onPay: (BigDecimal) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWalletColors.current
    val amount = input.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val exceedsBalance = amount > balance
    val outOfRange = biller.isReload && amount.signum() > 0 && amount !in ReloadRange
    val canPay = amount.signum() > 0 && !exceedsBalance && !outOfRange
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(title = if (biller.isReload) "Reload ${biller.shortName}" else "Pay ${biller.shortName}", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            AmountDisplay(currency = "LKR", input = input, isOverBalance = exceedsBalance)
            Text(
                text = when {
                    exceedsBalance -> "Insufficient balance"
                    outOfRange -> "Reloads are LKR ${ReloadRange.start.toAmountString(Asset.LKR)} to ${ReloadRange.endInclusive.toAmountString(Asset.LKR)}"
                    else -> "Available: LKR ${balance.toAmountString(Asset.LKR)}"
                },
                color = if (exceedsBalance || outOfRange) colors.priceDown else colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (bill != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Due LKR ${bill.amountDue.toAmountString(Asset.LKR)} by ${DueDateFormat.format(Date(bill.dueDate))}",
                        color = colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (amount.compareTo(bill.amountDue) != 0) {
                        Text(
                            text = "Pay full",
                            modifier = Modifier.tap { onInput(bill.amountDue.toKeypadInput(Asset.LKR.decimals)) },
                            color = PayCeylonPrimary,
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                QuickAmountRow(
                    amounts = QuickReloads,
                    current = amount,
                    onPick = { onInput(it.toKeypadInput(Asset.LKR.decimals)) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MethodBadge(biller.category.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (biller.isReload) "Reload" else "Pay to",
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    listOfNotNull(biller.shortName, formatAccount(biller, account), bill?.accountName).joinToString(" · "),
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AmountKeypad(
            onKey = { key -> onInput(appendAmountKey(input, key, Asset.LKR.decimals)) },
            onDelete = { onInput(input.dropLast(1)) },
        )
        Spacer(Modifier.height(16.dp))
        PayCeylonButton(
            label = when {
                busy -> "Paying…"
                canPay -> "Pay LKR ${amount.toAmountString(Asset.LKR)}"
                else -> "Pay"
            },
            onClick = { onPay(amount) },
            enabled = canPay && !busy,
            style = PayCeylonButtonStyle.Accent,
        )
    }
}
