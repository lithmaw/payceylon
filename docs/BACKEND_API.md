# PayCeylon backend API (v1 contract)

This is the contract the Android app is built against. Every screen already works with
in-memory data; each endpoint below lists the app function it replaces, so the mobile side
can swap the fake for a network call one feature at a time.

- Base URL: `https://api.payceylon.lk/v1` (configurable per build)
- JSON over HTTPS, UTF-8. Field names are `camelCase`.
- **Money is always a decimal string**, never a float: `"32483.75"`, `"0.042150"`.
  The app parses with `BigDecimal`. Decimal places per asset are listed below.
- Timestamps are epoch **milliseconds** (`1759460000000`), UTC.
- Auth: `Authorization: Bearer <accessToken>` on everything except `/auth/*`.
- Write endpoints take an `Idempotency-Key` header (UUID). Retrying with the same key must
  return the original result, never move money twice.

## Errors

Non-2xx responses use one shape. The app shows `message` to the user, so write it as a sentence.

```json
{ "error": { "code": "INSUFFICIENT_FUNDS", "message": "Insufficient USDT balance." } }
```

| HTTP | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | A field fails the rules below (`field` is included) |
| 401 | `UNAUTHENTICATED` | Missing / expired token |
| 401 | `INVALID_OTP` | Wrong or expired SMS code |
| 402 | `INSUFFICIENT_FUNDS` | Amount plus fee exceeds the balance |
| 403 | `LIMIT_EXCEEDED` | Over a per-transaction limit |
| 404 | `USER_NOT_FOUND` | Unknown `@handle` |
| 409 | `USERNAME_TAKEN` | Username already in use |
| 409 | `QUOTE_EXPIRED` | Swap quote no longer valid |
| 429 | `RATE_LIMITED` | Too many OTP requests, etc. |

## Shared types

### Asset

| `code` | Wallet | Decimals | Network (crypto) |
| --- | --- | --- | --- |
| `LKR` | `FIAT` | 2 | – |
| `USDT` | `CRYPTO` | 2 | TRC-20 |
| `BTC` | `CRYPTO` | 6 | Bitcoin |
| `ETH` | `CRYPTO` | 4 | ERC-20 |
| `SOL` | `CRYPTO` | 2 | Solana |

App: `Asset`, `CryptoToken` in `ui/wallet/WalletViewModel.kt`.

### User (public)

```json
{ "name": "Kasun Perera", "handle": "kasun.p", "photoUrl": "https://…/avatar.jpg" }
```

### Transaction

```json
{
  "id": "tx_01J9Z…",
  "direction": "OUTGOING",
  "currency": "USDT",
  "amount": "10.00",
  "fee": "1.00",
  "feeCurrency": "USDT",
  "wallet": "CRYPTO",
  "timestamp": 1759460000000,
  "status": "COMPLETED",
  "reference": "PC-20481139",
  "counterparty": { "name": "Janidu Perera", "handle": "janidup" },
  "toCurrency": null,
  "toAmount": null,
  "txHash": "9f2c…"
}
```

- `direction`: `OUTGOING` | `INCOMING` | `SWAP` | `TOP_UP` | `WITHDRAWAL`
- `status`: `PENDING` | `COMPLETED` | `FAILED` (the app currently shows everything as completed)
- Swaps: `currency`/`amount` is what was paid, `toCurrency`/`toAmount` what arrived.
- Top-ups / withdrawals: `counterparty` is the linked account, e.g.
  `{ "name": "Commercial Bank", "handle": "•••• 2207" }`.
- `fee`: card top-up fee (in LKR), bank payout fee, or network fee. `null` when free.
- `txHash`: on-chain hash for crypto transfers, otherwise `null`.

App: `WalletTransaction`. Note the app uses numeric ids today; switch to string ids when wiring.

### Payment method (linked card / bank)

```json
{ "id": "pm_01J…", "type": "CARD", "name": "Visa", "brand": "VISA", "last4": "0928",
  "holder": "Kasun Perera", "detail": "Expires 08/29" }
```

- `type`: `CARD` | `BANK`
- `brand` (cards): `VISA` | `MASTERCARD` | `AMEX` | `UNIONPAY` | `JCB` | `DISCOVER` | `DINERS`
- Banks: `name` is the bank, `detail` is `"Savings account"` / `"Current account"`.

**Never accept or store full card numbers or CVVs.** The app must tokenize the card with the
payment provider (PCI DSS) and send only the token. See *Payment methods* below.

