import asyncio
import hashlib
import json
import logging
import math
import os
import re
import secrets
import warnings
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation, ROUND_DOWN, ROUND_UP
from pathlib import Path
from typing import Annotated, Any
from uuid import UUID

import asyncpg
import firebase_admin
import httpx
from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, Request, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response
from firebase_admin import auth, credentials, messaging
from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict
from starlette.exceptions import HTTPException as StarletteHTTPException


ASSETS = ("LKR", "USDT", "BTC", "ETH", "SOL")
DECIMALS = {"LKR": 2, "USDT": 2, "BTC": 6, "ETH": 4, "SOL": 2}
PRICES_LKR = {
    "LKR": Decimal("1"),
    "USDT": Decimal("300"),
    "BTC": Decimal("30000000"),
    "ETH": Decimal("1050000"),
    "SOL": Decimal("45000"),
}
NETWORK_FEES = {
    "USDT": Decimal("1.00"),
    "BTC": Decimal("0.00002"),
    "ETH": Decimal("0.0004"),
    "SOL": Decimal("0.000005"),
}
BANKS = {
    "Bank of Ceylon", "People's Bank", "Commercial Bank", "Hatton National Bank",
    "Sampath Bank", "Seylan Bank", "NDB Bank", "Nations Trust Bank",
    "DFCC Bank", "Pan Asia Bank",
}
DEFAULT_BALANCES = {
    "LKR": Decimal("32483.75"),
    "USDT": Decimal("1284.32"),
    "BTC": Decimal("0.042150"),
    "ETH": Decimal("0.8532"),
    "SOL": Decimal("41.20"),
}
DEMO_PEERS = [
    ("Sajjad Ahamed", "sajjad67"),
    ("Janidu Perera", "janidup"),
    ("Ruveen Jayasinghe", "ruveenobject"),
    ("Anuka Fernando", "anuka67"),
    ("Dialog Axiata", "dialoglk"),
    ("Kavindi Perera", "kavindi.p"),
    ("Tharindu Silva", "tharindu_s"),
    ("Nimesha Rathnayake", "nimesha99"),
    ("Dulanjaya Wickramasinghe", "dulanjaya"),
    ("Sachini Weerasekara", "sachini.w"),
    ("Pasan Gunawardena", "pasan.lk"),
    ("Hiruni Madushani", "hiruni_m"),
    ("Keells Super", "keells"),
    ("PickMe", "pickme.lk"),
]


USER_COLUMNS = "id, firebase_uid, phone, name, handle, email, photo_url, photo_id"
OTP_TTL_SECONDS = 300
OTP_RESEND_SECONDS = 30
OTP_MAX_ATTEMPTS = 5
EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)
IMAGE_SIGNATURES = {b"\xff\xd8\xff": "image/jpeg", b"\x89PNG\r\n\x1a\n": "image/png"}


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=Path(__file__).parent / ".env", extra="ignore")
    database_url: str = "postgresql://payceylon:payceylon@localhost:5432/payceylon"
    firebase_project_id: str = "demo-payceylon"
    firebase_service_account_json: str = ""
    firebase_auth_emulator_host: str = "127.0.0.1:9099"
    # Development mode trusts the Auth emulator, which accepts unsigned tokens, so it must be opted into.
    app_env: str = "production"
    cors_origins: str = "http://10.0.2.2:8080,http://localhost:8080"
    # How long a withdrawal stays PENDING before the (simulated) bank confirms it.
    withdrawal_settle_seconds: int = 300


settings = Settings()


class Body(BaseModel):
    model_config = ConfigDict(extra="forbid")


class VerifyBody(Body):
    id_token: str = Field(alias="idToken", min_length=1)


class OtpRequestBody(Body):
    phone: str


class OtpVerifyBody(Body):
    phone: str
    code: str


def reject_null(value: Any) -> Any:
    if value is None:
        raise ValueError("This field cannot be null.")
    return value


class ProfilePatch(Body):
    name: str | None = Field(default=None, min_length=2, max_length=40)
    handle: str | None = Field(default=None, min_length=3, max_length=20)
    # null or "" clears the email.
    email: str | None = Field(default=None, max_length=254)

    _not_null = field_validator("name", "handle", mode="before")(reject_null)

    @field_validator("handle")
    @classmethod
    def valid_handle(cls, value: str | None) -> str | None:
        if value is not None and not re.fullmatch(r"[a-z0-9._]{3,20}", value.lower()):
            raise ValueError("Username must be 3–20 lowercase letters, numbers, dots, or underscores.")
        return value.lower() if value else value

    @field_validator("name")
    @classmethod
    def valid_name(cls, value: str | None) -> str | None:
        if value is not None and not re.fullmatch(r"[\w .'-]{2,40}", value, re.UNICODE):
            raise ValueError("Enter a valid name.")
        return value

    @field_validator("email")
    @classmethod
    def valid_email(cls, value: str | None) -> str | None:
        if value and not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", value):
            raise ValueError("Enter a valid email address.")
        return value.lower() if value else None


class SettingsPatch(Body):
    biometric_approval: bool | None = Field(default=None, alias="biometricApproval")
    hide_balance: bool | None = Field(default=None, alias="hideBalance")
    transaction_alerts: bool | None = Field(default=None, alias="transactionAlerts")
    promotions: bool | None = None

    _not_null = field_validator("*", mode="before")(reject_null)


class PaymentBody(Body):
    to: str = Field(min_length=3, max_length=20)
    amount: str
    currency: str


class SwapQuoteBody(Body):
    from_asset: str = Field(alias="from")
    to_asset: str = Field(alias="to")
    amount: str


class SwapBody(Body):
    quote_id: UUID = Field(alias="quoteId")


class ExternalFundsBody(Body):
    amount: str
    payment_method_id: UUID = Field(alias="paymentMethodId")


class CardBody(Body):
    token: str = Field(min_length=8, max_length=128)
    holder: str = Field(min_length=2, max_length=80)
    brand: str = Field(min_length=2, max_length=20)
    last4: str = Field(pattern=r"^\d{4}$")
    expiry: str = Field(pattern=r"^(0[1-9]|1[0-2])/\d{2}$")


class BankBody(Body):
    bank: str
    account_number: str = Field(alias="accountNumber", pattern=r"^\d{8,16}$")
    holder: str = Field(min_length=2, max_length=80)
    account_type: str = Field(alias="accountType", pattern=r"^(SAVINGS|CURRENT)$")


class NotificationReadBody(Body):
    ids: list[UUID] = Field(default_factory=list)
    all: bool = False


class DeviceBody(Body):
    fcm_token: str = Field(alias="fcmToken", min_length=10, max_length=4096)
    platform: str = Field(pattern=r"^ANDROID$")


def problem(status: int, code: str, message: str, field: str | None = None) -> HTTPException:
    detail: dict[str, str] = {"code": code, "message": message}
    if field:
        detail["field"] = field
    return HTTPException(status_code=status, detail=detail)


def amount(value: str, asset: str) -> Decimal:
    if asset not in ASSETS:
        raise problem(400, "VALIDATION_FAILED", "Unsupported currency.", "currency")
    try:
        number = Decimal(value)
    except (InvalidOperation, ValueError):
        raise problem(400, "VALIDATION_FAILED", "Enter a valid amount.", "amount") from None
    if (
        not number.is_finite()
        or number <= 0
        or number >= Decimal("1000000000")
        or number.as_tuple().exponent < -DECIMALS[asset]
    ):
        raise problem(400, "VALIDATION_FAILED", "Enter a positive amount with valid decimal places.", "amount")
    return number


def money(value: Decimal | None, asset: str | None = None) -> str | None:
    """Formats with the asset's decimal places, keeping any extra precision (e.g. SOL network fees)."""
    if value is None:
        return None
    if asset in DECIMALS:
        quantized = value.quantize(Decimal(1).scaleb(-DECIMALS[asset]))
        if quantized == value:
            return format(quantized, "f")
    normalized = value.normalize()
    return format(normalized if normalized.as_tuple().exponent <= 0 else value.quantize(Decimal(1)), "f")


def encode_cursor(row: asyncpg.Record) -> str:
    micros = (row["created_at"] - EPOCH) // timedelta(microseconds=1)
    return f"{micros}_{row['id']}"


def decode_cursor(cursor: str | None) -> tuple[datetime | None, UUID | None]:
    if not cursor:
        return None, None
    try:
        micros, row_id = cursor.split("_", 1)
        return EPOCH + timedelta(microseconds=int(micros)), UUID(row_id)
    except (ValueError, OverflowError):
        raise problem(400, "VALIDATION_FAILED", "Invalid cursor.", "cursor") from None


def image_type(content: bytes) -> str | None:
    for signature, content_type in IMAGE_SIGNATURES.items():
        if content.startswith(signature):
            return content_type
    if content[:4] == b"RIFF" and content[8:12] == b"WEBP":
        return "image/webp"
    return None


async def verify_token(token: str) -> dict[str, Any]:
    # firebase_admin is synchronous and may call Google's API; keep it off the event loop.
    return await run_in_threadpool(auth.verify_id_token, token, check_revoked=True)


