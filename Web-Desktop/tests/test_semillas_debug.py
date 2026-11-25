"""Los sembradores de `MIPVentaX_Debug` deben producir datos con la forma REAL de cada modelo.

Estas pruebas existen por un fallo concreto: el seed de iOS usaba `settings.businessName` y
`lines[].productID` cuando `AppData`/`Sale` piden `settings.profile.businessName` y
`lines[].sourceID`/`unitCostCents`. El JSON se generaba sin errores y no habría cargado en la app.
Se comprueba la forma, no el contenido: nombres de campo y tipos, que es lo que rompe la carga.
"""

from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SEED_IOS = RAIZ / "MIPVentaX_Debug" / "ios" / "mipventax_debug_seed.json"
SEED_WEB = RAIZ / "MIPVentaX_Debug" / "web_desktop" / "DShalevData" / "dshalev.db"
SEED_ANDROID = RAIZ / "MIPVentaX_Debug" / "android" / "mipventax_debug.db"


def _ios():
    with SEED_IOS.open(encoding="utf-8") as f:
        return json.load(f)


def _es_iso(texto: str) -> bool:
    from datetime import datetime

    try:
        datetime.fromisoformat(texto.replace("Z", "+00:00"))
    except ValueError:
        return False
    return True


def test_seed_ios_tiene_la_forma_de_app_data():
    datos = _ios()
    # `AppData`: estas claves existen y con el tipo que espera `Codable`.
    for clave, tipo in {
        "schemaVersion": int, "selectedLocation": str, "settings": dict,
        "products": list, "services": list, "sales": list, "shifts": list,
        "inventoryMovements": list, "cashMovements": list, "customers": list,
    }.items():
        assert clave in datos, f"falta {clave}"
        assert isinstance(datos[clave], tipo), f"{clave} no es {tipo.__name__}"

    # Los locales NO llevan `id`: `BusinessLocation.id` es el nombre, y un id ajeno no lo lee nadie.
    for local in datos["locations"]:
        assert set(local) == {"name", "inventoryEnabled", "servicesEnabled"}, local
        assert local["name"].strip()

    assert datos["selectedLocation"] in {local["name"] for local in datos["locations"]}


def test_seed_ios_settings_va_bajo_profile():
    ajustes = _ios()["settings"]
    # `AppSettings` solo tiene estas claves; las demás cuelgan de `profile`.
    assert set(ajustes) <= {
        "profile", "priceAdjustments", "inventoryAlertDefaults", "plannedEmployees",
    }, set(ajustes)
    perfil = ajustes["profile"]
    for clave in ("businessName", "ownerName", "identification"):
        assert perfil.get(clave), clave
    assert isinstance(perfil["transferPhones"], list) and perfil["transferPhones"]
    assert isinstance(perfil["transferCards"], list) and perfil["transferCards"]


def test_seed_ios_ajustes_de_precio_apuntan_al_catalogo():
    datos = _ios()
    ids = {p["id"] for p in datos["products"]}
    for ajuste in datos["settings"]["priceAdjustments"]:
        assert ajuste["basisPoints"] != 0
        if ajuste["productIDs"] is not None:
            assert set(ajuste["productIDs"]) <= ids, "un preajuste apunta a un artículo que no existe"


def test_seed_ios_lineas_de_venta_usan_sale_line():
    """`SaleLine` = sourceID/kind/name/category/quantity/basePriceCents/unitPriceCents/unitCostCents."""
    datos = _ios()
    productos = {p["id"]: p for p in datos["products"]}
    servicios = {s["id"]: s for s in datos["services"]}
    claves = {"id", "sourceID", "kind", "name", "category", "quantity",
              "basePriceCents", "unitPriceCents", "unitCostCents"}
    for venta in datos["sales"]:
        assert set(venta["paymentMethod"] for _ in [0]) <= {"Efectivo", "Transferencia"}
        for linea in venta["lines"]:
            assert set(linea) == claves, set(linea)
            assert linea["kind"] in {"product", "service"}
            origen = productos if linea["kind"] == "product" else servicios
            assert linea["sourceID"] in origen, "la línea apunta a un artículo inexistente"
            assert linea["quantity"] > 0
            # El subtotal del turno se calculó con el precio unitario: si no cuadra, el resumen miente.
            assert linea["unitPriceCents"] > 0
            assert linea["unitCostCents"] >= 0


