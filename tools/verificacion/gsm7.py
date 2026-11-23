#!/usr/bin/env python3
"""Comprueba que los textos de licencia de Android, iOS y DShalev usan solo GSM-7 (C1).

Los mensajes de solicitud, ampliacion y recuperacion se envian por SMS (y WhatsApp).
Un solo caracter fuera del alfabeto GSM-7 obliga a la operadora a usar UCS-2 y reduce
la capacidad de cada parte de 160 a 70 caracteres.

- DShalev se ejecuta de verdad: se generan los mensajes con `generar_solicitud`.
- Android y iOS no se pueden ejecutar desde Python: se extraen sus literales con
  expresiones regulares y se reconstruye la plantilla con datos de muestra.

Uso: python tools/verificacion/gsm7.py [--detalle]
Sale con 0 si todo es GSM-7 y la solicitud estandar cabe en 5 partes; 1 en caso contrario.
"""
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]

# Alfabeto GSM 03.38. Los caracteres de extension cuentan como dos septetos.
GSM7_BASICO = set(
    "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\x1bÆæßÉ !\"#¤%&'()*+,-./"
    "0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
)
GSM7_EXTENSION = set("^{}\\[~]|€")

# Nombres con tildes y enie del encargo: el texto debe seguir siendo GSM-7.
MUESTRAS_NOMBRE = (
    ("José", "Rodríguez", "Pérez"),
    ("Yoandry", "Núñez", "Piña"),
    ("María Ángela", "Hernández", ""),
    ("Lázaro", "Ibáñez", ""),
)
MUESTRAS_CI = ("90010112345", "85020345678", "78091234567", "95061012345")

AVISO_ESPERADO = "Vale 7 dias: enviala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."
MAX_PARTES_SOLICITUD = 5
ID_LICENCIA = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

RUTA_KOTLIN = RAIZ / "Android/licencia/src/main/kotlin/cu/shalev/licencia/contract/SolicitudCifrada.kt"
RUTA_SWIFT = RAIZ / "iOS/Sources/Domain/License.swift"


@dataclass(frozen=True)
class Texto:
    plataforma: str
    nombre: str
    contenido: str

    @property
    def fuera(self) -> list[str]:
        return caracteres_fuera_gsm7(self.contenido)

    @property
    def septetos(self) -> int:
        return contar_septetos(self.contenido)

    @property
    def partes(self) -> int:
        return partes_sms(self.contenido)


def caracteres_fuera_gsm7(texto: str) -> list[str]:
    """Caracteres que obligarian a UCS-2, sin repetir y en orden de aparicion."""
    vistos: list[str] = []
    for c in texto:
        if c not in GSM7_BASICO and c not in GSM7_EXTENSION and c not in vistos:
            vistos.append(c)
    return vistos


def contar_septetos(texto: str) -> int:
    return sum(2 if c in GSM7_EXTENSION else 1 for c in texto)


