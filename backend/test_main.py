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


if __name__ == "__main__":
    unittest.main()