async def current_user(
    authorization: Annotated[str | None, Header()] = None,
) -> asyncpg.Record:
    if not authorization or not authorization.startswith("Bearer "):
        raise problem(401, "UNAUTHENTICATED", "Please sign in again.")
    token = authorization[7:].strip()
    try:
        claims = await verify_token(token)
    except Exception:
        raise problem(401, "UNAUTHENTICATED", "Your sign-in has expired. Please sign in again.") from None
    user = await app.state.pool.fetchrow(
        f"SELECT {USER_COLUMNS} FROM users WHERE firebase_uid=$1",
        claims["uid"],
    )
    if user is None:
        raise problem(401, "UNAUTHENTICATED", "Verify your phone number before continuing.")
    return user


User = Annotated[asyncpg.Record, Depends(current_user)]


def photo_url(request: Request, user: asyncpg.Record) -> str | None:
    if user["photo_id"]:
        return str(request.url_for("get_photo", photo_id=user["photo_id"]))
    return user["photo_url"]


def public_user(request: Request, user: asyncpg.Record) -> dict[str, Any]:
    return {"name": user["name"], "handle": user["handle"], "photoUrl": photo_url(request, user)}


def profile(request: Request, user: asyncpg.Record) -> dict[str, Any]:
    return {
        "name": user["name"],
        "handle": user["handle"],
        "phone": user["phone"],
        "email": user["email"],
        "photoUrl": photo_url(request, user),
    }


async def emit_notice(
    conn: asyncpg.Connection,
    user_id: UUID,
    kind: str,
    title: str,
    body: str,
    transaction_id: UUID | None = None,
) -> None:
    if kind == "TRANSACTION":
        enabled = await conn.fetchval("SELECT transaction_alerts FROM user_settings WHERE user_id=$1", user_id)
        if not enabled:
            return
    await conn.execute(
        "INSERT INTO notifications(user_id,kind,title,body,transaction_id) VALUES($1,$2,$3,$4,$5)",
        user_id, kind, title, body, transaction_id,
    )


async def transaction_json(conn: asyncpg.Connection, tx: asyncpg.Record) -> dict[str, Any]:
    return {
        "id": str(tx["id"]),
        "direction": tx["direction"],
        "currency": tx["currency"],
        "amount": money(tx["amount"], tx["currency"]),
        "fee": money(tx["fee"], tx["fee_currency"]),
        "feeCurrency": tx["fee_currency"],
        "wallet": tx["wallet"],
        "timestamp": int(tx["created_at"].timestamp() * 1000),
        "status": tx["status"],
        "reference": tx["reference"],
        "counterparty": {"name": tx["counterparty_name"], "handle": tx["counterparty_handle"]},
        "toCurrency": tx["to_currency"],
        "toAmount": money(tx["to_amount"], tx["to_currency"]),
        "txHash": tx["tx_hash"],
    }


async def add_transaction(
    conn: asyncpg.Connection,
    *,
    user_id: UUID,
    direction: str,
    currency: str,
    value: Decimal,
    wallet: str,
    name: str,
    handle: str,
    fee: Decimal | None = None,
    fee_currency: str | None = None,
    to_currency: str | None = None,
    to_amount: Decimal | None = None,
    status: str = "COMPLETED",
) -> asyncpg.Record:
    return await conn.fetchrow(
        """INSERT INTO transactions
        (user_id,direction,currency,amount,fee,fee_currency,wallet,counterparty_name,counterparty_handle,
         to_currency,to_amount,status,reference)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13) RETURNING *""",
        user_id, direction, currency, value, fee, fee_currency, wallet, name, handle,
        to_currency, to_amount, status, f"PC-{secrets.token_hex(5).upper()}",
    )


async def prior_response(
    conn: asyncpg.Connection,
    user_id: UUID,
    operation: str,
    key: str | None,
) -> dict[str, Any] | None:
    if not key or len(key) > 128:
        raise problem(400, "VALIDATION_FAILED", "A valid Idempotency-Key header is required.", "Idempotency-Key")
    await conn.execute(
        "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))",
        f"{user_id}:{operation}:{key}",
    )
    response = await conn.fetchval(
        "SELECT response FROM idempotency_keys WHERE user_id=$1 AND operation=$2 AND idempotency_key=$3",
        user_id, operation, key,
    )
    if not response:
        return None
    return json.loads(response) if isinstance(response, str) else dict(response)


async def save_response(
    conn: asyncpg.Connection,
    user_id: UUID,
    operation: str,
    key: str,
    response: dict[str, Any],
) -> None:
    await conn.execute(
        "INSERT INTO idempotency_keys(user_id,operation,idempotency_key,response) VALUES($1,$2,$3,$4)",
        user_id, operation, key, json.dumps(response),
    )


def sri_lankan_mobile(phone: str) -> str:
    if not re.fullmatch(r"\+947\d{8}", phone):
        raise problem(400, "INVALID_PHONE", "Enter a Sri Lankan mobile number.", "phone")
    return phone


def otp_hash(phone: str, code: str) -> str:
    return hashlib.sha256(f"{phone}:{code}".encode()).hexdigest()


