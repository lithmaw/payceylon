"""Endpoint tests against a real PostgreSQL, with Firebase Authentication faked.

Uses TEST_DATABASE_URL (an empty, disposable database: its tables are dropped) or, if the
`pgserver` package is installed, an embedded PostgreSQL. Skipped when neither is available.
"""
import asyncio
import itertools
import os
import re
import secrets
import tempfile
import unittest
from decimal import Decimal
from types import SimpleNamespace
from unittest import mock

import asyncpg
import httpx

from backend import main

_phones = itertools.count(1)
_server = None
_tempdir = None


def setUpModule():
    global _server, _tempdir
    url = os.environ.get("TEST_DATABASE_URL")
    if not url:
        try:
            import pgserver
        except ImportError:
            raise unittest.SkipTest("Set TEST_DATABASE_URL or pip install pgserver to run API tests.")
        _tempdir = tempfile.TemporaryDirectory()
        _server = pgserver.get_server(_tempdir.name, cleanup_mode="stop")
        url = _server.get_uri()

    async def reset():
        conn = await asyncpg.connect(url)
        try:
            await conn.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
        finally:
            await conn.close()

    asyncio.run(reset())
    os.environ.pop("FIREBASE_AUTH_EMULATOR_HOST", None)
    for name, value in (("database_url", url), ("app_env", "test")):
        patcher = mock.patch.object(main.settings, name, value)
        patcher.start()
        unittest.addModuleCleanup(patcher.stop)


def tearDownModule():
    if _server is not None:
        _server.cleanup()
        _tempdir.cleanup()


def fake_verify(token, check_revoked=False):
    kind, uid, phone = token.split("|")
    if kind != "tok":
        raise ValueError("invalid token")
    return {"uid": uid, "phone_number": phone}


class ApiTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.revoked = []
        self.firebase_phones = {}  # phone -> Firebase uid
        fakes = (
            ("verify_id_token", fake_verify),
            ("revoke_refresh_tokens", self.revoked.append),
            ("get_user_by_phone_number", self.fake_get_user_by_phone),
            ("create_user", self.fake_create_user),
            ("create_custom_token", lambda uid, claims: f"custom|{uid}|{claims['phone']}".encode()),
            ("update_user", self.fake_update_user),
        )
        for name, fake in fakes:
            patcher = mock.patch.object(main.auth, name, fake)
            patcher.start()
            self.addCleanup(patcher.stop)
        self.lifespan = main.app.router.lifespan_context(main.app)
        await self.lifespan.__aenter__()
        transport = httpx.ASGITransport(app=main.app, raise_app_exceptions=False)
        self.client = httpx.AsyncClient(transport=transport, base_url="http://test")

    async def asyncTearDown(self):
        await self.client.aclose()
        await self.lifespan.__aexit__(None, None, None)

    def fake_get_user_by_phone(self, phone):
        if phone not in self.firebase_phones:
            raise main.auth.UserNotFoundError("No user record found for the given identifier.")
        return SimpleNamespace(uid=self.firebase_phones[phone])

    def fake_create_user(self, phone_number):
        self.firebase_phones[phone_number] = f"fb-{secrets.token_hex(4)}"
        return SimpleNamespace(uid=self.firebase_phones[phone_number])

    def fake_update_user(self, uid, phone_number):
        if self.firebase_phones.get(phone_number, uid) != uid:
            raise main.auth.PhoneNumberAlreadyExistsError("The user with the provided phone number already exists.", None)
        self.firebase_phones = {phone: owner for phone, owner in self.firebase_phones.items() if owner != uid}
        self.firebase_phones[phone_number] = uid

    async def request_code(self, phone):
        with self.assertLogs("payceylon", "WARNING") as logs:
            response = await self.client.post("/v1/auth/otp", json={"phone": phone})
        self.assertEqual(response.status_code, 200, response.text)
        return re.search(rf"OTP for {re.escape(phone)}: (\d{{6}})", logs.output[-1]).group(1)

    async def sign_in_with_code(self, phone):
        """Request, read and confirm a code; returns the ID token the app would then hold."""
        code = await self.request_code(phone)
        response = await self.client.post("/v1/auth/otp/verify", json={"phone": phone, "code": code})
        self.assertEqual(response.status_code, 200, response.text)
        _, uid, claimed_phone = response.json()["customToken"].split("|")
        self.assertEqual(claimed_phone, phone)
        return f"tok|{uid}|{phone}"

    async def sign_up(self, name="Kasun Perera", phone=None):
        phone = phone or f"+9477{next(_phones):07d}"
        token = f"tok|uid-{secrets.token_hex(4)}|{phone}"
        response = await self.client.post("/v1/auth/verify", json={"idToken": token})
        self.assertEqual(response.status_code, 200, response.text)
        handle = f"u{secrets.token_hex(4)}"
        response = await self.client.patch("/v1/me", headers=self.auth(token), json={"name": name, "handle": handle})
        self.assertEqual(response.status_code, 200, response.text)
        return token, handle

    @staticmethod
    def auth(token, key=None):
        headers = {"Authorization": f"Bearer {token}"}
        if key:
            headers["Idempotency-Key"] = key
        return headers

    async def balance(self, token, asset="LKR"):
        response = await self.client.get("/v1/wallet/balances", headers=self.auth(token))
        return Decimal(response.json()["balances"][asset])

    async def test_sign_up_creates_profile_and_balances(self):
        token, handle = await self.sign_up()
        me = (await self.client.get("/v1/me", headers=self.auth(token))).json()
        self.assertEqual(me["handle"], handle)
        balances = (await self.client.get("/v1/wallet/balances", headers=self.auth(token))).json()["balances"]
        self.assertEqual(balances["LKR"], "32483.75")
        self.assertEqual(balances["BTC"], "0.042150")

    async def test_code_from_the_log_signs_in_the_same_account_each_time(self):
        phone = f"+9477{next(_phones):07d}"
        token = await self.sign_in_with_code(phone)
        first = (await self.client.post("/v1/auth/verify", json={"idToken": token})).json()["user"]
        again = await self.sign_in_with_code(phone)
        second = (await self.client.post("/v1/auth/verify", json={"idToken": again})).json()["user"]
        self.assertEqual(token, again)
        self.assertEqual(first["handle"], second["handle"])
        self.assertEqual(first["phone"], phone)

    async def test_codes_are_random_and_single_use(self):
        phone = f"+9477{next(_phones):07d}"
        code = await self.request_code(phone)
        verified = await self.client.post("/v1/auth/otp/verify", json={"phone": phone, "code": code})
        reused = await self.client.post("/v1/auth/otp/verify", json={"phone": phone, "code": code})
        self.assertEqual(verified.status_code, 200)
        self.assertEqual(reused.status_code, 401)
        self.assertEqual(reused.json()["error"]["code"], "INVALID_OTP")
        self.assertNotEqual(code, await self.request_code(phone))

    async def test_resend_is_throttled(self):
        phone = f"+9477{next(_phones):07d}"
        await self.request_code(phone)
        response = await self.client.post("/v1/auth/otp", json={"phone": phone})
        self.assertEqual(response.status_code, 429)

    async def test_wrong_codes_lock_the_code(self):
        phone = f"+9477{next(_phones):07d}"
        code = await self.request_code(phone)
        wrong = f"{(int(code) + 1) % 1_000_000:06d}"
        for _ in range(main.OTP_MAX_ATTEMPTS):
            response = await self.client.post("/v1/auth/otp/verify", json={"phone": phone, "code": wrong})
            self.assertEqual(response.status_code, 401)
        response = await self.client.post("/v1/auth/otp/verify", json={"phone": phone, "code": code})
        self.assertEqual(response.status_code, 401)
        self.assertIn("Too many", response.json()["error"]["message"])

    async def test_code_request_rejects_non_sri_lankan_number(self):
        response = await self.client.post("/v1/auth/otp", json={"phone": "+15551234567"})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "INVALID_PHONE")

    async def test_phone_change_needs_a_code_for_the_new_number(self):
        token = await self.sign_in_with_code(f"+9477{next(_phones):07d}")
        await self.client.post("/v1/auth/verify", json={"idToken": token})
        new_phone = f"+9477{next(_phones):07d}"
        wrong = await self.client.patch("/v1/me/phone", headers=self.auth(token), json={"phone": new_phone, "code": "000000"})
        self.assertEqual(wrong.status_code, 401)
        code = await self.request_code(new_phone)
        response = await self.client.patch("/v1/me/phone", headers=self.auth(token), json={"phone": new_phone, "code": code})
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["phone"], new_phone)
        self.assertEqual(self.firebase_phones[new_phone], token.split("|")[1])

    async def test_phone_change_to_a_taken_number_conflicts(self):
        token = await self.sign_in_with_code(f"+9477{next(_phones):07d}")
        await self.client.post("/v1/auth/verify", json={"idToken": token})
        other = await self.sign_in_with_code(f"+9477{next(_phones):07d}")
        await self.client.post("/v1/auth/verify", json={"idToken": other})
        taken = other.split("|")[2]
        code = await self.request_code(taken)
        response = await self.client.patch("/v1/me/phone", headers=self.auth(token), json={"phone": taken, "code": code})
        self.assertEqual(response.status_code, 409)
        me = (await self.client.get("/v1/me", headers=self.auth(token))).json()
        self.assertEqual(me["phone"], token.split("|")[2])

    async def test_sign_up_rejects_non_sri_lankan_number(self):
        response = await self.client.post("/v1/auth/verify", json={"idToken": "tok|uid-x|+15551234567"})
        self.assertEqual(response.status_code, 400)

    async def test_new_number_is_never_merged_into_a_demo_peer(self):
        await self.sign_up()  # seeds demo peers
        response = await self.client.post("/v1/auth/verify", json={"idToken": "tok|uid-z|+94770000000"})
        self.assertNotEqual(response.json()["user"]["handle"], "sajjad67")

    async def test_duplicate_handle_conflicts(self):
        token, _ = await self.sign_up()
        _, taken = await self.sign_up()
        response = await self.client.patch("/v1/me", headers=self.auth(token), json={"handle": taken})
        self.assertEqual(response.status_code, 409)

    async def test_null_setting_is_a_validation_error(self):
        token, _ = await self.sign_up()
        response = await self.client.patch("/v1/me/settings", headers=self.auth(token), json={"hideBalance": None})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["field"], "hideBalance")

    async def test_payment_is_idempotent(self):
        sender, _ = await self.sign_up()
        receiver, receiver_handle = await self.sign_up("Nimal Silva")
        body = {"to": f"@{receiver_handle}", "amount": "100.00", "currency": "LKR"}
        first = await self.client.post("/v1/payments", headers=self.auth(sender, "pay-1"), json=body)
        again = await self.client.post("/v1/payments", headers=self.auth(sender, "pay-1"), json=body)
        self.assertEqual(first.status_code, 201, first.text)
        self.assertEqual(first.json()["id"], again.json()["id"])
        self.assertEqual(first.json()["amount"], "100.00")
        self.assertEqual(await self.balance(receiver), Decimal("32583.75"))
        self.assertEqual(await self.balance(sender), Decimal("32383.75"))

    async def test_crypto_payment_charges_network_fee(self):
        sender, _ = await self.sign_up()
        _, receiver_handle = await self.sign_up("Nimal Silva")
        body = {"to": receiver_handle, "amount": "1.00", "currency": "SOL"}
        response = await self.client.post("/v1/payments", headers=self.auth(sender, "sol-1"), json=body)
        self.assertEqual(response.json()["fee"], "0.000005")
        self.assertEqual(await self.balance(sender, "SOL"), Decimal("40.199995"))

    async def test_payment_rejects_insufficient_funds_and_self(self):
        sender, handle = await self.sign_up()
        _, other = await self.sign_up("Nimal Silva")
        cases = (({"to": other, "amount": "99999999", "currency": "LKR"}, 402),
                 ({"to": handle, "amount": "1", "currency": "LKR"}, 400))
        for index, (body, status) in enumerate(cases):
            response = await self.client.post("/v1/payments", headers=self.auth(sender, f"k{index}"), json=body)
            self.assertEqual(response.status_code, status, response.text)

    async def test_opposite_direction_payments_do_not_deadlock(self):
        first, first_handle = await self.sign_up()
        second, second_handle = await self.sign_up("Nimal Silva")

        def pay(index):
            token, to = (first, second_handle) if index % 2 else (second, first_handle)
            body = {"to": to, "amount": "1.00", "currency": "LKR"}
            return self.client.post("/v1/payments", headers=self.auth(token, f"c{index}"), json=body)

        responses = await asyncio.gather(*(pay(index) for index in range(40)))
        self.assertEqual({response.status_code for response in responses}, {201})
        self.assertEqual(await self.balance(first) + await self.balance(second), Decimal("64967.50"))

    async def test_swap_receives_exact_amount_and_quote_is_single_use(self):
        token, _ = await self.sign_up()
        quote = (await self.client.post("/v1/swaps/quote", headers=self.auth(token),
                                        json={"from": "LKR", "to": "USDT", "amount": "3000"})).json()
        self.assertEqual((quote["amount"], quote["fee"], quote["receive"]), ("3000.00", "15.00", "9.95"))
        swap = await self.client.post("/v1/swaps", headers=self.auth(token, "s1"), json={"quoteId": quote["quoteId"]})
        self.assertEqual(swap.status_code, 201, swap.text)
        self.assertEqual(await self.balance(token, "USDT"), Decimal("1294.27"))
        reuse = await self.client.post("/v1/swaps", headers=self.auth(token, "s2"), json={"quoteId": quote["quoteId"]})
        self.assertEqual(reuse.status_code, 409)

    async def test_card_top_up_credits_full_amount(self):
        token, _ = await self.sign_up()
        card = (await self.client.post("/v1/payment-methods/cards", headers=self.auth(token), json={
            "token": "demo_tok_abcdef12", "holder": "Kasun Perera", "brand": "Visa",
            "last4": "4242", "expiry": "08/29"})).json()
        response = await self.client.post("/v1/topups", headers=self.auth(token, "t1"),
                                          json={"amount": "1000.00", "paymentMethodId": card["id"]})
        # The 1.5% fee is charged to the card on top (docs/BACKEND_API.md), not taken from the wallet.
        self.assertEqual(response.json()["fee"], "15.00")
        self.assertEqual(await self.balance(token), Decimal("33483.75"))

    async def test_withdrawal_only_to_bank_and_charges_fee(self):
        token, _ = await self.sign_up()
        bank = (await self.client.post("/v1/payment-methods/banks", headers=self.auth(token), json={
            "bank": "Commercial Bank", "accountNumber": "123456789", "holder": "Kasun Perera",
            "accountType": "SAVINGS"})).json()
        response = await self.client.post("/v1/withdrawals", headers=self.auth(token, "w1"),
                                          json={"amount": "500.00", "paymentMethodId": bank["id"]})
        self.assertEqual(response.status_code, 201, response.text)
        self.assertEqual(await self.balance(token), Decimal("31958.75"))

    async def _bank_withdrawal(self):
        token, _ = await self.sign_up()
        bank = (await self.client.post("/v1/payment-methods/banks", headers=self.auth(token), json={
            "bank": "Sampath Bank", "accountNumber": "987654321", "holder": "Kasun Perera",
            "accountType": "CURRENT"})).json()
        tx = (await self.client.post("/v1/withdrawals", headers=self.auth(token, "w-settle"),
                                     json={"amount": "100.00", "paymentMethodId": bank["id"]})).json()
        return token, tx

    async def test_withdrawal_stays_pending_until_the_settle_time(self):
        token, tx = await self._bank_withdrawal()
        self.assertEqual(tx["status"], "PENDING")
        self.assertEqual(await main.settle_withdrawals(main.app.state.pool), 0)
        stored = (await self.client.get(f"/v1/transactions/{tx['id']}", headers=self.auth(token))).json()
        self.assertEqual(stored["status"], "PENDING")

    async def test_withdrawal_completes_and_notifies_after_the_settle_time(self):
        token, tx = await self._bank_withdrawal()
        with mock.patch.object(main.settings, "withdrawal_settle_seconds", 0):
            self.assertEqual(await main.settle_withdrawals(main.app.state.pool), 1)
            self.assertEqual(await main.settle_withdrawals(main.app.state.pool), 0)  # only once
        stored = (await self.client.get(f"/v1/transactions/{tx['id']}", headers=self.auth(token))).json()
        self.assertEqual(stored["status"], "COMPLETED")
        latest = (await self.client.get("/v1/notifications", headers=self.auth(token))).json()["items"][0]
        self.assertEqual((latest["title"], latest["transactionId"]), ("Withdrawal complete", tx["id"]))

    async def test_removing_a_card_creates_a_security_notification(self):
        token, _ = await self.sign_up()
        card = (await self.client.post("/v1/payment-methods/cards", headers=self.auth(token), json={
            "token": "demo_tok_remove01", "holder": "Kasun Perera", "brand": "Visa",
            "last4": "1111", "expiry": "09/30"})).json()
        response = await self.client.delete(f"/v1/payment-methods/{card['id']}", headers=self.auth(token))
        self.assertEqual(response.status_code, 204)
        latest = (await self.client.get("/v1/notifications", headers=self.auth(token))).json()["items"][0]
        self.assertEqual((latest["kind"], latest["title"]), ("SECURITY", "Card removed"))
        self.assertIn("1111", latest["body"])

    async def test_deposit_addresses_are_per_user_and_in_each_tokens_format(self):
        first, _ = await self.sign_up()
        second, _ = await self.sign_up("Nimal Silva")
        mine = (await self.client.get("/v1/wallet/addresses", headers=self.auth(first))).json()
        again = (await self.client.get("/v1/wallet/addresses", headers=self.auth(first))).json()
        theirs = (await self.client.get("/v1/wallet/addresses", headers=self.auth(second))).json()
        self.assertEqual(mine, again)  # stable
        self.assertTrue(all(mine[asset] != theirs[asset] for asset in mine))
        self.assertRegex(mine["USDT"], r"^T[1-9A-HJ-NP-Za-km-z]{33}$")
        self.assertRegex(mine["BTC"], r"^bc1q[02-9ac-hj-np-z]{38}$")
        self.assertRegex(mine["ETH"], r"^0x[0-9a-f]{40}$")
        self.assertRegex(mine["SOL"], r"^[1-9A-HJ-NP-Za-km-z]{44}$")

    async def test_other_users_transactions_are_hidden(self):
        sender, _ = await self.sign_up()
        other, receiver_handle = await self.sign_up("Nimal Silva")
        sent = (await self.client.post("/v1/payments", headers=self.auth(sender, "p"),
                                       json={"to": receiver_handle, "amount": "5", "currency": "LKR"})).json()
        response = await self.client.get(f"/v1/transactions/{sent['id']}", headers=self.auth(other))
        self.assertEqual(response.status_code, 404)

    async def test_paging_returns_items_that_share_a_timestamp(self):
        token, _ = await self.sign_up()
        # One profile update writes several notifications in a single DB transaction (same now()).
        await self.client.patch("/v1/me", headers=self.auth(token),
                                json={"name": "Kasun P Perera", "handle": f"u{secrets.token_hex(4)}", "email": "k@x.lk"})
        everything = (await self.client.get("/v1/notifications", headers=self.auth(token))).json()["items"]
        paged, cursor = [], None
        while True:
            params = {"limit": 1} | ({"cursor": cursor} if cursor else {})
            page = (await self.client.get("/v1/notifications", headers=self.auth(token), params=params)).json()
            paged += [item["id"] for item in page["items"]]
            cursor = page["nextCursor"]
            if not cursor:
                break
        self.assertGreaterEqual(len(everything), 4)
        self.assertEqual(paged, [item["id"] for item in everything])

    async def test_user_lookup_requires_auth_and_matches_underscore_literally(self):
        token, _ = await self.sign_up()
        for path in ("/v1/users/search?q=a", "/v1/users/sajjad67", "/v1/users/check-handle?handle=abc"):
            with self.subTest(path=path):
                self.assertEqual((await self.client.get(path)).status_code, 401)
        found = (await self.client.get("/v1/users/search", headers=self.auth(token), params={"q": "_"})).json()
        self.assertTrue(all("_" in user["handle"] or "_" in user["name"] for user in found["items"]))

    async def test_unknown_route_uses_error_shape(self):
        response = await self.client.get("/v1/does-not-exist")
        self.assertEqual(response.json()["error"]["code"], "REQUEST_FAILED")

    async def test_photo_is_served_by_url_and_removable(self):
        token, _ = await self.sign_up()
        png = b"\x89PNG\r\n\x1a\n" + b"\x00" * 32
        # The declared type is wrong on purpose: the bytes decide.
        uploaded = await self.client.put("/v1/me/photo", headers=self.auth(token),
                                         files={"photo": ("a.jpg", png, "image/jpeg")})
        url = uploaded.json()["photoUrl"]
        served = await self.client.get(url)
        self.assertEqual((served.headers["content-type"], served.content), ("image/png", png))
        removed = await self.client.delete("/v1/me/photo", headers=self.auth(token))
        self.assertIsNone(removed.json()["photoUrl"])
        self.assertEqual((await self.client.get(url)).status_code, 404)

    async def test_non_image_upload_is_rejected(self):
        token, _ = await self.sign_up()
        response = await self.client.put("/v1/me/photo", headers=self.auth(token),
                                         files={"photo": ("a.png", b"<svg/>", "image/png")})
        self.assertEqual(response.status_code, 400)

    async def test_logout_revokes_tokens(self):
        token, _ = await self.sign_up()
        response = await self.client.delete("/v1/auth/logout", headers=self.auth(token))
        self.assertEqual(response.status_code, 204, response.text)
        self.assertEqual(len(self.revoked), 1)

    async def test_nearby_places_merge_agents_and_osm_sorted_by_distance(self):
        token, _ = await self.sign_up()
        elements = [
            {"type": "node", "id": 1, "lat": 7.4870, "lon": 80.3650, "tags": {"amenity": "atm", "brand": "NDB"}},
            {"type": "way", "id": 2, "center": {"lat": 7.4900, "lon": 80.3600},
             "tags": {"amenity": "bank", "name": "Sampath Bank", "atm": "yes"}},
            {"type": "node", "id": 3, "lat": 7.6000, "lon": 80.5000, "tags": {"amenity": "bank"}},  # too far
        ]
        with mock.patch.object(main, "osm_banking", mock.AsyncMock(return_value=elements)):
            response = await self.client.get("/v1/places/nearby", headers=self.auth(token),
                                             params={"lat": 7.4863, "lon": 80.3647})
        body = response.json()
        self.assertEqual(response.status_code, 200, response.text)
        self.assertTrue(body["banksAvailable"])
        kinds = {place["kind"] for place in body["items"]}
        self.assertEqual(kinds, {"ATM", "BANK", "AGENT"})
        self.assertNotIn("osm-node-3", [place["id"] for place in body["items"]])
        distances = [place["distanceMeters"] for place in body["items"]]
        self.assertEqual(distances, sorted(distances))
        atm = next(place for place in body["items"] if place["id"] == "osm-node-1")
        self.assertEqual((atm["name"], atm["hasAtm"]), ("NDB", True))

    async def test_nearby_places_fall_back_when_osm_is_down_and_far_from_agents(self):
        token, _ = await self.sign_up()
        with mock.patch.object(main, "osm_banking", mock.AsyncMock(side_effect=RuntimeError("down"))):
            response = await self.client.get("/v1/places/nearby", headers=self.auth(token),
                                             params={"lat": 6.9271, "lon": 79.8612})  # Colombo
        body = response.json()
        self.assertFalse(body["banksAvailable"])
        self.assertEqual(len(body["items"]), 3)
        self.assertTrue(all(place["kind"] == "AGENT" and place["distanceMeters"] < 1000 for place in body["items"]))

    async def test_osm_banking_uses_kurunegala_snapshot_when_overpass_is_down(self):
        main._places_cache.clear()
        with mock.patch.object(main, "overpass", mock.AsyncMock(side_effect=RuntimeError("down"))):
            elements = await main.osm_banking(7.4863, 80.3647, 3000)
            self.assertTrue(elements)
            self.assertTrue(all(element["tags"]["amenity"] in ("atm", "bank") for element in elements))
            with self.assertRaises(RuntimeError):
                await main.osm_banking(6.9271, 79.8612, 3000)  # Colombo: outside the snapshot

    async def test_nearby_places_require_auth_and_valid_coordinates(self):
        token, _ = await self.sign_up()
        self.assertEqual((await self.client.get("/v1/places/nearby", params={"lat": 7, "lon": 80})).status_code, 401)
        bad = await self.client.get("/v1/places/nearby", headers=self.auth(token), params={"lat": 120, "lon": 80})
        self.assertEqual(bad.status_code, 400)


if __name__ == "__main__":
    unittest.main()
