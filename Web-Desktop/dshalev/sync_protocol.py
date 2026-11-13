from __future__ import annotations

import base64
import gzip
import hashlib
import hmac
import json
import secrets
import struct
import zlib
from dataclasses import dataclass
from typing import Any, BinaryIO

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

VERSION = 1
MAX_FRAME = 16 * 1024 * 1024
MAX_CLEAR = 4 * MAX_FRAME
MAX_GREETING = 64 * 1024
PAIRING_PREFIX = "SPVI-VINC1:"
PAIRING_SECONDS = 10 * 60


def b64(data: bytes) -> str:
    return base64.b64encode(data).decode("ascii")


def unb64(value: str) -> bytes:
    return base64.b64decode(value, validate=True)


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def unb64url(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * ((4 - len(value) % 4) % 4))


def canonical_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def frame(data: bytes) -> bytes:
    if len(data) > MAX_FRAME:
        raise ValueError("trama demasiado grande")
    return struct.pack(">I", len(data)) + data


def read_exact(stream: BinaryIO, count: int) -> bytes:
    if count < 0 or count > MAX_FRAME:
        raise ValueError("longitud de trama no válida")
    result = bytearray()
    while len(result) < count:
        chunk = stream.read(count - len(result))
        if not chunk:
            raise EOFError("conexión cerrada")
        result.extend(chunk)
    return bytes(result)


def read_frame(stream: BinaryIO, *, limit: int = MAX_FRAME) -> bytes:
    length = struct.unpack(">I", read_exact(stream, 4))[0]
    if length > limit:
        raise ValueError("trama demasiado grande")
    return read_exact(stream, length)


def read_json_frame(stream: BinaryIO, *, limit: int = MAX_FRAME) -> dict[str, Any]:
    value = json.loads(read_frame(stream, limit=limit).decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError("se esperaba un objeto JSON")
    return value


def send_frame(stream: BinaryIO, data: bytes) -> None:
    stream.write(frame(data))
    stream.flush()


def send_json_frame(stream: BinaryIO, value: dict[str, Any], *, limit: int = MAX_FRAME) -> None:
    payload = canonical_json(value)
    if len(payload) > limit:
        raise ValueError("JSON demasiado grande")
    send_frame(stream, payload)


def generate_pairing_code(*, business_id: str, business_name: str, hosts: list[str], port: int,
                          employee_id: int, employee_name: str, token: bytes, expires_at_ms: int) -> str:
    if not business_id or not hosts or not 1 <= port <= 65535 or employee_id <= 0 or len(token) != 16:
        raise ValueError("datos de vinculación no válidos")
    content = {"n": business_id, "nn": business_name, "h": hosts, "p": port, "e": employee_id,
               "en": employee_name, "t": b64url(token), "x": expires_at_ms}
    return PAIRING_PREFIX + b64url(canonical_json(content))


def decode_pairing_code(code: str, *, now_ms: int | None = None) -> dict[str, Any]:
    text = code.strip()
    if len(text) > 2_000 or not text.startswith(PAIRING_PREFIX):
        raise ValueError("QR no válido")
    data = json.loads(unb64url(text[len(PAIRING_PREFIX):]).decode("utf-8"))
    if not isinstance(data, dict):
        raise ValueError("QR no válido")
    token = unb64url(data.get("t", ""))
    if len(token) != 16 or not data.get("n") or not data.get("h") or not 1 <= int(data.get("p", 0)) <= 65535 or int(data.get("e", 0)) <= 0:
        raise ValueError("QR no válido")
    if now_ms is not None and now_ms >= int(data.get("x", 0)):
        raise ValueError("QR vencido")
    data["token_bytes"] = token
    return data


def derive_employee_key(private_key: ec.EllipticCurvePrivateKey, peer_spki: bytes, token: bytes) -> tuple[bytes, bytes]:
    peer = serialization.load_der_public_key(peer_spki)
    if not isinstance(peer, ec.EllipticCurvePublicKey) or not isinstance(peer.curve, ec.SECP256R1):
        raise ValueError("clave P-256 no válida")
    shared = private_key.exchange(ec.ECDH(), peer)
    key = HKDF(algorithm=hashes.SHA256(), length=32, salt=token,
               info=b"spvi-clave-empleado-v1").derive(shared)
    return key, shared


def pairing_mac_secondary(token: bytes, public_key: bytes) -> bytes:
    return hmac.new(token, b"spvi-vinc-c" + public_key, hashlib.sha256).digest()


def pairing_mac_primary(token: bytes, public_primary: bytes, public_secondary: bytes) -> bytes:
    return hmac.new(token, b"spvi-vinc-s" + public_primary + public_secondary, hashlib.sha256).digest()


def session_keys(employee_key: bytes, nonce_secondary: bytes, nonce_primary: bytes) -> tuple[bytes, bytes]:
    if len(employee_key) != 32 or len(nonce_secondary) != 16 or len(nonce_primary) != 16:
        raise ValueError("material de sesión no válido")
    salt = nonce_secondary + nonce_primary
    c2s = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=b"spvi-c2s-v1").derive(employee_key)
    s2c = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=b"spvi-s2c-v1").derive(employee_key)
    return c2s, s2c