async def issue_otp(phone: str) -> None:
    code = f"{secrets.randbelow(1_000_000):06d}"
    issued = await app.state.pool.fetchval(
        """INSERT INTO otp_codes(phone, code_hash, expires_at)
        VALUES($1, $2, now() + $3::int * interval '1 second')
        ON CONFLICT(phone) DO UPDATE
        SET code_hash=EXCLUDED.code_hash, expires_at=EXCLUDED.expires_at, attempts=0, created_at=now()
        WHERE otp_codes.created_at <= now() - $4::int * interval '1 second'
        RETURNING true""",
        phone, otp_hash(phone, code), OTP_TTL_SECONDS, OTP_RESEND_SECONDS,
    )
    if not issued:
        raise problem(429, "RATE_LIMITED", f"Wait {OTP_RESEND_SECONDS} seconds before asking for another code.")
    # There is no SMS provider: the code is read from the server log (e.g. `railway logs`).
    logging.getLogger("payceylon").warning("OTP for %s: %s (valid %d minutes)", phone, code, OTP_TTL_SECONDS // 60)


async def check_otp(phone: str, code: str) -> None:
    """Consumes the pending code for [phone], or raises INVALID_OTP."""
    async with app.state.pool.acquire() as conn, conn.transaction():
        row = await conn.fetchrow(
            "SELECT code_hash, attempts, expires_at > now() AS live FROM otp_codes WHERE phone=$1 FOR UPDATE",
            phone,
        )
        if row is None or not row["live"]:
            failure = "That code has expired. Request a new one."
        elif row["attempts"] >= OTP_MAX_ATTEMPTS:
            failure = "Too many wrong codes. Request a new one."
        elif not secrets.compare_digest(row["code_hash"], otp_hash(phone, code)):
            # Recorded before raising, so the transaction doesn't roll the count back.
            await conn.execute("UPDATE otp_codes SET attempts=attempts+1 WHERE phone=$1", phone)
            failure = "That code didn't match. Try again."
        else:
            await conn.execute("DELETE FROM otp_codes WHERE phone=$1", phone)
            return
    raise problem(401, "INVALID_OTP", failure)


def firebase_uid_for_phone(phone: str) -> str:
    """The Firebase user that owns [phone], created on first sign-in."""
    try:
        return auth.get_user_by_phone_number(phone).uid
    except auth.UserNotFoundError:
        pass
    try:
        return auth.create_user(phone_number=phone).uid
    except auth.PhoneNumberAlreadyExistsError:
        # A parallel sign-in created it first.
        return auth.get_user_by_phone_number(phone).uid


async def create_user_from_firebase(claims: dict[str, Any]) -> asyncpg.Record:
    # "phone" is the claim our own sign-in tokens carry, in case the ID token has no phone_number.
    phone = claims.get("phone_number") or claims.get("phone", "")
    if not re.fullmatch(r"\+947\d{8}", phone):
        raise problem(400, "VALIDATION_FAILED", "A verified Sri Lankan mobile number is required.", "phone")
    uid = claims["uid"]
    handle = "pc" + secrets.token_hex(5)
    async with app.state.pool.acquire() as conn, conn.transaction():
        user = await conn.fetchrow(
            f"SELECT {USER_COLUMNS} FROM users WHERE phone=$1 OR firebase_uid=$2",
            phone, uid,
        )
        if user is not None:
            if user["firebase_uid"] != uid:
                user = await conn.fetchrow(
                    f"UPDATE users SET firebase_uid=$1, updated_at=now() WHERE id=$2 RETURNING {USER_COLUMNS}",
                    uid, user["id"],
                )
            return user

        user = await conn.fetchrow(
            f"INSERT INTO users(firebase_uid,phone,handle) VALUES($1,$2,$3) RETURNING {USER_COLUMNS}",
            uid, phone, handle,
        )
        await conn.execute("INSERT INTO user_settings(user_id) VALUES($1) ON CONFLICT DO NOTHING", user["id"])
        for asset, balance in DEFAULT_BALANCES.items():
            await conn.execute(
                """INSERT INTO wallet_balances(user_id,asset,balance) VALUES($1,$2,$3)
                ON CONFLICT(user_id,asset) DO NOTHING""",
                user["id"], asset, balance,
            )
        for name, handle in DEMO_PEERS:
            # Not a phone number, so a real verified number can never match a demo peer.
            peer_id = await conn.fetchval(
                """INSERT INTO users(firebase_uid,phone,name,handle)
                VALUES($1,$2,$3,$4) ON CONFLICT DO NOTHING RETURNING id""",
                f"demo-peer:{handle}", f"demo:{handle}", name, handle,
            )
            if peer_id:
                for asset in ASSETS:
                    await conn.execute(
                        "INSERT INTO wallet_balances(user_id,asset,balance) VALUES($1,$2,0)",
                        peer_id, asset,
                    )
        return user


@asynccontextmanager
async def lifespan(application: FastAPI):
    if settings.app_env == "development":
        os.environ.setdefault("FIREBASE_AUTH_EMULATOR_HOST", settings.firebase_auth_emulator_host)
    elif os.environ.get("FIREBASE_AUTH_EMULATOR_HOST"):
        raise RuntimeError("FIREBASE_AUTH_EMULATOR_HOST is set but APP_ENV is not 'development'; refusing to trust emulator tokens.")
    if not firebase_admin._apps:
        if settings.firebase_service_account_json:
            raw = settings.firebase_service_account_json.strip()
            if raw.startswith("{"):
                account_info = json.loads(raw)
            else:
                # Relative paths are relative to this folder, not wherever uvicorn was started.
                key_path = Path(raw) if Path(raw).is_absolute() else Path(__file__).parent / raw
                account_info = json.loads(key_path.read_text(encoding="utf-8"))
            firebase_admin.initialize_app(
                credentials.Certificate(account_info),
                {"projectId": settings.firebase_project_id},
            )
        else:
            firebase_admin.initialize_app(options={"projectId": settings.firebase_project_id})
    pool = await asyncpg.create_pool(settings.database_url, min_size=1, max_size=10)
    schema = (Path(__file__).parent / "schema.sql").read_text(encoding="utf-8")
    async with pool.acquire() as conn:
        await conn.execute(schema)
    application.state.pool = pool

    async def settle_forever() -> None:
        # Runs even when nobody has the app open, so the notification is waiting for them.
        while True:
            try:
                await settle_withdrawals(pool)
            except Exception:
                logging.getLogger("payceylon").exception("Settling withdrawals failed")
            await asyncio.sleep(15)

    async def push_forever() -> None:
        while True:
            try:
                await send_pushes(pool)
            except Exception:
                logging.getLogger("payceylon").exception("Sending push notifications failed")
            await asyncio.sleep(PUSH_INTERVAL_SECONDS)

    settler = asyncio.create_task(settle_forever())
    # Tests call send_pushes directly with Firebase faked.
    pusher = asyncio.create_task(push_forever()) if settings.app_env != "test" else None
    yield
    settler.cancel()
    if pusher:
        pusher.cancel()
    await pool.close()


PUSH_INTERVAL_SECONDS = 2
# Older notifications are not pushed (e.g. after the server was down for a while).
PUSH_MAX_AGE_SECONDS = 600
# FCM error codes meaning the token will never work again.
DEAD_TOKEN_ERRORS = {"UNREGISTERED", "INVALID_ARGUMENT", "SENDER_ID_MISMATCH"}
PUSH_BATCH = 100

# The app sends FCM registration tokens, which FCM still accepts; the SDK now prefers
# Firebase installation IDs (Message.fid) and warns on every message otherwise.
warnings.filterwarnings("ignore", message="Message.token is deprecated", category=DeprecationWarning)


async def send_pushes(pool: asyncpg.Pool) -> int:
    """
    Sends new notifications to the owner's phones through Firebase Cloud Messaging.

    Notifications are picked up after their transaction commits, so a payment that rolls back
    never buzzes a phone. Data-only messages: the app builds the notification itself.
    """
    total = 0
    while True:
        claimed, sent = await push_batch(pool)
        total += sent
        if claimed < PUSH_BATCH:
            return total


async def push_batch(pool: asyncpg.Pool) -> tuple[int, int]:
    """Claims up to PUSH_BATCH unpushed notifications and sends them; returns (claimed, sent)."""
    async with pool.acquire() as conn, conn.transaction():
        rows = await conn.fetch(
            """UPDATE notifications SET pushed=true
            WHERE id IN (
                SELECT id FROM notifications WHERE NOT pushed ORDER BY created_at LIMIT $1
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id, user_id, kind, title, body, transaction_id, created_at""",
            PUSH_BATCH,
        )
        fresh = [row for row in rows if row["created_at"] > datetime.now(timezone.utc) - timedelta(seconds=PUSH_MAX_AGE_SECONDS)]
        if not fresh:
            return len(rows), 0
        tokens = await conn.fetch(
            "SELECT user_id, fcm_token FROM device_tokens WHERE user_id=ANY($1::uuid[])",
            list({row["user_id"] for row in fresh}),
        )
    by_user: dict[UUID, list[str]] = {}
    for token in tokens:
        by_user.setdefault(token["user_id"], []).append(token["fcm_token"])
    messages = [
        messaging.Message(
            token=token,
            data={
                "id": str(row["id"]),
                "kind": row["kind"],
                "title": row["title"],
                "body": row["body"],
                "transactionId": str(row["transaction_id"]) if row["transaction_id"] else "",
            },
            android=messaging.AndroidConfig(priority="high", ttl=timedelta(seconds=PUSH_MAX_AGE_SECONDS)),
        )
        for row in fresh
        for token in by_user.get(row["user_id"], [])
    ]
    if not messages:
        return len(rows), 0
    sent = 0
    dead: set[str] = set()
    for start in range(0, len(messages), 500):
        batch = messages[start:start + 500]
        # firebase_admin is synchronous; keep it off the event loop.
        result = await run_in_threadpool(messaging.send_each, batch)
        for message, response in zip(batch, result.responses):
            if response.success:
                sent += 1
            elif getattr(response.exception, "code", None) in DEAD_TOKEN_ERRORS or isinstance(
                response.exception, messaging.UnregisteredError
            ):
                dead.add(message.token)
            else:
                logging.getLogger("payceylon").warning("Push failed: %s", response.exception)
    if dead:
        await pool.execute("DELETE FROM device_tokens WHERE fcm_token=ANY($1::text[])", list(dead))
    return len(rows), sent


def settle_minutes() -> str:
    minutes = max(1, round(settings.withdrawal_settle_seconds / 60))
    return f"{minutes} minute" + ("" if minutes == 1 else "s")


async def settle_withdrawals(pool: asyncpg.Pool) -> int:
    """Completes withdrawals older than the settle time (a stand-in for the bank confirming)."""
    async with pool.acquire() as conn, conn.transaction():
        settled = await conn.fetch(
            """UPDATE transactions SET status='COMPLETED'
            WHERE direction='WITHDRAWAL' AND status='PENDING'
            AND created_at <= now() - $1::int * interval '1 second'
            RETURNING id, user_id, amount, counterparty_name""",
            settings.withdrawal_settle_seconds,
        )
        for row in settled:
            await emit_notice(
                conn, row["user_id"], "TRANSACTION", "Withdrawal complete",
                f"LKR {money(row['amount'], 'LKR')} arrived at {row['counterparty_name']}. No real transfer was made.",
                row["id"],
            )
    return len(settled)


app = FastAPI(title="PayCeylon API", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()],
    allow_methods=["*"],
    allow_headers=["*"],
)


# Starlette's base class also covers router-level 404/405s, which FastAPI's subclass misses.
@app.exception_handler(StarletteHTTPException)
async def http_error_handler(_request: Request, exc: StarletteHTTPException) -> JSONResponse:
    if isinstance(exc.detail, dict):
        error = exc.detail
    else:
        error = {"code": "REQUEST_FAILED", "message": str(exc.detail)}
    return JSONResponse(status_code=exc.status_code, content={"error": error}, headers=exc.headers)


@app.exception_handler(RequestValidationError)
async def validation_error_handler(_request: Request, exc: RequestValidationError) -> JSONResponse:
    error = exc.errors()[0] if exc.errors() else {}
    field = str(error.get("loc", ["body", "request"])[-1])
    return JSONResponse(
        status_code=400,
        content={"error": {"code": "VALIDATION_FAILED", "message": str(error.get("msg", "Invalid request.")), "field": field}},
    )


@app.exception_handler(Exception)
async def unexpected_error_handler(_request: Request, exc: Exception) -> JSONResponse:
    logging.getLogger("payceylon").error(
        "Unhandled API error",
        exc_info=(type(exc), exc, exc.__traceback__),
    )
    return JSONResponse(
        status_code=500,
        content={"error": {"code": "INTERNAL_ERROR", "message": "Something went wrong. Please try again."}},
    )


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/auth/verify")
async def verify_phone(body: VerifyBody, request: Request) -> dict[str, Any]:
    try:
        claims = await verify_token(body.id_token)
    except Exception:
        raise problem(401, "INVALID_OTP", "The phone verification has expired. Please request a new code.") from None
    user = await create_user_from_firebase(claims)
    return {"user": profile(request, user)}


