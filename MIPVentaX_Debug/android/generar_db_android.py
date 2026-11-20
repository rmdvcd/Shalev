#!/usr/bin/env python3
"""
Genera la base de datos SQLite para MIPVentaX Android en modo debug
con el mismo nivel de seed de la app Android pero valores y negocio distintos
(Gourmet Habana Bistro & Café, 3 locales, 2 años de historial).
"""

import json
import os
import random
import sqlite3
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

SCHEMA_PATH = Path(__file__).resolve().parents[2] / "Android" / "data" / "schemas" / "cu.shalev.data.db.ShalevDatabase" / "21.json"
OUTPUT_DB = Path(__file__).resolve().parent / "mipventax_debug.db"

random.seed(20261120)

def crear_esquema(conn, schema_json):
    cursor = conn.cursor()
    cursor.execute("PRAGMA foreign_keys = OFF;")
    
    # Crear tablas e índices
    for entity in schema_json["database"]["entities"]:
        table_name = entity["tableName"]
        cursor.execute(entity["createSql"].replace("${TABLE_NAME}", table_name))
        for idx in entity.get("indices", []):
            sql = idx["createSql"].replace("${TABLE_NAME}", table_name)
            cursor.execute(sql)
            
    # Crear tabla maestra de Room
    identity_hash = schema_json["database"]["identityHash"]
    cursor.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    cursor.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)", (identity_hash,))
    conn.commit()

