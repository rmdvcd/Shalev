"""Perfil del negocio en DShalev. Datos locales dentro de la base cifrada; no incluye tarjetas bancarias."""
from __future__ import annotations

import base64
import json
import re
from datetime import date
from typing import Any

CLAVE = "perfil_personal"
_RE_TELEFONO = re.compile(r"^\+?[0-9]{8,15}$")


def validar_ci(value: str) -> bool:
    ci = re.sub(r"[ -]", "", value)
    if not re.fullmatch(r"\d{11}", ci):
        return False
    yy, mm, dd = int(ci[:2]), int(ci[2:4]), int(ci[4:6])
    siglo = 1800 if ci[6] == "9" else 1900 if ci[6] in "012345" else 2000
    try:
        return date(siglo + yy, mm, dd) <= date.today()
    except ValueError:
        return False


def _nombre_valido(value: str) -> bool:
    return (2 <= len(value) <= 80 and value[0].isalpha() and value[-1].isalpha()
            and all(char.isalpha() or char in " '-" for char in value))


def validar(data: dict[str, Any]) -> tuple[dict[str, Any] | None, str | None]:
    nombre = str(data.get("nombre", "")).strip()
    apellidos = str(data.get("apellidos", "")).strip()
    ci = re.sub(r"[ -]", "", str(data.get("ci", ""))).upper()
    telefonos_raw = [re.sub(r"[\s()-]", "", v) for v in str(data.get("telefonos", "")).splitlines() if v.strip()]
    telefonos: list[str] = []
    for raw in telefonos_raw:
        if not re.fullmatch(r"\+?[0-9\s().-]+", raw):
            return None, "telefonos"
        digits = re.sub(r"\D", "", raw)
        if raw.startswith("+"):
            normalizado = "+" + digits
        elif raw.startswith("00") and len(digits) > 2:
            normalizado = "+" + digits[2:]
        elif len(digits) == 8:
            normalizado = "+53" + digits
        elif len(digits) == 10 and digits.startswith("53"):
            normalizado = "+" + digits
        elif 11 <= len(digits) <= 15:
            normalizado = "+" + digits
        else:
            return None, "telefonos"
        if not _RE_TELEFONO.fullmatch(normalizado):
            return None, "telefonos"
        telefonos.append(normalizado)
    negocio = str(data.get("nombre_negocio", "")).strip()
    local_name = str(data.get("nombre_local", "")).strip()
    for campo, valor in (("nombre", nombre), ("apellidos", apellidos)):
        if valor and not _nombre_valido(valor):
            return None, campo
    if ci and not validar_ci(ci):
        return None, "ci"
    if len(negocio) > 80:
        return None, "nombre_negocio"
    if len(local_name) > 80:
        return None, "nombre_local"
    if len(telefonos) > 10:
        return None, "telefonos"
    return {"nombre": nombre, "apellidos": apellidos, "ci": ci, "telefonos": telefonos,
            "nombre_negocio": negocio, "nombre_local": local_name}, None


def cargar(conn) -> dict[str, Any]:
    row = conn.execute("SELECT value FROM app_meta WHERE key=?", (CLAVE,)).fetchone()
    try:
        stored = json.loads(row["value"]) if row else {}
    except (ValueError, TypeError):
        stored = {}
    if not isinstance(stored, dict):
        stored = {}
    negocio = conn.execute("SELECT value FROM app_meta WHERE key='business_name'").fetchone()
    local = conn.execute("SELECT value FROM app_meta WHERE key='local_name'").fetchone()
    stored["nombre_negocio"] = negocio["value"] if negocio else ""
    stored["nombre_local"] = local["value"] if local else ""
    stored.setdefault("nombre", "")
    stored.setdefault("apellidos", "")
    stored.setdefault("ci", "")
    stored.setdefault("telefonos", [])
    return stored


def guardar(conn, perfil: dict[str, Any]) -> None:
    serializado = json.dumps({k: perfil[k] for k in ("nombre", "apellidos", "ci", "telefonos")}, ensure_ascii=False)
    conn.execute("INSERT INTO app_meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (CLAVE, serializado))
    conn.execute("INSERT INTO app_meta(key,value) VALUES('business_name',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (perfil["nombre_negocio"],))
    conn.execute("INSERT INTO app_meta(key,value) VALUES('local_name',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (perfil["nombre_local"],))


def cargar_logo(conn) -> bytes | None:
    row = conn.execute("SELECT value FROM app_meta WHERE key='business_logo_png'").fetchone()
    if not row:
        return None
    try:
        raw = base64.b64decode(row["value"], validate=True)
        from .png import decodificar_png_rgb
        decodificar_png_rgb(raw)
        return raw
    except (ValueError, TypeError):
        return None


def guardar_logo(conn, datos: bytes) -> None:
    encoded = base64.b64encode(datos).decode("ascii")
    conn.execute("INSERT INTO app_meta(key,value) VALUES('business_logo_png',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (encoded,))


def eliminar_logo(conn) -> None:
    conn.execute("DELETE FROM app_meta WHERE key='business_logo_png'")
