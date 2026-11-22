"""Instrucciones y métodos de cobro para el pago de licencias (T2)."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class MetodoCobro:
    nombre: str
    destino: str
    nota: str | None = None


TITULO_PAGO = "Cómo pagar"
FRASE_PAGA_SOLO = "Paga solo a los datos que muestra esta pantalla"
AVISO_SIN_METODOS = "Te enviaremos los datos de pago al recibir tu solicitud."

# Lista única de métodos de cobro configurados. Marcadores vacíos por seguridad si el repo es público.
METODOS_COBRO: tuple[MetodoCobro, ...] = ()
