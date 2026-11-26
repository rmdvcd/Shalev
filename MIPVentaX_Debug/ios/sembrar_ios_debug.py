#!/usr/bin/env python3
"""
Genera el archivo seed JSON compatible con AppData (v2) de MIPVentaX iOS
con el mismo nivel de seed que Android pero valores y negocio distintos
(Gourmet Habana Bistro & Café, 2 años de historial).
"""

import json
import random
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

OUTPUT_JSON = Path(__file__).resolve().parent / "mipventax_debug_seed.json"

random.seed(20261120)

def iso(dt):
    return dt.isoformat().replace("+00:00", "Z")

def main():
    ahora = datetime.now(timezone.utc)
    local_principal = "Bistro Principal (Plaza)"

    # `BusinessLocation` (Domain.swift) no tiene un id propio: su identidad ES el nombre, y los dos flags de alcance
    # existen con valor por defecto. Un `id` inventado aquí no lo lee nadie y una versión futura lo rechazaría.
    locations = [
        {"name": "Bistro Principal (Plaza)", "inventoryEnabled": True, "servicesEnabled": True},
        {"name": "Sucursal Malecón (Vedado)", "inventoryEnabled": True, "servicesEnabled": True},
        {"name": "Kiosco Gourmet (Miramar)", "inventoryEnabled": True, "servicesEnabled": True},
    ]

    # Insumos
    insumos_data = [
        ("Café Arábica en Grano", "kg", 180000, 15000),
        ("Leche Fresca Entera", "L", 45000, 40000),
        ("Pan Baguette Artesanal", "u", 8000, 80000),
        ("Queso Gouda Holandés", "kg", 380000, 12000),
        ("Jamón Serrano Curado", "kg", 520000, 10000),
        ("Carne de Res Molida Especial", "kg", 420000, 25000),
        ("Pierna de Cerdo Asada", "kg", 360000, 30000),
        ("Hierbabuena Fresca", "manojo", 3000, 50000),
        ("Limones Criollos", "kg", 12000, 35000),
        ("Ron Añejo Carta Blanca", "L", 85000, 20000),
        ("Harina de Trigo Especial", "kg", 16000, 45000),
        ("Azúcar Morena de Caña", "kg", 14000, 60000),
    ]

    products = []
    insumo_ids = []
    for nombre, unidad, costo_cent, cant_milli in insumos_data:
        i_id = str(uuid.uuid4())
        insumo_ids.append(i_id)
        products.append({
            "id": i_id,
            "name": nombre,
            "category": "Otros",
            "kind": "ingredient",
            "description": "Insumo de cocina y bar",
            "stockMilli": cant_milli,
            "unit": unidad,
            "lowStockMilli": 5000,
            "criticalStockMilli": 2000,
            "priceCents": 0,
            "costCents": costo_cent,
            "uses": [],
            "isActive": True,
            "createdAt": iso(ahora),
            "localName": local_principal,
        })

    productos_data = [
        ("Café Espresso Cubano", "Cafetería", 1800, 5000),
        ("Café Cortado Especial", "Cafetería", 2600, 7000),
        ("Cappuccino Cremoso Canela", "Cafetería", 3200, 9000),
        ("Café Bombón Habana", "Cafetería", 3500, 9500),
        ("Té Helado con Limón y Menta", "Bebidas", 1500, 5500),
        ("Limonada Natural Frapé", "Bebidas", 2000, 6000),
        ("Jugo Natural de Guayaba 500ml", "Bebidas", 2200, 6500),
        ("Agua Mineral Natural 500ml", "Bebidas", 4000, 9000),
        ("Cerveza Cristal 350ml", "Bebidas", 14000, 22000),
        ("Cerveza Bucanero Fuerte 350ml", "Bebidas", 15000, 24000),
        ("Coctel Mojito Tradicional", "Coctelería", 8500, 22000),
        ("Coctel Daiquirí Clásico", "Coctelería", 8000, 20000),
        ("Coctel Cuba Libre Añejo", "Coctelería", 7500, 19000),
        ("Sándwich Cubano Tradicional", "Alimentos", 12000, 32000),
        ("Pan con Lechón y Mojo Criollo", "Alimentos", 11000, 28000),
        ("Croissant Jamón Serrano y Queso", "Alimentos", 14000, 34000),
        ("Hamburguesa Gourmet de Res", "Alimentos", 18000, 42000),
        ("Pizza Artesanal Margarita", "Alimentos", 15000, 38000),
        ("Tarta Habanera de Chocolate", "Dulcería", 8000, 20000),
        ("Flan Casero con Caramelo", "Dulcería", 5000, 14000),
    ]

    prod_ids = []
    for nombre, cat, costo_cent, venta_cent in productos_data:
        p_id = str(uuid.uuid4())
        prod_ids.append(p_id)
        products.append({
            "id": p_id,
            "name": nombre,
            "category": cat,
            "kind": "product",
            "description": "",
            "stockMilli": 50000,
            "unit": "u",
            "lowStockMilli": 10000,
            "criticalStockMilli": 3000,
            "priceCents": venta_cent,
            "costCents": costo_cent,
            "uses": [],
            "isActive": True,
            "createdAt": iso(ahora),
            "localName": local_principal,
        })

    # `ServiceItem`: tipo y descripción (no categoría), sin costo, y el consumo de insumos va en `uses`.
    # Preajustes de precio: descuento por volumen en los tres cócteles (referencias del catálogo).
    coctel_ids = [prod_ids[i] for i, (nombre, *_resto) in enumerate(productos_data) if nombre.startswith("Coctel")]

    services = [
        {"id": str(uuid.uuid4()), "name": "Reserva Salón VIP Climatizado (1 hora)", "type": "Eventos",
         "description": "Salón privado con proyector y climatización", "priceCents": 150000,
         "uses": [], "isActive": True, "createdAt": iso(ahora), "localName": local_principal},
        {"id": str(uuid.uuid4()), "name": "Paquete Coctelería Abierta (por persona)", "type": "Catering",
         "description": "Barra abierta de cocteles clásicos", "priceCents": 85000,
         "uses": [{"id": str(uuid.uuid4()), "ingredientID": insumo_ids[9], "milliUnits": 60}],
         "isActive": True, "createdAt": iso(ahora), "localName": local_principal},
        {"id": str(uuid.uuid4()), "name": "Mesa Ejecutiva con Servicio Preferencial", "type": "Mesa",
         "description": "Atención dedicada para almuerzos de negocios", "priceCents": 40000,
         "uses": [], "isActive": True, "createdAt": iso(ahora), "localName": local_principal},
    ]

    customers = [
        {"id": str(uuid.uuid4()), "name": "Pedro Morales", "identification": "85041212345", "phone": "+5352881122"},
        {"id": str(uuid.uuid4()), "name": "Beatriz Vega", "identification": "91072334567", "phone": "+5353114455"},
        {"id": str(uuid.uuid4()), "name": "Javier Fernández", "identification": "78110556789", "phone": "+5354997788"},
        {"id": str(uuid.uuid4()), "name": "Marleny Peña", "identification": "95021878901", "phone": "+5358223344"},
    ]

    employees = [
        {"id": str(uuid.uuid4()), "name": "Carlos Martínez", "permissions": ["Vender productos", "Vender servicios", "Ver inventario", "Editar inventario y servicios", "Ver registros", "Abrir/cerrar turno y caja", "Cambiar precios", "Exportar PDF y Excel", "Modificar/anular ventas", "Acceder a Ajustes"], "isActive": True},
        {"id": str(uuid.uuid4()), "name": "Laura Valdés", "permissions": ["Vender productos", "Abrir/cerrar turno y caja", "Ver registros"], "isActive": True},
        {"id": str(uuid.uuid4()), "name": "Dayana Gómez", "permissions": ["Vender productos"], "isActive": True},
    ]

    # 730 días de historial
    fecha_base = ahora - timedelta(days=730)
    print("Generando 730 días de historial para MIPVentaX iOS...")

    shifts = []
    sales = []
    cash_movements = []

    for d in range(730):
        dia = fecha_base + timedelta(days=d)
        inicio = dia.replace(hour=8, minute=30, second=0)
        cierre = dia.replace(hour=21, minute=0, second=0)

        shift_id = str(uuid.uuid4())
        fondo = random.choice([200000, 300000, 500000])
        efectivo = fondo
        transferencia = 0
        costo_total = 0
        total_ventas = 0
        unidades = 0

        # Apertura de caja
        cash_movements.append({
            "id": str(uuid.uuid4()),
            "shiftID": shift_id,
            "date": iso(inicio),
            "kind": "Entrada",
            "amountCents": fondo,
            "reason": "Fondo de apertura de caja",
            "actorName": "Carlos Martínez",
            "localName": local_principal,
        })

        num_ventas = random.randint(8, 18)
        for _ in range(num_ventas):
            minuto = random.randint(15, 720)
            hora_v = inicio + timedelta(minutes=minuto)
            metodo = "Efectivo" if random.random() < 0.65 else "Transferencia"

            lineas = []
            num_art = random.randint(1, 3)
            tot_venta = 0
            for _ in range(num_art):
                p_idx = random.randint(0, len(productos_data) - 1)
                p_id = prod_ids[p_idx]
                p_nom, p_cat, p_cost, p_vent = productos_data[p_idx]
                cant = random.randint(1, 3)
                sub = cant * p_vent
                cost_sub = cant * p_cost
                tot_venta += sub
                costo_total += cost_sub
                unidades += cant
                lineas.append({
                    "id": str(uuid.uuid4()),
                    "sourceID": p_id,
                    "kind": "product",
                    "name": p_nom,
                    "category": p_cat,
                    "quantity": cant,
                    "basePriceCents": p_vent,
                    "unitPriceCents": p_vent,
                    "unitCostCents": p_cost,
                })

            sales.append({
                "id": str(uuid.uuid4()),
                "shiftID": shift_id,
                "date": iso(hora_v),
                "paymentMethod": metodo,
                "lines": lineas,
                "transactionNumber": f"TX-{random.randint(100000, 999999)}" if metodo == "transfer" else "",
                "isCancelled": False,
                "sellerName": "Carlos Martínez",
                "localName": local_principal,
            })

            total_ventas += tot_venta
            if metodo == "cash":
                efectivo += tot_venta
            else:
                transferencia += tot_venta

        # Cierre del turno
        shifts.append({
            "id": shift_id,
            "openedAt": iso(inicio),
            "closedAt": iso(cierre),
            "openingCashCents": fondo,
            "countedCashCents": efectivo,
            "openedBy": "Carlos Martínez",
            "closedBy": "Carlos Martínez",
            "summary": {
                "saleCount": num_ventas,
                "units": unidades,
                "totalCents": total_ventas,
                "cashCents": efectivo - fondo,
                "transferCents": transferencia,
                "costCents": costo_total,
                "cancelledCount": 0,
            },
            "localName": local_principal,
        })

    # `AppSettings`: perfil del negocio, umbrales de aviso en milésimas y preajustes de precio.
    # OJO: la contraseña de respaldo NO se escribe en claro en el JSON (iOS guarda su PBKDF2); "" = sin contraseña.
    settings = {
        "profile": {
            "businessName": "Gourmet Habana Bistro & Café",
            "ownerName": "Carlos Martínez",
            "identification": "84051212345",
            "transferPhone": "+5352881122",
            "transferCard": "9225123456789012",
            "transferPhones": [{"id": str(uuid.uuid4()), "number": "+5352881122", "alias": "Cobros"}],
            "transferCards": [{"id": str(uuid.uuid4()), "number": "9225123456789012", "alias": "Cuenta del bistró"}],
        },
        "priceAdjustments": [
            {"id": str(uuid.uuid4()), "name": "Coctelería por volumen (6 o más)", "basisPoints": -1000,
             "productIDs": coctel_ids, "paymentMethod": "Efectivo", "minimumAmountCents": None, "active": True},
            {"id": str(uuid.uuid4()), "name": "Empresas por transferencia", "basisPoints": 500,
             "productIDs": None, "paymentMethod": "Transferencia", "minimumAmountCents": None, "active": True},
        ],
        "inventoryAlertDefaults": {"productLowMilli": 10000, "productCriticalMilli": 3000,
                                   "ingredientLowMilli": 5000, "ingredientCriticalMilli": 2000},
        "plannedEmployees": 4,
    }

    app_data = {
        "schemaVersion": 2,
        "locations": locations,
        "selectedLocation": local_principal,
        "settings": settings,
        "products": products,
        "services": services,
        "sales": sales,
        "shifts": shifts,
        "inventoryMovements": [],
        "cashMovements": cash_movements,
        "customers": customers,
        "employees": employees,
    }

    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(app_data, f, ensure_ascii=False, indent=2)

    print(f"Seed iOS generado: {len(shifts)} turnos, {len(sales)} ventas en {OUTPUT_JSON} ({OUTPUT_JSON.stat().st_size / 1024 / 1024:.2f} MB)")

if __name__ == "__main__":
    main()
