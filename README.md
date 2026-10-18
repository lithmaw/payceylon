# PayCeylon

PayCeylon is an Android wallet for Sri Lanka, built with Kotlin and Jetpack Compose. It has a
fiat (LKR) wallet and a crypto wallet (USDT, BTC, ETH, SOL), with sending, receiving, swapping,
top-ups, withdrawals and account settings.

The app talks to the PayCeylon API in [`backend/`](backend/) (FastAPI + PostgreSQL, deployed on
Railway), signs users in with Firebase phone authentication, and keeps an on-device Room copy
of the wallet. The API contract is in [`docs/BACKEND_API.md`](docs/BACKEND_API.md). Money,
cards, banks and crypto are simulated: no real funds move.

**Demo login:** enter any Sri Lankan mobile number. No SMS is sent: the API prints a fresh
6-digit code to its log (`OTP for +94771234567: 482913`). Read it with `railway logs` from the
`backend` folder (after `railway login` and `railway link`) or in the Railway dashboard's
Deploy Logs. Codes last 5 minutes, allow 5 tries, and can be resent after 30 seconds.

## Project criteria

| Criterion | Where |
| --- | --- |
| Jetpack Compose (Kotlin) | All UI in `ui/` |
| Local database | Room in `data/local/`: last server snapshot (profile, photo, balances, transactions, notifications, cards/banks, settings); the app opens from it instantly and works read-only offline |
| Hardware features | Camera (CameraX + ML Kit QR scanning), biometric sensor (app unlock), GPS (nearby ATMs and agents) |
| Remote data source | PayCeylon REST API (`data/HttpBackend.kt`), Firebase (sign-in, Cloud Messaging push), OpenStreetMap (map tiles; ATMs and banks via the API) |
| Authentication | Phone + one-time code issued by the API, then Firebase sign-in (custom token) verified by the API on every request; biometric app lock |

## Features

- **Onboarding and sign-in:** onboarding pages, Sri Lankan phone number entry, one-time code
  (from the server log) with resend, then a one-time profile setup (name and @username required; photo and
  email optional).
- **Home:** a menu button (or a swipe from the left) opens the sidebar (profile, Fiat / Crypto wallet, transaction
  history, my QR code, pay bills, ATMs & agents nearby, cards & bank accounts, account settings,
  help, log out); the profile button opens Settings. Pulling down past the top of Home switches to
  the other wallet (as in Binance). Switching wallet fades through a short loader; crypto switches the app to a
  dark theme. Balance card with hide/show (blurred), month-on-month change pill, Nearby
  and Pay bills shortcuts (fiat) or the deposit address (crypto), recent transactions, and a
  glass action bar that drags open for more actions.
- **Token cards** (crypto wallet): a small card per token with the holding's value in LKR, the
  24 h price change and a price chart (simulated around the fixed demo prices).
  Tapping a card shows that token on the balance card.
- **Send:** QR scanner (CameraX + ML Kit) or search by username, amount keypad, balance and
  network-fee checks.
- **Receive:** branded QR code and shareable payment link, with an optional requested amount.
- **Swap** between LKR and tokens at indicative rates (0.5% fee).
- **Top Up / Withdraw** (fiat only): top up from a linked card (1.5% fee) or bank (free), withdraw
  to a bank (LKR 25 fee). Crypto is bought and sold by swapping with LKR.
- **Pay bills** (fiat wallet, with each company's logo): CEB, LECO, Water Board, SLT Fibre and Dialog TV bills (simulated
  amount due, editable) and Dialog / Mobitel / Hutch reloads, paid from LKR with no fee. Paid
  accounts appear under Recent for one-tap repeat payments.
- **Nearby** (fiat wallet, on the balance card): map of ATMs and bank branches (real
  OpenStreetMap data for all of Sri Lanka, saved in the API) and PayCeylon cash agents
  (simulated, around Kurunegala) within 3 km of the phone's GPS position (the nearest ATMs and
  banks up to 15 km away when there are none that close), with All / Agents / ATMs / Banks filters, a list by distance,
  "Search this area" after panning; tapping a place opens walking directions in Google Maps. Without location it
  shows Kurunegala town.
