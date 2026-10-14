package com.payceylon.app.data.local

import com.payceylon.app.ui.account.AccountSetting
import com.payceylon.app.ui.account.AppNotification
import com.payceylon.app.ui.account.CardBrand
import com.payceylon.app.ui.account.NotificationKind
import com.payceylon.app.ui.account.PaymentMethod
import com.payceylon.app.ui.account.PaymentMethodType
import com.payceylon.app.ui.account.UserProfile
import com.payceylon.app.ui.wallet.Asset
import com.payceylon.app.ui.wallet.CryptoToken
import com.payceylon.app.ui.wallet.TransactionDirection
import com.payceylon.app.ui.wallet.WalletTransaction
import com.payceylon.app.ui.wallet.WalletType
import java.math.BigDecimal

/** Everything the app shows for the signed-in user, as last loaded from the server. */
data class WalletSnapshot(
    val profile: UserProfile,
    /** JPEG/PNG bytes of the profile photo, if any. */
    val photo: ByteArray?,
    val balances: Map<Asset, BigDecimal>,
    val transactions: List<WalletTransaction>,
    val notifications: List<AppNotification>,
    val paymentMethods: List<PaymentMethod>,
    val settings: Map<AccountSetting, Boolean>,
    /** Per-user deposit addresses; empty until first loaded. */
    val addresses: Map<CryptoToken, String> = emptyMap(),
    val savedAt: Long = System.currentTimeMillis(),
)

/** The wallet's on-device store. [RoomWalletCache] is the real one. */
interface WalletCache {
    suspend fun load(): WalletSnapshot?
    suspend fun save(snapshot: WalletSnapshot)
    suspend fun clear()
}

class RoomWalletCache(private val dao: WalletDao) : WalletCache {
    override suspend fun load(): WalletSnapshot? {
        val profile = dao.profile() ?: return null
        return WalletSnapshot(
            profile = UserProfile(profile.name, profile.handle, profile.phone, profile.email, profile.photoUrl),
            photo = profile.photo,
            balances = dao.balances().mapNotNull { row -> Asset.fromCode(row.asset)?.let { it to BigDecimal(row.amount) } }.toMap(),
            transactions = dao.transactions().map { it.toModel() },
            notifications = dao.notifications().map { it.toModel() },
            paymentMethods = dao.paymentMethods().map { it.toModel() },
            settings = dao.settings().mapNotNull { row ->
                AccountSetting.entries.firstOrNull { it.name == row.key }?.let { it to row.enabled }
            }.toMap(),
            addresses = dao.addresses().mapNotNull { row ->
                CryptoToken.entries.firstOrNull { it.symbol == row.token }?.let { it to row.address }
            }.toMap(),
            savedAt = profile.savedAt,
        )
    }

    override suspend fun save(snapshot: WalletSnapshot) {
        val profile = snapshot.profile
        dao.replaceAll(
            profile = ProfileEntity(
                name = profile.name,
                handle = profile.handle,
                phone = profile.phone,
                email = profile.email,
                photoUrl = profile.photoUrl,
                photo = snapshot.photo,
                savedAt = snapshot.savedAt,
            ),
            balances = snapshot.balances.map { (asset, amount) -> BalanceEntity(asset.code, amount.toPlainString()) },
            transactions = snapshot.transactions.map { it.toEntity() },
            notifications = snapshot.notifications.map { it.toEntity() },
            methods = snapshot.paymentMethods.mapIndexed { index, method -> method.toEntity(index) },
            settings = snapshot.settings.map { (setting, enabled) -> SettingEntity(setting.name, enabled) },
            addresses = snapshot.addresses.map { (token, address) -> AddressEntity(token.symbol, address) },
        )
    }

    override suspend fun clear() = dao.clearAll()
}

private fun WalletTransaction.toEntity() = TransactionEntity(
    id = id,
    counterpartyName = counterpartyName,
    counterpartyHandle = counterpartyHandle,
    direction = direction.name,
    currency = currency,
    amount = amount.toPlainString(),
    wallet = wallet.name,
    timestamp = timestamp,
    toCurrency = toCurrency,
    toAmount = toAmount?.toPlainString(),
    fee = fee?.toPlainString(),
    feeCurrency = feeCurrency,
    reference = reference,
    status = status,
    txHash = txHash,
)

private fun TransactionEntity.toModel() = WalletTransaction(
    id = id,
    counterpartyName = counterpartyName,
    counterpartyHandle = counterpartyHandle,
    direction = TransactionDirection.valueOf(direction),
    currency = currency,
    amount = BigDecimal(amount),
    wallet = WalletType.valueOf(wallet),
    timestamp = timestamp,
    toCurrency = toCurrency,
    toAmount = toAmount?.let(::BigDecimal),
    fee = fee?.let(::BigDecimal),
    feeCurrency = feeCurrency,
    reference = reference,
    status = status,
    txHash = txHash,
)

private fun AppNotification.toEntity() = NotificationEntity(id, kind.name, title, body, timestamp, transactionId, read)

private fun NotificationEntity.toModel() =
    AppNotification(id, NotificationKind.valueOf(kind), title, body, timestamp, transactionId, read)

private fun PaymentMethod.toEntity(position: Int) =
    PaymentMethodEntity(id, type.name, name, last4, holder, detail, brand?.name, position)

private fun PaymentMethodEntity.toModel() = PaymentMethod(
    id = id,
    type = PaymentMethodType.valueOf(type),
    name = name,
    last4 = last4,
    holder = holder,
    detail = detail,
    brand = brand?.let { CardBrand.valueOf(it) },
)