@app.post("/v1/auth/otp")
async def request_otp(body: OtpRequestBody) -> dict[str, int]:
    await issue_otp(sri_lankan_mobile(body.phone))
    return {"expiresIn": OTP_TTL_SECONDS}


@app.post("/v1/auth/otp/verify")
async def verify_otp(body: OtpVerifyBody) -> dict[str, str]:
    """Swaps a correct code for a Firebase custom token the app signs in with."""
    phone = sri_lankan_mobile(body.phone)
    await check_otp(phone, body.code)
    uid = await run_in_threadpool(firebase_uid_for_phone, phone)
    token = await run_in_threadpool(auth.create_custom_token, uid, {"phone": phone})
    return {"customToken": token.decode() if isinstance(token, bytes) else token}


@app.get("/v1/me")
async def get_me(user: User, request: Request) -> dict[str, Any]:
    return profile(request, user)


@app.patch("/v1/me")
async def update_me(body: ProfilePatch, user: User, request: Request) -> dict[str, Any]:
    values = body.model_dump(exclude_unset=True)
    if not values:
        return profile(request, user)
    # Keys come from the model's fixed field names, never from the request.
    updates = ", ".join(f"{key}=${index + 2}" for index, key in enumerate(values))
    async with app.state.pool.acquire() as conn:
        try:
            async with conn.transaction():
                updated = await conn.fetchrow(
                    f"UPDATE users SET {updates}, updated_at=now() WHERE id=$1 RETURNING {USER_COLUMNS}",
                    user["id"], *values.values(),
                )
                for key, value in values.items():
                    if value != user[key]:
                        kind = "SECURITY" if key == "email" else "ACCOUNT"
                        await emit_notice(conn, user["id"], kind, f"{key.title()} updated", f"Your {key} was updated.")
        except asyncpg.UniqueViolationError:
            raise problem(409, "USERNAME_TAKEN", "That username is already in use.", "handle") from None
    return profile(request, updated)


@app.patch("/v1/me/phone")
async def update_phone(body: OtpVerifyBody, user: User, request: Request) -> dict[str, Any]:
    phone = sri_lankan_mobile(body.phone)
    await check_otp(phone, body.code)
    try:
        async with app.state.pool.acquire() as conn, conn.transaction():
            updated = await conn.fetchrow(
                f"UPDATE users SET phone=$2,updated_at=now() WHERE id=$1 RETURNING {USER_COLUMNS}",
                user["id"], phone,
            )
            # Inside the transaction so a Firebase failure leaves the old number in place.
            await run_in_threadpool(auth.update_user, user["firebase_uid"], phone_number=phone)
    except (asyncpg.UniqueViolationError, auth.PhoneNumberAlreadyExistsError):
        raise problem(409, "PHONE_TAKEN", "That phone number is already in use.", "phone") from None
    return profile(request, updated)


@app.put("/v1/me/photo")
async def upload_photo(user: User, request: Request, photo: UploadFile = File(...)) -> dict[str, Any]:
    content = await photo.read(2 * 1024 * 1024 + 1)
    if len(content) > 2 * 1024 * 1024:
        raise problem(413, "PHOTO_TOO_LARGE", "Profile photos must be 2 MB or smaller.")
    # Trust the file's bytes, not the client-declared content type.
    content_type = image_type(content)
    if content_type is None:
        raise problem(400, "VALIDATION_FAILED", "Upload a JPEG, PNG, or WebP image.", "photo")
    async with app.state.pool.acquire() as conn, conn.transaction():
        await conn.execute("DELETE FROM user_photos WHERE user_id=$1", user["id"])
        # A new random id per upload gives a fresh URL, so clients never show a cached old photo.
        photo_id = await conn.fetchval(
            "INSERT INTO user_photos(user_id,content_type,data) VALUES($1,$2,$3) RETURNING id",
            user["id"], content_type, content,
        )
        updated = await conn.fetchrow(
            f"UPDATE users SET photo_url=NULL,photo_id=$2,updated_at=now() WHERE id=$1 RETURNING {USER_COLUMNS}",
            user["id"], photo_id,
        )
    return profile(request, updated)


@app.delete("/v1/me/photo")
async def remove_photo(user: User, request: Request) -> dict[str, Any]:
    async with app.state.pool.acquire() as conn, conn.transaction():
        await conn.execute("DELETE FROM user_photos WHERE user_id=$1", user["id"])
        updated = await conn.fetchrow(
            f"UPDATE users SET photo_url=NULL,photo_id=NULL,updated_at=now() WHERE id=$1 RETURNING {USER_COLUMNS}",
            user["id"],
        )
    return profile(request, updated)


# Unauthenticated so image loaders can fetch it; the random id in the URL is the access check.
@app.get("/v1/photos/{photo_id}", name="get_photo")
async def get_photo(photo_id: UUID) -> Response:
    row = await app.state.pool.fetchrow("SELECT content_type,data FROM user_photos WHERE id=$1", photo_id)
    if row is None:
        raise problem(404, "PHOTO_NOT_FOUND", "Photo not found.")
    return Response(
        content=row["data"], media_type=row["content_type"],
        headers={"Cache-Control": "public, max-age=31536000, immutable"},
    )


@app.get("/v1/users/check-handle")
async def check_handle(_user: User, handle: str = Query(min_length=3, max_length=20)) -> dict[str, bool]:
    if not re.fullmatch(r"[a-z0-9._]{3,20}", handle.lower()):
        raise problem(400, "VALIDATION_FAILED", "Enter a valid username.", "handle")
    exists = await app.state.pool.fetchval("SELECT EXISTS(SELECT 1 FROM users WHERE handle=$1)", handle.lower())
    return {"available": not exists}


@app.get("/v1/me/settings")
async def get_settings(user: User) -> dict[str, bool]:
    row = await app.state.pool.fetchrow("SELECT * FROM user_settings WHERE user_id=$1", user["id"])
    return {
        "biometricApproval": row["biometric_approval"],
        "hideBalance": row["hide_balance"],
        "transactionAlerts": row["transaction_alerts"],
        "promotions": row["promotions"],
    }


@app.patch("/v1/me/settings")
async def patch_settings(body: SettingsPatch, user: User) -> dict[str, bool]:
    fields = {
        "biometric_approval": "biometric_approval",
        "hide_balance": "hide_balance",
        "transaction_alerts": "transaction_alerts",
        "promotions": "promotions",
    }
    values = body.model_dump(exclude_unset=True)
    if values:
        updates = ", ".join(f"{fields[key]}=${index + 2}" for index, key in enumerate(values))
        query = f"UPDATE user_settings SET {updates} WHERE user_id=$1"
        await app.state.pool.execute(query, user["id"], *values.values())
    return await get_settings(user)


@app.get("/v1/wallet/balances")
async def balances(user: User) -> dict[str, dict[str, str]]:
    rows = await app.state.pool.fetch("SELECT asset,balance FROM wallet_balances WHERE user_id=$1", user["id"])
    return {"balances": {row["asset"]: money(row["balance"], row["asset"]) for row in rows}}


BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
BECH32 = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"


def demo_address(user_id: UUID, asset: str) -> str:
    """A per-user address in the token's real format, derived from the user id.

    Demo only: no key pair exists behind it, so real crypto sent here would be lost.
    """
    digest = hashlib.sha256(f"payceylon-demo:{user_id}:{asset}".encode()).digest()
    number = int.from_bytes(digest + hashlib.sha256(digest).digest(), "big")

    def encode(alphabet: str, length: int) -> str:
        chars, rest = [], number
        for _ in range(length):
            rest, index = divmod(rest, len(alphabet))
            chars.append(alphabet[index])
        return "".join(chars)

    return {
        "USDT": lambda: "T" + encode(BASE58, 33),  # TRC-20 (Tron)
        "BTC": lambda: "bc1q" + encode(BECH32, 38),  # native SegWit
        "ETH": lambda: "0x" + digest.hex()[:40],
        "SOL": lambda: encode(BASE58, 44),
    }[asset]()


@app.get("/v1/wallet/addresses")
async def addresses(user: User) -> dict[str, str]:
    return {asset: demo_address(user["id"], asset) for asset in ("USDT", "BTC", "ETH", "SOL")}


