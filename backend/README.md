# PayCeylon backend

FastAPI + PostgreSQL API for the PayCeylon app. PostgreSQL is the source of truth; the Android
app keeps a Room copy of the last responses for instant launch and offline viewing. Phone
sign-in is Firebase Authentication: the app sends Firebase ID tokens and the API verifies them
on every request. Top-ups, withdrawals, swaps, linked payment methods and peer transfers are
simulated. No card PAN/CVV is sent to this API and no bank or blockchain transfer is made.

The API follows [`../docs/BACKEND_API.md`](../docs/BACKEND_API.md). It uses parameterized
`asyncpg` SQL directly (no ORM); tables are created and migrated from `schema.sql` at startup.
`GET /health` checks liveness; interactive docs are at `/docs`.

## Deployed (Railway)

The demo runs on Railway as project **PayCeylon**: an `api` service built from this folder and a
**Postgres** database. Public URL: `https://api-production-39f34.up.railway.app`.

Service variables:

| Variable | Value |
| --- | --- |
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` (reference to the Railway database) |
| `APP_ENV` | `production` |
| `FIREBASE_PROJECT_ID` | `payceylon-dev-lithma` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | The service-account key JSON, pasted inline (secret) |

`railway.json` sets the start command (`uvicorn main:app --host 0.0.0.0 --port $PORT`) and the
`/health` check; `.python-version` pins Python 3.12; `.railwayignore` keeps `.venv`, `.env` and
key files out of uploads. To redeploy from this folder after changes:

```bash
railway up --service api
```

## Run locally

1. Install PostgreSQL 13+ and create the database/user in `DATABASE_URL` from `.env.example`.
2. Copy `.env.example` to `backend/.env` and choose a mode:
   - **Real Firebase** (what the app uses): `APP_ENV=production`, `FIREBASE_PROJECT_ID`, and
     `FIREBASE_SERVICE_ACCOUNT_JSON=firebase-service-account.json` with the key file from
     Firebase Console → Project settings → Service accounts saved in this folder. It is
     gitignored; never commit it.
   - **Auth emulator** (offline development): `APP_ENV=development`, then from the repository
     root run `firebase emulators:start --only auth --project demo-payceylon`. Emulator codes
     are printed in its terminal. Development mode trusts unsigned emulator tokens, so the API
     refuses to start with an emulator host set in any other mode.
3. From `backend`, install and start:

   ```powershell
   py -m venv .venv
   .\.venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

4. Point the app at it with `payceylon.apiBaseUrl=http://<your PC's LAN IP>:8000/` in the app's
   `local.properties`, keep phones on the same Wi-Fi, and allow inbound TCP 8000 in the firewall
   (private networks only).

The first sign-in creates the account with demo balances; the app then asks for a name and
username.

## Tests

From the repository root:

```powershell
py -m unittest discover -s backend -t . -v
```

`test_api.py` runs every endpoint against a real PostgreSQL with Firebase faked. It needs an
empty, disposable database in `TEST_DATABASE_URL` (its tables are dropped), or `pip install
pgserver` for an embedded one; without either it is skipped.

**Do not use this demo for real money or real card/account credentials.** Demo balances, rates,
addresses, card tokens, and external-fund actions are fictitious. A real service would need a
PCI-compliant card tokenization provider, bank/payment providers, custody infrastructure,
secrets management, monitoring, and regulatory review.
