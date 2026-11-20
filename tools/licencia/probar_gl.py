#!/usr/bin/env python3
"""
Prueba del flujo de licencias SPVI → GL → SPVI, formato 0.23.0 (Prompt 64).

Reproduce en Python, byte a byte, lo que hacen `SolicitudCifrada`, `LicenciaCorta`, `MensajesLicencia` y
`LicenseManager` del módulo :licencia, y lo que debe hacer GL según `docs/GL_PROMPT_0.23.md`:

  Solicitud (SPVI → GL): texto introductorio con los datos del solicitante + renglón en blanco + `SPVIR1:…`
      (solicitud cifrada compacta). Solo texto (0.23.1), por WhatsApp o SMS.
  Licencia  (GL → SPVI): características de la licencia + renglón en blanco + `SPVI2:…` (licencia corta CIFRADA,
      216 caracteres), solo texto. Es la ÚNICA forma que SPVI activa.

Usa la MISMA clave de dispositivo de prueba que `GoldenVectorTest` (solo para tests: no protege nada) y las claves
públicas de GL fijadas en `LicenseTrust.kt`.

Uso (requiere `pip install cryptography`):
  python3 probar_gl.py solicitud [--tipo T] [--secundarias N] [--renueva ID | --recupera ID]
      → imprime el mensaje de solicitud (para pegar en GL). 0.25.0: --recupera = solicitud de RECUPERACIÓN.
  python3 probar_gl.py revocadas firmar ID [ID…]   (clave de firma de PRUEBA del vector)
  python3 probar_gl.py revocadas verificar ARCHIVO [--id ID]
      → 0.25.0: lista pública de revocadas `revocadas.json` (formato de `ListaRevocaciones.kt`).
  python3 probar_gl.py verificar ARCHIVO_CON_EL_MENSAJE_DE_GL [--secundarias N] [--tipo T] [--vence AAAA-MM-DD]
      → verifica firma, descifra y valida la licencia como lo hará SPVI (también si el archivo es solo el código).
  python3 probar_gl.py autoprueba
      → ida y vuelta completa con un «GL simulado» (claves efímeras), para comprobar este script.
  python3 probar_gl.py vector
      → vector de referencia para GL (clave de firma de PRUEBA fija; se copia en GL_PROMPT_0.23.md y LicenciaCortaTest).
"""

import argparse, base64, hashlib, json, os, re, struct, sys, uuid
from datetime import datetime, timedelta, timezone

from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import (
    decode_dss_signature,
    encode_dss_signature,
)
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

# ── LicenseTrust.kt (claves públicas de GL fijadas en el build) ─────────────────────────────────────────────
GL_ECDH_SPKI = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4UHhdTzS3X/2pk5wmEAibv760EBcnxDjERo8km4OCdIhVDc5D+N0yPQlq6UpiXV1exXVmMfD6vqezlNsYFEIYg=="  # GL 1.2.0 (2026-10-22)
GL_SIGN_SPKIS = [
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8TwMudgF0ioPFGp+VOQDU+xBDEQQQRHErwPyKQupkBGS8WL/dQVAFN4t3PIddOBl3Kg60ucThoOicZFctdtjIQ==",  # emisor vigente
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1LFKFD7Af1xhbza5CrD7ArwOwq5h4qG70edEp54zozXoUa/r7P7xRQwwV82rXcy0mPznM3TNMk8wGxYCwR7ubg==",  # bundle GL 2026-10-05
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPjc7tw29A0TaynecwlYDphDAtavpfNQ6njeGIX59hIDO+ID9KxTLo4ZaxlPJ/enkCzlBo+5JEMn5AUN8bneC7Q==",  # GL observado 2026-10-15, 2.ª rotación
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/Kx1wh2WpM/JcPeNs7e7a5QvRMARHAPreFZ9YauzFguI85mT7/wC/jmKxR1ml5SGrGWCfA88Qo+PQ4efSUSsdg==",  # GL observado 2026-10-15, 1.ª rotación
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+UpBZNnTcsbL5yR5LQRD1qATSub6tV7dgaVKNgXjFUbJFbxWmUQWOQvqsOBZmt7bKDdu3iLW02Z/RtiPfa3OWQ==",  # 2026-09-30
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvXxzuIsum0ztpiAistqL4TGTpbVu3Cbn33hC4kzQtesGBa2nvag6xqK4DX4xQED6R/8WuDushc7bAURRu3znGg==",  # clave de prueba del vector dorado
]

