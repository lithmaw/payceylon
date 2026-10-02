# PayCeylon

PayCeylon is an Android wallet for Sri Lanka, built with Kotlin and Jetpack Compose. It has a
fiat (LKR) wallet and a crypto wallet (USDT, BTC, ETH, SOL), with sending, receiving, swapping,
top-ups, withdrawals and account settings.

The app is a **complete front end running on in-memory demo data**. The backend contract it
expects is in [`docs/BACKEND_API.md`](docs/BACKEND_API.md).

**Demo login:** any valid Sri Lankan mobile number (e.g. `77 123 4567`), then code `123456`.

## Features

- **Onboarding and sign-in:** three swipeable onboarding pages, Sri Lankan phone number entry,
  6-digit SMS code with resend countdown.
- **Home:** fiat / crypto wallet switcher (full-screen transition, crypto switches the app to a
  dark theme), balance card with hide/show (blurred), month-on-month change pill, linked card
  chip, recent transactions, and a glass action bar that drags open for more actions.
- **Send:** QR scanner (CameraX + ML Kit) or search by username, amount keypad, balance and
  network-fee checks.
- **Receive:** branded QR code and shareable payment link, with an optional requested amount.
- **Swap** between LKR and tokens at indicative rates (0.5% fee).
- **Top Up / Withdraw** (fiat only): top up from a linked card (1.5% fee) or bank (free), withdraw
  to a bank (LKR 25 fee). Crypto is bought and sold by swapping with LKR.
- **Transactions:** full list with filters, and details with fees, reference, on-chain hash
  (crypto) and a shareable receipt.
- **Notifications** dropdown on the bell: transactions plus account and security changes.
- **Settings:** personal details (photo, name, username, phone with SMS check, email),
  cards & bank accounts (card brand detected from the number, with its logo), security and
  notification toggles, privacy policy, terms, help, log out.
- **Deep links:** `payceylon://pay?to=<handle>[&amount=][&currency=]` opens Send Money.

## Build and run

Requirements: Android Studio (recent stable), Android SDK 37, JDK 17+ (Android Studio's bundled
JDK works). The Gradle wrapper (9.5) downloads everything else.

```bash
./gradlew :app:assembleDebug          # Windows: .\gradlew.bat :app:assembleDebug
./gradlew :app:testDebugUnitTest      # unit tests
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Or open the folder in Android
Studio and run the `app` configuration. Minimum Android version: 7.0 (API 24). Blur effects
need Android 12+; older versions fall back to plain tints and fades.

## Project structure

```
app/src/main/java/com/payceylon/app/
├── MainActivity.kt            Entry point, deep-link intake
└── ui/
    ├── PayCeylonApp.kt        Navigation (single-activity, state-driven) and screen wiring
    ├── auth/                  Onboarding, phone number, OTP
    ├── wallet/                Home, Send, Receive, Swap, Top Up, Withdraw, transactions
    │   └── WalletViewModel.kt Wallet state, fees, rates, demo data  ← backend seam
    ├── account/               Settings, profile, cards & banks, notifications, legal
    │   └── AccountState.kt    Profile, linked accounts, settings, notifications ← backend seam
    ├── components/            Shared UI: dropdowns, text field, QR code, motion helpers
    └── theme/                 Colours (fiat / crypto palettes), Inter typography
docs/BACKEND_API.md            REST contract for the backend
images/                        Source design assets from Figma
```

## Connecting the backend

All data lives in two classes, so the backend can be wired in without touching the screens:

| Today (in memory) | Replace with | API section |
| --- | --- | --- |
| `DemoOtpCode` in `ui/auth/PayCeylonAuthFlow.kt` | `/auth/otp`, `/auth/verify` | Auth |
| `WalletViewModel` balances, `transactions`, `send`, `swap`, `topUp`, `withdraw`, `monthlyChangePercent` | `/wallet/*`, `/transactions`, `/payments`, `/swaps`, `/topups`, `/withdrawals` | Wallet, Payments |
| `SwapRates`, `WalletFees` | `/rates`, `/swaps/quote`, server-side fees | Swap |
| `PayCeylonDirectory`, `searchPayCeylonUsers` | `/users/search`, `/users/{handle}` | Payments |
| `AccountState` profile, settings, payment methods, notifications | `/me`, `/me/settings`, `/payment-methods`, `/notifications` | Profile, Settings, Payment methods, Notifications |
| `CryptoToken.address` (example addresses) | `/wallet/addresses` | Wallet |

Suggested approach on the app side: add Retrofit + kotlinx.serialization, put each area behind
a repository interface (`AuthRepository`, `WalletRepository`, `AccountRepository`) with the
current in-memory logic as the fake implementation, and have `WalletViewModel` call the
repository from `viewModelScope`. Keep the validation in `AccountValidation.kt` on both sides.

### Security rules

- Never send or store full card numbers or CVVs: tokenize cards with a PCI DSS compliant
  provider and send only the token.
- Keep auth tokens in Android Keystore-backed storage, never plain SharedPreferences.
- App backup is disabled (`allowBackup="false"`) so wallet data never leaves the device.
- Money is `BigDecimal` in the app and decimal strings in JSON, never floating point.

## Known gaps

- All data is in memory and resets when the app restarts (no Room cache yet).
- Biometric approval is a stored setting only; `androidx.biometric` is not wired up.
- Swap rates and crypto deposit addresses are examples.
- Logging out returns to onboarding but keeps the in-memory demo data.

## Credits

- [Inter](https://rsms.me/inter/) typeface (SIL Open Font License).
- Token icons: [web3icons](https://github.com/0xa3k5/web3icons) (MIT).
- Card network logos: [svg-credit-card-payment-icons](https://github.com/aaronfagan/svg-credit-card-payment-icons)
  (Apache 2.0), converted to vector drawables.
- Glass blur: [Haze](https://github.com/chrisbanes/haze) (Apache 2.0).