def reject_mac(employee_key: bytes, nonce_secondary: bytes, reason: str) -> bytes:
    return hmac.new(employee_key, b"spvi-rechazo" + nonce_secondary + reason.encode("utf-8"), hashlib.sha256).digest()


def _gunzip_bounded(data: bytes) -> bytes:
    decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
    clear = decoder.decompress(data, MAX_CLEAR + 1)
    if len(clear) > MAX_CLEAR or decoder.unconsumed_tail:
        raise ValueError("contenido descomprimido demasiado grande")
    clear += decoder.flush(MAX_CLEAR + 1 - len(clear))
    if len(clear) > MAX_CLEAR or not decoder.eof or decoder.unused_data:
        raise ValueError("contenido gzip no válido")
    return clear


@dataclass
class EncryptedChannel:
    send_key: bytes
    receive_key: bytes
    send_label: int
    receive_label: int
    send_sequence: int = 0
    receive_sequence: int = 0

    @classmethod
    def primary(cls, employee_key: bytes, nonce_secondary: bytes, nonce_primary: bytes) -> "EncryptedChannel":
        c2s, s2c = session_keys(employee_key, nonce_secondary, nonce_primary)
        return cls(send_key=s2c, receive_key=c2s, send_label=2, receive_label=1)

    @staticmethod
    def _aad(label: int, sequence: int) -> bytes:
        return b"SPVI-S1\0" + bytes([label]) + struct.pack(">q", sequence)

    def seal(self, message: dict[str, Any]) -> bytes:
        if self.send_sequence >= 2**63 - 1:
            raise ValueError("secuencia agotada")
        clear = gzip.compress(canonical_json(message), mtime=0)
        iv = secrets.token_bytes(12)
        sequence = self.send_sequence
        self.send_sequence += 1
        sealed = AESGCM(self.send_key).encrypt(iv, clear, self._aad(self.send_label, sequence))
        return iv + sealed

    def open(self, packet: bytes) -> dict[str, Any]:
        if len(packet) < 28 or len(packet) > MAX_FRAME or self.receive_sequence >= 2**63 - 1:
            raise ValueError("trama cifrada no válida")
        sequence = self.receive_sequence
        self.receive_sequence += 1
        clear = AESGCM(self.receive_key).decrypt(packet[:12], packet[12:], self._aad(self.receive_label, sequence))
        obj = json.loads(_gunzip_bounded(clear).decode("utf-8"))
        if not isinstance(obj, dict) or not isinstance(obj.get("t"), str):
            raise ValueError("mensaje cifrado no válido")
        return obj

    def send(self, stream: BinaryIO, message: dict[str, Any]) -> None:
        send_frame(stream, self.seal(message))

    def receive(self, stream: BinaryIO) -> dict[str, Any]:
        return self.open(read_frame(stream))
