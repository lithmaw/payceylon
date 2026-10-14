package com.payceylon.app.data

import android.content.Context
import com.payceylon.app.data.local.PayCeylonDatabase
import com.payceylon.app.data.local.RoomWalletCache
import com.payceylon.app.data.local.WalletCache
import com.payceylon.app.ui.account.AccountSetting
import com.payceylon.app.ui.account.AppNotification
import com.payceylon.app.ui.account.PaymentMethod
import com.payceylon.app.ui.account.ProfileField
import com.payceylon.app.ui.account.UserProfile
import com.payceylon.app.ui.wallet.Asset
import com.payceylon.app.ui.wallet.CryptoToken
import com.payceylon.app.ui.wallet.PayCeylonUser
import com.payceylon.app.ui.wallet.WalletTransaction
import java.math.BigDecimal

/** A failed API call, carrying the server's user-facing [message]. */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val field: String? = null,
) : Exception(message) {
    val isUnauthenticated: Boolean get() = status == 401
    val isNetwork: Boolean get() = code == NetworkErrorCode

    companion object {
        const val NetworkErrorCode = "NETWORK"
    }
}

/** What the screens need from the server. [HttpBackend] talks to the real API. */
interface Backend {
    /** True when a Firebase user is signed in on this device. */
    fun hasSession(): Boolean

    /** Signs out of Firebase locally. */
    fun clearSession()

    /** Creates or loads the PayCeylon account for the signed-in phone number. */
    suspend fun verify(): UserProfile
    suspend fun me(): UserProfile
    suspend fun updateProfile(changes: Map<ProfileField, String?>): UserProfile

    /** Saves the phone number just verified on the Firebase user. */
    suspend fun updatePhone(): UserProfile
    suspend fun uploadPhoto(jpeg: ByteArray): UserProfile
    suspend fun removePhoto(): UserProfile
    suspend fun downloadPhoto(url: String): ByteArray
    suspend fun isHandleAvailable(handle: String): Boolean

    suspend fun settings(): Map<AccountSetting, Boolean>
    suspend fun updateSetting(setting: AccountSetting, enabled: Boolean): Map<AccountSetting, Boolean>

    suspend fun balances(): Map<Asset, BigDecimal>

    /** This user's deposit address for each token (demo addresses in each network's format). */
    suspend fun addresses(): Map<CryptoToken, String>
    suspend fun transactions(limit: Int = 100): List<WalletTransaction>
    suspend fun searchUsers(query: String, limit: Int): List<PayCeylonUser>

    suspend fun send(toHandle: String, amount: BigDecimal, asset: Asset, idempotencyKey: String): WalletTransaction

    /** Locks a quote and swaps at it straight away. */
    suspend fun swap(from: Asset, to: Asset, amount: BigDecimal, idempotencyKey: String): WalletTransaction
    suspend fun topUp(amount: BigDecimal, methodId: String, idempotencyKey: String): WalletTransaction
    suspend fun withdraw(amount: BigDecimal, bankId: String, idempotencyKey: String): WalletTransaction

    suspend fun paymentMethods(): List<PaymentMethod>
    suspend fun addCard(token: String, holder: String, brand: String, last4: String, expiry: String): PaymentMethod
    suspend fun addBank(bank: String, accountNumber: String, holder: String, savings: Boolean): PaymentMethod
    suspend fun removePaymentMethod(id: String)

    suspend fun notifications(): List<AppNotification>
    suspend fun markNotificationsRead(ids: List<String>, all: Boolean = false)

    /** Revokes this user's sessions on the server. */
    suspend fun logout()
}

object AppServices {
    lateinit var backend: Backend
        private set

    /** The on-device Room copy of the wallet. */
    lateinit var cache: WalletCache
        private set

    fun init(context: Context, baseUrl: String) {
        if (!::backend.isInitialized) backend = HttpBackend(baseUrl)
        if (!::cache.isInitialized) cache = RoomWalletCache(PayCeylonDatabase.create(context).walletDao())
    }
}