# ── GoldenVectorTest.kt (dispositivo de prueba) ─────────────────────────────────────────────────────────────
DEV_PRIV_PKCS8 = "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCA3KZoM7faOt6GlH6Bv4WpLS+4FC4Xovdfd2ugoNaBjOg=="
DEVICE_ID = "SPVI:golden0000000001"

# ── Vector de referencia: claves de PRUEBA fijas (no son las de GL; no protegen nada) ──────────────────────
VECTOR_FIRMA_D = int(
    "1f2e3d4c5b6a79881f2e3d4c5b6a79881f2e3d4c5b6a79881f2e3d4c5b6a7988", 16
)
VECTOR_EFIMERA_D = int(
    "0a0b0c0d0e0f10111213141516171819a0b0c0d0e0f1f2f3f4f5f6f7f8f9fafb", 16
)
VECTOR_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
VECTOR_EMITIDA = datetime(2026, 10, 3, 20, 0, tzinfo=timezone.utc)

# ── Precios (CUP): TipoLicencia ─────────────────────────────────────────────────────────────────────────────
PRECIO_BASE = {
    "MENSUAL": 6_000,
    "SEMESTRAL": 30_000,
    "ANUAL": 50_000,
    "PERPETUA": 90_000,
}
PRECIO_SECUNDARIA = {
    "MENSUAL": 1_000,
    "SEMESTRAL": 5_000,
    "ANUAL": 9_000,
    "PERPETUA": 17_000,
}
ETIQUETA = {
    "MENSUAL": "Mensual",
    "SEMESTRAL": "Semestral",
    "ANUAL": "Anual",
    "PERPETUA": "Perpetua",
}
DIAS = {"MENSUAL": 30, "SEMESTRAL": 180, "ANUAL": 365}
MAX_SECUNDARIAS = 10

# ── Formatos 0.23.0 ─────────────────────────────────────────────────────────────────────────────────────────
SOL_PREFIJO, SOL_INFO = "SPVIR1:", b"SPVI-R1"
LIC_PREFIJO, LIC_INFO, LIC_DOMINIO = "SPVI2:", b"SPVI-L2", b"SPVI-L2|"
LIC_LARGO_BASE64, LIC_LARGO_BINARIO = 210, 157
TIPOS = ["MENSUAL", "SEMESTRAL", "ANUAL", "PERPETUA"]
ESTADOS = ["ACTIVA", "VENCIDA", "REVOCADA", "PERPETUA"]
B64URL = re.compile(r"[A-Za-z0-9_-]")

b64 = lambda b: base64.b64encode(b).decode()
unb64 = base64.b64decode
b64url = lambda b: base64.urlsafe_b64encode(b).decode().rstrip("=")
unb64url = lambda s: base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))
iso = lambda d: d.isoformat().replace("+00:00", "Z")


def precio(tipo, n):
    return PRECIO_BASE[tipo] + n * PRECIO_SECUNDARIA[tipo]


def cup(pesos):
    return f"{pesos:,.2f} CUP"


def compacto(obj):
    """Igual que GlJson.encoder de Kotlin: sin espacios, UTF-8 tal cual."""
    return json.dumps(obj, separators=(",", ":"), ensure_ascii=False)


def comprimir(public_key):
    return public_key.public_bytes(
        serialization.Encoding.X962, serialization.PublicFormat.CompressedPoint
    )


def descomprimir(b):
    return ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), b)


def clave_iv(compartido, epk, info):
    okm = HKDF(algorithm=hashes.SHA256(), length=44, salt=epk, info=info).derive(
        compartido
    )
    return okm[:32], okm[32:]


