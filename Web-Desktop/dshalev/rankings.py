"""Rankings de Inicio calculados sobre ventas web y recibidas de Shalev móvil."""
from __future__ import annotations

import json
from collections import defaultdict
from typing import Any


def _top(values: dict[str, dict[str, int]], *, limit: int = 3) -> list[dict[str, Any]]:
    return [dict(name=name, **row) for name, row in sorted(
        values.items(), key=lambda pair: (-pair[1]["total"], pair[0].casefold())
    )[:limit]]


def _mobile_sales(conn, start: str, end: str) -> list[dict[str, Any]]:
    rows = conn.execute("""SELECT employee_id,employee_name,shift_uuid,sale_at_ms,total_cents,payload_json
        FROM sync_sales WHERE cancelled_at IS NULL
        AND date(sale_at_ms/1000,'unixepoch','localtime') BETWEEN ? AND ?""", (start, end)).fetchall()
    output = []
    for row in rows:
        try:
            wrapper = json.loads(row["payload_json"])
            sale = wrapper.get("venta", {})
            if isinstance(sale, dict):
                output.append({**dict(row), "sale": sale})
        except (TypeError, ValueError):
            continue
    return output


def _details(sale: dict[str, Any]):
    details = sale.get("detalles", [])
    return details if isinstance(details, list) else []


def _services_sold(conn, start: str, end: str) -> dict[str, dict[str, int]]:
    values: dict[str, dict[str, int]] = defaultdict(lambda: {"quantity": 0, "total": 0})
    for row in conn.execute("""SELECT sl.item_name name,SUM(sl.quantity) quantity,
            SUM(sl.quantity*sl.unit_price_cents) total
        FROM sale_lines sl JOIN sales s ON s.id=sl.sale_id JOIN items i ON i.id=sl.item_id
        WHERE s.cancelled_at IS NULL AND i.category='Servicio'
        AND date(s.created_at,'localtime') BETWEEN ? AND ? GROUP BY sl.item_name""", (start, end)):
        values[row["name"]]["quantity"] += row["quantity"]
        values[row["name"]]["total"] += row["total"]
    for row in _mobile_sales(conn, start, end):
        for detail in _details(row["sale"]):
            if not isinstance(detail, dict) or str(detail.get("clase", "PRODUCTO")).upper() != "SERVICIO":
                continue
            try:
                name = str(detail.get("nombre", "Servicio"))[:100]
                quantity = int(detail.get("cantidad", 0))
                price = int(detail.get("precioUnitarioCent", 0))
            except (TypeError, ValueError):
                continue
            if quantity > 0 and price >= 0:
                values[name]["quantity"] += quantity
                values[name]["total"] += quantity * price
    return values


def top_servicios(conn, start: str, end: str) -> list[dict[str, Any]]:
    return sorted(_top(_services_sold(conn, start, end)), key=lambda row: (-row["quantity"], row["name"].casefold()))


