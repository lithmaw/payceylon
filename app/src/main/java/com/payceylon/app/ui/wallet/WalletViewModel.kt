package com.payceylon.app.ui.wallet

import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payceylon.app.R
import com.payceylon.app.data.ApiException
import com.payceylon.app.data.AppServices
import com.payceylon.app.data.Backend
import com.payceylon.app.data.local.WalletCache
import com.payceylon.app.data.local.WalletSnapshot
import com.payceylon.app.ui.account.AccountSetting
import com.payceylon.app.ui.account.AccountState
import com.payceylon.app.ui.account.PaymentMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class WalletType(val label: String, val transactionLabel: String) {
    Fiat(label = "Fiat Wallet", transactionLabel = "Fiat"),
    Crypto(label = "Crypto Wallet", transactionLabel = "Crypto"),
}

enum class TransactionDirection {
    Outgoing,
    Incoming,
    Swap,
    /** LKR added from a linked card or bank account. */
    TopUp,
    /** LKR paid out to a linked bank account. */
    Withdrawal,
    ;

    /** True when money left the wallet. */
    val isDebit: Boolean get() = this == Outgoing || this == Withdrawal

    /** Top-ups and withdrawals move money to or from an outside account, not a PayCeylon user. */
    val isExternal: Boolean get() = this == TopUp || this == Withdrawal
}

/**
 * Tokens held in the crypto wallet. Addresses are well-known public example addresses for
 * the prototype; real ones come from the custody / wallet provider per user.
 */
