package com.payceylon.app.data

import com.google.firebase.auth.FirebaseAuth
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
import com.payceylon.app.ui.wallet.TransactionDirection
import com.payceylon.app.ui.wallet.WalletTransaction
import com.payceylon.app.ui.wallet.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val JsonMedia = "application/json".toMediaType()

class HttpBackend(baseUrl: String) : Backend {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun firebase(): FirebaseAuth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        ?: throw ApiException(0, "NOT_CONFIGURED", "Firebase isn't set up in this build. Add it to local.properties.")

    override fun hasSession(): Boolean = runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)

    override fun clearSession() {
        runCatching { FirebaseAuth.getInstance().signOut() }
    }

    private suspend fun idToken(forceRefresh: Boolean): String {
        val user = firebase().currentUser ?: throw ApiException(401, "UNAUTHENTICATED", "Please sign in again.")
        return user.getIdToken(forceRefresh).await().token
            ?: throw ApiException(401, "UNAUTHENTICATED", "Please sign in again.")
    }

    // --- Auth & profile -------------------------------------------------------------------

    override suspend fun verify(): UserProfile =
        post<VerifyResponse>("v1/auth/verify", jsonBody(IdTokenRequest(idToken(forceRefresh = false))), authenticated = false).user.toProfile()

    override suspend fun me(): UserProfile = get<ProfileDto>("v1/me").toProfile()

    override suspend fun updateProfile(changes: Map<ProfileField, String?>): UserProfile {
        val body = buildJsonObject {
            changes.forEach { (field, value) ->
                val key = when (field) {
                    ProfileField.Name -> "name"
                    ProfileField.Username -> "handle"
                    ProfileField.Email -> "email"
                    ProfileField.Phone -> error("Phone changes go through updatePhone()")
                }
                put(key, value?.let(::JsonPrimitive) ?: JsonNull)
            }
        }
        return send<ProfileDto>("PATCH", "v1/me", body.toString().toRequestBody(JsonMedia)).toProfile()
    }

    override suspend fun updatePhone(): UserProfile =
        // A fresh token carries the newly linked phone number.
        send<ProfileDto>("PATCH", "v1/me/phone", jsonBody(IdTokenRequest(idToken(forceRefresh = true)))).toProfile()

    override suspend fun uploadPhoto(jpeg: ByteArray): UserProfile {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("photo", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        return send<ProfileDto>("PUT", "v1/me/photo", body).toProfile()
    }

    override suspend fun removePhoto(): UserProfile = send<ProfileDto>("DELETE", "v1/me/photo").toProfile()

    override suspend fun downloadPhoto(url: String): ByteArray = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url).build()).use { it.body?.bytes() ?: ByteArray(0) }
    }

    override suspend fun isHandleAvailable(handle: String): Boolean =
        get<AvailabilityResponse>("v1/users/check-handle", "handle" to handle).available

    override suspend fun settings(): Map<AccountSetting, Boolean> = get<SettingsDto>("v1/me/settings").toMap()

    override suspend fun updateSetting(setting: AccountSetting, enabled: Boolean): Map<AccountSetting, Boolean> {
        val body = buildJsonObject { put(setting.apiKey, JsonPrimitive(enabled)) }
        return send<SettingsDto>("PATCH", "v1/me/settings", body.toString().toRequestBody(JsonMedia)).toMap()
    }

    // --- Wallet ---------------------------------------------------------------------------

    override suspend fun balances(): Map<Asset, BigDecimal> =
        get<BalancesResponse>("v1/wallet/balances").balances
            .mapNotNull { (code, value) -> Asset.fromCode(code)?.let { it to BigDecimal(value) } }
            .toMap()

    override suspend fun addresses(): Map<CryptoToken, String> =
        get<Map<String, String>>("v1/wallet/addresses")
            .mapNotNull { (symbol, address) -> CryptoToken.entries.firstOrNull { it.symbol == symbol }?.let { it to address } }
            .toMap()

    override suspend fun transactions(limit: Int): List<WalletTransaction> =
        get<TransactionsPage>("v1/transactions", "limit" to limit.toString()).items.map { it.toTransaction() }

    override suspend fun searchUsers(query: String, limit: Int): List<PayCeylonUser> =
        get<UsersResponse>("v1/users/search", "q" to query, "limit" to limit.toString()).items
            .map { PayCeylonUser(it.name, it.handle) }

    override suspend fun send(toHandle: String, amount: BigDecimal, asset: Asset, idempotencyKey: String): WalletTransaction =
        post<TransactionDto>(
            "v1/payments",
            jsonBody(PaymentBody(toHandle, amount.toPlainString(), asset.code)),
            idempotencyKey = idempotencyKey,
        ).toTransaction()

    override suspend fun swap(from: Asset, to: Asset, amount: BigDecimal, idempotencyKey: String): WalletTransaction {
        val quote = post<SwapQuoteDto>("v1/swaps/quote", jsonBody(SwapQuoteBody(from.code, to.code, amount.toPlainString())))
        return post<TransactionDto>("v1/swaps", jsonBody(SwapBody(quote.quoteId)), idempotencyKey = idempotencyKey).toTransaction()
    }

    override suspend fun topUp(amount: BigDecimal, methodId: String, idempotencyKey: String): WalletTransaction =
        post<TransactionDto>("v1/topups", jsonBody(FundsBody(amount.toPlainString(), methodId)), idempotencyKey = idempotencyKey)
            .toTransaction()

    override suspend fun withdraw(amount: BigDecimal, bankId: String, idempotencyKey: String): WalletTransaction =
        post<TransactionDto>("v1/withdrawals", jsonBody(FundsBody(amount.toPlainString(), bankId)), idempotencyKey = idempotencyKey)
            .toTransaction()

    // --- Payment methods ------------------------------------------------------------------

    override suspend fun paymentMethods(): List<PaymentMethod> =
        get<PaymentMethodsResponse>("v1/payment-methods").items.map { it.toPaymentMethod() }

    override suspend fun addCard(token: String, holder: String, brand: String, last4: String, expiry: String): PaymentMethod =
        post<PaymentMethodDto>("v1/payment-methods/cards", jsonBody(CardBody(token, holder, brand, last4, expiry))).toPaymentMethod()

    override suspend fun addBank(bank: String, accountNumber: String, holder: String, savings: Boolean): PaymentMethod =
        post<PaymentMethodDto>(
            "v1/payment-methods/banks",
            jsonBody(BankBody(bank, accountNumber, holder, if (savings) "SAVINGS" else "CURRENT")),
        ).toPaymentMethod()

    override suspend fun removePaymentMethod(id: String) {
        send<Unit>("DELETE", "v1/payment-methods/$id")
    }

    // --- Notifications & session ----------------------------------------------------------

    override suspend fun notifications(): List<AppNotification> =
        get<NotificationsPage>("v1/notifications", "limit" to "100").items.map { it.toNotification() }

    override suspend fun markNotificationsRead(ids: List<String>, all: Boolean) {
        send<Unit>("POST", "v1/notifications/read", jsonBody(NotificationReadBody(ids, all)))
    }

    override suspend fun logout() {
        send<Unit>("DELETE", "v1/auth/logout")
    }

    // --- Plumbing -------------------------------------------------------------------------

    private suspend inline fun <reified T> get(path: String, vararg query: Pair<String, String>): T =
        send("GET", path, query = query.toList())

    private suspend inline fun <reified T> post(
        path: String,
        body: RequestBody,
        idempotencyKey: String? = null,
        authenticated: Boolean = true,
    ): T = send("POST", path, body, idempotencyKey = idempotencyKey, authenticated = authenticated)

    private suspend inline fun <reified T> send(
        method: String,
        path: String,
        body: RequestBody? = null,
        query: List<Pair<String, String>> = emptyList(),
        idempotencyKey: String? = null,
        authenticated: Boolean = true,
    ): T {
        val text = request(method, path, body, query, idempotencyKey, authenticated)
        return if (T::class == Unit::class) Unit as T else ApiJson.decodeFromString<T>(text)
    }

    /** Performs the call and returns the response body, throwing [ApiException] on failure. */
    private suspend fun request(
        method: String,
        path: String,
        body: RequestBody?,
        query: List<Pair<String, String>>,
        idempotencyKey: String?,
        authenticated: Boolean,
    ): String = withContext(Dispatchers.IO) {
        // Reading a response body is blocking I/O, which Android forbids on the main thread.
        val url = base.newBuilder().addPathSegments(path).apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        var response = execute(buildRequest(url, method, body, idempotencyKey, authenticated, forceRefresh = false))
        if (response.code == 401 && authenticated) {
            // The cached ID token may have just expired; retry once with a fresh one.
            response.close()
            response = execute(buildRequest(url, method, body, idempotencyKey, authenticated, forceRefresh = true))
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw it.toApiException(text)
            text
        }
    }

    private suspend fun buildRequest(
        url: HttpUrl,
        method: String,
        body: RequestBody?,
        idempotencyKey: String?,
        authenticated: Boolean,
        forceRefresh: Boolean,
    ): Request = Request.Builder().url(url).method(method, body).apply {
        if (authenticated) header("Authorization", "Bearer ${idToken(forceRefresh)}")
        idempotencyKey?.let { header("Idempotency-Key", it) }
    }.build()

    private inline fun <reified T> jsonBody(value: T): RequestBody =
        ApiJson.encodeToString(value).toRequestBody(JsonMedia)

    private suspend fun execute(request: Request): Response = try {
        client.newCall(request).await()
    } catch (e: IOException) {
        throw ApiException(0, ApiException.NetworkErrorCode, "Can't reach PayCeylon. Check your connection and try again.")
    }
}

