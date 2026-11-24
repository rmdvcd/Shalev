"""Pruebas completas del Directorio de Confianza v1 (Anexo A de IMPLEMENTACION_SHALEV.md)."""

from __future__ import annotations

import base64
import json
from pathlib import Path
import pytest
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.hazmat.primitives import serialization

from dshalev.licencia.directorio import (
    DirectorioConfianza,
    DirectorioError,
    calcular_huella,
    firmar_directorio,
    normalizar_huella,
    verificar_y_parsear_directorio,
)

# Claves de prueba reproducibles
SCALAR_A = 0x4141414141414141414141414141414141414141414141414141414141414141
SCALAR_B = 0x4242424242424242424242424242424242424242424242424242424242424242
PRIV_A = ec.derive_private_key(SCALAR_A, ec.SECP256R1())
PRIV_B = ec.derive_private_key(SCALAR_B, ec.SECP256R1())

SPKI_A_DER = PRIV_A.public_key().public_bytes(
    serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
)
SPKI_B_DER = PRIV_B.public_key().public_bytes(
    serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
)

VECTORS_PATH = Path(__file__).resolve().parents[2] / "docs" / "vectores" / "directorio-v1.json"


def _generar_par_p256() -> tuple[ec.EllipticCurvePrivateKey, str, bytes, str]:
    priv = ec.generate_private_key(ec.SECP256R1())
    der = priv.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    b64 = base64.b64encode(der).decode("ascii")
    huella = calcular_huella(der)
    return priv, b64, der, huella


def test_vector_directorio_v1_valido():
    """El archivo docs/vectores/directorio-v1.json se valida correctamente con la Raíz A."""
    assert VECTORS_PATH.is_file(), f"No se encontró el vector en {VECTORS_PATH}"
    contenido = VECTORS_PATH.read_text(encoding="utf-8")
    d = verificar_y_parsear_directorio(contenido, raices_spki=[SPKI_A_DER, SPKI_B_DER])
    assert d.v == 1
    assert d.seq == 1
    assert d.emitida_en == 1776470400
    # Fase 1.3: ecdh + firma vigente + 5 firmas históricas (retiradas) + una clave de canje.
    assert len(d.claves) == 8
    assert d.ecdh_vigente() is not None
    # En la fecha de emisión del directorio solo la firma vigente está en su intervalo.
    sign_keys = d.sign_keys_for(1776470400)
    assert len(sign_keys) == 1
    # Pero una licencia emitida antes de la rotación sigue verificando con su firma histórica.
    assert len(d.sign_keys_for(1759622400)) == 6
    assert len(d.sign_keys_for(1761177601)) == 1
    # El bloque `canje` publica la base autorizada y los appIds de los tres productos.
    assert d.endpoint_base == "https://gl-redeem.example.test"
    assert set(d.app_ids) == {"SHALEV-ANDROID", "SHALEV-IOS", "DSHALEV"}
    assert [c.kid for c in d.claves if c.uso == "canje"] == ["gl-redeem-vector"]


def test_firmado_con_raiz_b():
    """Un directorio firmado por la Raíz B es aceptado si Raíz B está en las raíces de confianza."""
    _, b64, der, fp = _generar_par_p256()
    datos = {
        "v": 1,
        "seq": 2,
        "emitidaEn": 1780000000,
        "claves": [
            {
                "kid": "k1",
                "uso": "firma",
                "spki": b64,
                "huella": fp,
                "desde": 1700000000,
                "hasta": None,
                "estado": "vigente",
            }
        ],
    }
    sobre = firmar_directorio(datos, PRIV_B, identificador_raiz="B")
    d = verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER, SPKI_B_DER])
    assert d.seq == 2
    assert len(d.claves) == 1


def test_firma_alterada_rechazada():
    """Cualquier alteración en la firma debe causar DirectorioError."""
    contenido = VECTORS_PATH.read_text(encoding="utf-8")
    sobre = json.loads(contenido)
    # Corromper firma
    firma_orig = sobre["firma"]
    sobre["firma"] = ("A" if firma_orig[0] != "A" else "B") + firma_orig[1:]
    with pytest.raises(DirectorioError, match="Firma del directorio no válida"):
        verificar_y_parsear_directorio(json.dumps(sobre), raices_spki=[SPKI_A_DER])


