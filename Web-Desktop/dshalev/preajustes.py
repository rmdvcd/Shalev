"""Preajustes de precios de DShalev.

Un preajuste es un ajuste porcentual (en puntos básicos, 1000 = +10 %) que se aplica a los precios de determinados
productos y servicios cuando se cumple **una** condición: el método de pago, el volumen de unidades de la venta o el
tipo de cliente. Varios preajustes aplicables al mismo artículo se suman.

La regla es la misma que `PreajustePrecios` de Android
(`Android/domain/src/main/kotlin/cu/shalev/domain/model/Precios.kt`) y la que la app envía al escritorio en la
instantánea de sincronización (`sync_server.py`, clave `preajustes`). Aquí solo se edita lo que ya existe: el
escritorio no calcula precios de venta, pero mantiene los preajustes para que el teléfono los reciba iguales.

Condición declarada en la tabla `presets` del escritorio:
  - `payment_method` NULL y sin volumen ni tipo de cliente → aplica a todas las ventas.
  - `payment_method` con valor → solo a las ventas cobradas con ese método.
  - `volume_minimum` con valor → solo si la venta lleva al menos esas unidades.
  - `customer_type` con valor → solo si la venta es de ese tipo de cliente.

`minimum_cents` se conserva para leer respaldos antiguos y **no** interviene en el cálculo (desde la versión 14 de
la base de Android); el escritorio no lo edita.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Iterable

#: Métodos de pago que entienden las tres plataformas (`MetodoPago` de Android, `PaymentMethod` de iOS, ventas web).
METODOS_PAGO: dict[str, str] = {"efectivo": "Efectivo", "transferencia": "Transferencia"}

#: Tipos de cliente (`TipoClientePreajuste` de Android).
TIPOS_CLIENTE: dict[str, str] = {"PARTICULAR": "Particular", "EMPRESA": "Empresa"}

#: Límites del porcentaje: ±100 % (10 000 puntos básicos). Android admite el mismo rango en el editor.
PUNTOS_MIN = -10_000
PUNTOS_MAX = 10_000
NOMBRE_MIN = 3
NOMBRE_MAX = 60


@dataclass
class Preajuste:
    id: int
    nombre: str
    puntos_basicos: int
    producto_ids: list[int] = field(default_factory=list)
    servicio_ids: list[int] = field(default_factory=list)
    metodo_pago: str | None = None
    volumen_minimo: int | None = None
    tipo_cliente: str | None = None
    activo: bool = True

    @property
    def porcentaje(self) -> float:
        return self.puntos_basicos / 100

    @property
    def condicion_texto(self) -> str:
        partes: list[str] = []
        if self.metodo_pago:
            partes.append(f"pago en {METODOS_PAGO.get(self.metodo_pago, self.metodo_pago).lower()}")
        if self.volumen_minimo:
            partes.append(f"{self.volumen_minimo} unidades o más")
        if self.tipo_cliente:
            partes.append(f"cliente {TIPOS_CLIENTE.get(self.tipo_cliente, self.tipo_cliente).lower()}")
        return " y ".join(partes) if partes else "todas las ventas"

    @property
    def alcance_texto(self) -> str:
        partes = []
        if self.producto_ids:
            partes.append(f"{len(self.producto_ids)} producto{'s' if len(self.producto_ids) != 1 else ''}")
        if self.servicio_ids:
            partes.append(f"{len(self.servicio_ids)} servicio{'s' if len(self.servicio_ids) != 1 else ''}")
        return " y ".join(partes) if partes else "ningún artículo"


def validar_puntos(texto: str) -> int:
    """Porcentaje escrito por la persona → puntos básicos. Acepta coma o punto y hasta dos decimales."""
    limpio = (texto or "").strip().replace(" ", "").replace(",", ".")
    if not limpio:
        raise ValueError("Escribe el porcentaje del ajuste.")
    if limpio.count(".") > 1:
        raise ValueError("Escribe un porcentaje válido.")
    entero, _, fraccion = limpio.partition(".")
    signo = -1 if entero.startswith("-") else 1
    entero = entero.lstrip("+-")
    if not entero.isdigit() or (fraccion and not fraccion.isdigit()) or len(fraccion) > 2:
        raise ValueError("Escribe un porcentaje con hasta dos decimales.")
    puntos = signo * (int(entero) * 100 + int((fraccion + "00")[:2]))
    if not PUNTOS_MIN <= puntos <= PUNTOS_MAX:
        raise ValueError("El ajuste debe estar entre -100 % y 100 %.")
    return puntos


def validar_nombre(nombre: str) -> str:
    limpio = " ".join((nombre or "").split())
    if not NOMBRE_MIN <= len(limpio) <= NOMBRE_MAX:
        raise ValueError(f"El nombre debe tener de {NOMBRE_MIN} a {NOMBRE_MAX} caracteres.")
    return limpio


def precio_ajustado(precio_cents: int, puntos_basicos: int) -> int:
    """Precio con el ajuste aplicado, redondeado al céntimo y nunca por debajo de cero.

    1 punto básico = 0,01 %, así que 1 000 puntos son +10 % (la misma escala que `PreajustePrecios` de Android).
    Se calcula con aritmética entera y medio céntimo hacia arriba (en negativo, hacia abajo) para no depender del
    redondeo bancario de `round()`: Android e iOS redondean la mitad hacia arriba y los tres deben dar lo mismo.
    """
    if puntos_basicos == 0 or precio_cents == 0:
        return max(0, precio_cents)
    # Se redondea el precio final, no el incremento: redondear el incremento pierde la fracción y se desvía un
    # céntimo justo en las mitades exactas (comprobado contra el oráculo decimal en las pruebas).
    total = precio_cents * (10_000 + puntos_basicos)
    redondeado = (total + 5_000) // 10_000 if total >= 0 else -((-total + 5_000) // 10_000)
    return max(0, redondeado)


def importe_con_preajustes(base_cents: int, puntos: Iterable[int]) -> int:
    """Suma de varios preajustes aplicables al mismo artículo (Android: «varios preajustes se suman»)."""
    return precio_ajustado(base_cents, sum(puntos))


def _ids(json_texto: str | None) -> list[int]:
    """Identificadores de una columna JSON; una fila dañada se lee como lista vacía en vez de romper la página."""
    import json

    try:
        valor = json.loads(json_texto or "[]")
    except (ValueError, TypeError):
        return []
    if not isinstance(valor, list):
        return []
    return [int(v) for v in valor if isinstance(v, int) or (isinstance(v, str) and v.isdigit())]


def cargar(conn: Any) -> list[Preajuste]:
    """Lee los preajustes guardados. Tolera filas antiguas sin las columnas nuevas."""
    columnas = {row[1] for row in conn.execute("PRAGMA table_info(presets)")}
    tiene_volumen = "volume_minimum" in columnas
    tiene_tipo = "customer_type" in columnas
    seleccion = (
        "SELECT id,name,basis_points,product_ids_json,service_ids_json,payment_method,active"
        + (",volume_minimum" if tiene_volumen else ",NULL")
        + (",customer_type" if tiene_tipo else ",NULL")
        + " FROM presets ORDER BY name COLLATE NOCASE"
    )
    salida: list[Preajuste] = []
    for fila in conn.execute(seleccion).fetchall():
        salida.append(Preajuste(
            id=fila[0], nombre=fila[1], puntos_basicos=int(fila[2]),
            producto_ids=_ids(fila[3]), servicio_ids=_ids(fila[4]),
            metodo_pago=fila[5], activo=bool(fila[6]),
            volumen_minimo=int(fila[7]) if fila[7] is not None else None,
            tipo_cliente=fila[8],
        ))
    return salida


def aplicar_a_catalogo(precios: dict[int, int], preajustes: Iterable[Preajuste]) -> dict[int, int]:
    """Precio final por producto para una venta concreta, con todos los preajustes activos sumados.

    `precios` es `{id_producto: precio_cents}`. Solo se tocan los artículos incluidos en algún preajuste; lo demás
    se devuelve igual. Se conserva para que las pruebas fijen la regla aunque el escritorio no cobre con ella.
    """
    activos = [p for p in preajustes if p.activo and p.puntos_basicos]
    if not activos:
        return dict(precios)
    salida = dict(precios)
    for producto_id, precio in precios.items():
        puntos = sum(p.puntos_basicos for p in activos if producto_id in p.producto_ids)
        if puntos:
            salida[producto_id] = precio_ajustado(precio, puntos)
    return salida