def dispositivo():
    return serialization.load_der_private_key(unb64(DEV_PRIV_PKCS8), None)


# ── Solicitud (SPVI → GL) ──────────────────────────────────────────────────────────────────────────────────
def sellar_solicitud(plano, gl_ecdh_pub):
    eph = ec.generate_private_key(ec.SECP256R1())
    epk = comprimir(eph.public_key())
    clave, iv = clave_iv(eph.exchange(ec.ECDH(), gl_ecdh_pub), epk, SOL_INFO)
    return SOL_PREFIJO + b64url(
        epk + AESGCM(clave).encrypt(iv, plano, SOL_INFO + b"|" + epk)
    )


def abrir_solicitud(texto, gl_ecdh_priv):
    """Lo que debe hacer GL: busca SPVIR1:, toma los caracteres Base64url que siguen (ignorando espacios y saltos) y descifra."""
    i = texto.find(SOL_PREFIJO)
    if i < 0:
        raise ValueError("no hay SPVIR1: en el mensaje")
    # Tolerante al transporte: WhatsApp/SMS pueden partir la línea e insertar espacios o saltos.
    chars = []
    for c in texto[i + len(SOL_PREFIJO) :]:
        if B64URL.fullmatch(c):
            chars.append(c)
        elif not c.isspace():
            break
    datos = unb64url("".join(chars)) if chars else b""
    if len(datos) <= 49:
        raise ValueError("solicitud incompleta")
    epk = datos[:33]
    clave, iv = clave_iv(
        gl_ecdh_priv.exchange(ec.ECDH(), descomprimir(epk)), epk, SOL_INFO
    )
    p = json.loads(AESGCM(clave).decrypt(iv, datos[33:], SOL_INFO + b"|" + epk))
    if p.get("v") != 2:
        raise ValueError("v != 2")
    return p


def texto_solicitud(p, codigo):
    """MensajesLicencia.solicitud: texto introductorio + renglón en blanco + solicitud cifrada."""
    lineas = [
        "Solicitud de licencia SPVI",
        f"Nombre: {p['nombre']} {p['apellidos']}",
        f"Carné de identidad: {p['ci']}",
        f"Teléfono: {p['telefono']}",
        f"Tipo: {ETIQUETA[p['tipo']]}",
        f"Apps secundarias: {p['secundarias']}",
        f"Precio: {cup(precio(p['tipo'], p['secundarias']))}",
    ]
    if p.get("recupera"):  # 0.25.0: SolicitudCifrada.recuperacion
        lineas = [
            "Recuperar licencia SPVI",
            f"Nombre: {p['nombre']} {p['apellidos']}",
            f"Carné de identidad: {p['ci']}",
            f"Teléfono: {p['telefono']}",
            f"Licencia anterior: {p['recupera']}",
        ]
    elif p.get("renueva"):
        lineas.append(f"Renueva: {p['renueva']}")
    return "\n".join(lineas) + "\n\n" + codigo


def solicitud(tipo, n, gl_ecdh_pub=None, renueva=None, recupera=None):
    """Mismo payload y orden de campos que RequestPayload v2."""
    payload = {
        "v": 2,
        "nombre": "Prueba",
        "apellidos": "Compatibilidad Spvi",
        "ci": "00000000000",
        "via": "WHATSAPP",
        "telefono": "+5351815604",
        "deviceId": DEVICE_ID,
        "tipo": tipo,
        "solicitadaEn": iso(datetime.now(timezone.utc).replace(microsecond=0)),
        "nonce": b64url(os.urandom(16)),
        "devicePub": b64url(comprimir(dispositivo().public_key())),
        "secundarias": n,
    }
    if renueva and recupera:
        raise ValueError("renueva y recupera son excluyentes")
    if renueva:
        payload["renueva"] = renueva
    if recupera:
        payload["recupera"] = recupera.strip().lower()
    codigo = sellar_solicitud(
        compacto(payload).encode(),
        gl_ecdh_pub or serialization.load_der_public_key(unb64(GL_ECDH_SPKI)),
    )
    return texto_solicitud(payload, codigo), codigo, payload