# PayCeylon cash agents (shops that take and pay out cash for the wallet). Simulated, around
# Kurunegala town; the names are made up.
AGENTS = [
    ("Lakmini Communication", "Colombo Road, Kurunegala", 7.4818, 80.3620),
    ("Wayamba Mobile Centre", "Puttalam Road, Kurunegala", 7.4891, 80.3586),
    ("Siri Grocery", "Kandy Road, Kurunegala", 7.4842, 80.3712),
    ("Nuwan Pharmacy", "Negombo Road, Kurunegala", 7.4780, 80.3570),
    ("City Cell Point", "Dambulla Road, Kurunegala", 7.4945, 80.3668),
    ("Malkanthi Stores", "Wehera, Kurunegala", 7.4710, 80.3735),
    ("Ruwan Book Shop", "Mallawapitiya, Kurunegala", 7.4980, 80.3780),
    ("Sathi Super", "Wilgoda, Kurunegala", 7.4920, 80.3500),
]
# Used when the user is nowhere near the seeded agents, so the map always has some.
NEARBY_AGENT_NAMES = ("Sunil Stores", "Nimal Communication", "Janatha Pharmacy")
AGENT_HOURS = "Mo-Sa 08:00-20:00"
# Public Overpass servers are often slow or down, so all are asked at once and the first answer wins.
OVERPASS_URLS = (
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
)
OVERPASS_TIMEOUT_SECONDS = 5
PLACES_CACHE_SECONDS = 3600
# (lat, lon, radius) rounded -> (fetched at, OpenStreetMap elements)
_places_cache: dict[tuple[float, float, int], tuple[float, list[dict[str, Any]]]] = {}
# Saved OpenStreetMap ATMs and banks within OSM_SNAPSHOT_RADIUS of Kurunegala, used when Overpass is down.
OSM_SNAPSHOT_PATH = Path(__file__).parent / "data" / "osm_banking_kurunegala.json"
OSM_SNAPSHOT_CENTRE = (7.4863, 80.3647)
OSM_SNAPSHOT_RADIUS = 12000


def distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> int:
    """Great-circle distance in metres (haversine)."""
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return round(2 * 6_371_000 * math.asin(math.sqrt(a)))


async def overpass(query: str) -> list[dict[str, Any]]:
    """Asks every Overpass server at once; returns the first successful answer."""

    async def ask(client: httpx.AsyncClient, url: str) -> list[dict[str, Any]]:
        response = await client.post(url, data={"data": query})
        response.raise_for_status()
        return response.json()["elements"]

    headers = {"User-Agent": "PayCeylon/1.0 (university demo)"}
    async with httpx.AsyncClient(timeout=OVERPASS_TIMEOUT_SECONDS, headers=headers) as client:
        pending = {asyncio.create_task(ask(client, url)) for url in OVERPASS_URLS}
        try:
            while pending:
                done, pending = await asyncio.wait(pending, return_when=asyncio.FIRST_COMPLETED)
                for task in done:
                    if task.exception() is None:
                        return task.result()
        finally:
            for task in pending:
                task.cancel()
    raise RuntimeError("OpenStreetMap is unavailable")


async def osm_banking(lat: float, lon: float, radius: int) -> list[dict[str, Any]]:
    """ATMs and bank branches from OpenStreetMap (Overpass API), cached per area for an hour.

    When Overpass is down: the last answer for the area, else the saved Kurunegala snapshot.
    """
    key = (round(lat, 2), round(lon, 2), radius)
    cached = _places_cache.get(key)
    now = asyncio.get_running_loop().time()
    if cached and now - cached[0] < PLACES_CACHE_SECONDS:
        return cached[1]
    # The rounded centre can be ~800 m off, so search wider; callers filter by exact distance.
    query = (
        f'[out:json][timeout:15];nwr["amenity"~"^(atm|bank)$"](around:{radius + 800},{key[0]},{key[1]});'
        "out center tags 150;"
    )
    try:
        elements = await overpass(query)
    except RuntimeError:
        logging.getLogger("payceylon").warning("Overpass unavailable for %s", key)
        if cached:
            return cached[1]
        if distance_m(lat, lon, *OSM_SNAPSHOT_CENTRE) + radius <= OSM_SNAPSHOT_RADIUS and OSM_SNAPSHOT_PATH.exists():
            return json.loads(OSM_SNAPSHOT_PATH.read_text(encoding="utf-8"))["elements"]
        raise
    _places_cache[key] = (now, elements)
    return elements


def osm_place(element: dict[str, Any], lat: float, lon: float) -> dict[str, Any] | None:
    tags = element.get("tags", {})
    point = element.get("center", element)
    if "lat" not in point or "lon" not in point:
        return None
    is_atm = tags.get("amenity") == "atm"
    name = tags.get("name") or tags.get("brand") or tags.get("operator") or ("ATM" if is_atm else "Bank branch")
    address = ", ".join(filter(None, (tags.get("addr:street"), tags.get("addr:city"))))
    return {
        "id": f"osm-{element['type']}-{element['id']}",
        "kind": "ATM" if is_atm else "BANK",
        "name": name,
        "address": address,
        "latitude": point["lat"],
        "longitude": point["lon"],
        "distanceMeters": distance_m(lat, lon, point["lat"], point["lon"]),
        "hasAtm": is_atm or tags.get("atm") == "yes",
        "openingHours": tags.get("opening_hours"),
    }


def agent_places(lat: float, lon: float, radius: int) -> list[dict[str, Any]]:
    agents = [
        (f"agent-{index}", name, address, a_lat, a_lon)
        for index, (name, address, a_lat, a_lon) in enumerate(AGENTS, start=1)
        if distance_m(lat, lon, a_lat, a_lon) <= radius
    ]
    if not agents:
        # A few hundred metres from wherever the user is (stable for the same spot).
        base_lat, base_lon = round(lat, 3), round(lon, 3)
        offsets = ((0.0031, 0.0012), (-0.0024, 0.0035), (0.0008, -0.0047))
        agents = [
            (f"agent-near-{base_lat}-{base_lon}-{index}", name, "Near you", base_lat + d_lat, base_lon + d_lon)
            for index, (name, (d_lat, d_lon)) in enumerate(zip(NEARBY_AGENT_NAMES, offsets), start=1)
        ]
    return [
        {
            "id": agent_id,
            "kind": "AGENT",
            "name": name,
            "address": address,
            "latitude": a_lat,
            "longitude": a_lon,
            "distanceMeters": distance_m(lat, lon, a_lat, a_lon),
            "hasAtm": False,
            "openingHours": AGENT_HOURS,
        }
        for agent_id, name, address, a_lat, a_lon in agents
    ]


@app.get("/v1/places/nearby")
async def nearby_places(
    _user: User,
    lat: float = Query(ge=-90, le=90),
    lon: float = Query(ge=-180, le=180),
    radius: int = Query(default=3000, ge=500, le=10000),
) -> dict[str, Any]:
    places = agent_places(lat, lon, radius)
    banking_available = True
    try:
        elements = await osm_banking(lat, lon, radius)
        places += [p for p in (osm_place(e, lat, lon) for e in elements) if p and p["distanceMeters"] <= radius]
    except RuntimeError:
        banking_available = False
    places.sort(key=lambda place: place["distanceMeters"])
    return {"items": places, "banksAvailable": banking_available}


@app.get("/v1/wallet/summary")
async def wallet_summary(asset: str, user: User) -> dict[str, Any]:
    if asset not in ASSETS:
        raise problem(400, "VALIDATION_FAILED", "Unsupported currency.", "asset")
    value = await app.state.pool.fetchval(
        "SELECT balance FROM wallet_balances WHERE user_id=$1 AND asset=$2", user["id"], asset,
    )
    return {"asset": asset, "balance": money(value or Decimal("0"), asset), "changePercent30d": None}


@app.get("/v1/transactions")
async def transactions(
    user: User,
    wallet: str | None = None,
    limit: int = Query(default=50, ge=1, le=100),
    cursor: str | None = None,
) -> dict[str, Any]:
    if wallet not in (None, "FIAT", "CRYPTO"):
        raise problem(400, "VALIDATION_FAILED", "Wallet must be FIAT or CRYPTO.", "wallet")
    before, before_id = decode_cursor(cursor)
    # (created_at, id) keyset: rows written in one DB transaction share created_at.
    rows = await app.state.pool.fetch(
        """SELECT * FROM transactions WHERE user_id=$1
        AND ($2::text IS NULL OR wallet=$2 OR (direction='SWAP' AND ($2='FIAT' AND (currency='LKR' OR to_currency='LKR') OR $2='CRYPTO' AND (currency<>'LKR' OR to_currency<>'LKR'))))
        AND ($3::timestamptz IS NULL OR (created_at, id) < ($3, $4::uuid))
        ORDER BY created_at DESC, id DESC LIMIT $5""",
        user["id"], wallet, before, before_id, limit + 1,
    )
    more = len(rows) > limit
    rows = rows[:limit]
    items = [await transaction_json(app.state.pool, row) for row in rows]
    next_cursor = encode_cursor(rows[-1]) if more and rows else None
    return {"items": items, "nextCursor": next_cursor}


@app.get("/v1/transactions/{transaction_id}")
async def get_transaction(transaction_id: UUID, user: User) -> dict[str, Any]:
    row = await app.state.pool.fetchrow(
        "SELECT * FROM transactions WHERE id=$1 AND user_id=$2", transaction_id, user["id"],
    )
    if row is None:
        raise problem(404, "TRANSACTION_NOT_FOUND", "Transaction not found.")
    return await transaction_json(app.state.pool, row)


