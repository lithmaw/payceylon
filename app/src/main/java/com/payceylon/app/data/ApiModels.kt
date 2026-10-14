package com.payceylon.app.data

import kotlinx.serialization.Serializable

// Wire types for the PayCeylon API (docs/BACKEND_API.md). Money is always a decimal string.

@Serializable
internal data class ProfileDto(
    val name: String,
    val handle: String,
    val phone: String,
    val email: String? = null,
    val photoUrl: String? = null,
)

@Serializable
internal data class VerifyResponse(val user: ProfileDto)

@Serializable
internal data class IdTokenRequest(val idToken: String)

@Serializable
internal data class AvailabilityResponse(val available: Boolean)

@Serializable
internal data class SettingsDto(
    val biometricApproval: Boolean,
    val hideBalance: Boolean,
    val transactionAlerts: Boolean,
    val promotions: Boolean,
)

@Serializable
internal data class BalancesResponse(val balances: Map<String, String>)

@Serializable
internal data class CounterpartyDto(val name: String, val handle: String)

@Serializable
internal data class TransactionDto(
    val id: String,
    val direction: String,
    val currency: String,
    val amount: String,
    val fee: String? = null,
    val feeCurrency: String? = null,
    val wallet: String,
    val timestamp: Long,
    val status: String,
    val reference: String,
    val counterparty: CounterpartyDto,
    val toCurrency: String? = null,
    val toAmount: String? = null,
    val txHash: String? = null,
)

@Serializable
internal data class TransactionsPage(val items: List<TransactionDto>, val nextCursor: String? = null)

@Serializable
internal data class UserDto(val name: String, val handle: String, val photoUrl: String? = null)

@Serializable
internal data class UsersResponse(val items: List<UserDto>)

@Serializable
internal data class PaymentBody(val to: String, val amount: String, val currency: String)

@Serializable
internal data class SwapQuoteBody(val from: String, val to: String, val amount: String)

@Serializable
internal data class SwapQuoteDto(val quoteId: String, val receive: String)

@Serializable
internal data class SwapBody(val quoteId: String)

@Serializable
internal data class FundsBody(val amount: String, val paymentMethodId: String)

@Serializable
internal data class PaymentMethodDto(
    val id: String,
    val type: String,
    val name: String,
    val brand: String? = null,
    val last4: String,
    val holder: String,
    val detail: String,
)

@Serializable
internal data class PaymentMethodsResponse(val items: List<PaymentMethodDto>)

@Serializable
internal data class CardBody(
    val token: String,
    val holder: String,
    val brand: String,
    val last4: String,
    val expiry: String,
)

@Serializable
internal data class BankBody(
    val bank: String,
    val accountNumber: String,
    val holder: String,
    val accountType: String,
)

@Serializable
internal data class NotificationDto(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val transactionId: String? = null,
    val read: Boolean,
)

@Serializable
internal data class NotificationsPage(
    val items: List<NotificationDto>,
    val nextCursor: String? = null,
    val unreadCount: Int = 0,
)

@Serializable
internal data class NotificationReadBody(val ids: List<String> = emptyList(), val all: Boolean = false)

@Serializable
internal data class ErrorDto(val code: String, val message: String, val field: String? = null)

@Serializable
internal data class ErrorEnvelope(val error: ErrorDto)