# ── Licencia corta cifrada (GL → SPVI) ─────────────────────────────────────────────────────────────────────
def huella(device_id, device_pub_comprimida):
    return hashlib.sha256(
        LIC_DOMINIO + device_id.encode() + b"|" + device_pub_comprimida
    ).digest()[:16]


def cuerpo(lic_id, huella16, tipo, estado, n, emitida, vence):
    return (
        b"\x02"
        + uuid.UUID(lic_id).bytes
        + huella16
        + bytes([TIPOS.index(tipo), ESTADOS.index(estado), n])
        + struct.pack(
            ">II", int(emitida.timestamp()), int(vence.timestamp()) if vence else 0
        )
    )


def emitir(cuerpo44, device_pub_comprimida, gl_sign_priv, efimera=None):
    """Lo que hará GL: cifra el cuerpo hacia devicePub y firma dominio‖epk‖ct‖tag (r‖s crudo)."""
    eph = efimera or ec.generate_private_key(ec.SECP256R1())
    epk = comprimir(eph.public_key())
    clave, iv = clave_iv(
        eph.exchange(ec.ECDH(), descomprimir(device_pub_comprimida)), epk, LIC_INFO
    )
    cifrado = AESGCM(clave).encrypt(iv, cuerpo44, LIC_DOMINIO + epk)
    r, s_ = decode_dss_signature(
        gl_sign_priv.sign(LIC_DOMINIO + epk + cifrado, ec.ECDSA(hashes.SHA256()))
    )
    return LIC_PREFIJO + b64url(
        epk + cifrado + r.to_bytes(32, "big") + s_.to_bytes(32, "big")
    )


def texto_licencia(lic_id, tipo, n, emitida, vence, codigo):
    """MensajesLicencia.licencia (referencia de GL): características + renglón en blanco + código. Fechas en hora de Cuba."""
    from zoneinfo import ZoneInfo

    dia = lambda d: d.astimezone(ZoneInfo("America/Havana")).strftime("%d/%m/%Y")
    return (
        "\n".join(
            [
                "Licencia SPVI",
                f"Tipo: {ETIQUETA[tipo]}",
                f"Apps secundarias: {n}",
                f"Emitida: {dia(emitida)}",
                f"Vence: {dia(vence) if vence else 'nunca'}",
                f"ID: {lic_id}",
            ]
        )
        + "\n\n"
        + codigo
    )


def extraer(texto):
    i = texto.find(LIC_PREFIJO)
    while i >= 0:
        chars, k = [], i + len(LIC_PREFIJO)
        while k < len(texto) and len(chars) < LIC_LARGO_BASE64:
            c = texto[k]
            if B64URL.fullmatch(c):
                chars.append(c)
            elif not c.isspace():
                break
            k += 1
        if len(chars) == LIC_LARGO_BASE64:
            return unb64url("".join(chars))
        i = texto.find(LIC_PREFIJO, i + 1)
    return None