private fun Response.toApiException(text: String): ApiException {
    val error = runCatching { ApiJson.decodeFromString<ErrorEnvelope>(text).error }.getOrNull()
    return ApiException(code, error?.code ?: "HTTP_$code", error?.message ?: "Something went wrong. Please try again.", error?.field)
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    })
}

// --- Mapping to the app's models ----------------------------------------------------------

private val AccountSetting.apiKey: String
    get() = when (this) {
        AccountSetting.BiometricApproval -> "biometricApproval"
        AccountSetting.HideBalance -> "hideBalance"
        AccountSetting.TransactionAlerts -> "transactionAlerts"
        AccountSetting.Promotions -> "promotions"
    }

private fun SettingsDto.toMap(): Map<AccountSetting, Boolean> = mapOf(
    AccountSetting.BiometricApproval to biometricApproval,
    AccountSetting.HideBalance to hideBalance,
    AccountSetting.TransactionAlerts to transactionAlerts,
    AccountSetting.Promotions to promotions,
)

internal fun ProfileDto.toProfile() = UserProfile(
    name = name,
    handle = handle,
    // The app keeps the nine digits after +94.
    phone = phone.removePrefix("+94"),
    email = email.orEmpty(),
    photoUrl = photoUrl,
)

internal fun TransactionDto.toTransaction() = WalletTransaction(
    id = id,
    counterpartyName = counterparty.name,
    counterpartyHandle = counterparty.handle,
    direction = when (direction) {
        "INCOMING" -> TransactionDirection.Incoming
        "SWAP" -> TransactionDirection.Swap
        "TOP_UP" -> TransactionDirection.TopUp
        "WITHDRAWAL" -> TransactionDirection.Withdrawal
        else -> TransactionDirection.Outgoing
    },
    currency = currency,
    amount = BigDecimal(amount),
    wallet = if (wallet == "CRYPTO") WalletType.Crypto else WalletType.Fiat,
    timestamp = timestamp,
    toCurrency = toCurrency,
    toAmount = toAmount?.let(::BigDecimal),
    fee = fee?.let(::BigDecimal),
    feeCurrency = feeCurrency,
    reference = reference,
    status = status,
    txHash = txHash,
)

internal fun PaymentMethodDto.toPaymentMethod() = PaymentMethod(
    id = id,
    type = if (type == "BANK") PaymentMethodType.Bank else PaymentMethodType.Card,
    name = name,
    last4 = last4,
    holder = holder,
    detail = detail,
    brand = brand?.let { code -> CardBrand.entries.firstOrNull { it.label.equals(code, true) || it.name.equals(code, true) } },
)

internal fun NotificationDto.toNotification() = AppNotification(
    id = id,
    kind = when (kind) {
        "TRANSACTION" -> NotificationKind.Transaction
        "SECURITY" -> NotificationKind.Security
        else -> NotificationKind.Account
    },
    title = title,
    body = body,
    timestamp = timestamp,
    transactionId = transactionId,
    read = read,
)
