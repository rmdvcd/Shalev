from __future__ import annotations

from urllib.parse import parse_qs

from fastapi import HTTPException, Request


async def form_data(request: Request) -> dict[str, str]:
    body = await request.body()
    if len(body) > 128 * 1024:
        raise HTTPException(status_code=413, detail="El formulario es demasiado grande.")
    try:
        parsed = parse_qs(body.decode("utf-8", errors="replace"), keep_blank_values=True, max_num_fields=100)
    except ValueError as exc:
        raise HTTPException(status_code=413, detail="El formulario contiene demasiados campos.") from exc
    return {key: values[-1] for key, values in parsed.items()}


def integer(value: str | None, *, minimum: int = 0, maximum: int = 10**12) -> int:
    if value is None:
        raise ValueError("Falta un valor numérico.")
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ValueError("Escribe un número entero válido.") from exc
    if parsed < minimum or parsed > maximum:
        raise ValueError("El valor está fuera del límite permitido.")
    return parsed


def money_to_cents(value: str | None) -> int:
    if value is None:
        raise ValueError("Falta el importe.")
    text = value.strip().replace(" ", "").replace(",", ".")
    if text.count(".") > 1:
        raise ValueError("Escribe un importe válido.")
    whole, dot, fraction = text.partition(".")
    if not whole.isdigit() or (dot and (not fraction.isdigit() or len(fraction) > 2)):
        raise ValueError("Escribe un importe positivo con hasta dos decimales.")
    cents = int(whole) * 100 + int((fraction + "00")[:2])
    if cents > 10**12:
        raise ValueError("El importe es demasiado grande.")
    return cents


def format_cup(cents: int | None) -> str:
    cents = int(cents or 0)
    whole, fraction = divmod(cents, 100)
    return f"{whole:,}".replace(",", " ") + (f".{fraction:02d}" if fraction else "") + " CUP"
