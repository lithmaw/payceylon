package com.payceylon.app.ui.bills

import androidx.annotation.DrawableRes
import com.payceylon.app.R
import java.math.BigDecimal

enum class BillCategory(val label: String, @param:DrawableRes val icon: Int) {
    Electricity("Electricity", R.drawable.ic_bolt),
    Water("Water", R.drawable.ic_water),
    Mobile("Mobile reload", R.drawable.ic_phone),
    InternetTv("Internet & TV", R.drawable.ic_wifi),
}

/** A company the user can pay. Reload billers (mobile) take any amount; the rest have a bill. */
data class Biller(
    val id: String,
    val name: String,
    val shortName: String,
    val category: BillCategory,
    val isReload: Boolean,
    /** "Account number" or "Mobile number". */
    val accountLabel: String,
    /** Format hint, e.g. "10 digits". */
    val accountHint: String,
)

/** The (simulated) outstanding bill for one account. */
data class Bill(
    val account: String,
    val accountName: String,
    val amountDue: BigDecimal,
    /** Epoch milliseconds. */
    val dueDate: Long,
)

/** An account the user has paid before, for one-tap repeat payments. */
data class SavedBiller(
    val billerId: String,
    val account: String,
    val lastAmount: BigDecimal,
    val lastPaidAt: Long,
)

/** Smallest and largest reload a mobile operator accepts. */
val ReloadRange: ClosedRange<BigDecimal> = BigDecimal("20.00")..BigDecimal("10000.00")

/** Digits only, without spaces, slashes or dashes, as the API expects. */
fun normalizeAccount(input: String): String = input.filter { it.isDigit() }

/** Groups a mobile number as "077 123 4567"; other accounts are shown as typed. */
fun formatAccount(biller: Biller?, account: String): String =
    if (biller?.isReload == true && account.length == 10) {
        "${account.take(3)} ${account.substring(3, 6)} ${account.drop(6)}"
    } else {
        account
    }
