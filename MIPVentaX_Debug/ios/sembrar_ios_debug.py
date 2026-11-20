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

    locations = [
        {"id": str(uuid.uuid4()), "name": "Bistro Principal (Plaza)"},
        {"id": str(uuid.uuid4()), "name": "Sucursal Malecón (Vedado)"},
        {"id": str(uuid.uuid4()), "name": "Kiosco Gourmet (Miramar)"},
    ]

    settings = {
        "businessName": "Gourmet Habana Bistro & Café",
        "ownerName": "Carlos Martínez",
        "ownerIdentification": "84051212345",
        "transferPhone": "+5352881122",
        "transferCard": "9225123456789012",
        "lowStockThreshold": 10,
        "criticalStockThreshold": 3,
        "backupPassword": "",
        "selectedLocation": local_principal,
    }

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
            "category": "Insumos",
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
        ("Cerveza Cristal 350ml", "Bebidas alcohólicas", 14000, 22000),
        ("Cerveza Bucanero Fuerte 350ml", "Bebidas alcohólicas", 15000, 24000),
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

    services = [
        {"id": str(uuid.uuid4()), "name": "Reserva Salón VIP Climatizado (1 hora)", "category": "Eventos", "priceCents": 150000, "costCents": 0, "isActive": True, "localName": local_principal},
        {"id": str(uuid.uuid4()), "name": "Paquete Coctelería Abierta (por persona)", "category": "Catering", "priceCents": 85000, "costCents": 30000, "isActive": True, "localName": local_principal},
        {"id": str(uuid.uuid4()), "name": "Mesa Ejecutiva con Servicio Preferencial", "category": "Mesa", "priceCents": 40000, "costCents": 0, "isActive": True, "localName": local_principal},
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
            "kind": "cashIn",
            "amountCents": fondo,
            "reason": "Fondo de apertura de caja",
            "actorName": "Carlos Martínez",
            "localName": local_principal,
        })

        num_ventas = random.randint(8, 18)
        for _ in range(num_ventas):
            minuto = random.randint(15, 720)
            hora_v = inicio + timedelta(minutes=minuto)
            metodo = "cash" if random.random() < 0.65 else "transfer"

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
                    "productID": p_id,
                    "productName": p_nom,
                    "category": p_cat,
                    "unitPriceCents": p_vent,
                    "costCents": p_cost,
                    "quantity": cant,
                    "unit": "u",
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
