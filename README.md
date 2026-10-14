# PayCeylon

PayCeylon is an Android wallet for Sri Lanka, built with Kotlin and Jetpack Compose. It has a
fiat (LKR) wallet and a crypto wallet (USDT, BTC, ETH, SOL), with sending, receiving, swapping,
top-ups, withdrawals and account settings.

The app talks to the PayCeylon API in [`backend/`](backend/) (FastAPI + PostgreSQL, deployed on
Railway), signs users in with Firebase phone authentication, and keeps an on-device Room copy
of the wallet. The API contract is in [`docs/BACKEND_API.md`](docs/BACKEND_API.md). Money,
cards, banks and crypto are simulated: no real funds move.

**Demo login:** the Firebase project is on the free plan, so sign in with a test number:
`77 000 0001` (code `111111`) or `77 000 0002` (code `222222`). More can be added in Firebase
Console → Authentication → Sign-in method → Phone. Real SMS needs the Blaze plan.

## Project criteria

| Criterion | Where |
| --- | --- |
| Jetpack Compose (Kotlin) | All UI in `ui/` |
| Local database | Room in `data/local/`: last server snapshot (profile, photo, balances, transactions, notifications, cards/banks, settings); the app opens from it instantly and works read-only offline |
| Hardware features | Camera (CameraX + ML Kit QR scanning), biometric sensor (app unlock) |
| Remote data source | PayCeylon REST API (`data/HttpBackend.kt`) and Firebase |
| Authentication | Firebase phone (SMS code) sign-in, verified by the API on every request; biometric app lock |

## Features

- **Onboarding and sign-in:** onboarding pages, Sri Lankan phone number entry, SMS code via
  Firebase with resend, then a one-time profile setup (name and @username required; photo and
  email optional).
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
  cards & bank accounts (card brand detected from the number, with its logo), biometric unlock,
  security and notification toggles, privacy policy, terms, help, log out.
- **Live updates:** balances, history and notifications refresh every 5 seconds while the app
  is open, so a payment from another phone appears within seconds.
- **Offline:** the last loaded wallet stays visible with an "Offline · showing saved data" note.
- **Deep links:** `payceylon://pay?to=<handle>[&amount=][&currency=]` opens Send Money.

## Build and run

Requirements: Android Studio (recent stable), Android SDK 37, JDK 17+ (Android Studio's bundled
JDK works). The Gradle wrapper (9.5) downloads everything else.

Per-machine settings go in the untracked `local.properties` (next to `sdk.dir`):

```properties
payceylon.apiBaseUrl=https://api-production-39f34.up.railway.app/
payceylon.firebase.apiKey=<Web API key from Firebase project settings>
payceylon.firebase.appId=1:62365306724:android:3ff33f7a6f4309ecb03471
payceylon.firebase.projectId=payceylon-dev-lithma
```

For a local backend instead, use `http://<your PC's LAN IP>:8000/` (debug builds allow plain
HTTP). Firebase phone auth on Android also needs the SHA-1 and SHA-256 of the signing key
registered in the Firebase project; each computer's debug key is different, so register yours
(`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`)
or build the shared APK from one machine.

```bash
./gradlew :app:assembleDebug            # Windows: .\gradlew.bat :app:assembleDebug
./gradlew :app:testDebugUnitTest        # unit tests (fake backend and cache)
./gradlew :app:connectedDebugAndroidTest  # Room tests on a device/emulator (uninstalls the app)
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`; share it to install on other
phones (allow "Install unknown apps"). Minimum Android version: 7.0 (API 24). Blur effects
need Android 12+; older versions fall back to plain tints and fades.

`gradle.properties` sets `android.disallowKotlinSourceSets=false` because Room's code generator
(KSP) doesn't yet register sources the way AGP 9's built-in Kotlin expects.

## Project structure

```
app/src/main/java/com/payceylon/app/
├── MainActivity.kt              Entry point (FragmentActivity for the biometric prompt), deep links
├── PayCeylonApplication.kt      Firebase + service setup from local.properties
├── data/
│   ├── Backend.kt               What the screens need from the server (interface), ApiException
│   ├── HttpBackend.kt           OkHttp + kotlinx.serialization client for the API
│   ├── PhoneAuth.kt             Firebase phone verification as suspend calls
│   └── local/                   Room database (entities, DAO) and the WalletCache over it
└── ui/
    ├── PayCeylonApp.kt          Navigation, session states, polling, app lock, offline note
    ├── auth/                    Onboarding, OTP, profile setup, connection-problem screen
    ├── wallet/                  Home, Send, Receive, Swap, Top Up, Withdraw, transactions
    │   └── WalletViewModel.kt   Session, wallet state, actions; API first, Room as the local copy
    ├── account/                 Settings, profile, cards & banks, notifications, legal
    │   └── AccountState.kt      Profile, linked accounts, settings, notifications
    ├── components/              Shared UI, submit helper, biometric app lock
    └── theme/                   Colours (fiat / crypto palettes), Inter typography
backend/                         FastAPI + PostgreSQL API (see backend/README.md)
docs/BACKEND_API.md              REST contract
images/                          Source design assets from Figma
```

## How data flows

1. Sign-in: Firebase verifies the phone number; the app sends the Firebase ID token to
   `POST /v1/auth/verify`, which creates or loads the account.
2. Every request carries the ID token; the API verifies it with the Firebase Admin SDK.
3. At launch the wallet is shown from Room immediately, then refreshed from the API; each
   successful refresh is written back to Room. Logging out clears Room.
4. Payments, swaps, top-ups and withdrawals send an `Idempotency-Key`, so a retried request is
   never applied twice.

### Security rules

- Card numbers and CVVs never leave the device: the app sends only brand, last four, expiry and
  a demo token (a real build would use a PCI DSS tokenization provider's SDK).
- Firebase manages the sign-in tokens; the Room cache never stores them.
- App backup is disabled (`allowBackup="false"`) so wallet data never leaves the device.
- Money is `BigDecimal` in the app and decimal strings in JSON, never floating point.
- Debug builds allow plain HTTP for a local backend; release builds don't.

## Simulated behaviour

- **Biometric approval:** when on, Send, Withdraw and Top up ask for fingerprint, face or screen
  lock first (skipped on phones with no screen lock).
- **Withdrawals** start as *Pending* and the server completes them after 5 minutes
  (`WITHDRAWAL_SETTLE_SECONDS`), sending a "Withdrawal complete" notification, like a bank
  transfer clearing. The balance is debited when the withdrawal is made.
- **Deposit addresses** are per user and in each network's real format (TRC-20, bc1q, 0x,
  Solana), derived from the user id. They have no keys behind them: never send real crypto.

## Known gaps

- Swap rates are fixed demo prices; no real bank, card or blockchain transfers happen.
- Real SMS codes need the Firebase Blaze plan; until then use the test numbers above.
- No push notifications (FCM); the app polls every 5 seconds while open.

## Credits

- [Inter](https://rsms.me/inter/) typeface (SIL Open Font License).
- Token icons: [web3icons](https://github.com/0xa3k5/web3icons) (MIT).
- Card network logos: [svg-credit-card-payment-icons](https://github.com/aaronfagan/svg-credit-card-payment-icons)
  (Apache 2.0), converted to vector drawables.
- Glass blur: [Haze](https://github.com/chrisbanes/haze) (Apache 2.0).