@app.get("/v1/users/search")
async def search_users(
    _user: User,
    request: Request,
    q: str = Query(default="", max_length=40),
    limit: int = Query(default=5, ge=1, le=20),
) -> dict[str, list[dict[str, str | None]]]:
    query = q.strip().removeprefix("@").lower()
    if not query:
        return {"items": []}
    # Escape LIKE wildcards: "_" is a valid handle character and must match literally.
    pattern = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    rows = await app.state.pool.fetch(
        """SELECT name,handle,photo_url,photo_id,
        CASE WHEN handle LIKE $1 THEN 0 WHEN lower(name) LIKE $1 OR lower(name) LIKE $3 THEN 1 ELSE 2 END AS rank
        FROM users WHERE lower(handle) LIKE $2 OR lower(name) LIKE $2
        ORDER BY rank,handle LIMIT $4""",
        pattern + "%", "%" + pattern + "%", "% " + pattern + "%", limit,
    )
    return {"items": [public_user(request, row) for row in rows]}


@app.get("/v1/users/{handle}")
async def get_user(handle: str, _user: User, request: Request) -> dict[str, Any]:
    row = await app.state.pool.fetchrow(
        "SELECT name,handle,photo_url,photo_id FROM users WHERE lower(handle)=lower($1)", handle.removeprefix("@"),
    )
    if row is None:
        raise problem(404, "USER_NOT_FOUND", "That PayCeylon user could not be found.")
    return public_user(request, row)