def test_firma_clave_desconocida_rechazada():
    """Si la raíz de la firma no está autorizada, se rechaza."""
    ajena = ec.generate_private_key(ec.SECP256R1())
    datos = {"v": 1, "seq": 1, "emitidaEn": 1776470400, "claves": []}
    sobre = firmar_directorio(datos, ajena, identificador_raiz="A")
    with pytest.raises(DirectorioError, match="Firma del directorio no válida"):
        verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])


def test_spki_no_p256_rechazada():
    """Si una clave no es P-256 (p.ej. RSA), debe rechazarse."""
    rsa_priv = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    rsa_der = rsa_priv.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    rsa_b64 = base64.b64encode(rsa_der).decode("ascii")
    rsa_fp = calcular_huella(rsa_der)

    datos = {
        "v": 1,
        "seq": 1,
        "emitidaEn": 1776470400,
        "claves": [
            {
                "kid": "rsa-key",
                "uso": "firma",
                "spki": rsa_b64,
                "huella": rsa_fp,
                "desde": 1700000000,
                "hasta": None,
                "estado": "vigente",
            }
        ],
    }
    sobre = firmar_directorio(datos, PRIV_A)
    with pytest.raises(DirectorioError, match="no es P-256"):
        verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])


def test_huella_incorrecta_rechazada():
    """Si la huella no coincide con el SPKI DER, debe rechazarse."""
    _, b64, _, _ = _generar_par_p256()
    datos = {
        "v": 1,
        "seq": 1,
        "emitidaEn": 1776470400,
        "claves": [
            {
                "kid": "k1",
                "uso": "firma",
                "spki": b64,
                "huella": "sha256:0000:1111:2222:3333:4444:5555:6666:7777:8888:9999:aaaa:bbbb:cccc:dddd:eeee:ffff",
                "desde": 1700000000,
                "hasta": None,
                "estado": "vigente",
            }
        ],
    }
    sobre = firmar_directorio(datos, PRIV_A)
    with pytest.raises(DirectorioError, match="La huella no coincide"):
        verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])


def test_payload_excede_64k_rechazado():
    """Sobres mayores a 64 KiB deben rechazarse sin procesar."""
    datos = {
        "v": 1,
        "seq": 1,
        "emitidaEn": 1776470400,
        "claves": [],
        "relleno": "X" * (65 * 1024),
    }
    sobre = firmar_directorio(datos, PRIV_A)
    assert len(sobre.encode("utf-8")) > 64 * 1024
    with pytest.raises(DirectorioError, match="excede el tamaño máximo"):
        verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])


def test_dos_ecdh_vigentes_rechazadas():
    """No puede haber más de una clave ECDH con estado vigente."""
    _, b64_1, _, fp1 = _generar_par_p256()
    _, b64_2, _, fp2 = _generar_par_p256()
    datos = {
        "v": 1,
        "seq": 1,
        "emitidaEn": 1776470400,
        "claves": [
            {
                "kid": "ecdh-1",
                "uso": "ecdh",
                "spki": b64_1,
                "huella": fp1,
                "desde": 1700000000,
                "hasta": None,
                "estado": "vigente",
            },
            {
                "kid": "ecdh-2",
                "uso": "ecdh",
                "spki": b64_2,
                "huella": fp2,
                "desde": 1700000000,
                "hasta": None,
                "estado": "vigente",
            },
        ],
    }
    sobre = firmar_directorio(datos, PRIV_A)
    with pytest.raises(DirectorioError, match="más de una clave ECDH en estado 'vigente'"):
        verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])


def test_version_invalida_rechazada():
    """v != 1 debe rechazarse."""
    datos = {"v": 2, "seq": 1, "emitidaEn": 1776470400, "claves": []}
    sobre = firmar_directorio(datos, PRIV_A)
    with pytest.raises(DirectorioError, match="Versión de directorio no soportada"):
        verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])


def test_clave_comprometida():
    """Una clave comprometida es marcada y nunca usada para validar licencias."""
    _, b64, der, fp = _generar_par_p256()
    datos = {
        "v": 1,
        "seq": 1,
        "emitidaEn": 1776470400,
        "claves": [
            {
                "kid": "k-bad",
                "uso": "firma",
                "spki": b64,
                "huella": fp,
                "desde": 1700000000,
                "hasta": None,
                "estado": "comprometida",
            }
        ],
    }
    sobre = firmar_directorio(datos, PRIV_A)
    d = verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])
    assert d.es_clave_comprometida(der) is True
    assert len(d.sign_keys_for(1776470400)) == 0