App: `PaymentMethod`, `CardBrand` in `ui/account/AccountState.kt`.

### Notification

```json
{ "id": "n_01J…", "kind": "TRANSACTION", "title": "Payment received",
  "body": "Sajjad sent you USDT 73.54.", "timestamp": 1759460000000,
  "transactionId": "tx_01J…", "read": false }
```

`kind`: `TRANSACTION` | `ACCOUNT` | `SECURITY`. App: `AppNotification`.

## Auth (phone + SMS code)

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| POST | `/auth/otp` | `{ "phone": "+94771234567" }` | `204` |
| POST | `/auth/verify` | `{ "phone": "+94771234567", "code": "123456" }` | `{ accessToken, refreshToken, expiresIn, user }` |
| POST | `/auth/refresh` | `{ "refreshToken": "…" }` | `{ accessToken, refreshToken, expiresIn }` |
| POST | `/auth/logout` | – | `204` |

- Phone: Sri Lankan mobile, `+94` followed by 9 digits starting with `7`
  (app: `validateSriLankanMobileNumber` in `ui/auth/SriLankanPhoneNumber.kt`).
- Code: 6 digits. Expire after 5 minutes; limit resends (the app shows a resend countdown).
- `user` in the verify response is the full profile (see `/me`). New numbers create an account.
- App today: `DemoOtpCode = "123456"` in `ui/auth/PayCeylonAuthFlow.kt`.

## Profile

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| GET | `/me` | – | Profile |
| PATCH | `/me` | any of `{ name, handle, email }` | Profile |
| POST | `/me/phone/otp` | `{ "phone": "+94…" }` | `204` |
| POST | `/me/phone/verify` | `{ "phone": "+94…", "code": "…" }` | Profile |
| PUT | `/me/photo` | `multipart/form-data` image | Profile |
| DELETE | `/me/photo` | – | Profile |
| GET | `/users/check-handle?handle=kasun_lk` | – | `{ "available": true }` |

Profile:

```json
{ "name": "Kasun Perera", "handle": "kasun.p", "phone": "+94771234567",
  "email": "kasun.perera@gmail.com", "photoUrl": null }
```

Validation (mirror `ui/account/AccountValidation.kt`):

- `name`: 2–40 characters, letters, spaces, `.`, `'`, `-`.
- `handle`: 3–20 characters `[a-z0-9._]`, stored lowercase, unique (`USERNAME_TAKEN`).
  Changing it invalidates old QR codes and payment links.
- `email`: standard address, stored lowercase.
- Each profile change should create an `ACCOUNT` / `SECURITY` notification
  (phone and email changes are `SECURITY`).

App: `AccountState.updateProfile`, `updatePhoto`, `signedIn`.

## Settings

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| GET | `/me/settings` | – | Settings |
| PATCH | `/me/settings` | any subset | Settings |

```json
{ "biometricApproval": true, "hideBalance": false,
  "transactionAlerts": true, "promotions": false }
```

`transactionAlerts: false` stops `TRANSACTION` notifications (and push) being created.
App: `AccountSetting`, `AccountState.setEnabled`.

## Wallet

| Method | Path | Response |
| --- | --- | --- |
| GET | `/wallet/balances` | `{ "balances": { "LKR": "32483.75", "USDT": "1284.32", … } }` |
| GET | `/wallet/addresses` | `{ "USDT": "TQn9…", "BTC": "bc1q…", "ETH": "0x71C7…", "SOL": "7EcD…" }` |
| GET | `/wallet/summary?asset=USDT` | `{ "asset": "USDT", "balance": "1284.32", "changePercent30d": "6.1" }` |
| GET | `/transactions?wallet=FIAT&cursor=…&limit=50` | `{ "items": [Transaction], "nextCursor": "…" }` |
| GET | `/transactions/{id}` | Transaction |

- `changePercent30d` drives the green/red pill on the balance card: balance now vs 30 days ago,
  one decimal place, `null` if there was no balance then. App: `monthlyChangePercent`.
- `wallet` filter: swaps that touch LKR appear in both wallets (app: `belongsTo`).
- Deposit addresses are per user (the app currently shows example addresses).

## Payments

### Look up users

`GET /users/search?q=sajj&limit=5` → `{ "items": [User] }`

Rank handle prefix matches first, then name-word prefixes, then substring matches
(app: `searchPayCeylonUsers`). `GET /users/{handle}` → User, or `USER_NOT_FOUND`.

### Send

`POST /payments` (with `Idempotency-Key`)

```json
{ "to": "janidup", "amount": "10.00", "currency": "USDT" }
```