def partes_sms(texto: str) -> int:
    """Partes GSM-7: 160 septetos la primera, 153 las concatenadas."""
    septetos = contar_septetos(texto)
    if septetos <= 160:
        return 1
    return -(-septetos // 153)


def _literal_a_texto(lit: str) -> str:
    return lit.replace("\\n", "\n").replace("\\t", "\t").replace('\\"', '"').replace("\\\\", "\\")


ACENTOS_GSM7 = str.maketrans(
    "áéíóúüñÁÉÍÓÚÜÑ", "aeiouunAEIOUUN"
)


def _ascii(texto: str) -> str:
    """Emula la normalizacion gsm7() de Android e iOS: quita tildes y deja solo ASCII imprimible."""
    return "".join(c for c in texto.translate(ACENTOS_GSM7) if 0x20 <= ord(c) <= 0x7E)


def _resolver_kotlin(lit: str, nombre: str, apellidos: str, ci: str) -> str:
    reemplazos = {
        "${gsm7(p.nombre)}": _ascii(nombre),
        "${gsm7(p.apellidos)}": _ascii(apellidos),
        "${p.nombre}": nombre,
        "${p.apellidos}": apellidos,
        "${p.ci}": ci,
        "${p.telefono}": "+5351234567",
        "${p.tipo.etiqueta}": "Anual",
        "${p.secundarias}": "10",
        "${p.recupera}": ID_LICENCIA,
        "${p.amplia}": ID_LICENCIA,
        "$previas": "0",
        "$adicionales": "10",
        "$it": "num-123",
    }
    for clave, valor in reemplazos.items():
        lit = lit.replace(clave, valor)
    lit = re.sub(r"\$\{Money\.(?:cup|formatExport)\([^}]*\)\}", "45,000 CUP", lit)
    return re.sub(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*", "X", lit)


def _resolver_swift(lit: str, nombre: str, apellidos: str, ci: str) -> str:
    reemplazos = {
        "\\(gsm7(payload.nombre))": _ascii(nombre),
        "\\(gsm7(payload.apellidos))": _ascii(apellidos),
        "\\(payload.nombre)": nombre,
        "\\(payload.apellidos)": apellidos,
        "\\(payload.ci)": ci,
        "\\(payload.telefono)": "+5351234567",
        "\\(payload.tipo.title)": "Anual",
        "\\(payload.secundarias)": "10",
        "\\(recovery)": ID_LICENCIA,
        "\\(extensionID)": ID_LICENCIA,
        "\\(prev)": "0",
        "\\(added)": "10",
        "\\(tx)": "num-123",
    }
    for clave, valor in reemplazos.items():
        lit = lit.replace(clave, valor)
    lit = re.sub(r"\\\(Cup\([^)]*\)\.formatted\)", "140,000.00 CUP", lit)
    return re.sub(r"\\\([^)]*\)", "X", lit)


def _bloque(texto: str, inicio: str, fin: str | None) -> str:
    desde = texto.index(inicio)
    hasta = texto.index(fin, desde) if fin else len(texto)
    return texto[desde:hasta]


def _literales(fragmento: str) -> list[str]:
    """Literales de cadena del fragmento, en orden, sin las interpolaciones resueltas."""
    return [
        _literal_a_texto(m)
        for m in re.findall(r'"((?:[^"\\]|\\.)*)"', fragmento)
    ]


def _codigo(prefijo: str) -> str:
    return prefijo + "A" * 210


def textos_kotlin() -> list[Texto]:
    fuente = RUTA_KOTLIN.read_text(encoding="utf-8")
    aviso = re.search(r'const val AVISO_FRESCURA = "([^"]*)"', fuente)
    if not aviso:
        raise SystemExit(f"No se encontro AVISO_FRESCURA en {RUTA_KOTLIN}")
    aviso_valor = aviso.group(1)
    bloques = {
        "solicitud": _bloque(fuente, "fun solicitud(", "fun ampliacion("),
        "ampliacion": _bloque(fuente, "fun ampliacion(", "fun recuperacion("),
        "recuperacion": _bloque(fuente, "fun recuperacion(", "fun licencia("),
    }
    textos: list[Texto] = []
    for indice, (nombre_texto, bloque) in enumerate(bloques.items()):
        nombre, apellidos, _ = MUESTRAS_NOMBRE[indice % len(MUESTRAS_NOMBRE)]
        ci = MUESTRAS_CI[indice % len(MUESTRAS_CI)]
        lineas = [
            _resolver_kotlin(lit, nombre, apellidos, ci)
            for lit in _literales(bloque)
            if lit.strip() or lit == ""
        ]
        # Los literales de estas funciones son, en orden, las lineas appendLine(...).
        partes = [linea for linea in lineas if linea != ""]
        partes.append(aviso_valor)
        partes.append("")
        partes.append(_codigo("SPVIR1:"))
        textos.append(Texto("Android", nombre_texto, "\n".join(partes)))
    return textos


def textos_swift() -> list[Texto]:
    fuente = RUTA_SWIFT.read_text(encoding="utf-8")
    bloque = _bloque(fuente, "private func requestMessage(", "public enum GLLicenseMessage")
    aviso = re.search(r'AVISO_FRESCURA = "([^"]*)"', fuente)
    if not aviso:
        raise SystemExit(f"No se encontro AVISO_FRESCURA en {RUTA_SWIFT}")
    aviso_valor = aviso.group(1)
    literales = [lit for lit in _literales(bloque) if lit not in ("", "\n")]
    return [
        Texto("iOS", "solicitud", _reconstruir_swift(literales, "Solicitud de licencia Shalev", aviso_valor, 0)),
        Texto("iOS", "ampliacion", _reconstruir_swift(literales, "Ampliar secundarias Shalev", aviso_valor, 1)),
        Texto("iOS", "recuperacion", _reconstruir_swift(literales, "Recuperar licencia Shalev", aviso_valor, 2)),
    ]


def _reconstruir_swift(literales: list[str], cabecera: str, aviso: str, indice: int) -> str:
    nombre, apellidos, _ = MUESTRAS_NOMBRE[indice % len(MUESTRAS_NOMBRE)]
    ci = MUESTRAS_CI[indice % len(MUESTRAS_CI)]

    def resolver(lit: str) -> str:
        return _resolver_swift(lit, nombre, apellidos, ci)

    por_prefijo = {resolver(lit).split(":")[0]: resolver(lit) for lit in literales if ":" in lit}
    comunes = [
        resolver(lit)
        for lit in literales
        if resolver(lit).startswith(("Nombre:", "Carné de identidad:", "Teléfono:"))
    ]
    lineas = [cabecera] + comunes
    if cabecera == "Recuperar licencia Shalev":
        lineas.append(por_prefijo.get("Licencia anterior", "Licencia anterior: X"))
    else:
        lineas.append(por_prefijo.get("Tipo", "Tipo: X"))
        if cabecera == "Ampliar secundarias Shalev":
            lineas.append(por_prefijo.get("Apps secundarias actuales", "Apps secundarias actuales: X"))
            lineas.append(por_prefijo.get("Secundarias adicionales", "Secundarias adicionales: X"))
            lineas.append(por_prefijo.get("Apps secundarias", "Apps secundarias: X"))
            lineas.append(por_prefijo.get("Precio", "Precio: X"))
            lineas.append(por_prefijo.get("Amplia", "Amplia: X"))
        else:
            lineas.append(por_prefijo.get("Apps secundarias", "Apps secundarias: X"))
            lineas.append(por_prefijo.get("Precio", "Precio: X"))
    lineas.append(aviso)
    lineas.append("")
    lineas.append(_codigo("SPVIR1:"))
    return "\n".join(lineas)


def textos_python() -> list[Texto]:
    sys.path.insert(0, str(RAIZ))
    sys.path.insert(0, str(RAIZ / "Web-Desktop"))
    from cryptography.hazmat.primitives.asymmetric import ec

    from dshalev.licencia.identidad import Identidad
    from dshalev.licencia.solicitud import generar_solicitud

    identidad = Identidad(Path("memoria"), ec.generate_private_key(ec.SECP256R1()))
    gl = ec.generate_private_key(ec.SECP256R1()).public_key()
    ahora = datetime(2026, 11, 20, 12, 0, tzinfo=timezone.utc)

    textos: list[Texto] = []
    for indice, (nombre, apellidos, _) in enumerate(MUESTRAS_NOMBRE):
        comun = dict(
            identidad=identidad,
            nombre=nombre,
            apellidos=apellidos,
            ci=MUESTRAS_CI[indice],
            telefono="+5351234567",
            tipo="ANUAL",
            secundarias=10,
            gl_ecdh_pub=gl,
            ahora=ahora,
        )
        casos = (
            ("solicitud", {}),
            ("ampliacion", {"amplia": ID_LICENCIA, "secundarias_previas": 0}),
            ("recuperacion", {"recupera": ID_LICENCIA}),
        )
        for sufijo, extra in casos:
            mensaje, _, _ = generar_solicitud(**comun, **extra)
            textos.append(Texto("DShalev", f"{sufijo} ({nombre})", mensaje))
    return textos


def _imprimir(textos: list[Texto], detalle: bool) -> list[str]:
    problemas: list[str] = []
    print(f"{'Plataforma':<10} {'Texto':<28} {'Car.':>5} {'Sept.':>6} {'Partes':>7}  GSM-7")
    print("-" * 70)
    for t in textos:
        estado = "OK" if not t.fuera else "FUERA: " + " ".join(f"U+{ord(c):04X}" for c in t.fuera)
        print(f"{t.plataforma:<10} {t.nombre:<28} {len(t.contenido):>5} {t.septetos:>6} {t.partes:>7}  {estado}")
        if t.fuera:
            problemas.append(f"{t.plataforma}/{t.nombre}: caracteres fuera de GSM-7 {t.fuera!r}")
        if detalle:
            print(t.contenido)
            print("-" * 70)
    for t in textos:
        if t.plataforma == "DShalev" and t.nombre.startswith("solicitud") and t.partes > MAX_PARTES_SOLICITUD:
            problemas.append(f"{t.nombre}: {t.partes} partes SMS (maximo {MAX_PARTES_SOLICITUD})")
    return problemas


def _avisos_en_fuentes() -> list[str]:
    """AVISO_FRESCURA debe existir y decir 7 dias en las tres plataformas."""
    problemas: list[str] = []
    fuentes = {
        "Android": RUTA_KOTLIN,
        "iOS": RUTA_SWIFT,
        "DShalev": RAIZ / "Web-Desktop/dshalev/licencia/solicitud.py",
    }
    for plataforma, ruta in fuentes.items():
        texto = ruta.read_text(encoding="utf-8")
        if AVISO_ESPERADO not in texto:
            problemas.append(f"{plataforma}: no contiene el AVISO_FRESCURA de 7 dias esperado")
    return problemas


def main() -> int:
    textos = textos_python() + textos_kotlin() + textos_swift()
    problemas = _avisos_en_fuentes() + _imprimir(textos, "--detalle" in sys.argv)
    print()
    if problemas:
        print("FALLOS:")
        for p in problemas:
            print(f"  - {p}")
        return 1
    print("GSM-7: todas las plataformas conformes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