@app.post("/v1/payments", status_code=201)
async def send_payment(
    body: PaymentBody,
    user: User,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> dict[str, Any]:
    asset = body.currency.upper()
    value = amount(body.amount, asset)
    recipient_handle = body.to.strip().removeprefix("@").lower()
    if not idempotency_key or len(idempotency_key) > 128:
        raise problem(400, "VALIDATION_FAILED", "A valid Idempotency-Key header is required.", "Idempotency-Key")
    async with app.state.pool.acquire() as conn, conn.transaction():
        previous = await prior_response(conn, user["id"], "payment", idempotency_key)
        if previous:
            return previous
        recipient = await conn.fetchrow("SELECT id,name,handle FROM users WHERE lower(handle)=$1", recipient_handle)
        if recipient is None:
            raise problem(404, "USER_NOT_FOUND", "That PayCeylon user could not be found.")
        if recipient["id"] == user["id"]:
            raise problem(400, "VALIDATION_FAILED", "You cannot send money to yourself.", "to")
        fee = NETWORK_FEES.get(asset)
        await conn.execute(
            "INSERT INTO wallet_balances(user_id,asset,balance) VALUES($1,$2,0) ON CONFLICT DO NOTHING",
            recipient["id"], asset,
        )
        # Lock both balances in user_id order so opposite-direction payments can't deadlock.
        locked = await conn.fetch(
            """SELECT user_id,balance FROM wallet_balances WHERE asset=$1 AND user_id=ANY($2::uuid[])
            ORDER BY user_id FOR UPDATE""",
            asset, [user["id"], recipient["id"]],
        )
        sender_balance = next((row["balance"] for row in locked if row["user_id"] == user["id"]), None)
        if sender_balance is None or sender_balance < value + (fee or 0):
            raise problem(402, "INSUFFICIENT_FUNDS", f"Insufficient {asset} balance.")
        await conn.execute(
            "UPDATE wallet_balances SET balance=balance-$3 WHERE user_id=$1 AND asset=$2",
            user["id"], asset, value + (fee or 0),
        )
        await conn.execute(
            "UPDATE wallet_balances SET balance=balance+$3 WHERE user_id=$1 AND asset=$2",
            recipient["id"], asset, value,
        )
        sent = await add_transaction(
            conn, user_id=user["id"], direction="OUTGOING", currency=asset, value=value,
            wallet="FIAT" if asset == "LKR" else "CRYPTO", name=recipient["name"],
            handle=recipient["handle"], fee=fee, fee_currency=asset if fee else None,
        )
        received = await add_transaction(
            conn, user_id=recipient["id"], direction="INCOMING", currency=asset, value=value,
            wallet="FIAT" if asset == "LKR" else "CRYPTO", name=user["name"], handle=user["handle"],
        )
        await emit_notice(conn, user["id"], "TRANSACTION", "Payment sent", f"{asset} {value} sent to @{recipient['handle']}.", sent["id"])
        await emit_notice(conn, recipient["id"], "TRANSACTION", "Payment received", f"{user['name']} sent you {asset} {value}.", received["id"])
        result = await transaction_json(conn, sent)
        await save_response(conn, user["id"], "payment", idempotency_key, result)
    return result


@app.get("/v1/rates")
async def rates() -> dict[str, Any]:
    return {
        "base": "LKR",
        "prices": {asset: money(price, "LKR") for asset, price in PRICES_LKR.items()},
        "feeRate": "0.005",
    }


@app.post("/v1/swaps/quote")
async def quote_swap(body: SwapQuoteBody, user: User) -> dict[str, Any]:
    source, target = body.from_asset.upper(), body.to_asset.upper()
    if source not in ASSETS or target not in ASSETS or source == target:
        raise problem(400, "VALIDATION_FAILED", "Choose two different supported currencies.")
    value = amount(body.amount, source)
    quantum = Decimal(1).scaleb(-DECIMALS[source])
    fee = (value * Decimal("0.005")).quantize(quantum, rounding=ROUND_UP)
    rate = PRICES_LKR[source] / PRICES_LKR[target]
    # Multiply before dividing: a pre-rounded rate (1/300 = 0.00333…) would round receive down a unit.
    receive = ((value - fee) * PRICES_LKR[source] / PRICES_LKR[target]).quantize(
        Decimal(1).scaleb(-DECIMALS[target]), rounding=ROUND_DOWN,
    )
    if receive <= 0:
        raise problem(400, "VALIDATION_FAILED", "The swap amount is too small.")
    expires = datetime.now(timezone.utc) + timedelta(seconds=60)
    quote_id = await app.state.pool.fetchval(
        """INSERT INTO swap_quotes(user_id,from_asset,to_asset,amount,fee,receive,rate,expires_at)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id""",
        user["id"], source, target, value, fee, receive, rate, expires,
    )
    return {
        "quoteId": str(quote_id), "from": source, "to": target, "amount": money(value, source),
        "fee": money(fee, source), "receive": money(receive, target), "rate": money(rate),
        "expiresAt": int(expires.timestamp() * 1000),
    }


@app.post("/v1/swaps", status_code=201)
async def complete_swap(
    body: SwapBody,
    user: User,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> dict[str, Any]:
    async with app.state.pool.acquire() as conn, conn.transaction():
        previous = await prior_response(conn, user["id"], "swap", idempotency_key)
        if previous:
            return previous
        quote = await conn.fetchrow(
            "SELECT * FROM swap_quotes WHERE id=$1 AND user_id=$2 FOR UPDATE", body.quote_id, user["id"],
        )
        if quote is None or quote["expires_at"] < datetime.now(timezone.utc):
            raise problem(409, "QUOTE_EXPIRED", "This quote has expired. Please request a new one.")
        balance = await conn.fetchval(
            "SELECT balance FROM wallet_balances WHERE user_id=$1 AND asset=$2 FOR UPDATE",
            user["id"], quote["from_asset"],
        )
        if balance is None or balance < quote["amount"]:
            raise problem(402, "INSUFFICIENT_FUNDS", f"Insufficient {quote['from_asset']} balance.")
        await conn.execute(
            "UPDATE wallet_balances SET balance=balance-$3 WHERE user_id=$1 AND asset=$2",
            user["id"], quote["from_asset"], quote["amount"],
        )
        await conn.execute(
            """INSERT INTO wallet_balances(user_id,asset,balance) VALUES($1,$2,$3)
            ON CONFLICT(user_id,asset) DO UPDATE SET balance=wallet_balances.balance+EXCLUDED.balance""",
            user["id"], quote["to_asset"], quote["receive"],
        )
        tx = await add_transaction(
            conn, user_id=user["id"], direction="SWAP", currency=quote["from_asset"],
            value=quote["amount"], wallet="FIAT" if quote["from_asset"] == "LKR" else "CRYPTO",
            name="PayCeylon Swap", handle="payceylon", fee=quote["fee"],
            fee_currency=quote["from_asset"], to_currency=quote["to_asset"], to_amount=quote["receive"],
        )
        await emit_notice(conn, user["id"], "TRANSACTION", "Swap complete", f"{quote['amount']} {quote['from_asset']} swapped to {quote['to_asset']}.", tx["id"])
        await conn.execute("DELETE FROM swap_quotes WHERE id=$1", body.quote_id)
        result = await transaction_json(conn, tx)
        await save_response(conn, user["id"], "swap", idempotency_key or "", result)
    return result


@app.get("/v1/payment-methods")
async def list_payment_methods(user: User) -> dict[str, list[dict[str, Any]]]:
    rows = await app.state.pool.fetch(
        "SELECT id,type,name,brand,last4,holder,detail FROM payment_methods WHERE user_id=$1 ORDER BY created_at",
        user["id"],
    )
    return {"items": [
        {"id": str(row["id"]), "type": row["type"], "name": row["name"], "brand": row["brand"],
         "last4": row["last4"].strip(), "holder": row["holder"], "detail": row["detail"]}
        for row in rows
    ]}


@app.post("/v1/payment-methods/cards", status_code=201)
async def add_card(body: CardBody, user: User) -> dict[str, Any]:
    if not body.token.startswith("demo_tok_"):
        raise problem(400, "VALIDATION_FAILED", "This demo accepts simulated card tokens only.", "token")
    if body.holder.strip().lower() != user["name"].strip().lower():
        raise problem(400, "VALIDATION_FAILED", "The cardholder name must match your profile.", "holder")
    async with app.state.pool.acquire() as conn, conn.transaction():
        await conn.execute(
            """INSERT INTO demo_card_tokens(user_id,token,brand,last4,expiry) VALUES($1,$2,$3,$4,$5)
            ON CONFLICT DO NOTHING""",
            user["id"], body.token, body.brand.upper(), body.last4, body.expiry,
        )
        row = await conn.fetchrow(
            """INSERT INTO payment_methods(user_id,type,name,brand,last4,holder,detail)
            VALUES($1,'CARD',$2,$3,$4,$5,$6) RETURNING *""",
            user["id"], body.brand, body.brand.upper(), body.last4, body.holder.strip(), f"Expires {body.expiry}",
        )
        await emit_notice(conn, user["id"], "SECURITY", "Card added", f"{body.brand} •••• {body.last4} can now be used in the demo.")
    return {"id": str(row["id"]), "type": "CARD", "name": row["name"], "brand": row["brand"],
            "last4": row["last4"].strip(), "holder": row["holder"], "detail": row["detail"]}


@app.post("/v1/payment-methods/banks", status_code=201)
async def add_bank(body: BankBody, user: User) -> dict[str, Any]:
    if body.bank not in BANKS:
        raise problem(400, "VALIDATION_FAILED", "Choose a supported Sri Lankan bank.", "bank")
    if body.holder.strip().lower() != user["name"].strip().lower():
        raise problem(400, "VALIDATION_FAILED", "The account holder name must match your profile.", "holder")
    account_type = "Savings" if body.account_type == "SAVINGS" else "Current"
    row = await app.state.pool.fetchrow(
        """INSERT INTO payment_methods(user_id,type,name,last4,holder,detail,account_type)
        VALUES($1,'BANK',$2,$3,$4,$5,$6) RETURNING *""",
        user["id"], body.bank, body.account_number[-4:], body.holder.strip(), f"{account_type} account", body.account_type,
    )
    await app.state.pool.execute(
        "INSERT INTO notifications(user_id,kind,title,body) VALUES($1,'SECURITY','Bank account linked',$2)",
        user["id"], f"{body.bank} •••• {body.account_number[-4:]} linked for simulated transfers.",
    )
    return {"id": str(row["id"]), "type": "BANK", "name": row["name"], "brand": None,
            "last4": row["last4"].strip(), "holder": row["holder"], "detail": row["detail"]}


@app.delete("/v1/payment-methods/{method_id}", status_code=204)
async def delete_payment_method(method_id: UUID, user: User) -> None:
    async with app.state.pool.acquire() as conn, conn.transaction():
        removed = await conn.fetchrow(
            "DELETE FROM payment_methods WHERE id=$1 AND user_id=$2 RETURNING type,name,last4",
            method_id, user["id"],
        )
        if removed is None:
            raise problem(404, "PAYMENT_METHOD_NOT_FOUND", "Payment method not found.")
        kind = "Card" if removed["type"] == "CARD" else "Bank account"
        await emit_notice(
            conn, user["id"], "SECURITY", f"{kind} removed",
            f"{removed['name']} •••• {removed['last4'].strip()} is no longer linked.",
        )


@app.post("/v1/topups", status_code=201)
async def top_up(
    body: ExternalFundsBody,
    user: User,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> dict[str, Any]:
    value = amount(body.amount, "LKR")
    if value > Decimal("500000"):
        raise problem(403, "LIMIT_EXCEEDED", "Top-ups are limited to LKR 500,000 at a time.")
    async with app.state.pool.acquire() as conn, conn.transaction():
        previous = await prior_response(conn, user["id"], "top-up", idempotency_key)
        if previous:
            return previous
        method = await conn.fetchrow(
            "SELECT * FROM payment_methods WHERE id=$1 AND user_id=$2", body.payment_method_id, user["id"],
        )
        if method is None:
            raise problem(404, "PAYMENT_METHOD_NOT_FOUND", "Payment method not found.")
        fee = (value * Decimal("0.015")).quantize(Decimal("0.01"), rounding=ROUND_UP) if method["type"] == "CARD" else Decimal("0")
        await conn.execute(
            """UPDATE wallet_balances SET balance=balance+$2 WHERE user_id=$1 AND asset='LKR'""",
            user["id"], value,
        )
        tx = await add_transaction(
            conn, user_id=user["id"], direction="TOP_UP", currency="LKR", value=value,
            wallet="FIAT", name=method["name"], handle=f"•••• {method['last4'].strip()}",
            fee=fee or None, fee_currency="LKR" if fee else None,
        )
        await emit_notice(conn, user["id"], "TRANSACTION", "Top-up successful", f"LKR {value} added from {method['name']}.", tx["id"])
        result = await transaction_json(conn, tx)
        await save_response(conn, user["id"], "top-up", idempotency_key or "", result)
    return result


@app.post("/v1/withdrawals", status_code=201)
async def withdraw(
    body: ExternalFundsBody,
    user: User,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> dict[str, Any]:
    value = amount(body.amount, "LKR")
    async with app.state.pool.acquire() as conn, conn.transaction():
        previous = await prior_response(conn, user["id"], "withdrawal", idempotency_key)
        if previous:
            return previous
        method = await conn.fetchrow(
            "SELECT * FROM payment_methods WHERE id=$1 AND user_id=$2 AND type='BANK'",
            body.payment_method_id, user["id"],
        )
        if method is None:
            raise problem(404, "PAYMENT_METHOD_NOT_FOUND", "Choose one of your linked bank accounts.")
        fee = Decimal("25.00")
        balance = await conn.fetchval(
            "SELECT balance FROM wallet_balances WHERE user_id=$1 AND asset='LKR' FOR UPDATE", user["id"],
        )
        if balance is None or balance < value + fee:
            raise problem(402, "INSUFFICIENT_FUNDS", "Insufficient LKR balance.")
        await conn.execute(
            "UPDATE wallet_balances SET balance=balance-$2 WHERE user_id=$1 AND asset='LKR'",
            user["id"], value + fee,
        )
        # Starts PENDING like a bank transfer; settle_withdrawals() completes it later.
        tx = await add_transaction(
            conn, user_id=user["id"], direction="WITHDRAWAL", currency="LKR", value=value,
            wallet="FIAT", name=method["name"], handle=f"•••• {method['last4'].strip()}",
            fee=fee, fee_currency="LKR", status="PENDING",
        )
        await emit_notice(
            conn, user["id"], "TRANSACTION", "Withdrawal on its way",
            f"LKR {money(value, 'LKR')} to {method['name']}. Arrives in about {settle_minutes()}.", tx["id"],
        )
        result = await transaction_json(conn, tx)
        await save_response(conn, user["id"], "withdrawal", idempotency_key or "", result)
    return result


# Billers. BILL billers have a (simulated) amount due per account; RELOAD billers take any amount
# for a mobile number. Nothing is sent to the real companies.
BILLERS = {
    "ceb": {"name": "Ceylon Electricity Board", "shortName": "CEB", "category": "ELECTRICITY", "kind": "BILL",
            "accountLabel": "Account number", "accountPattern": r"\d{10}", "accountHint": "10 digits"},
    "leco": {"name": "Lanka Electricity Company", "shortName": "LECO", "category": "ELECTRICITY", "kind": "BILL",
             "accountLabel": "Account number", "accountPattern": r"\d{10}", "accountHint": "10 digits"},
    "nwsdb": {"name": "National Water Supply & Drainage Board", "shortName": "Water Board", "category": "WATER",
              "kind": "BILL", "accountLabel": "Account number", "accountPattern": r"\d{10,12}",
              "accountHint": "10 to 12 digits"},
    "dialog": {"name": "Dialog", "shortName": "Dialog", "category": "MOBILE", "kind": "RELOAD",
               "accountLabel": "Mobile number", "accountPattern": r"07\d{8}", "accountHint": "07X XXX XXXX"},
    "mobitel": {"name": "SLT-Mobitel", "shortName": "Mobitel", "category": "MOBILE", "kind": "RELOAD",
                "accountLabel": "Mobile number", "accountPattern": r"07\d{8}", "accountHint": "07X XXX XXXX"},
    "hutch": {"name": "Hutch", "shortName": "Hutch", "category": "MOBILE", "kind": "RELOAD",
              "accountLabel": "Mobile number", "accountPattern": r"07\d{8}", "accountHint": "07X XXX XXXX"},
    "slt": {"name": "SLT-Mobitel Fibre", "shortName": "SLT Fibre", "category": "INTERNET_TV", "kind": "BILL",
            "accountLabel": "Account number", "accountPattern": r"\d{10}", "accountHint": "10 digits"},
    "dialogtv": {"name": "Dialog Television", "shortName": "Dialog TV", "category": "INTERNET_TV", "kind": "BILL",
                 "accountLabel": "Account number", "accountPattern": r"\d{8}", "accountHint": "8 digits"},
}
BILL_MAX = Decimal("500000.00")
RELOAD_MIN = Decimal("20.00")
RELOAD_MAX = Decimal("10000.00")


class BillPayBody(Body):
    biller_id: str = Field(alias="billerId")
    account: str
    amount: str


def biller_account(biller_id: str, account: str) -> tuple[dict[str, Any], str]:
    biller = BILLERS.get(biller_id)
    if biller is None:
        raise problem(404, "BILLER_NOT_FOUND", "That biller isn't supported.")
    cleaned = re.sub(r"[\s/-]", "", account)
    if biller["kind"] == "RELOAD" and re.fullmatch(r"(\+94|94)?7\d{8}", cleaned):
        cleaned = "0" + cleaned[-9:]
    if not re.fullmatch(biller["accountPattern"], cleaned):
        raise problem(400, "INVALID_ACCOUNT", f"Enter a valid {biller['accountLabel'].lower()} ({biller['accountHint']}).", "account")
    return biller, cleaned


def bill_due(biller_id: str, account: str) -> tuple[Decimal, datetime]:
    """The simulated outstanding bill: stable per account and month."""
    month = datetime.now(timezone.utc).strftime("%Y-%m")
    seed = int.from_bytes(hashlib.sha256(f"bill:{biller_id}:{account}:{month}".encode()).digest()[:4], "big")
    due = Decimal(800 + seed % 9200) + Decimal(seed % 4) * Decimal("0.25")
    due_date = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=5 + seed % 20)
    return due.quantize(Decimal("0.01")), due_date


def biller_json(biller_id: str) -> dict[str, Any]:
    biller = BILLERS[biller_id]
    return {"id": biller_id, **{key: value for key, value in biller.items() if key != "accountPattern"}}


@app.get("/v1/billers")
async def list_billers(_user: User) -> dict[str, list[dict[str, Any]]]:
    return {"items": [biller_json(biller_id) for biller_id in BILLERS]}


@app.get("/v1/billers/{biller_id}/bill")
async def lookup_bill(biller_id: str, user: User, account: str = Query(max_length=20)) -> dict[str, Any]:
    biller, cleaned = biller_account(biller_id, account)
    if biller["kind"] != "BILL":
        raise problem(400, "NO_BILL", "Mobile reloads have no bill; enter any amount.")
    due, due_date = bill_due(biller_id, cleaned)
    return {
        "billerId": biller_id,
        "account": cleaned,
        "accountName": user["name"],
        "amountDue": money(due, "LKR"),
        "dueDate": int(due_date.timestamp() * 1000),
    }


@app.post("/v1/bills/payments", status_code=201)
async def pay_bill(
    body: BillPayBody,
    user: User,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> dict[str, Any]:
    biller, account = biller_account(body.biller_id, body.account)
    value = amount(body.amount, "LKR")
    low, high = (RELOAD_MIN, RELOAD_MAX) if biller["kind"] == "RELOAD" else (Decimal("1.00"), BILL_MAX)
    if not low <= value <= high:
        raise problem(400, "VALIDATION_FAILED", f"Enter between LKR {money(low, 'LKR')} and LKR {money(high, 'LKR')}.", "amount")
    async with app.state.pool.acquire() as conn, conn.transaction():
        previous = await prior_response(conn, user["id"], "bill", idempotency_key)
        if previous:
            return previous
        balance = await conn.fetchval(
            "SELECT balance FROM wallet_balances WHERE user_id=$1 AND asset='LKR' FOR UPDATE", user["id"],
        )
        if balance is None or balance < value:
            raise problem(402, "INSUFFICIENT_FUNDS", "Insufficient LKR balance.")
        await conn.execute(
            "UPDATE wallet_balances SET balance=balance-$2 WHERE user_id=$1 AND asset='LKR'", user["id"], value,
        )
        tx = await add_transaction(
            conn, user_id=user["id"], direction="BILL", currency="LKR", value=value,
            wallet="FIAT", name=biller["shortName"], handle=account,
        )
        await conn.execute(
            """INSERT INTO saved_billers(user_id,biller_id,account,last_amount,last_paid_at)
            VALUES($1,$2,$3,$4,now())
            ON CONFLICT (user_id,biller_id,account) DO UPDATE SET last_amount=$4, last_paid_at=now()""",
            user["id"], body.biller_id, account, value,
        )
        what = "Reload" if biller["kind"] == "RELOAD" else "Bill paid"
        await emit_notice(
            conn, user["id"], "TRANSACTION", what,
            f"LKR {money(value, 'LKR')} to {biller['shortName']} ({account}). Simulated: no real payment was made.",
            tx["id"],
        )
        result = await transaction_json(conn, tx)
        await save_response(conn, user["id"], "bill", idempotency_key or "", result)
    return result


@app.get("/v1/bills/saved")
async def saved_billers(user: User) -> dict[str, list[dict[str, Any]]]:
    rows = await app.state.pool.fetch(
        "SELECT * FROM saved_billers WHERE user_id=$1 ORDER BY last_paid_at DESC LIMIT 20", user["id"],
    )
    return {
        "items": [
            {
                "billerId": row["biller_id"],
                "account": row["account"],
                "lastAmount": money(row["last_amount"], "LKR"),
                "lastPaidAt": int(row["last_paid_at"].timestamp() * 1000),
            }
            for row in rows
            if row["biller_id"] in BILLERS
        ],
    }


@app.delete("/v1/bills/saved/{biller_id}/{account}", status_code=204)
async def remove_saved_biller(biller_id: str, account: str, user: User) -> None:
    await app.state.pool.execute(
        "DELETE FROM saved_billers WHERE user_id=$1 AND biller_id=$2 AND account=$3", user["id"], biller_id, account,
    )


@app.get("/v1/notifications")
async def list_notifications(
    user: User,
    cursor: str | None = None,
    limit: int = Query(default=50, ge=1, le=100),
) -> dict[str, Any]:
    before, before_id = decode_cursor(cursor)
    rows = await app.state.pool.fetch(
        """SELECT id,kind,title,body,transaction_id,read,created_at FROM notifications
        WHERE user_id=$1 AND ($2::timestamptz IS NULL OR (created_at, id) < ($2, $3::uuid))
        ORDER BY created_at DESC, id DESC LIMIT $4""",
        user["id"], before, before_id, limit + 1,
    )
    unread = await app.state.pool.fetchval(
        "SELECT count(*) FROM notifications WHERE user_id=$1 AND read=false", user["id"],
    )
    more = len(rows) > limit
    rows = rows[:limit]
    items = [{
        "id": str(row["id"]), "kind": row["kind"], "title": row["title"], "body": row["body"],
        "timestamp": int(row["created_at"].timestamp() * 1000),
        "transactionId": str(row["transaction_id"]) if row["transaction_id"] else None,
        "read": row["read"],
    } for row in rows]
    next_cursor = encode_cursor(rows[-1]) if more and rows else None
    return {"items": items, "nextCursor": next_cursor, "unreadCount": unread}


@app.post("/v1/notifications/read", status_code=204)
async def mark_notifications_read(body: NotificationReadBody, user: User) -> None:
    if body.all:
        await app.state.pool.execute("UPDATE notifications SET read=true WHERE user_id=$1", user["id"])
    elif body.ids:
        await app.state.pool.execute(
            "UPDATE notifications SET read=true WHERE user_id=$1 AND id=ANY($2::uuid[])",
            user["id"], body.ids,
        )


@app.post("/v1/devices", status_code=204)
async def register_device(body: DeviceBody, user: User) -> None:
    async with app.state.pool.acquire() as conn, conn.transaction():
        # A phone belongs to whoever signed in on it last.
        await conn.execute("DELETE FROM device_tokens WHERE fcm_token=$1 AND user_id<>$2", body.fcm_token, user["id"])
        await conn.execute(
            """INSERT INTO device_tokens(user_id,fcm_token,platform) VALUES($1,$2,$3)
            ON CONFLICT(user_id,fcm_token) DO UPDATE SET platform=EXCLUDED.platform,created_at=now()""",
            user["id"], body.fcm_token, body.platform,
        )


@app.delete("/v1/devices/{fcm_token}", status_code=204)
async def unregister_device(fcm_token: str, user: User) -> None:
    await app.state.pool.execute("DELETE FROM device_tokens WHERE user_id=$1 AND fcm_token=$2", user["id"], fcm_token)


@app.delete("/v1/auth/logout", status_code=204)
async def logout(user: User) -> None:
    try:
        # revoke_refresh_tokens is synchronous; awaiting its None result made every logout a 503.
        await run_in_threadpool(auth.revoke_refresh_tokens, user["firebase_uid"])
    except Exception:
        raise problem(503, "AUTH_PROVIDER_UNAVAILABLE", "Could not sign out. Please try again.") from None
