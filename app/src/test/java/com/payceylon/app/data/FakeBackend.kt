package com.payceylon.app.data

import com.payceylon.app.ui.account.AccountSetting
import com.payceylon.app.ui.account.AppNotification
import com.payceylon.app.ui.account.CardBrand
import com.payceylon.app.ui.account.NotificationKind
import com.payceylon.app.ui.account.PaymentMethod
import com.payceylon.app.ui.account.PaymentMethodType
import com.payceylon.app.ui.account.ProfileField
import com.payceylon.app.ui.account.UserProfile
import com.payceylon.app.ui.wallet.Asset
import com.payceylon.app.ui.wallet.CryptoToken
import com.payceylon.app.ui.wallet.PayCeylonUser
import com.payceylon.app.ui.wallet.SwapRates
import com.payceylon.app.ui.wallet.TransactionDirection
import com.payceylon.app.ui.wallet.WalletFees
import com.payceylon.app.ui.wallet.WalletTransaction
import com.payceylon.app.ui.wallet.WalletType
import java.math.BigDecimal

/** In-memory stand-in for the API with the same rules for balances, fees and idempotency. */
class FakeBackend(
    var signedIn: Boolean = true,
    var profile: UserProfile = UserProfile(name = "Kasun Perera", handle = "kasun.p", phone = "771234567", email = ""),
) : Backend {
    val balances = Asset.entries.associateWith { BigDecimal.ZERO }.toMutableMap().apply {
        put(Asset.LKR, BigDecimal("1000.00"))
        put(Asset.USDT, BigDecimal("50.00"))
    }
    val history = mutableListOf<WalletTransaction>()
    val methods = mutableListOf<PaymentMethod>()
    val settings = AccountSetting.entries.associateWith { it.default }.toMutableMap()
    val feed = mutableListOf<AppNotification>()
    val directory = mutableListOf(
        PayCeylonUser("Kasun Perera", "kasun.p"),
        PayCeylonUser("Nimal Silva", "nimal"),
        PayCeylonUser("Nimesha Rathnayake", "nimesha99"),
    )

    /** The next call throws this. */
    var failNext: ApiException? = null
    val cardsSent = mutableListOf<List<String>>()
    var loggedOut = false
    private val responses = mutableMapOf<String, WalletTransaction>()
    private var nextId = 1

    private fun call() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override fun hasSession() = signedIn

    override fun clearSession() {
        signedIn = false
    }

    override suspend fun verify(): UserProfile = also { call() }.profile
    override suspend fun me(): UserProfile = also { call() }.profile

    override suspend fun updateProfile(changes: Map<ProfileField, String?>): UserProfile {
        call()
        changes[ProfileField.Username]?.let { handle ->
            if (directory.any { it.handle == handle && handle != profile.handle }) {
                throw ApiException(409, "USERNAME_TAKEN", "That username is already in use.", "handle")
            }
        }
        profile = profile.copy(
            name = changes[ProfileField.Name] ?: profile.name,
            handle = changes[ProfileField.Username] ?: profile.handle,
            email = if (ProfileField.Email in changes) changes[ProfileField.Email].orEmpty() else profile.email,
        )
        notify(NotificationKind.Account, "Profile updated")
        return profile
    }

    override suspend fun updatePhone(): UserProfile = also { call() }.profile
    override suspend fun uploadPhoto(jpeg: ByteArray): UserProfile = also { call() }.profile
    override suspend fun removePhoto(): UserProfile = also { call() }.profile
    override suspend fun downloadPhoto(url: String): ByteArray = ByteArray(0)
    override suspend fun isHandleAvailable(handle: String): Boolean = directory.none { it.handle == handle }

    override suspend fun settings(): Map<AccountSetting, Boolean> = also { call() }.settings.toMap()

    override suspend fun updateSetting(setting: AccountSetting, enabled: Boolean): Map<AccountSetting, Boolean> {
        call()
        settings[setting] = enabled
        return settings.toMap()
    }

    override suspend fun balances(): Map<Asset, BigDecimal> = also { call() }.balances.toMap()

    override suspend fun addresses(): Map<CryptoToken, String> =
        also { call() }.let { CryptoToken.entries.associateWith { "${it.symbol}-ADDRESS-${profile.handle}" } }
    override suspend fun transactions(limit: Int): List<WalletTransaction> = also { call() }.history.take(limit)

    override suspend fun searchUsers(query: String, limit: Int): List<PayCeylonUser> {
        call()
        val q = query.removePrefix("@").lowercase()
        return directory.filter { q in it.handle || q in it.name.lowercase() }.take(limit)
    }

    override suspend fun send(toHandle: String, amount: BigDecimal, asset: Asset, idempotencyKey: String): WalletTransaction {
        call()
        responses[idempotencyKey]?.let { return it }
        val recipient = directory.firstOrNull { it.handle == toHandle }
            ?: throw ApiException(404, "USER_NOT_FOUND", "That PayCeylon user could not be found.")
        val fee = WalletFees.networkFee(asset)
        debit(asset, amount + (fee ?: BigDecimal.ZERO))
        return record(idempotencyKey, transaction(TransactionDirection.Outgoing, asset, amount, recipient.name, recipient.handle, fee))
    }

    override suspend fun swap(from: Asset, to: Asset, amount: BigDecimal, idempotencyKey: String): WalletTransaction {
        call()
        responses[idempotencyKey]?.let { return it }
        debit(from, amount)
        val received = SwapRates.quote(from, to, amount)
        balances[to] = balances.getValue(to) + received
        return record(
            idempotencyKey,
            transaction(TransactionDirection.Swap, from, amount, "PayCeylon Swap", "payceylon", SwapRates.fee(from, amount))
                .copy(toCurrency = to.code, toAmount = received),
        )
    }

    override suspend fun topUp(amount: BigDecimal, methodId: String, idempotencyKey: String): WalletTransaction {
        call()
        responses[idempotencyKey]?.let { return it }
        val method = methods.first { it.id == methodId }
        balances[Asset.LKR] = balances.getValue(Asset.LKR) + amount
        return record(idempotencyKey, transaction(TransactionDirection.TopUp, Asset.LKR, amount, method.name, method.masked, WalletFees.topUpFee(amount, method)))
    }

    override suspend fun withdraw(amount: BigDecimal, bankId: String, idempotencyKey: String): WalletTransaction {
        call()
        responses[idempotencyKey]?.let { return it }
        val bank = methods.first { it.id == bankId && it.type == PaymentMethodType.Bank }
        debit(Asset.LKR, amount + WalletFees.withdrawalFee)
        return record(idempotencyKey, transaction(TransactionDirection.Withdrawal, Asset.LKR, amount, bank.name, bank.masked, WalletFees.withdrawalFee))
    }

    override suspend fun paymentMethods(): List<PaymentMethod> = also { call() }.methods.toList()

    override suspend fun addCard(token: String, holder: String, brand: String, last4: String, expiry: String): PaymentMethod {
        call()
        cardsSent += listOf(token, holder, brand, last4, expiry)
        val card = PaymentMethod(
            "pm${nextId++}", PaymentMethodType.Card, brand, last4, holder, "Expires $expiry",
            CardBrand.entries.firstOrNull { it.label == brand },
        )
        methods += card
        notify(NotificationKind.Security, "Card added")
        return card
    }

    override suspend fun addBank(bank: String, accountNumber: String, holder: String, savings: Boolean): PaymentMethod {
        call()
        val method = PaymentMethod(
            "pm${nextId++}", PaymentMethodType.Bank, bank, accountNumber.takeLast(4), holder,
            if (savings) "Savings account" else "Current account",
        )
        methods += method
        return method
    }

    override suspend fun removePaymentMethod(id: String) {
        call()
        methods.removeAll { it.id == id }
    }

    override suspend fun notifications(): List<AppNotification> = also { call() }.feed.toList()

    override suspend fun markNotificationsRead(ids: List<String>, all: Boolean) {
        call()
        feed.replaceAll { if (all || it.id in ids) it.copy(read = true) else it }
    }

    override suspend fun logout() {
        call()
        loggedOut = true
    }

    /** Money arriving from another user, as the other phone's payment would appear. */
    fun receive(fromName: String, fromHandle: String, amount: BigDecimal, asset: Asset = Asset.LKR) {
        balances[asset] = balances.getValue(asset) + amount
        history.add(0, transaction(TransactionDirection.Incoming, asset, amount, fromName, fromHandle, null))
        notify(NotificationKind.Transaction, "Payment received")
    }

    private fun debit(asset: Asset, total: BigDecimal) {
        val balance = balances.getValue(asset)
        if (total > balance) throw ApiException(402, "INSUFFICIENT_FUNDS", "Insufficient ${asset.code} balance.")
        balances[asset] = balance - total
    }

    private fun transaction(
        direction: TransactionDirection,
        asset: Asset,
        amount: BigDecimal,
        name: String,
        handle: String,
        fee: BigDecimal?,
    ) = WalletTransaction(
        id = "tx${nextId++}",
        counterpartyName = name,
        counterpartyHandle = handle,
        direction = direction,
        currency = asset.code,
        amount = amount,
        wallet = if (asset == Asset.LKR) WalletType.Fiat else WalletType.Crypto,
        timestamp = System.currentTimeMillis(),
        fee = fee?.takeIf { it.signum() > 0 },
        feeCurrency = asset.code.takeIf { fee != null && fee.signum() > 0 },
        reference = "PC-TEST${nextId}",
    )

    private fun record(key: String, tx: WalletTransaction): WalletTransaction {
        responses[key] = tx
        history.add(0, tx)
        notify(NotificationKind.Transaction, "Transaction")
        return tx
    }

    private fun notify(kind: NotificationKind, title: String) {
        feed.add(0, AppNotification("n${nextId++}", kind, title, title, System.currentTimeMillis()))
    }
}