def verificar(
    texto,
    firma_pubs=None,
    esperado_n=None,
    esperado_tipo=None,
    esperado_vence=None,
    device_priv=None,
    device_id=DEVICE_ID,
):
    datos = extraer(texto)
    if datos is None or len(datos) != LIC_LARGO_BINARIO:
        if "{" in texto and '"epk"' in texto:
            print(
                "RECHAZADA por SPVI 0.23.0: es una licencia LARGA (envelope v1). GL debe emitir SIEMPRE la corta cifrada SPVI2:"
            )
        else:
            print("RECHAZADA por SPVI: no hay una licencia SPVI2: completa en el texto")
        return None
    epk, cifrado, firma = datos[:33], datos[33:93], datos[93:]
    der = encode_dss_signature(
        int.from_bytes(firma[:32], "big"), int.from_bytes(firma[32:], "big")
    )
    for k in firma_pubs or [
        serialization.load_der_public_key(unb64(x)) for x in GL_SIGN_SPKIS
    ]:
        try:
            k.verify(der, LIC_DOMINIO + epk + cifrado, ec.ECDSA(hashes.SHA256()))
            break
        except InvalidSignature:
            continue
    else:
        print("RECHAZADA por SPVI: la firma no es de GL")
        return None
    dev = device_priv or dispositivo()
    try:
        clave, iv = clave_iv(dev.exchange(ec.ECDH(), descomprimir(epk)), epk, LIC_INFO)
        c = AESGCM(clave).decrypt(iv, cifrado, LIC_DOMINIO + epk)
    except (InvalidTag, ValueError):
        print(
            "RECHAZADA por SPVI: no se descifra con la clave de este dispositivo (cifrada hacia otra devicePub)"
        )
        return None
    problemas = []
    if c[0] != 2:
        problemas.append(f"formato {c[0]} != 2")
    lic_id = str(uuid.UUID(bytes=c[1:17]))
    if c[17:33] != huella(device_id, comprimir(dev.public_key())):
        problemas.append("huella de otro dispositivo (deviceId o devicePub)")
    t, e, n = c[33], c[34], c[35]
    tipo = TIPOS[t] if t < 4 else None
    estado = ESTADOS[e] if e < 4 else None
    if tipo is None or estado is None:
        problemas.append("tipo o estado desconocidos")
    emit, venc = struct.unpack(">II", c[36:44])
    fecha = lambda x: iso(datetime.fromtimestamp(x, timezone.utc))
    p = {
        "id": lic_id,
        "tipo": tipo,
        "estado": estado,
        "secundarias": n,
        "emitidaEn": fecha(emit),
        "venceEn": fecha(venc) if venc else None,
    }
    if tipo == "PERPETUA":
        if venc:
            problemas.append("PERPETUA con venceEn")
    elif not venc or venc <= emit:
        problemas.append("venceEn ausente o no posterior a emitidaEn")
    if not 0 <= n <= MAX_SECUNDARIAS:
        problemas.append(f"secundarias fuera de 0–{MAX_SECUNDARIAS}: {n}")
    if esperado_n is not None and n != esperado_n:
        problemas.append(f"secundarias = {n}, se pidieron {esperado_n}")
    if esperado_tipo and tipo != esperado_tipo:
        problemas.append(f"tipo = {tipo}, se pidió {esperado_tipo}")
    if esperado_vence and (p["venceEn"] or "")[:10] != esperado_vence:
        problemas.append(f"venceEn = {p['venceEn']}, se esperaba {esperado_vence}")
    arriba = texto[: texto.find(LIC_PREFIJO)].strip()
    if arriba and "\n\n" not in texto[: texto.find(LIC_PREFIJO) + 1]:
        print(
            "AVISO: falta el renglón en blanco entre las características y el código."
        )
    if arriba and tipo and not problemas:
        dt = lambda x: datetime.fromtimestamp(x, timezone.utc)
        esperado = texto_licencia(
            lic_id, tipo, n, dt(emit), dt(venc) if venc else None, ""
        ).strip()
        if arriba.splitlines()[-len(esperado.splitlines()) :] != esperado.splitlines():
            print(
                "AVISO: el texto de arriba no coincide con lo cifrado (SPVI no lo lee, pero confunde al cliente). Esperado:\n"
                + esperado
            )
    print(f"Licencia corta cifrada ({len(LIC_PREFIJO) + LIC_LARGO_BASE64} caracteres):")
    print(json.dumps(p, indent=2, ensure_ascii=False))
    if problemas:
        print("\nRECHAZADA por SPVI:\n - " + "\n - ".join(problemas))
        return None
    print(
        f"\nACEPTADA. Licencia {tipo} con {n} app(s) secundaria(s); precio esperado {cup(precio(tipo, n))}."
    )
    return p


# ── 0.25.0: lista pública de revocadas (ListaRevocaciones.kt) ──────────────────────────────────────────────
REV_DOMINIO_FIRMA, REV_DOMINIO_HUELLA = b"SPVI-REV1|", "SPVI-REV|"
VECTOR_REV_EMITIDA = 1_791_000_000  # segundos Unix fijos del vector


