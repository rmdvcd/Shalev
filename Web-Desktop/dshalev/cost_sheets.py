"""Cálculo decimal de fichas de costo, alineado con el dominio Android."""
from decimal import Decimal, ROUND_HALF_UP, localcontext
import re

CATEGORIES = {
    "MATERIAS": "Materias primas y materiales",
    "TRABAJO": "Fuerza de trabajo directa",
    "OTROS": "Otros gastos directos",
    "INDIRECTOS": "Gastos indirectos de producción",
}
CURRENCIES = ("CUP", "USD")
CENT = Decimal("0.01")


def parse_number(value: str, *, optional: bool = False) -> Decimal | None:
    text = value.strip().replace(",", ".")
    if optional and not text:
        return None
    if not re.fullmatch(r"[0-9]{1,12}(?:\.[0-9]{1,4})?", text):
        raise ValueError("Escribe un número no negativo con hasta 12 enteros y 4 decimales.")
    return Decimal(text)


def calculate(lines: list[dict], margin: Decimal | None, margin_unit: str) -> dict:
    if margin_unit not in ("PORCENTAJE", "FACTOR"):
        raise ValueError("Unidad de margen no válida.")
    with localcontext() as ctx:
        ctx.prec = 64
        by_category = {category: {currency: Decimal("0.00") for currency in CURRENCIES} for category in CATEGORIES}
        enriched = []
        for row in lines:
            quantity = parse_number(row["quantity"])
            cost = parse_number(row["unit_cost"])
            subtotal = (quantity * cost).quantize(CENT, rounding=ROUND_HALF_UP)
            by_category[row["category"]][row["currency"]] += subtotal
            enriched.append({**row, "subtotal": subtotal})
        totals = {currency: sum((by_category[category][currency] for category in CATEGORIES), Decimal("0.00")) for currency in CURRENCIES}
        prices = None
        if margin is not None:
            factor = Decimal(1) + margin / 100 if margin_unit == "PORCENTAJE" else margin
            prices = {currency: (total * factor).quantize(CENT, rounding=ROUND_HALF_UP) for currency, total in totals.items()}
    return {"lines": enriched, "categories": by_category, "totals": totals, "prices": prices}


def validate_line(data: dict[str, str]) -> tuple[str, str, str, str, str, str]:
    description, unit = data.get("description", "").strip(), data.get("unit", "").strip()
    category, currency = data.get("category", ""), data.get("currency", "")
    if not description or len(description) > 80 or not unit or len(unit) > 20:
        raise ValueError("Revisa la descripción y la unidad de la partida.")
    if category not in CATEGORIES or currency not in CURRENCIES:
        raise ValueError("Categoría o moneda no válida.")
    quantity, cost = parse_number(data.get("quantity", "")), parse_number(data.get("unit_cost", ""))
    return description, category, str(quantity), unit, str(cost), currency