def test_ventana_temporal_claves():
    """Clave fuera de ventana desde/hasta no es retornada por sign_keys_for."""
    _, b64, _, fp = _generar_par_p256()
    datos = {
        "v": 1,
        "seq": 1,
        "emitidaEn": 1776470400,
        "claves": [
            {
                "kid": "k-temp",
                "uso": "firma",
                "spki": b64,
                "huella": fp,
                "desde": 1700000000,
                "hasta": 1750000000,
                "estado": "retirada",
            }
        ],
    }
    sobre = firmar_directorio(datos, PRIV_A)
    d = verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])
    # Dentro de la ventana
    assert len(d.sign_keys_for(1720000000)) == 1
    # Antes de desde
    assert len(d.sign_keys_for(1600000000)) == 0
    # Después de hasta
    assert len(d.sign_keys_for(1760000000)) == 0


def test_claves_canje():
    """Claves con uso 'canje' se obtienen mediante redeem_key y claves_canje."""
    _, b64, der, fp = _generar_par_p256()
    datos = {
        "v": 1,
        "seq": 1,
        "emitidaEn": 1776470400,
        "claves": [
            {
                "kid": "gl-redeem-v1",
                "uso": "canje",
                "spki": b64,
                "huella": fp,
                "desde": 1700000000,
                "hasta": None,
                "estado": "vigente",
            }
        ],
    }
    sobre = firmar_directorio(datos, PRIV_A)
    d = verificar_y_parsear_directorio(sobre, raices_spki=[SPKI_A_DER])
    assert d.redeem_key("gl-redeem-v1") == der
    assert d.redeem_key("inexistente") is None
    assert "gl-redeem-v1" in d.claves_canje()


def test_anti_retroceso_seq_menor_rechazado():
    """Un directorio con seq menor que el actual debe ser rechazado."""
    _, b64, _, fp = _generar_par_p256()
    datos5 = {
        "v": 1,
        "seq": 5,
        "emitidaEn": 1776470400,
        "claves": [{"kid": "k1", "uso": "firma", "spki": b64, "huella": fp, "desde": 1700000000, "hasta": None, "estado": "vigente"}],
    }
    datos4 = {
        "v": 1,
        "seq": 4,
        "emitidaEn": 1776470400,
        "claves": [{"kid": "k1", "uso": "firma", "spki": b64, "huella": fp, "desde": 1700000000, "hasta": None, "estado": "vigente"}],
    }
    d5 = verificar_y_parsear_directorio(firmar_directorio(datos5, PRIV_A), raices_spki=[SPKI_A_DER])
    d4 = verificar_y_parsear_directorio(firmar_directorio(datos4, PRIV_A), raices_spki=[SPKI_A_DER])

    from dshalev.licencia.directorio import evaluar_actualizacion

    with pytest.raises(DirectorioError, match="Anti-retroceso.*menor que el actual"):
        evaluar_actualizacion(actual=d5, nuevo=d4)


def test_idempotencia_mismo_seq_identico():
    """Un directorio con el mismo seq y contenido idéntico es aceptado como idempotente."""
    _, b64, _, fp = _generar_par_p256()
    datos5 = {
        "v": 1,
        "seq": 5,
        "emitidaEn": 1776470400,
        "claves": [{"kid": "k1", "uso": "firma", "spki": b64, "huella": fp, "desde": 1700000000, "hasta": None, "estado": "vigente"}],
    }
    s5_1 = firmar_directorio(datos5, PRIV_A)
    s5_2 = firmar_directorio(datos5, PRIV_A)
    d5_1 = verificar_y_parsear_directorio(s5_1, raices_spki=[SPKI_A_DER])
    d5_2 = verificar_y_parsear_directorio(s5_2, raices_spki=[SPKI_A_DER])

    from dshalev.licencia.directorio import evaluar_actualizacion

    res = evaluar_actualizacion(actual=d5_1, nuevo=d5_2)
    assert res.seq == 5


