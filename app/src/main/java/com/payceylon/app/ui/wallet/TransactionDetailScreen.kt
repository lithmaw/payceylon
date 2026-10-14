package com.payceylon.app.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.auth.PayCeylonButton
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.components.staggeredEntrance
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Network fee for crypto transfers (fixed per token for the prototype); fiat transfers
 * between PayCeylon users are free, and swaps carry the PayCeylon fee instead.
 */
internal fun WalletTransaction.networkFee(): Pair<Asset, BigDecimal>? {
    if (direction != TransactionDirection.Outgoing) return null
    val asset = Asset.fromCode(currency) ?: return null
    return fee?.let { asset to it }
}

private fun statusLabel(status: String) = when (status) {
    "PENDING" -> "Pending"
    "FAILED" -> "Failed"
    else -> "Completed"
}

private fun shorten(value: String) = "${value.take(8)}…${value.takeLast(6)}"

private val DateFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)

@Composable
fun TransactionDetailScreen(
    transaction: WalletTransaction,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    val context = LocalContext.current
    val asset = Asset.fromCode(transaction.currency) ?: Asset.LKR
    val isSwap = transaction.direction == TransactionDirection.Swap
    val isOutgoing = transaction.direction.isDebit
    val isCrypto = asset.token != null
    var copiedLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedLabel) {
        if (copiedLabel != null) {
            delay(1_500)
            copiedLabel = null
        }
    }
    fun copy(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        copiedLabel = label
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            CircleBackButton(onClick = onBack, background = colors.card, iconTint = colors.content)
            Text(
                text = "Transaction",
                modifier = Modifier.align(Alignment.Center),
                color = colors.content,
                fontFamily = InterFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .staggeredEntrance(0)
                    .size(64.dp)
                    .background(colors.card, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                TransactionGlyph(transaction.direction, size = 28.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = headlineAmount(transaction),
                modifier = Modifier.staggeredEntrance(1),
                color = when {
                    isSwap -> colors.content
                    isOutgoing -> colors.priceDown
                    else -> colors.priceUp
                },
                fontFamily = InterFontFamily,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = transaction.title,
                modifier = Modifier.staggeredEntrance(1),
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Completed",
                modifier = Modifier
                    .staggeredEntrance(2)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.priceUp.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                color = colors.priceUp,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(28.dp))

            DetailCard(modifier = Modifier.staggeredEntrance(3)) {
                if (isSwap) {
                    val toAsset = Asset.fromCode(transaction.toCurrency) ?: Asset.USDT
                    DetailRow("You paid", "${asset.code} ${transaction.amount.toAmountString(asset)}")
                    DetailRow("You received", "${toAsset.code} ${(transaction.toAmount ?: BigDecimal.ZERO).toAmountString(toAsset)}")
                    DetailRow("Rate", "1 ${asset.code} = ${SwapRates.rate(asset, toAsset).toRateString(toAsset)} ${toAsset.code}")
                    DetailRow(
                        "PayCeylon fee",
                        "${asset.code} ${SwapRates.fee(asset, transaction.amount).toAmountString(asset)} (0.5%)",
                    )
                } else if (transaction.direction.isExternal) {
                    val isTopUp = transaction.direction == TransactionDirection.TopUp
                    DetailRow(
                        if (isTopUp) "Paid from" else "Sent to",
                        "${transaction.counterpartyName} ${transaction.counterpartyHandle}",
                    )
                    DetailRow("Wallet", if (isCrypto) "Crypto · ${asset.token?.network}" else "Fiat Wallet")
                    val feeAsset = Asset.fromCode(transaction.feeCurrency) ?: asset
                    val fee = transaction.fee ?: BigDecimal.ZERO
                    DetailRow(
                        when {
                            isTopUp -> "Fee"
                            isCrypto -> "Network fee"
                            else -> "Bank fee"
                        },
                        if (fee.signum() == 0) "Free" else "${feeAsset.code} ${fee.toAmountString(feeAsset)}",
                    )
                    if (isTopUp) {
                        val price = WalletFees.lkrPrice(asset, transaction.amount)
                        DetailRow("Total charged", "LKR ${(price + fee).toAmountString(Asset.LKR)}")
                    } else {
                        DetailRow(
                            "Total debited",
                            "${asset.code} ${(transaction.amount + fee).toAmountString(asset)}",
                        )
                    }
                } else {
                    PartyRow(
                        label = if (isOutgoing) "To" else "From",
                        transaction = transaction,
                    )
                    DetailRow("Wallet", if (isCrypto) "Crypto · ${asset.token?.network}" else "Fiat Wallet")
                    val fee = transaction.networkFee()
                    DetailRow(
                        if (isCrypto) "Network fee" else "Fee",
                        fee?.let { (feeAsset, value) -> "${feeAsset.code} ${value.toPlainString()}" } ?: "Free",
                    )
                }
                DetailRow("Date", DateFormat.format(Date(transaction.timestamp)))
                DetailRow(
                    label = "Reference",
                    value = if (copiedLabel == "Reference") "Copied" else transaction.reference,
                    onClick = { copy("Reference", transaction.reference) },
                )
                val txHash = transaction.txHash
                if (isCrypto && !isSwap && txHash != null) {
                    DetailRow(
                        label = "Transaction hash",
                        value = if (copiedLabel == "Transaction hash") "Copied" else shorten(txHash),
                        onClick = { copy("Transaction hash", txHash) },
                        showDivider = false,
                    )
                } else {
                    DetailRow("Status", statusLabel(transaction.status), showDivider = false)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        PayCeylonButton(
            label = "Share receipt",
            onClick = { shareReceipt(context, transaction) },
            modifier = Modifier.staggeredEntrance(4),
        )
    }
}

private fun headlineAmount(transaction: WalletTransaction): String {
    val asset = Asset.fromCode(transaction.currency) ?: Asset.LKR
    return when (transaction.direction) {
        TransactionDirection.Outgoing, TransactionDirection.Withdrawal ->
            "-${asset.code} ${transaction.amount.toAmountString(asset)}"
        TransactionDirection.Incoming, TransactionDirection.TopUp ->
            "+${asset.code} ${transaction.amount.toAmountString(asset)}"
        TransactionDirection.Swap -> {
            val to = Asset.fromCode(transaction.toCurrency) ?: Asset.USDT
            "+${to.code} ${(transaction.toAmount ?: BigDecimal.ZERO).toAmountString(to)}"
        }
    }
}

/** Formats an exchange rate with enough precision to be meaningful for small prices. */
internal fun BigDecimal.toRateString(to: Asset): String {
    val scale = if (this >= BigDecimal.ONE) 2 else (to.decimals + 2).coerceAtMost(8)
    val rounded = setScale(scale, java.math.RoundingMode.HALF_UP)
    return if (this >= BigDecimal.ONE) {
        val (whole, fraction) = rounded.toMoneyParts(2)
        whole + fraction
    } else {
        rounded.stripTrailingZeros().toPlainString()
    }
}

@Composable
private fun DetailCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LocalWalletColors.current.card)
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val colors = LocalWalletColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.bounceClick(pressedScale = 0.98f, onClick = onClick) else Modifier)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                )
                if (onClick != null) {
                    Spacer(Modifier.width(6.dp))
                    Image(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp),
                        colorFilter = ColorFilter.tint(colors.contentSecondary),
                    )
                }
            }
        }
        if (showDivider) HorizontalDivider(thickness = 1.dp, color = colors.subtle)
    }
}

@Composable
private fun PartyRow(label: String, transaction: WalletTransaction) {
    val colors = LocalWalletColors.current
    val user = PayCeylonUser(transaction.counterpartyName, transaction.counterpartyHandle)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = user.name,
                        color = colors.content,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "@${user.handle}",
                        color = colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                    )
                }
                UserAvatar(user, size = 36)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.subtle)
    }
}

private fun shareReceipt(context: Context, transaction: WalletTransaction) {
    val text = buildString {
        appendLine("PayCeylon receipt")
        appendLine(transaction.title)
        appendLine(headlineAmount(transaction))
        appendLine(DateFormat.format(Date(transaction.timestamp)))
        append("Ref ${transaction.reference}")
    }
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Share receipt"))
}