def servicios_lento(conn, start: str, end: str, top: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not top:
        return []
    totals = _services_sold(conn, start, end)
    for row in conn.execute("SELECT name FROM items WHERE active=1 AND category='Servicio' UNION SELECT name FROM sync_services WHERE deleted=0"):
        totals.setdefault(row["name"], {"quantity": 0, "total": 0})
    return [dict(name=name, **row) for name, row in sorted(
        totals.items(), key=lambda pair: (pair[1]["quantity"], pair[0].casefold())
    )[:3]]


def _article_values(conn, start: str, end: str) -> dict[str, dict[str, Any]]:
    values: dict[str, dict[str, Any]] = {}
    rows = conn.execute("""SELECT sl.item_id,sl.item_name,sl.quantity,sl.unit_price_cents,
            COALESCE(i.cost_cents,0) cost_cents,i.category
        FROM sale_lines sl JOIN sales s ON s.id=sl.sale_id LEFT JOIN items i ON i.id=sl.item_id
        WHERE s.cancelled_at IS NULL AND date(s.created_at,'localtime') BETWEEN ? AND ?""", (start, end))
    for row in rows:
        category = str(row["category"] or "Producto")
        if category == "Servicio":
            continue
        kind = "INSUMO" if category == "Insumo" else "PRODUCTO"
        key = f"{kind}:{row['item_id']}"
        entry = values.setdefault(key, {"name": row["item_name"], "quantity": 0, "total": 0, "profit": 0})
        entry["quantity"] += row["quantity"]
        entry["total"] += row["quantity"] * row["unit_price_cents"]
        entry["profit"] += row["quantity"] * (row["unit_price_cents"] - row["cost_cents"])
    for row in _mobile_sales(conn, start, end):
        for detail in _details(row["sale"]):
            if not isinstance(detail, dict):
                continue
            kind = str(detail.get("clase", "PRODUCTO")).upper()
            if kind == "SERVICIO":
                continue
            try:
                item_id = int(detail.get("productoId", 0))
                quantity = int(detail.get("cantidad", 0))
                price = int(detail.get("precioUnitarioCent", 0))
                cost = int(detail.get("costoUnitarioCent", 0))
            except (TypeError, ValueError):
                continue
            if quantity <= 0 or price < 0 or item_id <= 0 or kind not in {"PRODUCTO", "INSUMO"}:
                continue
            key = f"{kind}:{item_id}"
            entry = values.setdefault(key, {"name": str(detail.get("nombre", "Artículo"))[:100], "quantity": 0, "total": 0, "profit": 0})
            entry["quantity"] += quantity
            entry["total"] += quantity * price
            entry["profit"] += quantity * (price - cost)
    return values


def top_articulos(conn, start: str, end: str) -> list[dict[str, Any]]:
    values = _article_values(conn, start, end)
    return sorted((dict(**value) for value in values.values()), key=lambda row: (-row["quantity"], row["name"].casefold()))[:3]


def articulos_lentos(conn, start: str, end: str) -> list[dict[str, Any]]:
    values = _article_values(conn, start, end)
    if not values:
        return []
    for row in conn.execute("SELECT id,name,category FROM items WHERE active=1 AND category!='Servicio'"):
        kind = "INSUMO" if row["category"] == "Insumo" else "PRODUCTO"
        values.setdefault(f"{kind}:{row['id']}", {"name": row["name"], "quantity": 0, "total": 0, "profit": 0})
    return sorted((dict(**value) for value in values.values()), key=lambda row: (row["quantity"], row["name"].casefold()))[:3]


def top_rentabilidad(conn, start: str, end: str) -> list[dict[str, Any]]:
    values = [dict(**value) for value in _article_values(conn, start, end).values() if value["profit"] > 0]
    return sorted(values, key=lambda row: (-row["profit"], row["name"].casefold()))[:3]


def top_clientes(conn, start: str, end: str) -> list[dict[str, Any]]:
    values: dict[str, dict[str, int]] = defaultdict(lambda: {"quantity": 0, "total": 0})
    for row in conn.execute("""SELECT c.name,SUM(s.total_cents) total,COUNT(*) quantity
        FROM sales s JOIN customers c ON c.id=s.customer_id
        WHERE s.cancelled_at IS NULL AND s.payment_method='TRANSFERENCIA'
        AND date(s.created_at,'localtime') BETWEEN ? AND ? GROUP BY c.id,c.name""", (start, end)):
        values[row["name"]]["total"] += row["total"]
        values[row["name"]]["quantity"] += row["quantity"]
    for row in _mobile_sales(conn, start, end):
        transaction = row["sale"].get("transaccion")
        if not isinstance(transaction, dict):
            continue
        name = str(transaction.get("clienteNombre", "")).strip()[:120]
        ci = str(transaction.get("clienteCi", "")).strip()
        if not name:
            continue
        key = f"{name} · {ci}" if ci else name
        try:
            amount = int(transaction.get("importeCent", row["total_cents"]))
        except (TypeError, ValueError):
            amount = int(row["total_cents"])
        values[key]["total"] += max(0, amount)
        values[key]["quantity"] += 1
    return _top(values)


def top_empleados(conn, start: str, end: str) -> list[dict[str, Any]]:
    values: dict[str, dict[str, int]] = defaultdict(lambda: {"quantity": 0, "total": 0})
    for row in conn.execute("""SELECT e.name,SUM(s.total_cents) total,COUNT(*) quantity
        FROM sales s JOIN employees e ON e.id=s.employee_id WHERE s.cancelled_at IS NULL
        AND date(s.created_at,'localtime') BETWEEN ? AND ? GROUP BY e.id,e.name""", (start, end)):
        values[row["name"]]["total"] += row["total"]
        values[row["name"]]["quantity"] += row["quantity"]
    for row in _mobile_sales(conn, start, end):
        name = str(row["employee_name"] or "Empleado").strip()
        values[name]["total"] += int(row["total_cents"])
        values[name]["quantity"] += 1
    return _top(values)


def top_locales(conn, start: str, end: str, local_id: str) -> list[dict[str, Any]]:
    values: dict[str, dict[str, int]] = defaultdict(lambda: {"quantity": 0, "total": 0})
    local_name = str(local_id or "original").strip() or "original"
    for row in conn.execute("""SELECT SUM(total_cents) total,COUNT(*) quantity FROM sales
        WHERE cancelled_at IS NULL AND date(created_at,'localtime') BETWEEN ? AND ?""", (start, end)):
        if row["quantity"]:
            values[local_name]["total"] += row["total"] or 0
            values[local_name]["quantity"] += row["quantity"]
    for row in _mobile_sales(conn, start, end):
        name = str(row["sale"].get("local", "")).strip()
        if not name:
            shift = conn.execute("SELECT payload_json FROM sync_shifts WHERE employee_id=? AND uuid=?",
                                 (row["employee_id"], row["shift_uuid"])).fetchone()
            if shift:
                try:
                    turno = json.loads(shift["payload_json"]).get("turno", {})
                    name = str(turno.get("local", "")).strip() if isinstance(turno, dict) else ""
                except (TypeError, ValueError):
                    name = ""
        if name:
            values[name]["total"] += int(row["total_cents"])
            values[name]["quantity"] += 1
    return _top(values, limit=len(values))
