package com.payceylon.app.ui.account

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.auth.PayCeylonButtonStyle
import com.payceylon.app.ui.components.PayCeylonDropdownMenu
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.PayCeylonTextField
import com.payceylon.app.ui.components.rememberSubmitState
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import com.payceylon.app.ui.wallet.MethodBadge
import com.payceylon.app.ui.wallet.PageHeader

@Composable
fun PaymentMethodsScreen(
    account: AccountState,
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onAddBank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val submit = rememberSubmitState()
    var pendingRemoval by remember { mutableStateOf<PaymentMethod?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        PageHeader(title = "Cards & bank accounts", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection("Cards · top up instantly, 1.5% fee", Modifier) {
                MethodList(account.cards, empty = "No cards linked yet", onRemove = { pendingRemoval = it })
            }
            SettingsSection("Bank accounts · free top-ups and withdrawals", Modifier) {
                MethodList(account.banks, empty = "No bank accounts linked yet", onRemove = { pendingRemoval = it })
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(colors.contentSecondary),
                )
                Text(
                    text = "PayCeylon keeps only the last four digits of your cards and accounts.",
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        PayCeylonButton(
            label = "Add card",
            onClick = onAddCard,
            style = PayCeylonButtonStyle.Accent,
            modifier = Modifier,
        )
        Spacer(Modifier.height(10.dp))
        PayCeylonButton(
            label = "Add bank account",
            onClick = onAddBank,
            style = PayCeylonButtonStyle.Secondary,
            modifier = Modifier,
        )
    }

    pendingRemoval?.let { method ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = colors.card,
            title = {
                Text("Remove ${method.title}?", color = colors.content, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    if (method.type == PaymentMethodType.Card) {
                        "You won't be able to top up with this card until you add it again."
                    } else {
                        "You won't be able to top up from or withdraw to this account until you link it again."
                    },
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    submit.run { account.removePaymentMethod(method.id) }
                }) {
                    Text("Remove", color = PayCeylonPrimary, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Cancel", color = colors.content, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium)
                }
            },
        )
    }
}

@Composable
private fun MethodList(methods: List<PaymentMethod>, empty: String, onRemove: (PaymentMethod) -> Unit) {
    val colors = LocalWalletColors.current
    if (methods.isEmpty()) {
        Text(
            text = empty,
            modifier = Modifier.padding(vertical = 18.dp),
            color = colors.contentSecondary,
            fontFamily = InterFontFamily,
            fontSize = 14.sp,
        )
        return
    }
    methods.forEachIndexed { index, method ->
        SettingsRow(
            icon = method.icon,
            leading = method.brand?.let { brand -> { CardLogo(brand) } },
            title = method.title,
            value = method.detail,
            showDivider = index < methods.lastIndex,
            trailing = {
                Image(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = "Remove ${method.title}",
                    modifier = Modifier
                        .tap { onRemove(method) }
                        .size(22.dp),
                    colorFilter = ColorFilter.tint(colors.contentSecondary),
                )
            },
        )
    }
}

@Composable
fun AddCardScreen(
    account: AccountState,
    onBack: () -> Unit,
    onAdded: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val submit = rememberSubmitState()
    val context = LocalContext.current
    // Card details live only in this screen's state; only brand, last four and expiry are saved.
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var holder by rememberSaveable { mutableStateOf(account.profile.name) }
    var showErrors by remember { mutableStateOf(false) }

    val brand = CardBrand.detect(number)
    val maxDigits = brand?.lengths?.last ?: 16
    val numberError = cardNumberError(number)
    val expiryError = expiryError(expiry)
    val cvvError = cvvError(cvv, brand)
    val holderError = holderNameError(holder)
    fun shown(error: String?, value: String, complete: Boolean) =
        error.takeIf { showErrors || (complete && value.isNotEmpty()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
    ) {
        PageHeader(title = "Add card", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardPreview(
                brand = brand,
                number = number,
                holder = holder,
                expiry = expiry,
                modifier = Modifier,
            )
            PayCeylonTextField(
                value = number,
                onValueChange = { number = it.filter(Char::isDigit).let { d -> d.take(CardBrand.detect(d)?.lengths?.last ?: 19) } },
                label = "Card number",
                placeholder = "1234 5678 9012 3456",
                error = shown(numberError, number, number.length >= maxDigits),
                keyboardType = KeyboardType.Number,
                visualTransformation = CardNumberTransformation,
                modifier = Modifier,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier) {
                PayCeylonTextField(
                    value = expiry,
                    onValueChange = { expiry = it.filter(Char::isDigit).take(4) },
                    label = "Expiry",
                    placeholder = "MM/YY",
                    error = shown(expiryError, expiry, expiry.length == 4),
                    keyboardType = KeyboardType.Number,
                    visualTransformation = ExpiryTransformation,
                    modifier = Modifier.weight(1f),
                )
                PayCeylonTextField(
                    value = cvv,
                    onValueChange = { cvv = it.filter(Char::isDigit).take(brand?.cvvLength ?: 3) },
                    label = "Security code",
                    placeholder = "${brand?.cvvLength ?: 3} digits",
                    error = shown(cvvError, cvv, false),
                    keyboardType = KeyboardType.NumberPassword,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
            }
            PayCeylonTextField(
                value = holder,
                onValueChange = { holder = it.take(40) },
                label = "Name on card",
                error = shown(holderError, holder, false),
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
                modifier = Modifier,
            )
        }
        PayCeylonButton(
            label = "Add card",
            onClick = {
                if (listOf(numberError, expiryError, cvvError, holderError).any { it != null }) {
                    showErrors = true
                } else {
                    submit.run {
                        val method = account.addCard(number, expiry, holder)
                        Toast.makeText(context, "${method.title} added", Toast.LENGTH_SHORT).show()
                        onAdded(method)
                    }
                }
            },
            enabled = number.isNotEmpty() && expiry.isNotEmpty() && cvv.isNotEmpty() && holder.isNotBlank() && !submit.busy,
            style = PayCeylonButtonStyle.Accent,
        )
    }
}

/** Live preview of the card being added, in PayCeylon red. */
@Composable
private fun CardPreview(brand: CardBrand?, number: String, holder: String, expiry: String, modifier: Modifier = Modifier) {
    val digits = number.padEnd(if (brand == CardBrand.Amex) 15 else 16, '•')
    val grouped = digits.chunked(4).joinToString("  ")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE40046), PayCeylonPrimary, Color(0xFF8E0A33))))
            .padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_payceylon_mark),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
            Spacer(Modifier.weight(1f))
            // Brand logos keep their own colours on a white tab.
            if (brand != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    CardLogo(brand, maxWidth = 44.dp, maxHeight = 18.dp)
                }
            }
        }
        Text(
            text = grouped,
            color = Color.White,
            fontFamily = InterFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("CARDHOLDER", color = Color.White.copy(alpha = 0.7f), fontFamily = InterFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = holder.ifBlank { "Your name" }.uppercase(),
                    color = Color.White,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("EXPIRES", color = Color.White.copy(alpha = 0.7f), fontFamily = InterFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = expiry.padEnd(4, '•').let { "${it.take(2)}/${it.drop(2)}" },
                    color = Color.White,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** "4111111111111111" -> "4111 1111 1111 1111". */
private object CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = digits.chunked(4).joinToString(" ")
        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) =
                    (offset + (offset - 1).coerceAtLeast(0) / 4).coerceIn(0, formatted.length)

                override fun transformedToOriginal(offset: Int) =
                    (offset - offset / 5).coerceIn(0, digits.length)
            },
        )
    }
}

