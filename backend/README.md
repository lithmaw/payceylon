# PayCeylon local backend

This is a local demonstration backend, not a financial service. Phone verification uses the
Firebase Authentication emulator; PostgreSQL is the source of truth, and the Android SQLite
database only caches the last successful API responses. Top-ups, withdrawals, swaps, linked
payment methods, and peer transfers are simulated. No card PAN/CVV is sent to this API and no
bank or blockchain transfer is made.

## Start locally

1. Install PostgreSQL and create a database/user matching `DATABASE_URL` in `.env.example`.
   Copy that file to `.env` if you need to change the defaults. No Firebase service account is
   needed for the Auth emulator.
2. From the repository root, start the Firebase Auth emulator with Firebase CLI:

   ```powershell
   firebase emulators:start --only auth --project demo-payceylon
   ```

   Emulator phone verification codes are printed in its terminal.
3. In another terminal, from `backend`, install dependencies and start the API:

   ```powershell
   py -m venv .venv
   .\.venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

   The API creates its tables from `schema.sql` on startup. `GET /health` checks liveness;
   interactive endpoint docs are at `http://localhost:8000/docs`.
4. Run the Android app on an emulator. Its debug build connects to the Auth emulator at
   `10.0.2.2:9099` and API at `10.0.2.2:8000`. A physical device needs the computer's LAN
   address in `BackendApi.kt` and Firebase emulator host configuration.

The first sign-in creates the PostgreSQL account and demo balances. The app stores profile and
API snapshots in its private SQLite cache. Firebase ID tokens are refreshed by Firebase and
verified by the API on every authenticated request; the SQLite cache does not contain them.

## API scope

The API follows [`../docs/BACKEND_API.md`](../docs/BACKEND_API.md) for profile, wallet,
transactions, user search, peer payments, swaps, simulated top-ups/withdrawals, payment methods,
settings, and notifications. It uses parameterized `asyncpg` SQL directly; no ORM or migrations
framework is required. Schema changes belong in `schema.sql`.

Run focused backend tests from the repository root:

```powershell
py -m unittest backend.test_main -v
```

**Do not use this demo for real money or real card/account credentials.** Demo balances, rates,
addresses, card tokens, and external-fund actions are fictitious. A real deployment needs a
production Firebase project, HTTPS, a PCI-compliant card tokenization provider, bank/payment
providers, custody infrastructure, secrets management, monitoring, and regulatory review.
