from __future__ import annotations

import json
from pathlib import Path


def test_precios_json_valido_y_completo():
    ruta = Path(__file__).resolve().parents[2] / "docs" / "vectores" / "precios.json"
    assert ruta.is_file(), f"Falta archivo {ruta}"
    datos = json.loads(ruta.read_text(encoding="utf-8"))

    assert datos["moneda"] == "CUP"
    assert datos["maxSecundarias"] == 10
    tarifas = datos["tarifas"]

    assert tarifas["MENSUAL"]["precioBasePesos"] == 6000
    assert tarifas["MENSUAL"]["precioSecundariaPesos"] == 1000
    assert tarifas["MENSUAL"]["duracionDias"] == 30

    assert tarifas["SEMESTRAL"]["precioBasePesos"] == 30000
    assert tarifas["SEMESTRAL"]["precioSecundariaPesos"] == 5000
    assert tarifas["SEMESTRAL"]["duracionDias"] == 180

    assert tarifas["ANUAL"]["precioBasePesos"] == 50000
    assert tarifas["ANUAL"]["precioSecundariaPesos"] == 9000
    assert tarifas["ANUAL"]["duracionDias"] == 365

    assert tarifas["PERPETUA"]["precioBasePesos"] == 90000
    assert tarifas["PERPETUA"]["precioSecundariaPesos"] == 17000
    assert tarifas["PERPETUA"]["duracionDias"] is None


def test_reglas_negocio_json_valido_y_completo():
    ruta = Path(__file__).resolve().parents[2] / "docs" / "vectores" / "reglas-negocio.json"
    assert ruta.is_file(), f"Falta archivo {ruta}"
    datos = json.loads(ruta.read_text(encoding="utf-8"))

    assert datos["dinero"]["moneda"] == "CUP"
    assert datos["dinero"]["centavosPorPeso"] == 100
    assert datos["dinero"]["modoRedondeo"] == "HALF_UP"

    ci = datos["carneCubano"]
    assert ci["longitud"] == 11
    assert ci["siglos"]["9"] == 1800
    assert ci["siglos"]["0"] == 1900
    assert ci["siglos"]["6"] == 2000

    tel = datos["telefonoMovilCubano"]
    assert tel["paisPrefijo"] == "+53"
    assert "5" in tel["seriesValidas"]
    assert "6" in tel["seriesValidas"]

    nombres = datos["nombres"]
    assert nombres["longitudMinima"] == 2
    assert nombres["longitudMaxima"] == 80

    stock = datos["stock"]
    assert stock["producto"]["unidad"] == "entera"
    assert stock["insumo"]["precisionDecimales"] == 3
    assert stock["insumo"]["factorConversionMil"] == 1000

    caja = datos["turnoYCaja"]
    assert caja["exigeTurnoAbierto"] is True
    assert "EFECTIVO" in caja["metodosPago"]
    assert "TRANSFERENCIA" in caja["metodosPago"]