/** "0829" -> "08/29". */
private object ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = if (digits.length > 2) "${digits.take(2)}/${digits.drop(2)}" else digits
        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) =
                    (if (offset > 2) offset + 1 else offset).coerceIn(0, formatted.length)

                override fun transformedToOriginal(offset: Int) =
                    (if (offset > 2) offset - 1 else offset).coerceIn(0, digits.length)
            },
        )
    }
}

@Composable
fun AddBankScreen(
    account: AccountState,
    onBack: () -> Unit,
    onAdded: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val submit = rememberSubmitState()
    val context = LocalContext.current
    var bank by rememberSaveable { mutableStateOf<String?>(null) }
    var holder by rememberSaveable { mutableStateOf(account.profile.name) }
    var number by rememberSaveable { mutableStateOf("") }
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    var showErrors by remember { mutableStateOf(false) }
    var isBankMenuOpen by remember { mutableStateOf(false) }

    val numberError = accountNumberError(number)
    val holderError = holderNameError(holder)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
    ) {
        PageHeader(title = "Add bank account", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Bank",
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                val chevronRotation = if (isBankMenuOpen) 0f else 180f
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tap(role = Role.DropdownList) { isBankMenuOpen = true }
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.card)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MethodBadge(R.drawable.ic_withdraw, size = 38)
                        Text(
                            text = bank ?: "Choose your bank",
                            modifier = Modifier.weight(1f),
                            color = if (bank == null) colors.contentTertiary else colors.content,
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Image(
                            painter = painterResource(R.drawable.ic_chevron_up_small),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(width = 9.5.dp, height = 5.5.dp)
                                .rotate(chevronRotation),
                            colorFilter = ColorFilter.tint(colors.content),
                        )
                    }
                    PayCeylonDropdownMenu(
                        expanded = isBankMenuOpen,
                        onDismissRequest = { isBankMenuOpen = false },
                        options = SriLankanBanks,
                        selected = bank,
                        onSelect = { bank = it },
                        label = { it },
                        width = 300.dp,
                    )
                }
                if (showErrors && bank == null) {
                    Text("Choose a bank.", color = colors.priceDown, fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            SegmentedToggle(
                options = listOf("Savings", "Current"),
                selectedIndex = typeIndex,
                onSelect = { typeIndex = it },
                modifier = Modifier,
            )
            PayCeylonTextField(
                value = number,
                onValueChange = { number = it.filter(Char::isDigit).take(16) },
                label = "Account number",
                placeholder = "8 to 16 digits",
                error = numberError.takeIf { showErrors },
                keyboardType = KeyboardType.Number,
                modifier = Modifier,
            )
            PayCeylonTextField(
                value = holder,
                onValueChange = { holder = it.take(40) },
                label = "Account holder name",
                error = holderError.takeIf { showErrors },
                helper = "Must match the name on your PayCeylon account",
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
                modifier = Modifier,
            )
        }
        PayCeylonButton(
            label = "Link bank account",
            onClick = {
                val chosen = bank
                if (chosen == null || numberError != null || holderError != null) {
                    showErrors = true
                } else {
                    submit.run {
                        val method = account.addBank(chosen, number, holder, isSavings = typeIndex == 0)
                        Toast.makeText(context, "${method.title} linked", Toast.LENGTH_SHORT).show()
                        onAdded(method)
                    }
                }
            },
            enabled = number.isNotEmpty() && holder.isNotBlank() && !submit.busy,
            style = PayCeylonButtonStyle.Accent,
        )
    }
}