def huella_revocada(lic_id):
    return (
        hashlib.sha256((REV_DOMINIO_HUELLA + lic_id.strip().lower()).encode())
        .digest()[:16]
        .hex()
    )


def firmar_revocadas(ids, gl_sign_priv, emitida=None):
    """Lo que hace GL: firma el TEXTO exacto de `datos` (no hace falta canonicalizar JSON)."""
    datos = compacto(
        {
            "v": 1,
            "emitidaEn": emitida or int(datetime.now(timezone.utc).timestamp()),
            "revocadas": sorted({huella_revocada(i) for i in ids}),
        }
    )
    r, s_ = decode_dss_signature(
        gl_sign_priv.sign(REV_DOMINIO_FIRMA + datos.encode(), ec.ECDSA(hashes.SHA256()))
    )
    return compacto(
        {
            "datos": datos,
            "firma": b64url(r.to_bytes(32, "big") + s_.to_bytes(32, "big")),
        }
    )


def verificar_revocadas(texto, firma_pubs, lic_id=None):
    """Lo que hace SPVI: firma válida de GL, v == 1 y entradas de 32 hex; si no, se ignora el archivo."""
    try:
        a = json.loads(texto)
        raw = unb64url(a["firma"].strip())
        der = encode_dss_signature(
            int.from_bytes(raw[:32], "big"), int.from_bytes(raw[32:], "big")
        )
        mensaje = REV_DOMINIO_FIRMA + a["datos"].encode()
        if not any(silencioso(_verifica, k, der, mensaje) for k in firma_pubs):
            print("✗ firma no válida: SPVI ignora el archivo")
            return None
        d = json.loads(a["datos"])
        if d.get("v", 1) != 1 or not all(
            re.fullmatch(r"[0-9a-f]{32}", h) for h in d.get("revocadas", [])
        ):
            print("✗ formato no válido: SPVI ignora el archivo")
            return None
    except Exception as e:  # noqa: BLE001
        print("✗ archivo ilegible:", e)
        return None
    print(
        f"✓ lista válida: {len(d.get('revocadas', []))} revocadas, emitida {iso(datetime.fromtimestamp(d['emitidaEn'], timezone.utc))}"
    )
    if lic_id:
        print(
            (
                "✓ incluye"
                if huella_revocada(lic_id) in d["revocadas"]
                else "· no incluye"
            ),
            lic_id,
        )
    return d


def _verifica(k, der, mensaje):
    try:
        k.verify(der, mensaje, ec.ECDSA(hashes.SHA256()))
        return True
    except InvalidSignature:
        return False


def silencioso(f, *a, **k):
    import contextlib, io

    with contextlib.redirect_stdout(io.StringIO()):
        return f(*a, **k)