def sembrar_datos(conn):
    c = conn.cursor()
    ahora_ms = int(time.time() * 1000)
    
    # 1. Locales (almacen_local)
    locales = [
        ("Bistro Principal (Plaza)", "local-principal"),
        ("Sucursal Malecón (Vedado)", "local-malecon"),
        ("Kiosco Gourmet (Miramar)", "local-miramar"),
    ]
    for nombre, loc_id in locales:
        c.execute("""
            INSERT INTO almacen_local (nombre, inventario, servicios, id)
            VALUES (?, 1, 1, ?)
        """, (nombre, loc_id))
    local_principal = "Bistro Principal (Plaza)"
        
    # 2. Perfil de cobro
    c.execute("""
        INSERT INTO perfil (id, nombre, apellidos, ci, pagoTarjetaId, pagoTelefonoId, nombreNegocio, logoUri)
        VALUES (1, 'Carlos', 'Martínez Valdés', '84051212345', 1, 1, 'Gourmet Habana Bistro & Café', NULL)
    """)
    
    c.execute("""
        INSERT INTO telefono (id, numero, alias)
        VALUES (1, '+5352881122', 'Móvil Caja')
    """)
    
    c.execute("""
        INSERT INTO tarjeta (id, numero, alias)
        VALUES (1, '9225123456789012', 'BANDEC Principal')
    """)

    # 3. Empleados
    empleados = [
        ("Carlos Martínez", "[\"VENDER\",\"INVENTARIO\",\"REGISTROS\",\"CAJA\",\"CONFIGURAR\",\"EXPORTAR\"]", 1),
        ("Laura Valdés", "[\"VENDER\",\"CAJA\",\"REGISTROS\"]", 2),
        ("Dayana Gómez", "[\"VENDER\"]", 3),
        ("Marcos Fonseca", "[\"VENDER\",\"INVENTARIO\",\"CAJA\"]", 4),
    ]
    for nombre, permisos, local_id in empleados:
        c.execute("""
            INSERT INTO empleado (nombre, permisos, creadoEn, activo, localId)
            VALUES (?, ?, ?, 1, ?)
        """, (nombre, permisos, ahora_ms, local_id))

    # 4. Clientes fijos
    clientes = [
        ("Pedro Morales", "85041212345", "+5352881122"),
        ("Beatriz Vega", "91072334567", "+5353114455"),
        ("Javier Fernández", "78110556789", "+5354997788"),
        ("Marleny Peña", "95021878901", "+5358223344"),
    ]
    for nombre, ci, tel in clientes:
        c.execute("""
            INSERT INTO cliente_fijo (nombreApellidos, ci, telefono, creadoEn, actualizadoEn)
            VALUES (?, ?, ?, ?, ?)
        """, (nombre, ci, tel, ahora_ms, ahora_ms))

    # 5. Insumos
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
            INSERT INTO insumo (nombre, unidad, precioCent, cantidadMil, nivelBajoMil, nivelCriticoMil, creadoEn, actualizadoEn, local)
            VALUES (?, ?, ?, ?, 5000, 2000, ?, ?, ?)
        """, (nombre, unidad, costo_cent, cant_milli, ahora_ms, ahora_ms, local_principal))
        insumo_ids.append(c.lastrowid)

    # 6. Productos
    productos_data = [
        # (nombre, categoria, costo_cent, venta_cent, caduca)
        ("Café Espresso Cubano", "Cafetería", 1800, 5000, False),
        ("Café Cortado Especial", "Cafetería", 2600, 7000, False),
        ("Cappuccino Cremoso Canela", "Cafetería", 3200, 9000, False),
        ("Café Bombón Habana", "Cafetería", 3500, 9500, False),
        ("Té Helado con Limón y Menta", "Bebidas", 1500, 5500, False),
        ("Limonada Natural Frapé", "Bebidas", 2000, 6000, False),
        ("Jugo Natural de Guayaba 500ml", "Bebidas", 2200, 6500, True),
        ("Agua Mineral Natural 500ml", "Bebidas", 4000, 9000, False),
        ("Cerveza Cristal 350ml", "Bebidas alcohólicas", 14000, 22000, False),
        ("Cerveza Bucanero Fuerte 350ml", "Bebidas alcohólicas", 15000, 24000, False),
        ("Coctel Mojito Tradicional", "Coctelería", 8500, 22000, False),
        ("Coctel Daiquirí Clásico", "Coctelería", 8000, 20000, False),
        ("Coctel Cuba Libre Añejo", "Coctelería", 7500, 19000, False),
        ("Sándwich Cubano Tradicional", "Alimentos", 12000, 32000, True),
        ("Pan con Lechón y Mojo Criollo", "Alimentos", 11000, 28000, True),
        ("Croissant Jamón Serrano y Queso", "Panadería", 14000, 34000, True),
        ("Hamburguesa Gourmet de Res", "Alimentos", 18000, 42000, True),
        ("Pizza Artesanal Margarita", "Alimentos", 15000, 38000, True),
        ("Tarta Habanera de Chocolate", "Dulcería", 8000, 20000, True),
        ("Flan Casero con Caramelo", "Dulcería", 5000, 14000, True),
    ]
    producto_ids = []
    for nombre, cat, costo_cent, venta_cent, caduca in productos_data:
        c.execute("""
            INSERT INTO producto (categoria, nombre, descripcion, precioCostoCent, precioVentaCent, cantidad, nivelBajo, nivelCritico, creadoEn, actualizadoEn, eliminado, local)
            VALUES (?, ?, '', ?, ?, 50, 10, 3, ?, ?, 0, ?)
        """, (cat, nombre, costo_cent, venta_cent, ahora_ms, ahora_ms, local_principal))
        producto_ids.append(c.lastrowid)

    # 7. Recetas
    c.execute("INSERT INTO receta_linea (productoId, insumoId, cantidadMil) VALUES (?, ?, ?)", (producto_ids[0], insumo_ids[0], 18))
    c.execute("INSERT INTO receta_linea (productoId, insumoId, cantidadMil) VALUES (?, ?, ?)", (producto_ids[1], insumo_ids[0], 18))
    c.execute("INSERT INTO receta_linea (productoId, insumoId, cantidadMil) VALUES (?, ?, ?)", (producto_ids[1], insumo_ids[1], 50))
    c.execute("INSERT INTO receta_linea (productoId, insumoId, cantidadMil) VALUES (?, ?, ?)", (producto_ids[10], insumo_ids[9], 60))
    c.execute("INSERT INTO receta_linea (productoId, insumoId, cantidadMil) VALUES (?, ?, ?)", (producto_ids[10], insumo_ids[7], 1000))
    c.execute("INSERT INTO receta_linea (productoId, insumoId, cantidadMil) VALUES (?, ?, ?)", (producto_ids[13], insumo_ids[2], 1000))
    c.execute("INSERT INTO receta_linea (productoId, insumoId, cantidadMil) VALUES (?, ?, ?)", (producto_ids[13], insumo_ids[6], 120))

    # 8. Servicios
    servicios_data = [
        ("Reserva Salón VIP Climatizado (1 hora)", "Eventos", 150000),
        ("Paquete Coctelería Abierta (por persona)", "Catering", 85000),
        ("Mesa Ejecutiva con Servicio Preferencial", "Mesa", 40000),
        ("Catering Corporativo para Reuniones", "Empresas", 220000),
    ]
    for nombre, tipo, imp_cent in servicios_data:
        c.execute("""
            INSERT INTO servicio (nombre, tipo, importeCent, descripcion, creadoEn, actualizadoEn, eliminado, local)
            VALUES (?, ?, ?, '', ?, ?, 0, ?)
        """, (nombre, tipo, imp_cent, ahora_ms, ahora_ms, local_principal))

    # 9. Generar 2 años de historial de turnos y ventas (730 días)
    fecha_base = datetime.now(timezone.utc) - timedelta(days=730)
    print("Generando 730 días de historial operativo para MIPVentaX Android...")

    total_ventas = 0
    turno_id = 0
    venta_id = 0

    for d in range(730):
        dia_actual = fecha_base + timedelta(days=d)
        inicio_turno = dia_actual.replace(hour=8, minute=30, second=0)
        cierre_turno = dia_actual.replace(hour=21, minute=0, second=0)
        inicio_ms = int(inicio_turno.timestamp() * 1000)
        cierre_ms = int(cierre_turno.timestamp() * 1000)

        fondo_apertura = random.choice([200000, 300000, 500000]) # 2,000 / 3,000 / 5,000 CUP
        efectivo_acumulado = fondo_apertura
        transferencia_acumulada = 0
        costo_acumulado = 0
        unidades_acumuladas = 0

        turno_id += 1
        num_ventas_dia = random.randint(10, 22)

        # Ventas del día
        ventas_turno = []
        for _ in range(num_ventas_dia):
            venta_id += 1
            total_ventas += 1
            minuto = random.randint(15, 720)
            hora_venta = inicio_turno + timedelta(minutes=minuto)
            venta_ms = int(hora_venta.timestamp() * 1000)

            metodo = "EFECTIVO" if random.random() < 0.65 else "TRANSFERENCIA"
            lineas = random.randint(1, 3)
            
            total_venta_cent = 0
            costo_venta_cent = 0
            unidades_venta = 0
            detalles = []

            for _ in range(lineas):
                prod_idx = random.randint(0, len(productos_data) - 1)
                p_id = producto_ids[prod_idx]
                p_nombre, p_cat, p_costo, p_venta, _ = productos_data[prod_idx]
                cant = random.randint(1, 3)
                subtotal = cant * p_venta
                costo_sub = cant * p_costo
                total_venta_cent += subtotal
                costo_venta_cent += costo_sub
                unidades_venta += cant
                detalles.append((p_id, p_nombre, p_cat, cant, p_venta, p_costo))

            ventas_turno.append((venta_id, turno_id, venta_ms, metodo, total_venta_cent, costo_venta_cent, unidades_venta, detalles))
            costo_acumulado += costo_venta_cent
            unidades_acumuladas += unidades_venta
            if metodo == "EFECTIVO":
                efectivo_acumulado += total_venta_cent
            else:
                transferencia_acumulada += total_venta_cent

        # Movimiento de caja (salida esporádica)
        salida_caja = 0
        if random.random() < 0.3:
            salida_caja = random.choice([20000, 40000, 80000])
            efectivo_acumulado -= salida_caja

        # Insertar turno
        total_turno_cent = efectivo_acumulado - fondo_apertura + salida_caja + transferencia_acumulada
        c.execute("""
            INSERT INTO turno (
                id, abiertoEn, cerradoEn, numVentas, unidades, totalCent,
                efectivoCent, transferenciaCent, costoCent, numMovimientos,
                abiertoPor, cerradoPor, ventasEfectivo, ventasTransferencia,
                movimientosProducto, movimientosInsumo, uuid, sincronizado,
                fondoCent, contadoCent, entradasCent, salidasCent, numAnuladas, local
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'Carlos Martínez', 'Carlos Martínez', ?, ?, 0, 0, ?, 1, ?, ?, 0, ?, 0, ?)
        """, (
            turno_id, inicio_ms, cierre_ms, num_ventas_dia, unidades_acumuladas, total_turno_cent,
            efectivo_acumulado - fondo_apertura + salida_caja, transferencia_acumulada, costo_acumulado,
            sum(1 for v in ventas_turno if v[3] == "EFECTIVO"), sum(1 for v in ventas_turno if v[3] == "TRANSFERENCIA"),
            str(uuid.uuid4()), fondo_apertura, efectivo_acumulado, salida_caja, local_principal
        ))

        # Movimiento de apertura
        c.execute("""
            INSERT INTO movimiento_caja (turnoId, fecha, tipo, importeCent, motivo, hechoPor, uuid, sincronizado)
            VALUES (?, ?, 'ENTRADA', ?, 'Fondo de apertura de caja', 'Carlos Martínez', ?, 1)
        """, (turno_id, inicio_ms, fondo_apertura, str(uuid.uuid4())))

        if salida_caja > 0:
            c.execute("""
                INSERT INTO movimiento_caja (turnoId, fecha, tipo, importeCent, motivo, hechoPor, uuid, sincronizado)
                VALUES (?, ?, 'SALIDA', ?, 'Gasto operativo menor / Suministros diarios', 'Carlos Martínez', ?, 1)
            """, (turno_id, inicio_ms + 14400000, salida_caja, str(uuid.uuid4())))

        # Insertar ventas y detalles
        for v_id, t_id, v_ms, metodo, tot_cent, cost_cent, unids, detalles in ventas_turno:
            c.execute("""
                INSERT INTO venta (id, turnoId, fecha, metodoPago, totalCent, costoCent, unidades, uuid, sincronizado, local)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
            """, (v_id, t_id, v_ms, metodo, tot_cent, cost_cent, unids, str(uuid.uuid4()), local_principal))

            for p_id, p_nom, p_cat, cant, p_vent, p_cost in detalles:
                c.execute("""
                    INSERT INTO detalle_venta (ventaId, productoId, nombre, categoria, cantidad, precioBaseCent, precioUnitarioCent, costoUnitarioCent, clase)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PRODUCTO')
                """, (v_id, p_id, p_nom, p_cat, cant, p_vent, p_vent, p_cost))

    conn.commit()
    print(f"Base de datos Android generada exitosamente: {turno_id} turnos, {total_ventas} ventas.")

def main():
    if OUTPUT_DB.exists():
        OUTPUT_DB.unlink()
        
    print(f"Cargando esquema desde: {SCHEMA_PATH}")
    with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
        schema = json.load(f)
        
    conn = sqlite3.connect(OUTPUT_DB)
    crear_esquema(conn, schema)
    sembrar_datos(conn)
    conn.close()
    print(f"Base de datos Android lista en: {OUTPUT_DB} ({OUTPUT_DB.stat().st_size / 1024 / 1024:.2f} MB)")

if __name__ == "__main__":
    main()
