from __future__ import annotations

import base64
import hashlib
import hmac
import secrets

_ITERATIONS = 310_000


def hash_password(password: str, *, salt: bytes | None = None) -> str:
    if len(password) < 12:
        raise ValueError("La contraseña debe tener al menos 12 caracteres.")
    if len(password) > 256:
        raise ValueError("La contraseña no puede superar 256 caracteres.")
    salt = salt or secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, _ITERATIONS)
    return "$".join(("pbkdf2_sha256", str(_ITERATIONS), base64.urlsafe_b64encode(salt).decode(), base64.urlsafe_b64encode(digest).decode()))


def verify_password(password: str, encoded: str) -> bool:
    if len(password) > 256:
        return False
    try:
        algorithm, iteration_text, salt_text, expected_text = encoded.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        iterations = int(iteration_text)
        if iterations < 100_000 or iterations > 2_000_000:
            return False
        salt = base64.urlsafe_b64decode(salt_text.encode())
        expected = base64.urlsafe_b64decode(expected_text.encode())
        actual = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iterations)
        return hmac.compare_digest(actual, expected)
    except (ValueError, TypeError):
        return False


def csrf_token() -> str:
    return secrets.token_urlsafe(32)


def valid_csrf(expected: str | None, supplied: str | None) -> bool:
    return bool(expected and supplied and hmac.compare_digest(expected, supplied))