- **Transactions:** full list with filters, and details with fees, reference, on-chain hash
  (crypto) and a shareable receipt.
- **Push notifications** (Firebase Cloud Messaging): money received, sent, swapped, topped up,
  withdrawn or paid, and account and security changes reach the phone within a few seconds,
  even with the app closed. Tapping one opens the transaction. Android 13+ asks for permission
  when the wallet first opens; "Transaction alerts" in Settings turns payment pushes off.
- **Settings:** personal details (photo, name, username, phone with code check, email),
  cards & bank accounts (card brand detected from the number, with its logo), biometric unlock,
  security and notification toggles, privacy policy, terms, help, log out.
- **Live updates:** balances, history and notifications refresh every 5 seconds while the app
  is open, so a payment from another phone appears within seconds (and as a push when closed).
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
HTTP); codes then appear in your terminal running uvicorn. Sign-in uses Firebase custom tokens,
so no SHA-1 registration is needed: an APK built on any computer works.

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
│   ├── PhoneAuth.kt             Code request/confirm via the API, then Firebase custom-token sign-in
│   ├── PushTokens.kt            This phone's FCM token
│   └── local/                   Room database (entities, DAO) and the WalletCache over it
├── push/                        FCM service, notification channels, tap handling
└── ui/
    ├── PayCeylonApp.kt          Navigation, session states, polling, app lock, offline note
    ├── auth/                    Onboarding, OTP, profile setup, connection-problem screen
    ├── wallet/                  Home, Send, Receive, Swap, Top Up, Withdraw, transactions
    │   └── WalletViewModel.kt   Session, wallet state, actions; API first, Room as the local copy
    ├── bills/                   Bill payments and mobile reloads
    ├── nearby/                  Map of ATMs, banks and agents (osmdroid + GPS)
    ├── account/                 Settings, profile, cards & banks, notifications, legal
    │   └── AccountState.kt      Profile, linked accounts, settings, notifications
    ├── components/              Shared UI, submit helper, biometric app lock
    └── theme/                   Colours (fiat / crypto palettes), Inter typography
backend/                         FastAPI + PostgreSQL API (see backend/README.md)
docs/BACKEND_API.md              REST contract
images/                          Source design assets from Figma
```

## How data flows

1. Sign-in: `POST /v1/auth/otp` logs a random code for the number; `POST /v1/auth/otp/verify`
   checks it and returns a Firebase custom token for that number's Firebase user. The app signs
   in to Firebase with it and sends the Firebase ID token to `POST /v1/auth/verify`, which
   creates or loads the account.
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

- Swap rates are fixed demo prices and the token charts are simulated; no real bank, card or
  blockchain transfers happen.
- Codes go to the server log instead of SMS; a real build would send them through an SMS provider.
- Bills and reloads are simulated; a real build would connect to each biller or a bill aggregator.
- ATM and bank locations are a saved OpenStreetMap copy (`backend/scripts/fetch_osm_banking.py`
  refreshes it), so new places appear only after a refresh.

## Credits

- [Inter](https://rsms.me/inter/) typeface (SIL Open Font License).
- Token icons: [web3icons](https://github.com/0xa3k5/web3icons) (MIT).
- Card network logos: [svg-credit-card-payment-icons](https://github.com/aaronfagan/svg-credit-card-payment-icons)
  (Apache 2.0), converted to vector drawables.
- Glass blur: [Haze](https://github.com/chrisbanes/haze) (Apache 2.0).
- Biller logos (CEB, LECO, NWSDB, Dialog, SLT-Mobitel, Hutch, Dialog TV) are trademarks of their
  owners, taken from Wikipedia and used only to identify each company.
- Map: [osmdroid](https://github.com/osmdroid/osmdroid) (Apache 2.0); map tiles and ATM/bank data
  © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors (ODbL).
