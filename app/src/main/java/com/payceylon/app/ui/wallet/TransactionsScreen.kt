package com.payceylon.app.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors

private enum class TransactionFilter(val label: String) { All("All"), MoneyIn("Money in"), MoneyOut("Money out") }

/** Every transaction in the selected wallet ("See all" on Home), with simple filters. */
@Composable
fun TransactionsScreen(
    wallet: WalletType,
    transactions: List<WalletTransaction>,
    onBack: () -> Unit,
    onTransactionClick: (WalletTransaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWalletColors.current
    var filter by rememberSaveable { mutableStateOf(TransactionFilter.All) }
    val shown = transactions
        .filter { it.belongsTo(wallet) }
        .filter {
            when (filter) {
                TransactionFilter.All -> true
                // Swaps both pay and receive, so they only appear under All.
                TransactionFilter.MoneyIn -> it.direction == TransactionDirection.Incoming || it.direction == TransactionDirection.TopUp
                TransactionFilter.MoneyOut -> it.direction.isDebit
            }
        }
        .sortedByDescending { it.timestamp }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PageHeader(title = "${wallet.transactionLabel} transactions", onBack = onBack)
        Row(
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransactionFilter.entries.forEach { option ->
                val selected = option == filter
                Text(
                    text = option.label,
                    modifier = Modifier
                        .tap { filter = option }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.content else colors.card)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (selected) colors.page else colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (shown.isEmpty()) {
            Text(
                text = "No transactions here yet",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            items(shown, key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }
}