→ `201` Transaction (direction `OUTGOING`).

- Amount > 0, at most the asset's decimals, at most 9 whole digits.
- Crypto pays a network fee from the same balance; `amount + fee <= balance`
  or `INSUFFICIENT_FUNDS`. Fiat transfers between PayCeylon users are free.
- Creates `TRANSACTION` notifications for both sender ("Payment sent") and receiver
  ("Payment received").

App: `WalletViewModel.send`. Current network fees (`WalletFees.networkFee`):
USDT 1.00, BTC 0.00002, ETH 0.0004, SOL 0.000005.

### Swap

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| GET | `/rates` | – | `{ "base": "LKR", "prices": { "LKR": "1", "USDT": "300", "BTC": "30000000", … }, "feeRate": "0.005" }` |
| POST | `/swaps/quote` | `{ from, to, amount }` | `{ quoteId, from, to, amount, fee, receive, rate, expiresAt }` |
| POST | `/swaps` | `{ quoteId }` | Transaction (direction `SWAP`) |

- Fee is 0.5% of the amount paid, rounded **up** to the paying asset's decimals.
- `receive = (amount - fee) × rate`, rounded **down** to the receiving asset's decimals.
- App: `SwapRates`, `WalletViewModel.swap`. The app shows indicative rates today and swaps
  instantly; the quote step lets the server lock the rate.

## Top up and withdraw (fiat wallet only)

Crypto can't be topped up or withdrawn to a bank directly: it is bought and sold by swapping
with LKR, and leaves the app through Send.

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| POST | `/topups` | `{ "amount": "10000.00", "paymentMethodId": "pm_…" }` | Transaction (`TOP_UP`) |
| POST | `/withdrawals` | `{ "amount": "5000.00", "paymentMethodId": "pm_…" }` | Transaction (`WITHDRAWAL`, status `PENDING`) |

- Top-up: LKR only, from a card (fee 1.5% of the amount, charged to the card on top, rounded up)
  or a bank account (free). Maximum LKR 500,000 per top-up (`LIMIT_EXCEEDED`).
  Card top-ups are instant.
- Withdrawal: LKR only, to a **bank** method, LKR 25.00 fee added to the amount;
  `amount + 25.00 <= balance`. Expected to arrive within 1 hour (CEFTS): return
  `PENDING`, then update to `COMPLETED` and send a notification.

App: `WalletViewModel.topUp`, `withdraw`, `WalletFees`.

## Payment methods

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| GET | `/payment-methods` | – | `{ "items": [PaymentMethod] }` |
| POST | `/payment-methods/cards` | `{ "token": "tok_…", "holder": "Kasun Perera" }` | PaymentMethod |
| POST | `/payment-methods/banks` | `{ "bank": "Commercial Bank", "accountNumber": "1234567890", "holder": "…", "accountType": "SAVINGS" }` | PaymentMethod |
| DELETE | `/payment-methods/{id}` | – | `204` |

- Cards: the app collects number / expiry / CVV, sends them **directly to the payment
  provider's SDK**, and posts only the returned token. The server gets brand, last4 and expiry
  from the provider. Card validation in the app: Luhn, brand-specific length, unexpired,
  3-digit CVV (4 for Amex) (`AccountValidation.kt`).
- Banks: `bank` is one of the names in `SriLankanBanks`; account number 8–16 digits; holder
  name should match the PayCeylon account. Return `last4` only.
- Linking or removing a method creates a `SECURITY` notification.

App: `AccountState.addCard`, `addBank`, `removePaymentMethod`.

## Notifications

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| GET | `/notifications?cursor=…` | – | `{ "items": [Notification], "nextCursor": "…", "unreadCount": 2 }` |
| POST | `/notifications/read` | `{ "ids": ["n_…"] }` or `{ "all": true }` | `204` |
| POST | `/devices` | `{ "fcmToken": "…", "platform": "ANDROID" }` | `204` (push registration) |

App: `AccountState.notifications`, `markRead`, `markAllRead`.

## Payment links and QR codes (no endpoint, shared format)

The Receive QR, shared links and the scanner all use:

```
payceylon://pay?to=<handle>[&amount=<decimal>][&currency=<LKR|USDT|BTC|ETH|SOL>]
```

`currency` defaults to `LKR` when only an amount is given. If the backend later serves web
links (`https://pay.payceylon.lk/<handle>`), keep the same query parameters.
App: `PaymentRequest` in `ui/wallet/PaymentRequest.kt`.
