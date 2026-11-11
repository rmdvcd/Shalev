"""Exportación transaccional de fichas al catálogo web de DShalev."""
from decimal import Decimal, ROUND_HALF_UP, localcontext
import sqlite3

from .cost_sheets import calculate, parse_number

MAX_CENTS = 10**12
MAX_MILLI = 10**12


def export_sheet(conn: sqlite3.Connection, sheet_id: int, category: str, rate_text: str) -> int:
    """La conexión debe estar en una transacción; ante cualquier error el llamador revierte todo."""
    if category not in {"Producto", "Servicio"}:
        raise ValueError("Elige si exportar como producto o como servicio.")
    sheet = conn.execute("SELECT * FROM cost_sheets WHERE id=?", (sheet_id,)).fetchone()
    if not sheet:
        raise ValueError("La ficha no existe.")
    lines = [dict(row) for row in conn.execute("SELECT * FROM cost_sheet_lines WHERE sheet_id=? ORDER BY id", (sheet_id,))]
    materials = [line for line in lines if line["category"] == "MATERIAS"]
    if not materials:
        raise ValueError("Agrega al menos una partida de materias primas para crear la receta.")
    rate = parse_number(rate_text, optional=True)
    if any(line["currency"] == "USD" for line in lines) and (rate is None or rate <= 0):
        raise ValueError("Indica una tasa mayor que cero en CUP por USD para exportar partidas en USD.")
    rate = rate or Decimal(0)
    previous = conn.execute("""SELECT i.active FROM cost_sheet_exports e JOIN items i ON i.id=e.item_id
        WHERE e.sheet_id=? AND e.category=?""", (sheet_id, category)).fetchone()
    if previous and previous["active"]:
        raise ValueError("Esta ficha ya se exportó a un artículo activo de ese tipo; edítalo en el catálogo.")
    items = conn.execute("SELECT id,name,category,unit FROM items WHERE active=1").fetchall()
    name = sheet["name"].strip()
    if not 3 <= len(name) <= 80:
        raise ValueError("El nombre del artículo exportado debe tener entre 3 y 80 caracteres; renombra la ficha antes de exportar.")
    if any(item["category"] == category and item["name"].strip().casefold() == name.casefold() for item in items):
        raise ValueError("Ya existe un artículo activo con ese nombre y tipo; revisa el catálogo antes de exportar.")
    totals = calculate(lines, parse_number(sheet["margin"], optional=True), sheet["margin_unit"])
    with localcontext() as context:
        context.prec = 64
        def cents(cup: Decimal) -> int:
            value = (cup * 100).quantize(Decimal(1), rounding=ROUND_HALF_UP)
            if value < 0 or value > MAX_CENTS:
                raise ValueError("El importe supera el límite permitido para el catálogo.")
            return int(value)
        cost = cents(totals["totals"]["CUP"] + totals["totals"]["USD"] * rate)
        prices = totals["prices"] if totals["prices"] is not None else totals["totals"]
        price = cents(prices["CUP"] + prices["USD"] * rate)
        if category == "Producto" and price <= cost:
            price = cost + 100  # igual que Android: al menos un CUP por encima del costo
            if price > MAX_CENTS:
                raise ValueError("El precio calculado supera el límite permitido para el catálogo.")

        ingredients = {}
        for line in materials:
            key = line["description"].strip().casefold()
            unit = line["unit"].strip()
            milli = int((parse_number(line["quantity"]) * 1000).quantize(Decimal(1), rounding=ROUND_HALF_UP))
            if not 0 < milli <= MAX_MILLI:
                raise ValueError("Cada materia prima debe tener una cantidad válida de al menos 0.001.")
            matches = [item for item in items if item["category"] == "Insumo" and item["name"].strip().casefold() == key]
            if len(matches) > 1:
                raise ValueError("Hay varios insumos activos con el mismo nombre; corrige el catálogo antes de exportar.")
            if not matches and not 3 <= len(line["description"].strip()) <= 80:
                raise ValueError("El nombre de cada insumo creado debe tener entre 3 y 80 caracteres.")
            if matches and matches[0]["unit"].strip().casefold() != unit.casefold():
                raise ValueError("La unidad de una materia prima no coincide con la del insumo existente.")
            if key in ingredients:
                ingredient = ingredients[key]
                if ingredient["unit"].casefold() != unit.casefold():
                    raise ValueError("Una materia prima aparece con unidades distintas en la ficha.")
                ingredient["milli"] += milli
                if ingredient["milli"] > MAX_MILLI:
                    raise ValueError("La cantidad de una materia prima supera el límite permitido.")
            else:
                unit_cost = parse_number(line["unit_cost"]) * (rate if line["currency"] == "USD" else Decimal(1))
                ingredients[key] = {"name": line["description"].strip(), "unit": unit, "milli": milli,
                                    "id": matches[0]["id"] if matches else None, "unit_cost": cents(unit_cost)}
        for ingredient in ingredients.values():
            if ingredient["id"] is None:
                cursor = conn.execute("""INSERT INTO items(name,category,unit,stock,sale_price_cents,cost_cents,low_stock,critical_stock)
                    VALUES(?,'Insumo',?,0,0,?,0,0)""", (ingredient["name"], ingredient["unit"], ingredient["unit_cost"]))
                ingredient["id"] = cursor.lastrowid
        cursor = conn.execute("""INSERT INTO items(name,category,unit,stock,sale_price_cents,cost_cents,low_stock,critical_stock)
            VALUES(?,?,?,0,?,?,0,0)""", (name, category, 'servicio' if category == 'Servicio' else 'unidad', price, cost))
        item_id = cursor.lastrowid
        for ingredient in ingredients.values():
            conn.execute("INSERT INTO recipes(product_id,ingredient_id,quantity_milli) VALUES(?,?,?)", (item_id, ingredient["id"], ingredient["milli"]))
        conn.execute("""INSERT INTO cost_sheet_exports(sheet_id,category,item_id) VALUES(?,?,?)
            ON CONFLICT(sheet_id,category) DO UPDATE SET item_id=excluded.item_id""", (sheet_id, category, item_id))
        return item_id
