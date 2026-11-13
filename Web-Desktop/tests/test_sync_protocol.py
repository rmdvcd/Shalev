from __future__ import annotations

import gzip
import io
import time

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from dshalev import sync_protocol
from dshalev.sync_protocol import (
    EncryptedChannel,
    decode_pairing_code,
    derive_employee_key,
    generate_pairing_code,
    pairing_mac_primary,
    pairing_mac_secondary,
    read_json_frame,
    send_json_frame,
    session_keys,
)


def test_qr_matches_spvi_v1_payload_and_expiration():
    token = bytes(range(16))
    expires = int(time.time() * 1000) + 60_000
    code = generate_pairing_code(
        business_id="00112233445566778899aabb", business_name="Café Central", hosts=["192.168.1.20"],
        port=47811, employee_id=7, employee_name="Ana", token=token, expires_at_ms=expires,
    )
    decoded = decode_pairing_code(code, now_ms=expires - 1)
    assert code.startswith("SPVI-VINC1:")
    assert decoded["n"] == "00112233445566778899aabb"
    assert decoded["e"] == 7 and decoded["token_bytes"] == token
    try:
        decode_pairing_code(code, now_ms=expires)
    except ValueError as exc:
        assert "vencido" in str(exc)
    else:
        raise AssertionError("El código vencido no se rechazó")


def test_pairing_hmac_and_ecdh_derive_identical_employee_key():
    token = b"t" * 16
    secondary = ec.generate_private_key(ec.SECP256R1())
    primary = ec.generate_private_key(ec.SECP256R1())
    secondary_spki = secondary.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    primary_spki = primary.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    assert pairing_mac_secondary(token, secondary_spki) != pairing_mac_primary(token, primary_spki, secondary_spki)
    primary_key, _ = derive_employee_key(primary, secondary_spki, token)
    secondary_key, _ = derive_employee_key(secondary, primary_spki, token)
    assert primary_key == secondary_key


def test_encrypted_channel_matches_secondary_and_primary_directions():
    employee_key = b"k" * 32
    nonce_secondary = b"s" * 16
    nonce_primary = b"p" * 16
    c2s, s2c = session_keys(employee_key, nonce_secondary, nonce_primary)
    secondary = EncryptedChannel(c2s, s2c, send_label=1, receive_label=2)
    primary = EncryptedChannel.primary(employee_key, nonce_secondary, nonce_primary)
    encrypted = secondary.seal({"t": "ping", "id": 13})
    assert primary.open(encrypted) == {"t": "ping", "id": 13}
    reply = primary.seal({"t": "pong", "id": 13})
    assert secondary.open(reply) == {"t": "pong", "id": 13}


def test_json_frame_roundtrip_uses_utf8_and_length_prefix():
    stream = io.BytesIO()
    send_json_frame(stream, {"t": "vincular_ok", "nombreNegocio": "Café"})
    stream.seek(0)
    assert read_json_frame(stream) == {"nombreNegocio": "Café", "t": "vincular_ok"}


def test_gzip_payload_is_bounded():
    original = sync_protocol.MAX_CLEAR
    try:
        sync_protocol.MAX_CLEAR = 32
        assert sync_protocol._gunzip_bounded(gzip.compress(b"x" * 32)) == b"x" * 32
        try:
            sync_protocol._gunzip_bounded(gzip.compress(b"x" * 33))
        except ValueError:
            pass
        else:
            raise AssertionError("El límite de descompresión no se aplicó")
    finally:
        sync_protocol.MAX_CLEAR = original
