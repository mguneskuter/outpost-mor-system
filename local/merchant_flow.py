#!/usr/bin/env python3
"""Drives one merchant flow through the running local platform and asserts its outcome.

The flow creates an order for a Dutch shopper, pays it with the approved test card, reads
the balance reports once the capture is booked, requests a full refund, waits for the refund
to be booked, and reads the balance reports again. Expected amounts: net 8000, VAT 21% =
1680, gross 9680, merchant fee 5% of net = 400.

A run is named. Its idempotency keys derive from the name, so running again under the same
name repeats the same merchant requests: it must find the stored order, issue no second
PSP refund, and change no amount owed.

Exits 0 when every assertion holds and 1 on the first that does not.
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GATEWAY_BASE_URL = "http://localhost:8080"
PSP_CODE = "DEMO_PSP"
APPROVED_TEST_CARD = "4111111111111111"
SHOPPER_COUNTRY = "NL"
TAX_AUTHORITY_CODE = "TAX_AUTHORITY_NL"
CURRENCY = "EUR"

NET_AMOUNT = 8000
TAX_AMOUNT = 1680
GROSS_AMOUNT = 9680
FEE_AMOUNT = 400

# What the order adds to the amounts owed, which balance reports present as positive.
OWED_TO_MERCHANT_AFTER_CAPTURE = NET_AMOUNT - FEE_AMOUNT
OWED_TO_TAX_AUTHORITY_AFTER_CAPTURE = TAX_AMOUNT
OWED_TO_MERCHANT_AFTER_REFUND = -FEE_AMOUNT
OWED_TO_TAX_AUTHORITY_AFTER_REFUND = 0

HTTP_TIMEOUT_SECONDS = 10
WAIT_TIMEOUT_SECONDS = 90
POLL_INTERVAL_SECONDS = 0.5


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--run",
        default=str(uuid.uuid4()),
        help="run name; repeating a name repeats the same idempotency keys",
    )
    run = parser.parse_args().run
    print(f"merchant flow run: {run}")
    try:
        MerchantFlow(run, Credentials.from_environment()).drive()
    except (AssertionError, RuntimeError) as failure:
        print(f"FAILED: {failure}", file=sys.stderr)
        return 1
    print("merchant flow passed")
    return 0


class Credentials:
    """The credentials the flow presents, read from the environment."""

    NAMES = [
        "OUTPOST_MERCHANT_API_KEY",
        "OUTPOST_MERCHANT_HMAC_SECRET",
        "OUTPOST_OPERATOR_API_KEY",
        "OUTPOST_PSP_SIMULATOR_API_KEY",
    ]

    def __init__(self, merchant_api_key, merchant_hmac_secret, operator_api_key, psp_api_key):
        self.merchant_api_key = merchant_api_key
        self.merchant_hmac_secret = merchant_hmac_secret
        self.operator_api_key = operator_api_key
        self.psp_api_key = psp_api_key

    @staticmethod
    def from_environment():
        missing = [name for name in Credentials.NAMES if not os.environ.get(name)]
        if missing:
            raise SystemExit(f"required variables are not set: {', '.join(missing)}")
        return Credentials(*(os.environ[name] for name in Credentials.NAMES))


class MerchantFlow:
    """One named run of the merchant flow."""

    def __init__(self, run, credentials):
        self.run = run
        self.credentials = credentials
        self.order_idempotency_key = f"{run}-order"
        self.refund_idempotency_key = f"{run}-refund"

    def drive(self):
        is_repeat = self.stored_order_count() > 0
        owed_to_merchant_before = self.owed_to_merchant()
        owed_to_tax_authority_before = self.owed_to_tax_authority()

        order = self.create_order()
        assert_that(
            self.stored_order_count() == 1, "the idempotency key identifies exactly one order"
        )
        original_reference = order["order_reference"]
        psp_reference = self.psp_reference(original_reference)

        self.pay(order["payment_details"]["payment_link"], psp_reference, is_repeat)
        wait_for(
            lambda: has_transaction_event(original_reference, "CAPTURED"),
            "the capture to be booked",
        )
        self.assert_owed_by_order(
            owed_to_merchant_before,
            owed_to_tax_authority_before,
            0 if is_repeat else OWED_TO_MERCHANT_AFTER_CAPTURE,
            0 if is_repeat else OWED_TO_TAX_AUTHORITY_AFTER_CAPTURE,
            "after capture",
        )

        refund_reference = self.request_refund(order["order_reference"])
        wait_for(
            lambda: has_transaction_event(refund_reference, "REFUNDED"),
            "the refund to be booked",
        )
        refunds = psp_refund_count(psp_reference)
        assert_that(refunds == 1, f"the PSP issued one refund for the payment, found {refunds}")
        self.assert_owed_by_order(
            owed_to_merchant_before,
            owed_to_tax_authority_before,
            0 if is_repeat else OWED_TO_MERCHANT_AFTER_REFUND,
            0 if is_repeat else OWED_TO_TAX_AUTHORITY_AFTER_REFUND,
            "after refund",
        )

    def create_order(self):
        body = {
            "merchant_reference": f"{self.run}-merchant-order",
            "idempotency_key": self.order_idempotency_key,
            "shopper_details": {
                "full_name": "Merchant Flow Shopper",
                "email": "merchant-flow-shopper@example.com",
                "country": SHOPPER_COUNTRY,
            },
            "payment_method": PSP_CODE,
            "order_details": {
                "order_lines": [
                    {
                        "merchant_line_reference": "line-1",
                        "amount": NET_AMOUNT,
                        "currency": CURRENCY,
                        "type": "PHYSICAL_GOODS",
                    }
                ],
                "total_amount": NET_AMOUNT,
                "currency": CURRENCY,
            },
        }
        status, order = self.signed_gateway_request("POST", "/v1/order", body)
        assert_that(status == 201, f"order creation answers 201, got {status}: {order}")
        details = order["payment_details"]
        assert_that(
            (details["amount"], details["tax_amount"], details["total_amount"])
            == (NET_AMOUNT, TAX_AMOUNT, GROSS_AMOUNT),
            f"the order carries net {NET_AMOUNT}, tax {TAX_AMOUNT}, gross {GROSS_AMOUNT}: "
            f"{details}",
        )
        print(f"order {order['order_reference']}")
        return order

    def pay(self, payment_link, psp_reference, is_repeat):
        status, body = http_request(
            "POST",
            payment_link,
            write_json({"psp_reference": psp_reference, "card_number": APPROVED_TEST_CARD}),
            {"X-Outpost-Api-Key": self.credentials.psp_api_key},
        )
        accepted = {202, 409} if is_repeat else {202}
        assert_that(
            status in accepted, f"the payment answers {sorted(accepted)}, got {status}: {body}"
        )

    def request_refund(self, order_reference):
        body = {
            "order_reference": order_reference,
            "idempotency_key": self.refund_idempotency_key,
            "merchant_reference": f"{self.run}-merchant-refund",
            "type": "REFUND",
        }
        status, refund = self.signed_gateway_request("POST", "/v1/order/modification", body)
        assert_that(status == 202, f"the refund request answers 202, got {status}: {refund}")
        print(f"refund {refund['refund_reference']}")
        return refund["refund_reference"]

    def assert_owed_by_order(
        self,
        owed_to_merchant_before,
        owed_to_tax_authority_before,
        owed_to_merchant,
        owed_to_tax_authority,
        moment,
    ):
        owed_to_merchant_now = self.owed_to_merchant()
        owed_to_tax_authority_now = self.owed_to_tax_authority()
        assert_that(
            owed_to_merchant_now - owed_to_merchant_before == owed_to_merchant,
            f"{moment}, the order owes the merchant {owed_to_merchant}, "
            f"owes {owed_to_merchant_now - owed_to_merchant_before}",
        )
        assert_that(
            owed_to_tax_authority_now - owed_to_tax_authority_before == owed_to_tax_authority,
            f"{moment}, the order owes {TAX_AUTHORITY_CODE} {owed_to_tax_authority}, "
            f"owes {owed_to_tax_authority_now - owed_to_tax_authority_before}",
        )
        print(
            f"{moment}: merchant {owed_to_merchant_now}, "
            f"{TAX_AUTHORITY_CODE} {owed_to_tax_authority_now} {CURRENCY}"
        )

    def owed_to_merchant(self):
        status, report = self.signed_gateway_request("GET", "/v1/report/balance/merchant", None)
        assert_that(
            status == 200, f"the merchant balance report answers 200, got {status}: {report}"
        )
        accounts = report["accounts"]
        assert_that(len(accounts) <= 1, f"the merchant report is scoped to the caller: {report}")
        return currency_amount(accounts[0]["balances"]) if accounts else 0

    def owed_to_tax_authority(self):
        status, report = http_request(
            "GET",
            GATEWAY_BASE_URL + "/v1/report/balance/tax",
            None,
            {"X-Outpost-Api-Key": self.credentials.operator_api_key},
        )
        assert_that(status == 200, f"the tax balance report answers 200, got {status}: {report}")
        for account in report["accounts"]:
            if account["account_code"] == TAX_AUTHORITY_CODE:
                return currency_amount(account["balances"])
        return 0

    def signed_gateway_request(self, method, path, body):
        payload = None if body is None else write_json(body)
        signature = hmac.new(
            self.credentials.merchant_hmac_secret.encode("utf-8"), payload or b"", hashlib.sha256
        ).digest()
        headers = {
            "X-Outpost-Api-Key": self.credentials.merchant_api_key,
            "X-Outpost-Signature": base64.b64encode(signature).decode("ascii"),
        }
        return http_request(method, GATEWAY_BASE_URL + path, payload, headers)

    def stored_order_count(self):
        return int(
            query(
                "SELECT count(*) FROM merchant_order "
                f"WHERE idempotency_key = {sql_literal(self.order_idempotency_key)}"
            )
        )

    @staticmethod
    def psp_reference(order_reference):
        return query(
            "SELECT psp_reference FROM merchant_order "
            f"WHERE order_reference = {sql_literal(order_reference)}"
        )


def has_transaction_event(reference, event_code):
    return (
        query(
            "SELECT count(*) FROM transaction_event event "
            "JOIN transaction ON transaction.transaction_id = event.transaction_id "
            "LEFT JOIN transaction parent "
            "ON parent.transaction_id = transaction.parent_transaction_id "
            "JOIN transaction_event_type type "
            "ON type.transaction_event_type_id = event.transaction_event_type_id "
            f"WHERE type.code = {sql_literal(event_code)} "
            f"AND {sql_literal(reference)} IN (transaction.reference, parent.reference)"
        )
        != "0"
    )


def psp_refund_count(psp_reference):
    return int(
        query(
            "SELECT count(*) FROM psp_simulator.psp_refund "
            f"WHERE psp_reference = {int(psp_reference)}"
        )
    )


def wait_for(condition, description):
    deadline = time.monotonic() + WAIT_TIMEOUT_SECONDS
    while not condition():
        if time.monotonic() > deadline:
            raise AssertionError(
                f"timed out after {WAIT_TIMEOUT_SECONDS}s waiting for {description}"
            )
        time.sleep(POLL_INTERVAL_SECONDS)


def assert_that(condition, description):
    if not condition:
        raise AssertionError(description)


def currency_amount(balances):
    for balance in balances:
        if balance["currency"] == CURRENCY:
            return balance["amount"]
    return 0


def http_request(method, url, body, headers):
    request = urllib.request.Request(url, data=body, method=method)
    for name, value in headers.items():
        request.add_header(name, value)
    if body is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
            return response.status, read_json(response.read())
    except urllib.error.HTTPError as error:
        return error.code, read_json(error.read())
    except urllib.error.URLError as error:
        raise RuntimeError(f"{method} {url} could not be sent: {error.reason}") from error


def read_json(raw):
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw.decode("utf-8", errors="replace")


def write_json(value):
    return json.dumps(value, separators=(",", ":")).encode("utf-8")


def sql_literal(value):
    return "'" + value.replace("'", "''") + "'"


def query(sql):
    completed = subprocess.run(
        [
            "docker", "compose", "--env-file", ".env", "-f", "local/docker-compose.yml",
            "exec", "-T", "postgres",
            "psql", "-U", os.environ.get("OUTPOST_DB_USER", "outpost"), "-d", "outpost",
            "-v", "ON_ERROR_STOP=1", "-At", "-c", sql,
        ],
        cwd=REPOSITORY_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"database query failed: {completed.stderr.strip()}")
    return completed.stdout.strip()


if __name__ == "__main__":
    sys.exit(main())
