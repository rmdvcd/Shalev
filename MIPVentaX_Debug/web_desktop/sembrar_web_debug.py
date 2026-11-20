#!/usr/bin/env python3
"""
Genera la base de datos pre-sembrada para MIPVentaX Web-Desktop
con el mismo nivel de seed que Android pero valores y negocio distintos
(Gourmet Habana Bistro & Café, 2 años de historial).
"""

import os
import random
import sqlite3
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "Web-Desktop"))

from dshalev.database import SCHEMA
from dshalev.security import hash_password

OUTPUT_DIR = Path(__file__).resolve().parent / "DShalevData"
OUTPUT_DB = OUTPUT_DIR / "dshalev.db"

random.seed(20261120)

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    if OUTPUT_DB.exists():
        OUTPUT_DB.unlink()

    conn = sqlite3.connect(OUTPUT_DB)
    c = conn.cursor()
    c.executescript(SCHEMA)

    ahora_iso = datetime.now(timezone.utc).isoformat()
    ahora_ms = int(time.time() * 1000)

    # 1. Metadatos y usuario administrador
    c.execute("INSERT OR REPLACE INTO app_meta (key, value) VALUES ('business_name', 'Gourmet Habana Bistro & Café')")
    c.execute("INSERT OR REPLACE INTO app_meta (key, value) VALUES ('schema_version', '1')")
    c.execute("INSERT OR REPLACE INTO app_meta (key, value) VALUES ('local_id', 'principal')")

    pwd_hash = hash_password("Administrador123*")
    c.execute("""
        INSERT INTO users (username, password_hash, role, active, created_at)
        VALUES ('admin', ?, 'admin', 1, ?)
    """, (pwd_hash, ahora_iso))
    admin_id = c.lastrowid

    # 2. Empleados
    empleados_data = [
        (1, "Carlos Martínez", "[\"VENDER\",\"INVENTARIO\",\"REGISTROS\",\"CAJA\",\"CONFIGURAR\",\"EXPORTAR\"]"),
        (2, "Laura Valdés", "[\"VENDER\",\"CAJA\",\"REGISTROS\"]"),
        (3, "Dayana Gómez", "[\"VENDER\"]"),
        (4, "Marcos Fonseca", "[\"VENDER\",\"INVENTARIO\",\"CAJA\"]"),
    ]
    for slot, nombre, perms in empleados_data:
        c.execute("""
            INSERT INTO employees (slot, name, permissions_json, device_kind, active, created_at)
            VALUES (?, ?, ?, 'browser', 1, ?)
        """, (slot, nombre, perms, ahora_iso))

    # 3. Clientes fijos
    clientes_data = [
        ("Pedro Morales", "+5352881122", "pedro@empresa.cu", "Cliente VIP matutino"),
        ("Beatriz Vega", "+5353114455", "beatriz@corporativo.cu", "Cuenta corporativa almuerzos"),
        ("Javier Fernández", "+5354997788", "javi@coctel.cu", "Aficionado a la coctelería"),
        ("Marleny Peña", "+5358223344", "", "Vecina de la zona"),
    ]
    for nombre, tel, email, notas in clientes_data:
        c.execute("""
            INSERT INTO customers (name, phone, email, notes, active, created_at)
            VALUES (?, ?, ?, ?, 1, ?)
        """, (nombre, tel, email, notas, ahora_iso))

    # 4. Insumos
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
    insumo_ids = []
    for nombre, unidad, costo_cent, cant_milli in insumos_data:
        c.execute("""
            INSERT INTO items (name, category, unit, stock, stock_milli, sale_price_cents, cost_cents, low_stock_milli, critical_stock_milli, active, created_at)
            VALUES (?, 'Insumo', ?, ?, ?, 0, ?, 5000, 2000, 1, ?)
        """, (nombre, unidad, cant_milli / 1000.0, cant_milli, costo_cent, ahora_iso))
        insumo_ids.append(c.lastrowid)

    # 5. Productos
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
    producto_ids = []
    for nombre, cat, costo_cent, venta_cent in productos_data:
        c.execute("""
            INSERT INTO items (name, category, unit, stock, stock_milli, sale_price_cents, cost_cents, low_stock_milli, critical_stock_milli, active, created_at)
            VALUES (?, ?, 'unidad', 50.0, 50000, ?, ?, 10000, 3000, 1, ?)
        """, (nombre, cat, venta_cent, costo_cent, ahora_iso))
        producto_ids.append(c.lastrowid)

    # 6. Recetas
    c.execute("INSERT INTO recipes (product_id, ingredient_id, quantity_milli) VALUES (?, ?, ?)", (producto_ids[0], insumo_ids[0], 18))
    c.execute("INSERT INTO recipes (product_id, ingredient_id, quantity_milli) VALUES (?, ?, ?)", (producto_ids[1], insumo_ids[0], 18))
    c.execute("INSERT INTO recipes (product_id, ingredient_id, quantity_milli) VALUES (?, ?, ?)", (producto_ids[1], insumo_ids[1], 50))
    c.execute("INSERT INTO recipes (product_id, ingredient_id, quantity_milli) VALUES (?, ?, ?)", (producto_ids[10], insumo_ids[9], 60))
    c.execute("INSERT INTO recipes (product_id, ingredient_id, quantity_milli) VALUES (?, ?, ?)", (producto_ids[10], insumo_ids[7], 1000))
    c.execute("INSERT INTO recipes (product_id, ingredient_id, quantity_milli) VALUES (?, ?, ?)", (producto_ids[13], insumo_ids[2], 1000))
    c.execute("INSERT INTO recipes (product_id, ingredient_id, quantity_milli) VALUES (?, ?, ?)", (producto_ids[13], insumo_ids[6], 120))

    # 7. Servicios
    servicios_data = [
        (1, "Reserva Salón VIP Climatizado (1 hora)", "Eventos", 150000, "Salón privado con proyector y climatización"),
        (2, "Paquete Coctelería Abierta (por persona)", "Catering", 85000, "Barra abierta de cocteles clásicos"),
        (3, "Mesa Ejecutiva con Servicio Preferencial", "Mesa", 40000, "Atención dedicada para almuerzos de negocios"),
        (4, "Catering Corporativo para Reuniones", "Empresas", 220000, "Bocadillos gourmet y café premium para 10 personas"),
    ]
    for s_id, nombre, tipo, imp_cent, desc in servicios_data:
        c.execute("""
            INSERT INTO sync_services (service_id, name, service_type, price_cents, description, created_ms, updated_ms, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0)
        """, (s_id, nombre, tipo, imp_cent, desc, ahora_ms, ahora_ms))

    # 8. Generar 2 años de turnos y ventas
    print("Generando 730 días de historial operativo para MIPVentaX Web-Desktop...")
    fecha_base = datetime.now(timezone.utc) - timedelta(days=730)
    total_ventas = 0
    turno_id = 0

    for d in range(730):
        dia_actual = fecha_base + timedelta(days=d)
        inicio_turno = dia_actual.replace(hour=8, minute=30, second=0)
        cierre_turno = dia_actual.replace(hour=21, minute=0, second=0)
        inicio_iso = inicio_turno.isoformat()
        cierre_iso = cierre_turno.isoformat()

        fondo_apertura = random.choice([200000, 300000, 500000])
        efectivo_acumulado = fondo_apertura

        turno_id += 1
        c.execute("""
            INSERT INTO shifts (id, opened_at, closed_at, opening_cash_cents, counted_cash_cents)
            VALUES (?, ?, ?, ?, ?)
        """, (turno_id, inicio_iso, cierre_iso, fondo_apertura, fondo_apertura))

        # Movimiento de apertura
        c.execute("""
            INSERT INTO cash_movements (shift_id, movement_type, amount_cents, reason, created_at)
            VALUES (?, 'ENTRADA', ?, 'Fondo de apertura de caja', ?)
        """, (turno_id, fondo_apertura, inicio_iso))

        # Ventas
        num_ventas = random.randint(10, 22)
        for _ in range(num_ventas):
            total_ventas += 1
            minuto = random.randint(15, 720)
            hora_venta = inicio_turno + timedelta(minutes=minuto)
            venta_iso = hora_venta.isoformat()

            metodo = "EFECTIVO" if random.random() < 0.65 else "TRANSFERENCIA"
            num_lineas = random.randint(1, 3)
            
            lineas_venta = []
            total_venta_cent = 0
            for _ in range(num_lineas):
                p_idx = random.randint(0, len(productos_data) - 1)
                p_id = producto_ids[p_idx]
                p_nom, _, _, p_venta = productos_data[p_idx]
                cant = random.randint(1, 3)
                subtotal = cant * p_venta
                total_venta_cent += subtotal
                lineas_venta.append((p_id, p_nom, cant, p_venta))

            c.execute("""
                INSERT INTO sales (shift_id, total_cents, payment_method, created_at, user_id, inventory_logged)
                VALUES (?, ?, ?, ?, ?, 1)
            """, (turno_id, total_venta_cent, metodo, venta_iso, admin_id))
            sale_id = c.lastrowid

            if metodo == "EFECTIVO":
                efectivo_acumulado += total_venta_cent

            for p_id, p_nom, cant, p_unit in lineas_venta:
                c.execute("""
                    INSERT INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price_cents)
                    VALUES (?, ?, ?, ?, ?)
                """, (sale_id, p_id, p_nom, cant, p_unit))

        # Gasto esporádico
        if random.random() < 0.3:
            salida = random.choice([20000, 40000, 80000])
            c.execute("""
                INSERT INTO cash_movements (shift_id, movement_type, amount_cents, reason, created_at)
                VALUES (?, 'SALIDA', ?, 'Gasto operativo / Compra de insumos', ?)
            """, (turno_id, salida, inicio_iso))
            efectivo_acumulado -= salida

        # Actualizar arqueo final
        c.execute("UPDATE shifts SET counted_cash_cents = ? WHERE id = ?", (efectivo_acumulado, turno_id))

    conn.commit()
    conn.close()
    print(f"Base de datos Web-Desktop generada: {turno_id} turnos, {total_ventas} ventas en {OUTPUT_DB} ({OUTPUT_DB.stat().st_size / 1024 / 1024:.2f} MB)")

if __name__ == "__main__":
    main()