def test_conflicto_mismo_seq_contenido_distinto():
    """Mismo seq con contenido distinto se rechaza como conflicto."""
    _, b64_1, _, fp1 = _generar_par_p256()
    _, b64_2, _, fp2 = _generar_par_p256()
    datos_a = {
        "v": 1,
        "seq": 5,
        "emitidaEn": 1776470400,
        "claves": [{"kid": "k1", "uso": "firma", "spki": b64_1, "huella": fp1, "desde": 1700000000, "hasta": None, "estado": "vigente"}],
    }
    datos_b = {
        "v": 1,
        "seq": 5,
        "emitidaEn": 1776470400,
        "claves": [{"kid": "k2", "uso": "firma", "spki": b64_2, "huella": fp2, "desde": 1700000000, "hasta": None, "estado": "vigente"}],
    }
    da = verificar_y_parsear_directorio(firmar_directorio(datos_a, PRIV_A), raices_spki=[SPKI_A_DER])
    db = verificar_y_parsear_directorio(firmar_directorio(datos_b, PRIV_A), raices_spki=[SPKI_A_DER])

    from dshalev.licencia.directorio import evaluar_actualizacion

    with pytest.raises(DirectorioError, match="Conflicto de secuencia"):
        evaluar_actualizacion(actual=da, nuevo=db)


def test_descargar_directorio_restricciones():
    """Solo se permite HTTPS y solo el host github.com."""
    from dshalev.licencia.directorio import descargar_directorio_remoto

    # HTTP plano rechazado
    assert descargar_directorio_remoto("http://github.com/rmdvcd/Shalev/releases/download/revocaciones/directorio.json") is None
    # Host no autorizado rechazado
    assert descargar_directorio_remoto("https://evil.com/directorio.json") is None


def test_gestor_directorio_ciclo_vida(tmp_path: Path):
    """El gestor carga bootstrap, ignora caché corrupta y actualiza con seq superior."""
    from dshalev.licencia.directorio import GestorDirectorio

    cache_file = tmp_path / "directorio.json"

    _, b64_1, _, fp1 = _generar_par_p256()
    datos1 = {
        "v": 1,
        "seq": 1,
        "emitidaEn": 1776470400,
        "claves": [{"kid": "k1", "uso": "firma", "spki": b64_1, "huella": fp1, "desde": 1700000000, "hasta": None, "estado": "vigente"}],
    }
    bootstrap_str = firmar_directorio(datos1, PRIV_A)

    gestor = GestorDirectorio(
        ruta_cache=cache_file,
        raices_spki=[SPKI_A_DER],
        bootstrap_json=bootstrap_str,
    )
    efectivo = gestor.efectivo
    assert efectivo is not None
    assert efectivo.seq == 1

    # Escribir caché corrupta
    cache_file.write_text("contenido no json corrupto", encoding="utf-8")
    gestor_con_corrupto = GestorDirectorio(
        ruta_cache=cache_file,
        raices_spki=[SPKI_A_DER],
        bootstrap_json=bootstrap_str,
    )
    # Debe ignorar la caché corrupta y usar bootstrap
    assert gestor_con_corrupto.efectivo is not None
    assert gestor_con_corrupto.efectivo.seq == 1

    # Actualizar con seq 2
    _, b64_2, _, fp2 = _generar_par_p256()
    datos2 = {
        "v": 1,
        "seq": 2,
        "emitidaEn": 1780000000,
        "claves": [{"kid": "k2", "uso": "firma", "spki": b64_2, "huella": fp2, "desde": 1700000000, "hasta": None, "estado": "vigente"}],
    }
    sobre2 = firmar_directorio(datos2, PRIV_A)

    class MockHttpxResponse:
        status_code = 200
        content = sobre2.encode("utf-8")

    class MockHttpxClient:
        def get(self, url, follow_redirects=True):
            return MockHttpxResponse()

    ok = gestor.actualizar_desde_red(cliente_httpx=MockHttpxClient())
    assert ok is True
    assert gestor.efectivo.seq == 2
    assert cache_file.is_file()

    # Si se intenta actualizar con seq 1 (menor), debe ser rechazado y mantener seq 2
    class MockHttpxOldResponse:
        status_code = 200
        content = bootstrap_str.encode("utf-8")

    class MockHttpxOldClient:
        def get(self, url, follow_redirects=True):
            return MockHttpxOldResponse()

    ok_retroceso = gestor.actualizar_desde_red(cliente_httpx=MockHttpxOldClient())
    assert ok_retroceso is False
    assert gestor.efectivo.seq == 2


