"""Aritmética fija y libro inmutable de movimientos de inventario."""
from __future__ import annotations

from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
import sqlite3
from typing import Any

MILLI = 1000
MAX_QUANTITY_MILLI = 10**15


def to_milli(value: Any) -> int:
    """Convierte una cantidad decimal exacta a milésimas, sin operar con float."""
    try:
        decimal = Decimal(str(value).strip())
    except (InvalidOperation, ValueError, AttributeError) as exc:
        raise ValueError("La cantidad no es válida.") from exc
    if not decimal.is_finite() or decimal * MILLI != (decimal * MILLI).to_integral_value():
        raise ValueError("La cantidad debe expresarse en unidades enteras o milésimas.")
    result = int(decimal * MILLI)
    if abs(result) > MAX_QUANTITY_MILLI:
        raise ValueError("La cantidad supera el límite permitido.")
    return result


def migrate_legacy_quantity(value: Any) -> int:
    """Normaliza una cantidad SQLite previa a milésimas y conserva su origen en el ledger."""
    try:
        decimal = Decimal(str(value))
    except (InvalidOperation, ValueError, AttributeError) as exc:
        raise ValueError(f"Cantidad heredada no válida: {value!r}") from exc
    if not decimal.is_finite():
        raise ValueError(f"Cantidad heredada no finita: {value!r}")
    result = int((decimal * MILLI).quantize(Decimal(1), rounding=ROUND_HALF_UP))
    if abs(result) > MAX_QUANTITY_MILLI:
        raise ValueError(f"Cantidad heredada fuera de límite: {value!r}")
    return result


def format_milli(value: int) -> str:
    """Presenta una cantidad milimétrica sin ceros decimales superfluos."""
    value = int(value)
    sign = "-" if value < 0 else ""
    whole, fraction = divmod(abs(value), MILLI)
    suffix = f"{fraction:03d}".rstrip("0")
    return f"{sign}{whole}" + (f".{suffix}" if suffix else "")


def record_movement(
    conn: sqlite3.Connection,
    item_id: int,
    delta_milli: int,
    *,
    movement_type: str,
    actor: str,
    reason: str,
    source: str,
    reference: str | None = None,
    actor_type: str = "usuario",
    actor_id: int | None = None,
) -> int:
    """Agrega un evento no editable usando el saldo canónico ya actualizado."""
    delta_milli = int(delta_milli)
    if abs(delta_milli) > MAX_QUANTITY_MILLI:
        raise ValueError("El movimiento supera el límite permitido.")
    item = conn.execute(
        "SELECT name,category,unit,stock_milli FROM items WHERE id=?", (item_id,)
    ).fetchone()
    if item is None:
        raise ValueError("El artículo del movimiento ya no existe.")
    cursor = conn.execute(
        """INSERT INTO inventory_movements(
            item_id,item_name,item_category,item_unit,movement_type,delta_milli,stock_after_milli,
            actor_type,actor_id,actor,reason,source,reference
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        (
            item_id, item["name"], item["category"], item["unit"], movement_type,
            delta_milli, int(item["stock_milli"]), actor_type,
            int(actor_id) if actor_id is not None else None,
            str(actor)[:80] or "Desconocido", str(reason)[:500], str(source)[:40],
            str(reference)[:160] if reference else None,
        ),
    )
    return int(cursor.lastrowid)


def record_retirement(
    conn: sqlite3.Connection,
    item_id: int,
    *,
    actor: str,
    reason: str,
    source: str,
    reference: str | None = None,
    actor_type: str = "usuario",
    actor_id: int | None = None,
) -> int:
    """Agrega el evento inmutable de retiro sin alterar el saldo ni las ventas históricas."""
    item = conn.execute(
        "SELECT name,category,unit,stock_milli FROM items WHERE id=?", (item_id,)
    ).fetchone()
    if item is None:
        raise ValueError("El artículo del retiro ya no existe.")
    cursor = conn.execute(
        """INSERT INTO item_retirements(
            item_id,item_name,item_category,item_unit,stock_milli,actor_type,actor_id,actor,reason,source,reference
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
        (
            item_id, item["name"], item["category"], item["unit"], int(item["stock_milli"]), actor_type,
            int(actor_id) if actor_id is not None else None, str(actor)[:80] or "Desconocido",
            str(reason)[:500], str(source)[:40], str(reference)[:160] if reference else None,
        ),
    )
    return int(cursor.lastrowid)