def autoprueba():
    """GL simulado: descifra la solicitud, calcula el precio y emite la licencia como debe hacerlo la GL actualizada."""
    gl_ecdh, gl_sign = (
        ec.generate_private_key(ec.SECP256R1()),
        ec.generate_private_key(ec.SECP256R1()),
    )
    ok = True
    for tipo, n in (("MENSUAL", 2), ("PERPETUA", 0), ("ANUAL", 10)):
        mensaje, codigo, _ = solicitud(tipo, n, gl_ecdh.public_key())
        assert "\n\n" + codigo in mensaje and len(mensaje) < 1000, len(mensaje)
        for entrada in (mensaje, codigo):  # mensaje completo o solo el código
            recibido = abrir_solicitud(entrada, gl_ecdh)
            assert (
                recibido["secundarias"] == n
                and recibido["tipo"] == tipo
                and len(unb64url(recibido["devicePub"])) == 33
            )
        ahora = datetime.now(timezone.utc).replace(microsecond=0)
        lic_id = str(uuid.uuid4())
        estado = "PERPETUA" if tipo == "PERPETUA" else "ACTIVA"
        venc = None if tipo == "PERPETUA" else ahora + timedelta(days=DIAS[tipo])
        pubc = unb64url(recibido["devicePub"])
        lic = emitir(
            cuerpo(
                lic_id, huella(recibido["deviceId"], pubc), tipo, estado, n, ahora, venc
            ),
            pubc,
            gl_sign,
        )
        assert len(lic) == 216
        texto = texto_licencia(lic_id, tipo, n, ahora, venc, lic)
        print(f"── {tipo}, {n} secundarias ──\n{texto}\n")
        ok &= verificar(texto, [gl_sign.public_key()], n, tipo) is not None
        ok &= (
            silencioso(verificar, lic, [gl_sign.public_key()]) is not None
        )  # solo el código
        assert (
            silencioso(
                verificar, texto, [ec.generate_private_key(ec.SECP256R1()).public_key()]
            )
            is None
        )  # otra firma
        alterada = lic[:100] + ("A" if lic[100] != "A" else "B") + lic[101:]
        assert silencioso(verificar, alterada, [gl_sign.public_key()]) is None
        otro = ec.generate_private_key(ec.SECP256R1())  # otro teléfono no la descifra
        assert (
            silencioso(verificar, texto, [gl_sign.public_key()], device_priv=otro)
            is None
        )
    # Renovación con 5 días restantes → la nueva vence 30 días después de la anterior.
    ahora = datetime.now(timezone.utc).replace(microsecond=0)
    anterior_vence = ahora + timedelta(days=5)
    mensaje, _, _ = solicitud(
        "MENSUAL",
        2,
        gl_ecdh.public_key(),
        renueva="d53a020b-4062-4825-8219-024f997173f8",
    )
    assert "Renueva: d53a020b-4062-4825-8219-024f997173f8" in mensaje
    recibido = abrir_solicitud(mensaje, gl_ecdh)
    assert recibido["renueva"] == "d53a020b-4062-4825-8219-024f997173f8"
    nueva_vence = max(ahora, anterior_vence) + timedelta(days=DIAS["MENSUAL"])
    pubc = unb64url(recibido["devicePub"])
    lic = emitir(
        cuerpo(
            str(uuid.uuid4()),
            huella(DEVICE_ID, pubc),
            "MENSUAL",
            "ACTIVA",
            2,
            ahora,
            nueva_vence,
        ),
        pubc,
        gl_sign,
    )
    p = silencioso(verificar, lic, [gl_sign.public_key()])
    ok &= p is not None and p["venceEn"] == iso(nueva_vence)
    print(
        "── Renovación con 5 días restantes:",
        "vence a los 35 días ✓" if ok else "FALLO",
    )
    # 0.25.0: recuperación → GL revoca la anterior, emite con el mismo tipo/vence/secundarias y publica la lista.
    anterior = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    mensaje, _, _ = solicitud(
        "ANUAL", 3, gl_ecdh.public_key(), recupera=anterior.upper()
    )
    assert (
        mensaje.startswith("Recuperar licencia SPVI")
        and f"Licencia anterior: {anterior}" in mensaje
    )
    recibido = abrir_solicitud(mensaje, gl_ecdh)
    assert recibido["recupera"] == anterior and "renueva" not in recibido
    lista = firmar_revocadas([anterior], gl_sign)
    d = silencioso(verificar_revocadas, lista, [gl_sign.public_key()], anterior)
    ok &= d is not None and huella_revocada(anterior) in d["revocadas"]
    ok &= (
        silencioso(
            verificar_revocadas,
            lista,
            [ec.generate_private_key(ec.SECP256R1()).public_key()],
        )
        is None
    )
    ok &= (
        silencioso(
            verificar_revocadas,
            lista.replace(huella_revocada(anterior), "0" * 32),
            [gl_sign.public_key()],
        )
        is None
    )
    print("── Recuperación y lista de revocadas:", "✓" if ok else "FALLO")
    print("\nAUTOPRUEBA", "CORRECTA" if ok else "FALLIDA")
    return ok