enum class CryptoToken(
    val symbol: String,
    val tokenName: String,
    val network: String,
    val address: String,
    /** Branded icon from web3icons. */
    @param:DrawableRes val icon: Int,
    /** Decimal places shown and accepted for amounts. */
    val decimals: Int,
) {
    USDT("USDT", "Tether", "TRC-20", "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", R.drawable.ic_token_usdt, 2),
    BTC("BTC", "Bitcoin", "Bitcoin", "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", R.drawable.ic_token_btc, 6),
    ETH("ETH", "Ethereum", "ERC-20", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F", R.drawable.ic_token_eth, 4),
    SOL("SOL", "Solana", "Solana", "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV", R.drawable.ic_token_sol, 2),
}

/**
 * Anything the user can hold, send or receive: Sri Lankan rupees in the fiat wallet, or a
 * token in the crypto wallet. Payment links and transactions refer to assets by [code].
 */
enum class Asset(val code: String, val displayName: String, val token: CryptoToken?) {
    LKR("LKR", "Fiat Wallet", null),
    USDT("USDT", "Tether", CryptoToken.USDT),
    BTC("BTC", "Bitcoin", CryptoToken.BTC),
    ETH("ETH", "Ethereum", CryptoToken.ETH),
    SOL("SOL", "Solana", CryptoToken.SOL),
    ;

    val decimals: Int get() = token?.decimals ?: 2
    val wallet: WalletType get() = if (token == null) WalletType.Fiat else WalletType.Crypto

    /** Secondary line in asset pickers. */
    val detail: String get() = token?.let { "${it.symbol} \u00B7 ${it.network}" } ?: "Sri Lankan Rupee"

    companion object {
        fun of(token: CryptoToken): Asset = entries.first { it.token == token }
        fun fromCode(code: String?): Asset? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/** Shortens an address to its first and last characters, e.g. "TQn9Y2…KcbLSE". */
fun CryptoToken.shortAddress(): String = "${address.take(6)}\u2026${address.takeLast(6)}"

/** Same, for any address string (e.g. the per-user address from the server). */
fun shortAddress(address: String): String = "${address.take(6)}…${address.takeLast(6)}"

data class PayCeylonUser(val name: String, val handle: String) {
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

data class WalletTransaction(
    val id: String,
    val counterpartyName: String,
    val counterpartyHandle: String,
    val direction: TransactionDirection,
    /** Asset code that left (outgoing / swap) or arrived (incoming). */
    val currency: String,
    val amount: BigDecimal,
    val wallet: WalletType,
    /** When it happened, in epoch milliseconds. */
    val timestamp: Long,
    /** Swaps only: the asset and amount received. */
    val toCurrency: String? = null,
    val toAmount: BigDecimal? = null,
    /** Fee charged on top of [amount], in [feeCurrency]: card top-up, bank payout or network fee. */
    val fee: BigDecimal? = null,
    val feeCurrency: String? = null,
    /** PayCeylon reference shown to support, e.g. "PC-4F2A9C01DE". */
    val reference: String = "",
    /** PENDING, COMPLETED or FAILED. */
    val status: String = "COMPLETED",
    /** On-chain hash for crypto transfers that left PayCeylon; null otherwise. */
    val txHash: String? = null,
) {
    val title: String
        get() = when (direction) {
            TransactionDirection.Outgoing -> "Transfer to $counterpartyName"
            TransactionDirection.Incoming -> "Receive from $counterpartyName"
            TransactionDirection.Swap -> "Swap $currency to $toCurrency"
            TransactionDirection.TopUp -> "Top up from $counterpartyName"
            TransactionDirection.Withdrawal -> "Withdraw to $counterpartyName"
        }

    /** How this transaction changed the balance of [asset] (negative when money left). */
    fun netChange(asset: Asset): BigDecimal {
        var change = BigDecimal.ZERO
        if (currency == asset.code) {
            change += when (direction) {
                TransactionDirection.Incoming, TransactionDirection.TopUp -> amount
                TransactionDirection.Swap -> -amount
                TransactionDirection.Outgoing, TransactionDirection.Withdrawal -> -(amount + (fee ?: BigDecimal.ZERO))
            }
        }
        if (direction == TransactionDirection.Swap && toCurrency == asset.code) {
            change += toAmount ?: BigDecimal.ZERO
        }
        return change
    }

    /** Swaps touching LKR also appear in the fiat wallet. */
    fun belongsTo(wallet: WalletType): Boolean =
        if (direction == TransactionDirection.Swap) {
            listOfNotNull(Asset.fromCode(currency), Asset.fromCode(toCurrency)).any { it.wallet == wallet }
        } else {
            this.wallet == wallet
        }
}

object WalletFees {
    /** Most a single top-up can be worth, in LKR. */
    val maxTopUpLkr: BigDecimal = BigDecimal("500000")

    /** Flat CEFTS charge for paying LKR out to a bank account. */
    val withdrawalFee: BigDecimal = BigDecimal("25.00")

    /** Network fee for sending a token on-chain (fixed per token for the prototype). */
    fun networkFee(asset: Asset): BigDecimal? = when (asset) {
        Asset.USDT -> BigDecimal("1.00")
        Asset.BTC -> BigDecimal("0.00002")
        Asset.ETH -> BigDecimal("0.0004")
        Asset.SOL -> BigDecimal("0.000005")
        Asset.LKR -> null
    }

    /** What [amount] of [asset] costs in LKR at [SwapRates]. */
    fun lkrPrice(asset: Asset, amount: BigDecimal): BigDecimal =
        amount.multiply(SwapRates.rate(asset, Asset.LKR)).setScale(2, RoundingMode.UP)

    /** Fee [method] adds on top of an LKR top-up. */
    fun topUpFee(amount: BigDecimal, method: PaymentMethod): BigDecimal =
        amount.multiply(method.topUpFeeRate).setScale(2, RoundingMode.UP)
}

/**
 * Indicative prices in LKR used by the swap screen; a real build would stream these from
 * the exchange partner.
 */
object SwapRates {
    private val lkrPrice = mapOf(
        Asset.LKR to BigDecimal("1"),
        Asset.USDT to BigDecimal("300"),
        Asset.BTC to BigDecimal("30000000"),
        Asset.ETH to BigDecimal("1050000"),
        Asset.SOL to BigDecimal("45000"),
    )

    /** PayCeylon's swap fee, taken from the amount being swapped. */
    val feeRate: BigDecimal = BigDecimal("0.005")

    /** How many [to] one [from] buys, before fees. */
    fun rate(from: Asset, to: Asset): BigDecimal =
        lkrPrice.getValue(from).divide(lkrPrice.getValue(to), MathContext.DECIMAL64)

    fun fee(from: Asset, amount: BigDecimal): BigDecimal =
        amount.multiply(feeRate).setScale(from.decimals, RoundingMode.UP)

    /** What arrives for [amount] of [from], after the fee, cut to [to]'s precision. */
    fun quote(from: Asset, to: Asset, amount: BigDecimal): BigDecimal =
        // Multiply before dividing, as the server does, so a rounded rate never costs a unit.
        (amount - fee(from, amount)).max(BigDecimal.ZERO)
            .multiply(lkrPrice.getValue(from))
            .divide(lkrPrice.getValue(to), to.decimals, RoundingMode.DOWN)
}

/** Where the session stands; decides what PayCeylonApp shows. */
sealed interface SessionState {
    data object Checking : SessionState
    data object SignedOut : SessionState

    /** Signed in, but the account still has the server's placeholder name. */
    data object NeedsProfile : SessionState
    data object SignedIn : SessionState

    /** Signed in to Firebase but the API couldn't be reached. */
    data class Unreachable(val message: String) : SessionState
}

/** Name the server gives a new account until the user picks one. */
internal const val PlaceholderProfileName = "PayCeylon User"

/**
 * The signed-in user's wallet. The PayCeylon API is the source of truth; [cache] (Room on the
 * device) holds its last answers, so the wallet shows instantly at launch and offline.
 * [refresh] reloads from the API (the app polls while open) and saves what it got locally.
 */
class WalletViewModel(
    private val backend: Backend = AppServices.backend,
    private val cache: WalletCache = AppServices.cache,
) : ViewModel() {
    var session by mutableStateOf<SessionState>(SessionState.Checking)
        private set

    /** True while showing saved data because the server can't be reached. */
    var isOffline by mutableStateOf(false)
        private set

    private val balances = mutableStateOf(Asset.entries.associateWith { BigDecimal.ZERO })

    private val addresses = mutableStateOf<Map<CryptoToken, String>>(emptyMap())

    /** This user's deposit address for [token]; the example address until the server's arrives. */
    fun addressOf(token: CryptoToken): String = addresses.value[token] ?: token.address

    var selectedWallet by mutableStateOf(WalletType.Fiat)

    var selectedToken by mutableStateOf(CryptoToken.USDT)

    /** What Home shows and Send / Receive default to: LKR, or the selected crypto token. */
    val selectedAsset: Asset
        get() = if (selectedWallet == WalletType.Crypto) Asset.of(selectedToken) else Asset.LKR

    /**
     * The last balance the Home card finished showing, so it can count from there after a
     * payment instead of replaying its intro. Not UI state; read once per display.
     */
    var lastShownBalance: Pair<Asset, BigDecimal>? = null

    /** Newest first. */
    val transactions = mutableStateListOf<WalletTransaction>()

    /** One-off messages from background work (e.g. a setting that failed to save). */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Profile, linked accounts, settings and the notification feed. */
    val account = AccountState(backend, viewModelScope) { messages.tryEmit(it) }

    init {
        restoreSession()
    }

    /**
     * Loads the account for the Firebase user on this device, creating it on first sign-in.
     * Call after signing in, at launch, and from the retry button when the API was unreachable.
     */
    fun restoreSession() {
        if (!backend.hasSession()) {
            session = SessionState.SignedOut
            return
        }
        session = SessionState.Checking
        viewModelScope.launch {
            // 1. Show the wallet saved on this device straight away, if there is one.
            val saved = cache.load()?.takeIf { it.profile.name != PlaceholderProfileName }
            if (saved != null) {
                showSnapshot(saved)
                session = SessionState.SignedIn
            }
            // 2. Then bring it up to date from the server.
            try {
                val profile = backend.verify()
                account.load(profile)
                // Addresses never change for a user, so once per sign-in is enough.
                try {
                    addresses.value = backend.addresses()
                } catch (_: ApiException) {
                }
                refresh()
                session = if (profile.name == PlaceholderProfileName) SessionState.NeedsProfile else SessionState.SignedIn
            } catch (e: ApiException) {
                when {
                    e.status in 400..499 -> {
                        // The sign-in itself was rejected (expired, revoked or not a Sri Lankan number).
                        signOutLocally()
                        messages.tryEmit(e.message)
                    }
                    // Offline with a saved wallet: keep showing it; polling retries.
                    saved != null -> isOffline = true
                    else -> session = SessionState.Unreachable(e.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A bug or malformed response must not crash the app; offer a retry instead.
                if (saved != null) {
                    isOffline = true
                } else {
                    session = SessionState.Unreachable("Something went wrong (${e.javaClass.simpleName}). Please try again.")
                }
            }
        }
    }

    private fun showSnapshot(snapshot: WalletSnapshot) {
        account.restore(snapshot)
        addresses.value = snapshot.addresses
        balances.value = Asset.entries.associateWith { snapshot.balances[it] ?: BigDecimal.ZERO }
        transactions.clear()
        transactions.addAll(snapshot.transactions)
    }

    /** Writes what's on screen to the local database (skipped when nothing changed). */
    private suspend fun saveSnapshot() {
        val snapshot = WalletSnapshot(
            profile = account.profile,
            photo = account.photoBytes,
            balances = balances.value,
            transactions = transactions.toList(),
            notifications = account.notifications.toList(),
            paymentMethods = account.paymentMethods.toList(),
            settings = AccountSetting.entries.associateWith(account::isEnabled),
            addresses = addresses.value,
        )
        if (snapshot.copy(savedAt = 0) == lastSaved?.copy(savedAt = 0)) return
        cache.save(snapshot)
        lastSaved = snapshot
    }

    private var lastSaved: WalletSnapshot? = null

    /**
     * Saves what a new user entered on profile setup; returns the problem, or null. The photo is
     * optional and a failed upload doesn't block the account (it can be added later).
     */
    suspend fun completeProfile(name: String, handle: String, email: String = "", photo: ImageBitmap? = null): String? {
        account.setUpProfile(name, handle, email)?.let { return it }
        if (photo != null) {
            try {
                account.updatePhoto(photo)
            } catch (e: ApiException) {
                messages.tryEmit("Your photo couldn't be saved. You can add it in Settings.")
            }
        }
        session = SessionState.SignedIn
        return null
    }

    /**
     * Reloads balances, history and notifications from the server and saves them on the
     * device. Throws [ApiException] on failure.
     */
    suspend fun refresh() {
        coroutineScope {
            val latestBalances = async { backend.balances() }
            val latestTransactions = async { backend.transactions() }
            val notifications = async { account.refreshNotifications() }
            balances.value = Asset.entries.associateWith { latestBalances.await()[it] ?: BigDecimal.ZERO }
            val history = latestTransactions.await()
            if (history != transactions.toList()) {
                transactions.clear()
                transactions.addAll(history)
            }
            notifications.await()
        }
        isOffline = false
        saveSnapshot()
    }

    /** [refresh] for polling: a revoked session signs out; no connection shows the offline note. */
    suspend fun refreshQuietly() {
        try {
            refresh()
        } catch (e: ApiException) {
            when {
                e.isUnauthenticated -> signOutLocally()
                e.isNetwork || e.status >= 500 -> isOffline = true
            }
        }
    }

    /** Distinct people from recent transactions, newest first, for quick re-sends. */
    val recentRecipients: List<PayCeylonUser>
        get() = transactions
            .filter { it.direction == TransactionDirection.Outgoing || it.direction == TransactionDirection.Incoming }
            .map { PayCeylonUser(it.counterpartyName, it.counterpartyHandle) }
            .distinctBy { it.handle }

    /** PayCeylon users matching [query], excluding the signed-in user. */
    suspend fun searchUsers(query: String, limit: Int = 4): List<PayCeylonUser> {
        if (query.trim().removePrefix("@").isEmpty()) return emptyList()
        return backend.searchUsers(query.trim(), limit + 1)
            .filterNot { it.handle.equals(account.profile.handle, ignoreCase = true) }
            .take(limit)
    }

    fun balanceOf(asset: Asset): BigDecimal = balances.value[asset] ?: BigDecimal.ZERO

    /**
     * Percentage change in [asset]'s balance compared with 30 days ago, worked back from the
     * transactions since then. Null when there was no balance a month ago to compare with.
     */
    fun monthlyChangePercent(asset: Asset, now: Long = System.currentTimeMillis()): BigDecimal? {
        val since = now - 30L * 86_400_000
        val net = transactions.filter { it.timestamp >= since }
            .fold(BigDecimal.ZERO) { total, tx -> total + tx.netChange(asset) }
        val current = balanceOf(asset)
        val monthAgo = current - net
        if (monthAgo <= BigDecimal.ZERO) return null
        return (current - monthAgo).multiply(BigDecimal(100)).divide(monthAgo, 1, RoundingMode.HALF_UP)
    }

    // Actions throw ApiException with a message to show. Pass the same idempotency key when
    // retrying one payment, so a request that reached the server is never applied twice.

    suspend fun send(handle: String, amount: BigDecimal, asset: Asset, idempotencyKey: String): WalletTransaction =
        backend.send(handle.removePrefix("@"), amount, asset, idempotencyKey).also { recorded(it) }

    /** Returns what arrived. */
    suspend fun swap(from: Asset, to: Asset, amount: BigDecimal, idempotencyKey: String): BigDecimal =
        backend.swap(from, to, amount, idempotencyKey).also { recorded(it) }.toAmount ?: BigDecimal.ZERO

    suspend fun topUp(amount: BigDecimal, method: PaymentMethod, idempotencyKey: String): WalletTransaction =
        backend.topUp(amount, method.id, idempotencyKey).also { recorded(it) }

    suspend fun withdraw(amount: BigDecimal, bank: PaymentMethod, idempotencyKey: String): WalletTransaction =
        backend.withdraw(amount, bank.id, idempotencyKey).also { recorded(it) }

    private suspend fun recorded(tx: WalletTransaction) {
        if (transactions.none { it.id == tx.id }) transactions.add(0, tx)
        // The action succeeded; a failed reload just leaves the snapshot for the next poll.
        try {
            refresh()
        } catch (_: ApiException) {
        }
    }

    fun logOut() {
        viewModelScope.launch {
            try {
                backend.logout()
            } catch (_: ApiException) {
                // Still sign out on this device.
            }
            signOutLocally()
        }
    }

    private fun signOutLocally() {
        backend.clearSession()
        balances.value = Asset.entries.associateWith { BigDecimal.ZERO }
        addresses.value = emptyMap()
        transactions.clear()
        account.clear()
        selectedWallet = WalletType.Fiat
        lastShownBalance = null
        isOffline = false
        lastSaved = null
        session = SessionState.SignedOut
        // Nothing of this account stays on the device after signing out.
        viewModelScope.launch { cache.clear() }
    }
}

private val moneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

/** Splits an amount into its grouped whole part ("32,483") and decimal part (".75"). */
internal fun BigDecimal.toMoneyParts(decimals: Int = 2): Pair<String, String> {
    if (decimals == 0) {
        return DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(setScale(0, RoundingMode.DOWN)) to ""
    }
    val format = if (decimals == 2) {
        moneyFormat
    } else {
        DecimalFormat("#,##0." + "0".repeat(decimals), DecimalFormatSymbols(Locale.US))
    }
    val formatted = format.format(setScale(decimals, RoundingMode.DOWN))
    val dot = formatted.lastIndexOf('.')
    return formatted.substring(0, dot) to formatted.substring(dot)
}

internal fun BigDecimal.toMoneyString(): String = moneyFormat.format(setScale(2, RoundingMode.DOWN))

/**
 * Formats an amount for its currency: fiat and USDT always show 2 decimals; other tokens
 * show up to their own precision with trailing zeros trimmed (0.0125 BTC, not 0.01).
 */
internal fun BigDecimal.toAmountParts(currency: String): Pair<String, String> {
    val asset = Asset.fromCode(currency)
    if (asset == null || asset.decimals <= 2) return toMoneyParts()
    val (whole, fraction) = toMoneyParts(asset.decimals)
    return whole to fraction.trimEnd('0').padEnd(3, '0')
}

internal fun BigDecimal.toAmountString(asset: Asset): String =
    toAmountParts(asset.code).let { (whole, fraction) -> whole + fraction }
