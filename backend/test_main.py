import unittest
from decimal import Decimal

from fastapi import HTTPException

from backend.main import BankBody, CardBody, amount


class AmountValidationTests(unittest.TestCase):
    def test_asset_precision_is_preserved(self):
        self.assertEqual(amount("0.012500", "BTC"), Decimal("0.012500"))

    def test_excess_precision_is_rejected(self):
        with self.assertRaises(HTTPException) as raised:
            amount("1.001", "LKR")
        self.assertEqual(raised.exception.status_code, 400)

    def test_non_positive_amount_is_rejected(self):
        for value in ("0", "-2.00"):
            with self.subTest(value=value), self.assertRaises(HTTPException):
                amount(value, "LKR")

    def test_whole_amount_limit_is_enforced(self):
        with self.assertRaises(HTTPException):
            amount("1000000000", "LKR")

    def test_unsupported_asset_is_rejected(self):
        with self.assertRaises(HTTPException):
            amount("1.00", "DOGE")


class RequestModelTests(unittest.TestCase):
    def test_bank_request_uses_frontend_camel_case(self):
        request = BankBody.model_validate({
            "bank": "Commercial Bank",
            "accountNumber": "12345678",
            "holder": "Kasun Perera",
            "accountType": "SAVINGS",
        })
        self.assertEqual(request.account_number, "12345678")

    def test_card_request_never_requires_card_number_or_cvv(self):
        request = CardBody.model_validate({
            "token": "demo_tok_abcd1234",
            "holder": "Kasun Perera",
            "brand": "Visa",
            "last4": "0928",
            "expiry": "08/29",
        })
        self.assertEqual(request.last4, "0928")

    def test_profile_patch_validation(self):
        from backend.main import ProfilePatch
        patch = ProfilePatch.model_validate({"name": "Kasun Perera", "handle": "kasun_p", "email": "kasun@gmail.com"})
        self.assertEqual(patch.handle, "kasun_p")

    def test_settings_patch_validation(self):
        from backend.main import SettingsPatch
        settings = SettingsPatch.model_validate({"biometricApproval": True, "hideBalance": False})
        self.assertTrue(settings.biometric_approval)

    def test_explicit_nulls_are_rejected(self):
        from pydantic import ValidationError
        from backend.main import ProfilePatch, SettingsPatch
        for model, body in ((SettingsPatch, {"hideBalance": None}), (ProfilePatch, {"name": None})):
            with self.subTest(body=body), self.assertRaises(ValidationError):
                model.model_validate(body)

    def test_empty_or_null_email_clears_it(self):
        from backend.main import ProfilePatch
        for value in ("", None):
            with self.subTest(value=value):
                self.assertEqual(ProfilePatch.model_validate({"email": value}).model_dump(exclude_unset=True), {"email": None})


class FormattingTests(unittest.TestCase):
    def test_money_uses_asset_decimals(self):
        from backend.main import money
        self.assertEqual(money(Decimal("100.00000000"), "LKR"), "100.00")
        self.assertEqual(money(Decimal("0.04215000"), "BTC"), "0.042150")

    def test_money_never_drops_precision(self):
        from backend.main import money
        self.assertEqual(money(Decimal("0.00000500"), "SOL"), "0.000005")
        self.assertEqual(money(Decimal("100000"), None), "100000")

    def test_cursor_round_trip_keeps_microseconds(self):
        from datetime import datetime, timezone
        from uuid import uuid4
        from backend.main import decode_cursor, encode_cursor
        row = {"created_at": datetime(2026, 10, 14, 5, 6, 7, 123457, tzinfo=timezone.utc), "id": uuid4()}
        self.assertEqual(decode_cursor(encode_cursor(row)), (row["created_at"], row["id"]))

    def test_bad_cursor_is_rejected(self):
        from backend.main import decode_cursor
        for cursor in ("1759460000000", "abc_def"):
            with self.subTest(cursor=cursor), self.assertRaises(HTTPException):
                decode_cursor(cursor)

    def test_image_type_reads_file_signature(self):
        from backend.main import image_type
        self.assertEqual(image_type(b"\xff\xd8\xff\xe0rest"), "image/jpeg")
        self.assertEqual(image_type(b"RIFF\x00\x00\x00\x00WEBPVP8 "), "image/webp")
        self.assertIsNone(image_type(b"<svg></svg>"))


if __name__ == "__main__":
    unittest.main()