def vector():
    """Vector determinista salvo la firma (ECDSA es aleatoria): cualquier firma válida sirve."""
    firma = ec.derive_private_key(VECTOR_FIRMA_D, ec.SECP256R1())
    efimera = ec.derive_private_key(VECTOR_EFIMERA_D, ec.SECP256R1())
    pubc = comprimir(dispositivo().public_key())
    h = huella(DEVICE_ID, pubc)
    vence = VECTOR_EMITIDA + timedelta(days=30)
    c = cuerpo(VECTOR_ID, h, "MENSUAL", "ACTIVA", 2, VECTOR_EMITIDA, vence)
    lic = emitir(c, pubc, firma, efimera)
    print("devicePub comprimida (b64url):", b64url(pubc))
    print("huella:", h.hex())
    print("cuerpo:", c.hex())
    print("clave efímera de PRUEBA (d, hex):", format(VECTOR_EFIMERA_D, "064x"))
    print("epk:", comprimir(efimera.public_key()).hex())
    bruto = base64.urlsafe_b64decode(lic.split("SPVI2:")[1] + "==")
    print("ct||tag (determinista con esa efímera):", bruto[33:93].hex())
    print("clave de firma de PRUEBA (d, hex):", format(VECTOR_FIRMA_D, "064x"))
    print(
        "clave de firma de PRUEBA (SPKI):",
        b64(
            firma.public_key().public_bytes(
                serialization.Encoding.DER,
                serialization.PublicFormat.SubjectPublicKeyInfo,
            )
        ),
    )
    print("\n" + texto_licencia(VECTOR_ID, "MENSUAL", 2, VECTOR_EMITIDA, vence, lic))
    assert silencioso(verificar, lic, [firma.public_key()]) is not None


if __name__ == "__main__":
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("solicitud")
    s.add_argument("--tipo", default="MENSUAL", choices=PRECIO_BASE)
    s.add_argument("--secundarias", type=int, default=2)
    s.add_argument("--renueva", help="id de la licencia que se renueva")
    s.add_argument(
        "--recupera",
        help="0.25.0: id de la licencia que se recupera (excluyente con --renueva)",
    )
    r = sub.add_parser("revocadas")
    rs = r.add_subparsers(dest="accion", required=True)
    rf = rs.add_parser("firmar")
    rf.add_argument("ids", nargs="+")
    rv = rs.add_parser("verificar")
    rv.add_argument("archivo")
    rv.add_argument("--id")
    v = sub.add_parser("verificar")
    v.add_argument("archivo")
    v.add_argument("--secundarias", type=int)
    v.add_argument("--tipo", choices=PRECIO_BASE)
    v.add_argument("--vence", help="fecha de vencimiento esperada (AAAA-MM-DD, UTC)")
    sub.add_parser("autoprueba")
    sub.add_parser("vector")
    a = ap.parse_args()
    if a.cmd == "solicitud":
        if not 0 <= a.secundarias <= MAX_SECUNDARIAS:
            sys.exit(f"--secundarias debe estar entre 0 y {MAX_SECUNDARIAS}")
        if a.renueva and a.recupera:
            sys.exit("--renueva y --recupera son excluyentes")
        mensaje, codigo, p = solicitud(
            a.tipo, a.secundarias, renueva=a.renueva, recupera=a.recupera
        )
        print(mensaje)
    elif a.cmd == "verificar":
        sys.exit(
            0
            if verificar(
                open(a.archivo, encoding="utf-8").read(),
                esperado_n=a.secundarias,
                esperado_tipo=a.tipo,
                esperado_vence=a.vence,
            )
            else 1
        )
    elif a.cmd == "revocadas":
        prueba = ec.derive_private_key(VECTOR_FIRMA_D, ec.SECP256R1())
        if a.accion == "firmar":
            print(firmar_revocadas(a.ids, prueba, VECTOR_REV_EMITIDA))
        else:
            claves = [
                serialization.load_der_public_key(unb64(k)) for k in GL_SIGN_SPKIS
            ] + [prueba.public_key()]
            sys.exit(
                0
                if verificar_revocadas(
                    open(a.archivo, encoding="utf-8").read().strip(), claves, a.id
                )
                else 1
            )
    elif a.cmd == "vector":
        vector()
    else:
        sys.exit(0 if autoprueba() else 1)