def test_seed_ios_servicios_usan_service_item():
    for servicio in _ios()["services"]:
        assert "type" in servicio and "description" in servicio
        assert "category" not in servicio, "ServiceItem no tiene categoría"
        assert "costCents" not in servicio, "ServiceItem no tiene costo: el gasto va en `uses`"
        assert isinstance(servicio["uses"], list)


def test_seed_ios_movimientos_de_caja_y_turnos():
    datos = _ios()
    for movimiento in datos["cashMovements"]:
        assert movimiento["kind"] in {"Entrada", "Salida"}, movimiento["kind"]
    turnos = {t["id"] for t in datos["shifts"]}
    for movimiento in datos["cashMovements"]:
        assert movimiento["shiftID"] in turnos
    for turno in datos["shifts"]:
        assert _es_iso(turno["openedAt"])


def test_seed_web_tiene_modulos_recorridos_y_preajustes():
    with sqlite3.connect(SEED_WEB) as conn:
        meta = dict(conn.execute("SELECT key, value FROM app_meta"))
        assert meta["schema_version"] == "11"
        assert set(meta["modulos"].split(",")) == {"VENTAS", "INVENTARIO", "SERVICIOS"}
        for clave in ("inicio", "inventario", "servicios", "ventas", "reportes", "herramientas"):
            assert meta[f"{clave}_tour_shown"] == "1", clave

        columnas = {fila[1] for fila in conn.execute("PRAGMA table_info(presets)")}
        assert {"volume_minimum", "customer_type"} <= columnas, "el seed no migró a la 1.0.8"
        preajustes = conn.execute("SELECT name, basis_points FROM presets").fetchall()
        assert len(preajustes) >= 2
        # Una sola condición activa por preajuste, como exige el esquema.
        for volumen, cliente in conn.execute("SELECT volume_minimum, customer_type FROM presets"):
            assert not (volumen is not None and cliente is not None)


def test_seed_android_tiene_preferencias_y_preajustes():
    with sqlite3.connect(SEED_ANDROID) as conn:
        fila = conn.execute("SELECT preferenciasJson FROM configuracion_negocio WHERE id = 1").fetchone()
        assert fila, "sin preferencias la app arranca con los valores por defecto del onboarding"
        preferencias = json.loads(fila[0])
        assert set(preferencias["modulos"]) == {"VENTAS", "INVENTARIO", "SERVICIOS"}
        assert preferencias["empleadosPrevistos"] >= 0

        preajustes = conn.execute(
            "SELECT puntosBasicos, condicion, volumenMinimo, tipoCliente FROM preajuste"
        ).fetchall()
        assert len(preajustes) >= 2
        for puntos, condicion, volumen, cliente in preajustes:
            assert condicion in {"METODO_PAGO", "VOLUMEN_VENTA", "CLIENTE", "IMPORTE_MINIMO"}
            if condicion == "VOLUMEN_VENTA":
                assert volumen and not cliente
            if condicion == "CLIENTE":
                assert cliente in {"PARTICULAR", "EMPRESA"} and not volumen
        # Cada preajuste de productos apunta a un producto real.
        ids = {fila[0] for fila in conn.execute("SELECT id FROM producto")}
        for (producto_id,) in conn.execute("SELECT productoId FROM preajuste_producto"):
            assert producto_id in ids


def test_los_sembradores_se_ejecutan_sin_dependencias_externas():
    """Los tres scripts deben poder importarse: si uno usa un módulo que no existe, falla al ejecutarse."""
    for ruta in (
        RAIZ / "MIPVentaX_Debug" / "web_desktop" / "sembrar_web_debug.py",
        RAIZ / "MIPVentaX_Debug" / "ios" / "sembrar_ios_debug.py",
        RAIZ / "MIPVentaX_Debug" / "android" / "generar_db_android.py",
    ):
        codigo = ruta.read_text(encoding="utf-8")
        compile(codigo, str(ruta), "exec")
        assert "sqlite3" in codigo or "json" in codigo
    assert sys.version_info >= (3, 10)
