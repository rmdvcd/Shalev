package cu.shalev.data.seed

import android.content.Context
import androidx.room.withTransaction
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.ClienteFijoEntity
import cu.shalev.data.db.entity.DetalleVentaEntity
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.db.entity.InsumoEntity
import cu.shalev.data.db.entity.MovimientoCajaEntity
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.db.entity.PerfilEntity
import cu.shalev.data.db.entity.PreajusteEntity
import cu.shalev.data.db.entity.PreajusteProductoEntity
import cu.shalev.data.db.entity.ProductoEntity
import cu.shalev.data.db.entity.RecetaLineaEntity
import cu.shalev.data.db.entity.ServicioEntity
import cu.shalev.data.db.entity.ServicioInsumoEntity
import cu.shalev.data.db.entity.TarjetaEntity
import cu.shalev.data.db.entity.TelefonoEntity
import cu.shalev.data.db.entity.TransaccionEntity
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.db.entity.VentaEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

/**
 * **Solo builds de depuración.** Carga datos de demostración en la base de la app instalada:
 * **2 años (730 días)** de ventas por efectivo y transferencia, 50 artículos de inventario (productos, elaborados
 * e insumos), 20 servicios, clientes fijos, preajustes de precio y 8 apps secundarias «vinculadas» de prueba.
 *
 * No entra en el APK de release: vive en `data/src/debug` (D-05, auditoría de la 0.32.0). Borra los datos
 * operativos y de configuración antes de sembrar, así que se puede ejecutar varias veces.
 */
class Sembrador(
    private val db: ShalevDatabase,
    private val contexto: Context,
    private val clock: Clock,
) {
    private val azar = Random(20261022)
    private val zona: ZoneId = ZoneId.systemDefault()

    /** Histórico de demostración: 2 años de uso (730 días hacia atrás desde hoy). */
    private val diasHistorico = 730

    // ---------------------------------------------------------------------------------------------
    // Catálogo de demostración (índices = los usan las recetas y los planes de venta)
    // ---------------------------------------------------------------------------------------------

    /** [costo]/[venta] en centavos. */
    private data class Prod(
        val nombre: String,
        val categoria: String,
        val costo: Long,
        val venta: Long,
        val caduca: Boolean = false,
        val elaborado: Boolean = false,
        val peso: Int = 10,
    )

    private data class Insumo(val nombre: String, val unidad: String, val precioCent: Long)

    private data class Servicio(val nombre: String, val tipo: String, val importeCent: Long)

    private val productos = listOf(
        Prod("Café molido Serrano 250 g", "Alimentos", 34000, 48000, peso = 14),
        Prod("Arroz grano largo 1 kg", "Granos y cereales", 18000, 26000, peso = 12),
        Prod("Frijol negro 1 kg", "Granos y cereales", 26000, 38000, peso = 8),
        Prod("Azúcar refino 1 kg", "Alimentos", 12000, 18000, peso = 10),
        Prod("Aceite de girasol 1 L", "Aceites y grasas", 42000, 56000, peso = 8),
        Prod("Harina de trigo 1 kg", "Granos y cereales", 15000, 22000, peso = 7),
        Prod("Leche en polvo 400 g", "Lácteos", 38000, 52000, caduca = true, peso = 9),
        Prod("Leche condensada 397 g", "Lácteos", 24000, 34000, caduca = true, peso = 6),
        Prod("Yogur natural 200 ml", "Lácteos", 9000, 14000, caduca = true, peso = 8),
        Prod("Queso Gouda 250 g", "Lácteos", 41000, 58000, caduca = true, peso = 7),
        Prod("Jamón cocido 200 g", "Cárnicos y embutidos", 46000, 64000, caduca = true, peso = 6),
        Prod("Salchicha ahumada 500 g", "Cárnicos y embutidos", 32000, 45000, caduca = true, peso = 5),
        Prod("Atún en aceite 170 g", "Conservas", 22000, 31000, peso = 7),
        Prod("Sardinas en tomate 155 g", "Conservas", 15000, 23000, peso = 6),
        Prod("Pasta larga 500 g", "Alimentos", 11000, 17000, peso = 9),
        Prod("Salsa de tomate 250 g", "Conservas", 8000, 13000, peso = 8),
        Prod("Mayonesa 250 g", "Condimentos y especias", 13000, 19000, peso = 6),
        Prod("Mostaza 200 g", "Condimentos y especias", 9000, 14000, peso = 4),
        Prod("Refresco cola 1.5 L", "Bebidas", 16000, 24000, peso = 13),
        Prod("Agua mineral 1.5 L", "Bebidas", 6000, 10000, peso = 12),
        Prod("Jugo de mango 1 L", "Bebidas", 18000, 27000, peso = 7),
        Prod("Cerveza rubia 330 ml", "Bebidas alcohólicas", 14000, 22000, peso = 11),
        Prod("Ron añejo 750 ml", "Bebidas alcohólicas", 95000, 132000, peso = 3),
        Prod("Vino tinto seco 750 ml", "Bebidas alcohólicas", 62000, 88000, peso = 3),
        Prod("Pan de molde 400 g", "Panadería y dulcería", 10000, 16000, caduca = true, peso = 9),
        Prod("Galletas María 200 g", "Panadería y dulcería", 7000, 12000, peso = 8),
        Prod("Chocolate en barra 100 g", "Confituras y snacks", 9000, 15000, peso = 6),
        Prod("Papas fritas 120 g", "Confituras y snacks", 11000, 18000, peso = 5),
        Prod("Maní salado 150 g", "Confituras y snacks", 8000, 14000, peso = 4),
        Prod("Plátano 1 kg", "Frutas y vegetales", 5000, 9000, caduca = true, peso = 8),
        Prod("Tomate 1 kg", "Frutas y vegetales", 6000, 10000, caduca = true, peso = 6),
        Prod("Cebolla 1 kg", "Frutas y vegetales", 5500, 9500, peso = 5),
        Prod("Detergente 500 g", "Limpieza del hogar", 13000, 19000, peso = 5),
        Prod("Jabón de baño 120 g", "Aseo personal", 6000, 11000, peso = 6),
        Prod("Pasta dental 90 g", "Aseo personal", 12000, 18000, peso = 4),
        Prod("Papel higiénico 4 rollos", "Aseo personal", 20000, 28000, peso = 5),
        // Elaborados (stock virtual: se descuenta de los insumos)
        Prod("Café con leche", "Elaborado", 0, 15000, elaborado = true, peso = 16),
        Prod("Capuchino", "Elaborado", 0, 18000, elaborado = true, peso = 10),
        Prod("Refresco natural de mango", "Elaborado", 0, 12000, elaborado = true, peso = 9),
        Prod("Sándwich de jamón", "Elaborado", 0, 22000, elaborado = true, peso = 7),
    )

    private val insumos = listOf(
        Insumo("Café en grano", "GRAMO", 9),
        Insumo("Leche entera", "MILILITRO", 2),
        Insumo("Azúcar", "GRAMO", 1),
        Insumo("Harina", "GRAMO", 1),
        Insumo("Huevos", "UNIDAD", 1200),
        Insumo("Mantequilla", "GRAMO", 12),
        Insumo("Queso crema", "GRAMO", 14),
        Insumo("Jamón en lonjas", "GRAMO", 18),
        Insumo("Pulpa de mango", "MILILITRO", 6),
        Insumo("Chocolate en polvo", "GRAMO", 15),
        Insumo("Sirope de vainilla", "MILILITRO", 8),
        Insumo("Pan de sándwich", "UNIDAD", 900),
        Insumo("Vasos de 12 oz", "UNIDAD", 700),
        Insumo("Servilletas", "UNIDAD", 200),
    )

    /** producto → (insumo, milésimas por unidad) */
    private val recetas: Map<Int, List<Pair<Int, Long>>> = mapOf(
        36 to listOf(0 to 9000L, 1 to 120_000L, 2 to 10_000L, 12 to 1000L),
        37 to listOf(0 to 12_000L, 1 to 150_000L, 2 to 8_000L, 9 to 4_000L, 12 to 1000L),
        38 to listOf(8 to 100_000L, 2 to 12_000L, 12 to 1000L),
        39 to listOf(11 to 2000L, 7 to 40_000L, 6 to 15_000L, 13 to 1L),
    )

    private val servicios = listOf(
        Servicio("Recarga de saldo móvil", "Telefonía", 15000),
        Servicio("Impresión en blanco y negro", "Impresión", 3000),
        Servicio("Impresión a color", "Impresión", 8000),
        Servicio("Fotocopia a una cara", "Copias", 2000),
        Servicio("Fotocopia a doble cara", "Copias", 3500),
        Servicio("Plastificado de carné", "Encuadernación", 7000),
        Servicio("Encuadernación en espiral", "Encuadernación", 25000),
        Servicio("Revelado de fotos 4x6", "Fotografía", 6000),
        Servicio("Escaneo de documento", "Digitalización", 4000),
        Servicio("Corte de cabello", "Barbería", 30000),
        Servicio("Afelpado de barba", "Barbería", 20000),
        Servicio("Manicure", "Belleza", 25000),
        Servicio("Tinte de cabello", "Belleza", 45000),
        Servicio("Reparación de uña", "Belleza", 15000),
        Servicio("Lavado de ropa por libra", "Lavandería", 12000),
        Servicio("Planchado de prenda", "Lavandería", 8000),
        Servicio("Entrega a domicilio", "Domicilio", 10000),
        Servicio("Carga de batería de móvil", "Telefonía", 5000),
        Servicio("Instalación de aplicación", "Telefonía", 12000),
        Servicio("Funda de regalo", "Regalos", 5000),
    )

    /** servicio → (insumo, milésimas por vez) */
    private val consumoServicio: Map<Int, List<Pair<Int, Long>>> = mapOf(
        14 to listOf(13 to 2L),
        15 to listOf(13 to 1L),
        19 to listOf(13 to 3L),
        1 to listOf(13 to 1L),
        2 to listOf(13 to 1L),
    )

    private val nombresEmpleados = listOf(
        "Ana", "Luis", "Dayana", "Carlos", "Marta", "Yoel", "Yanelis", "Ernesto",
    )

    private data class Cliente(val nombre: String, val ci: String, val telefono: String)

    private val clientes = listOf(
        Cliente("María Pérez", "88042512345", "+5355512345"),
        Cliente("José Rodríguez", "75091234567", "+5355678901"),
        Cliente("Yaima Castro", "90112845678", "+5355334455"),
        Cliente("Osmany Díaz", "82031456789", "+5355981122"),
        Cliente("Lisandra Suárez", "93071567890", "+5355267788"),
        Cliente("Rafael Moreno", "70020378901", "+5355443322"),
    )

    // ---------------------------------------------------------------------------------------------
    // Planes (en memoria) y consumo
    // ---------------------------------------------------------------------------------------------

    private data class LineaPlan(val clase: String, val idx: Int, val cantidad: Long)

    private data class VentaPlan(val en: Long, val metodo: String, val lineas: List<LineaPlan>, val cliente: Int?)

    private data class CajaPlan(val en: Long, val tipo: String, val importeCent: Long, val motivo: String)

    private data class TurnoPlan(
        val empleado: Int?,
        val abierto: Long,
        val cerrado: Long,
        val fondoCent: Long,
        val ventas: List<VentaPlan>,
        val caja: List<CajaPlan>,
    )

    private val consumoProducto = LongArray(productos.size)
    private val consumoInsumo = LongArray(insumos.size) // milésimas

    suspend fun sembrar(): String {
        val inicio = clock.now()
        val desde = inicio.atZone(zona).toLocalDate().minusDays(diasHistorico.toLong())
        val plan = planificar(desde, inicio)
        val resumen = ejecutar(plan, desde, inicio)
        return resumen
    }

    /** Recorre los últimos 2 años (730 días) y arma turnos, ventas y movimientos de caja. */
    private fun planificar(desde: LocalDate, ahora: Instant): List<TurnoPlan> {
        val planes = mutableListOf<TurnoPlan>()
        var dia = desde
        val hoy = ahora.atZone(zona).toLocalDate()
        while (!dia.isAfter(hoy)) {
            if (dia.dayOfWeek != DayOfWeek.SUNDAY && azar.nextInt(100) < 93) {
                val delDia = mutableListOf<TurnoPlan>()
                // Turno del dueño: de 9:00 a 18:30, 2–5 ventas.
                delDia += turno(dia, null, LocalTime.of(9, 0), LocalTime.of(18, 30), 2 + azar.nextInt(4))
                // Uno o dos turnos de empleados en parte de los días.
                val nEmpleados = when {
                    azar.nextInt(100) < 35 -> 2
                    azar.nextInt(100) < 60 -> 1
                    else -> 0
                }
                repeat(nEmpleados) {
                    val e = azar.nextInt(nombresEmpleados.size)
                    delDia += turno(dia, e, LocalTime.of(10, 0), LocalTime.of(17, 0), 1 + azar.nextInt(3))
                }
                planes += delDia
            }
            dia = dia.plusDays(1)
        }
        return planes
    }

    private fun turno(
        dia: LocalDate,
        empleado: Int?,
        apertura: LocalTime,
        cierre: LocalTime,
        nVentas: Int,
    ): TurnoPlan {
        val abre = dia.atTime(apertura).atZone(zona).toInstant().toEpochMilli()
        val cierra = dia.atTime(cierre).atZone(zona).toInstant().toEpochMilli()
        val ventas = mutableListOf<VentaPlan>()
        var t = abre + 15 * 60_000L
        repeat(nVentas) {
            val lineas = mutableListOf<LineaPlan>()
            repeat(1 + azar.nextInt(3)) {
                when {
                    azar.nextInt(100) < 55 -> lineas += LineaPlan("PRODUCTO", elegirProducto(), (1 + azar.nextInt(3)).toLong())
                    azar.nextInt(100) < 30 -> lineas += LineaPlan("SERVICIO", azar.nextInt(servicios.size), (1 + azar.nextInt(2)).toLong())
                    else -> lineas += LineaPlan("PRODUCTO", elegirProducto(), 1)
                }
            }
            val metodo = if (azar.nextInt(100) < 55) "EFECTIVO" else "TRANSFERENCIA"
            val cliente = if (metodo == "TRANSFERENCIA") azar.nextInt(clientes.size) else null
            ventas += VentaPlan(t, metodo, lineas, cliente)
            t = minOf(t + (20 + azar.nextInt(90)) * 60_000L, cierra - 5 * 60_000L)
        }
        val caja = mutableListOf<CajaPlan>()
        if (azar.nextInt(100) < 18) {
            caja += CajaPlan(abre + 3 * 3_600_000L, "ENTRADA", (500 + azar.nextInt(1500)) * 100L, "Cambio para la caja")
        }
        if (azar.nextInt(100) < 12) {
            caja += CajaPlan(abre + 5 * 3_600_000L, "SALIDA", (300 + azar.nextInt(900)) * 100L, "Compra de hielo y bolsas")
        }
        return TurnoPlan(
            empleado = empleado,
            abierto = abre,
            cerrado = cierra,
            fondoCent = (500 + azar.nextInt(10) * 100) * 100L,
            ventas = ventas,
            caja = caja,
        )
    }

    /** Elige un producto con peso (los baratos y los elaborados salen más). */
    private fun elegirProducto(): Int {
        val total = productos.sumOf { it.peso }
        var r = azar.nextInt(total)
        for ((i, p) in productos.withIndex()) {
            r -= p.peso
            if (r < 0) return i
        }
        return 0
    }

    private fun costoLinea(l: LineaPlan): Long = when (l.clase) {
        "SERVICIO" -> consumoServicio[l.idx].orEmpty().sumOf { (i, mil) -> insumos[i].precioCent * mil / 1000 }
        else -> {
            val p = productos[l.idx]
            if (p.elaborado) {
                recetas[l.idx].orEmpty().sumOf { (i, mil) -> insumos[i].precioCent * mil / 1000 }
            } else {
                p.costo
            }
        }
    }

    private fun precioLinea(l: LineaPlan): Long = when (l.clase) {
        "SERVICIO" -> servicios[l.idx].importeCent
        else -> productos[l.idx].venta
    }

    /** Registra el consumo para calcular existencias iniciales. */
    private fun acumularConsumo(l: LineaPlan) {
        when (l.clase) {
            "SERVICIO" -> consumoServicio[l.idx].orEmpty().forEach { (i, mil) -> consumoInsumo[i] += mil * l.cantidad }
            else -> {
                val p = productos[l.idx]
                if (p.elaborado) {
                    recetas[l.idx].orEmpty().forEach { (i, mil) -> consumoInsumo[i] += mil * l.cantidad }
                } else {
                    consumoProducto[l.idx] += l.cantidad
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Inserción
    // ---------------------------------------------------------------------------------------------

    private suspend fun ejecutar(plan: List<TurnoPlan>, desde: LocalDate, ahora: Instant): String {
        plan.forEach { t -> t.ventas.forEach { v -> v.lineas.forEach(::acumularConsumo) } }

        val creadoEn = desde.minusDays(15).atStartOfDay(zona).toInstant().toEpochMilli()
        val existenciasProducto = LongArray(productos.size)
        val existenciasInsumo = LongArray(insumos.size)

        db.withTransaction {
            db.mantenimientoDao().borrarOperacion()
            db.mantenimientoDao().borrarConfiguracion()
            // Las apps vinculadas no las borra el vaciado de respaldo (no viajan en él): se limpian aquí.
            db.openHelper.writableDatabase.execSQL("DELETE FROM empleado")

            // --- Perfil, cobro y clientes -------------------------------------------------------
            val tarjetaId = db.perfilDao().insertarTarjeta(TarjetaEntity(numero = "9234567812349021", alias = "CUP principal"))
            val telefonoId = db.perfilDao().insertarTelefono(TelefonoEntity(numero = "+5352168959", alias = "Personal"))
            db.perfilDao().guardar(
                PerfilEntity(
                    id = 1, nombre = "Ronnie", apellidos = "Montero", ci = "91040922502",
                    pagoTarjetaId = tarjetaId, pagoTelefonoId = telefonoId,
                ),
            )
            db.clienteFijoDao().insertarTodos(
                clientes.map {
                    ClienteFijoEntity(
                        nombreApellidos = it.nombre, ci = it.ci, telefono = it.telefono,
                        creadoEn = creadoEn, actualizadoEn = creadoEn,
                    )
                },
            )

            // --- Insumos y productos ------------------------------------------------------------
            val idsInsumo = insumos.mapIndexed { i, ins ->
                // Existencias finales suficientes para que los elaborados se puedan seguir sirviendo.
                val unidades = if (ins.unidad == "UNIDAD") 120L + azar.nextInt(180) else 800L + azar.nextInt(2200)
                val resto = unidades * 1000L
                existenciasInsumo[i] = consumoInsumo[i] + resto
                db.insumoDao().insertar(
                    InsumoEntity(
                        nombre = ins.nombre, unidad = ins.unidad, precioCent = ins.precioCent,
                        cantidadMil = resto,
                        nivelBajoMil = resto / 4, nivelCriticoMil = resto / 10,
                        creadoEn = creadoEn, actualizadoEn = creadoEn,
                        precioVentaCent = null,
                    ),
                )
            }
            val idsProducto = productos.mapIndexed { i, p ->
                val resto = if (p.elaborado) 0L else (12 + azar.nextInt(45)).toLong()
                // El costo del elaborado es el de su receta (lo que gasta una unidad).
                val costoElaborado = recetas[i].orEmpty().sumOf { (j, mil) -> insumos[j].precioCent * mil / 1000 }
                existenciasProducto[i] = if (p.elaborado) 0L else consumoProducto[i] + resto
                db.productoDao().insertar(
                    ProductoEntity(
                        categoria = p.categoria, nombre = p.nombre,
                        descripcion = if (p.elaborado) "Preparado en el local" else null,
                        fotoUri = foto("prod", i),
                        fechaCaducidad = if (p.caduca) LocalDate.now(zona).plusDays(2L + azar.nextInt(25)).toEpochDay() else null,
                        precioCostoCent = if (p.elaborado) costoElaborado else p.costo, precioVentaCent = p.venta,
                        cantidad = resto,
                        nivelBajo = 10, nivelCritico = 4,
                        creadoEn = creadoEn, actualizadoEn = creadoEn, eliminado = false,
                    ),
                )
            }
            val idsServicio = servicios.mapIndexed { i, s ->
                db.servicioDao().insertar(
                    ServicioEntity(
                        nombre = s.nombre, tipo = s.tipo, importeCent = s.importeCent,
                        descripcion = null, fotoUri = foto("serv", i),
                        creadoEn = creadoEn, actualizadoEn = creadoEn, eliminado = false,
                    ),
                )
            }

            // --- Recetas y consumos de servicios ------------------------------------------------
            db.recetaDao().insertarTodas(
                recetas.flatMap { (p, lineas) ->
                    lineas.map { (i, mil) -> RecetaLineaEntity(idsProducto[p], idsInsumo[i], mil) }
                },
            )
            db.servicioDao().insertarInsumos(
                consumoServicio.flatMap { (s, lineas) ->
                    lineas.map { (i, mil) -> ServicioInsumoEntity(idsServicio[s], idsInsumo[i], mil) }
                },
            )

            // --- Movimientos de alta ------------------------------------------------------------
            val altas = mutableListOf<MovimientoEntity>()
            productos.forEachIndexed { i, p ->
                if (!p.elaborado) {
                    altas += MovimientoEntity(
                        fecha = creadoEn, tipo = "ALTA", entidad = "PRODUCTO", entidadId = idsProducto[i],
                        nombre = p.nombre, delta = existenciasProducto[i], existencia = existenciasProducto[i],
                        turnoId = null, ventaId = null, nota = null, hechoPor = "",
                    )
                }
            }
            insumos.forEachIndexed { i, ins ->
                altas += MovimientoEntity(
                    fecha = creadoEn, tipo = "ALTA", entidad = "INSUMO", entidadId = idsInsumo[i],
                    nombre = ins.nombre, delta = existenciasInsumo[i], existencia = existenciasInsumo[i],
                    turnoId = null, ventaId = null, nota = null, hechoPor = "",
                )
            }
            db.movimientoDao().insertarTodos(altas)

            // --- Empleados (apps secundarias de prueba) -----------------------------------------
            nombresEmpleados.forEachIndexed { i, nombre ->
                val vinculado = ahora.minusSeconds((7L + i * 9) * 86_400L).toEpochMilli()
                db.syncDao().insertarEmpleado(
                    EmpleadoEntity(
                        nombre = nombre,
                        permisos = "VENDER_PRODUCTOS,VENDER_SERVICIOS",
                        creadoEn = vinculado,
                        vinculadoEn = vinculado,
                        ultimaSincronizacion = ahora.minusSeconds((60L + i * 240L)).toEpochMilli(),
                        clave = Base64.getEncoder().encodeToString(ByteArray(32) { azar.nextInt(256).toByte() }),
                        codigoToken = null, codigoVence = null, activo = true,
                        tarjetaId = null, telefonoId = null,
                        telefono = "+5355%05d".format(100000 + i * 7919),
                        device = "SPVI:demo%010d".format(i + 1),
                        versionCode = 53,
                    ),
                )
            }

            // --- Turnos, ventas y movimientos ----------------------------------------------------
            val movimientos = mutableListOf<MovimientoEntity>()
            var totalVentas = 0
            plan.forEach { tp ->
                val abiertoPor = tp.empleado?.let { nombresEmpleados[it] } ?: "Ronnie"
                val efectivo = tp.ventas.filter { it.metodo == "EFECTIVO" }
                val transferencia = tp.ventas.filter { it.metodo == "TRANSFERENCIA" }
                var unidadesTurno = 0L
                var totalTurno = 0L
                var costoTurno = 0L
                val salidas = tp.caja.filter { it.tipo == "SALIDA" }.sumOf { it.importeCent }
                val entradas = tp.caja.filter { it.tipo == "ENTRADA" }.sumOf { it.importeCent }
                // Se abre vacío (la venta necesita su id) y se completa al cerrar, cuando ya hay totales.
                val turnoId = db.turnoDao().insertar(
                    TurnoEntity(
                        abiertoEn = tp.abierto, cerradoEn = tp.cerrado,
                        numVentas = null, unidades = null, totalCent = null,
                        efectivoCent = null, transferenciaCent = null, costoCent = null,
                        numMovimientos = null,
                        abiertoPor = abiertoPor, cerradoPor = abiertoPor,
                        ventasEfectivo = null, ventasTransferencia = null,
                        movimientosProducto = null, movimientosInsumo = null,
                        uuid = UUID.randomUUID().toString(),
                        sincronizado = true, fondoCent = tp.fondoCent,
                    ),
                )
                tp.caja.forEach { c ->
                    db.cajaDao().insertar(
                        MovimientoCajaEntity(
                            turnoId = turnoId, fecha = c.en, tipo = c.tipo, importeCent = c.importeCent,
                            motivo = c.motivo, hechoPor = abiertoPor, uuid = UUID.randomUUID().toString(),
                            sincronizado = true,
                        ),
                    )
                }
                tp.ventas.forEach { vp ->
                    var total = 0L
                    var costo = 0L
                    var unidades = 0L
                    val detalles = vp.lineas.map { l ->
                        val precio = precioLinea(l)
                        val c = costoLinea(l)
                        total += precio * l.cantidad
                        costo += c * l.cantidad
                        unidades += l.cantidad
                        val nombre = if (l.clase == "SERVICIO") servicios[l.idx].nombre else productos[l.idx].nombre
                        val categoria = if (l.clase == "SERVICIO") servicios[l.idx].tipo else productos[l.idx].categoria
                        val id = if (l.clase == "SERVICIO") idsServicio[l.idx] else idsProducto[l.idx]
                        DetalleVentaEntity(
                            ventaId = 0, productoId = id, nombre = nombre, categoria = categoria,
                            cantidad = l.cantidad, precioBaseCent = precio, precioUnitarioCent = precio,
                            costoUnitarioCent = c, clase = l.clase,
                        )
                    }
                    val ventaId = db.ventaDao().insertarVenta(
                        VentaEntity(
                            turnoId = turnoId, fecha = vp.en, metodoPago = vp.metodo,
                            totalCent = total, costoCent = costo, unidades = unidades,
                            uuid = UUID.randomUUID().toString(), empleadoId = null, sincronizado = true,
                        ),
                    )
                    db.ventaDao().insertarDetalles(detalles.map { it.copy(ventaId = ventaId) })
                    if (vp.metodo == "TRANSFERENCIA") {
                        val c = clientes[vp.cliente ?: 0]
                        db.ventaDao().insertarTransaccion(
                            TransaccionEntity(
                                ventaId = ventaId, fecha = vp.en, importeCent = total,
                                numero = "TRF-%06d".format(100000 + azar.nextInt(899999)),
                                clienteNombre = c.nombre, clienteCi = c.ci, clienteTelefono = c.telefono,
                                tarjetaCobro = "9234567812349021", telefonoCobro = "+5352168959",
                                clienteFijo = azar.nextInt(100) < 55,
                            ),
                        )
                    }
                    vp.lineas.forEach { l ->
                        val lineas = if (l.clase == "SERVICIO") consumoServicio[l.idx].orEmpty() else if (productos[l.idx].elaborado) recetas[l.idx].orEmpty() else emptyList()
                        if (l.clase == "PRODUCTO" && !productos[l.idx].elaborado) {
                            existenciasProducto[l.idx] -= l.cantidad
                            movimientos += MovimientoEntity(
                                fecha = vp.en, tipo = "VENTA", entidad = "PRODUCTO", entidadId = idsProducto[l.idx],
                                nombre = productos[l.idx].nombre, delta = -l.cantidad,
                                existencia = existenciasProducto[l.idx], turnoId = turnoId, ventaId = ventaId,
                                nota = null, hechoPor = abiertoPor,
                            )
                        }
                        lineas.forEach { (i, mil) ->
                            val delta = -(mil * l.cantidad)
                            existenciasInsumo[i] += delta
                            movimientos += MovimientoEntity(
                                fecha = vp.en, tipo = "CONSUMO", entidad = "INSUMO", entidadId = idsInsumo[i],
                                nombre = insumos[i].nombre, delta = delta,
                                existencia = existenciasInsumo[i], turnoId = turnoId, ventaId = ventaId,
                                nota = null, hechoPor = abiertoPor,
                            )
                        }
                    }
                    unidadesTurno += unidades
                    totalTurno += total
                    costoTurno += costo
                    totalVentas++
                }
                db.syncDao().actualizarTurno(TurnoEntity(
                    id = turnoId, abiertoEn = tp.abierto, cerradoEn = tp.cerrado,
                    numVentas = tp.ventas.size, unidades = unidadesTurno, totalCent = totalTurno,
                    efectivoCent = efectivo.sumOf { v -> v.lineas.sumOf { precioLinea(it) * it.cantidad } },
                    transferenciaCent = transferencia.sumOf { v -> v.lineas.sumOf { precioLinea(it) * it.cantidad } },
                    costoCent = costoTurno, numMovimientos = movimientos.count { it.turnoId == turnoId },
                    abiertoPor = abiertoPor, cerradoPor = abiertoPor,
                    ventasEfectivo = efectivo.size, ventasTransferencia = transferencia.size,
                    movimientosProducto = movimientos.count { it.turnoId == turnoId && it.entidad == "PRODUCTO" },
                    movimientosInsumo = movimientos.count { it.turnoId == turnoId && it.entidad == "INSUMO" },
                    uuid = UUID.randomUUID().toString(), empleadoId = null, sincronizado = true,
                    fondoCent = tp.fondoCent,
                    contadoCent = tp.fondoCent + efectivo.sumOf { v -> v.lineas.sumOf { precioLinea(it) * it.cantidad } } + entradas - salidas,
                    entradasCent = if (entradas == 0L) null else entradas,
                    salidasCent = if (salidas == 0L) null else salidas,
                    numAnuladas = 0,
                ))
            }
            movimientos.chunked(500).forEach { db.movimientoDao().insertarTodos(it) }

            // --- Preajustes de precio -----------------------------------------------------------
            val pre1 = db.preciosDao().insertar(
                PreajusteEntity(nombre = "Empleados", puntosBasicos = 1000, metodoPago = null, importeMinimoCent = null, activo = true),
            )
            db.preciosDao().insertarProductos(listOf(idsProducto[19], idsProducto[21], idsProducto[36]).map { PreajusteProductoEntity(pre1, it) })
            val pre2 = db.preciosDao().insertar(
                PreajusteEntity(nombre = "Por mayor (desde 5)", puntosBasicos = 500, metodoPago = null, importeMinimoCent = 500000, activo = true),
            )
            db.preciosDao().insertarProductos(listOf(idsProducto[1], idsProducto[2], idsProducto[4], idsProducto[13]).map { PreajusteProductoEntity(pre2, it) })
        }
        return buildString {
            appendLine("Datos de demostración cargados")
            appendLine("Ventas: ${plan.sumOf { it.ventas.size }} en ${plan.size} turnos")
            appendLine("Productos: ${productos.size} · servicios: ${servicios.size} · insumos: ${insumos.size}")
            appendLine("Apps secundarias: ${nombresEmpleados.size}")
            appendLine("Desde: ${desde} · hasta: ${ahora.atZone(zona).toLocalDate()}")
        }
    }

    /** URI de la foto si el archivo existe (se empujan por adb a files/fotos). */
    private fun foto(prefijo: String, indice: Int): String? {
        val f = File(File(contexto.filesDir, "fotos"), "%s%02d.jpg".format(prefijo, indice + 1))
        return if (f.isFile) "file://" + f.absolutePath else null
    }
}

/** Acceso al grafo de Hilt desde un proceso sin actividad inyectada (debug). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccesoSembrador {
    fun baseDatos(): ShalevDatabase
    fun reloj(): Clock
}

/** Punto de entrada del sembrador para la pantalla de debug de `:app`. */
object SembradorDebug {
    suspend fun ejecutar(contexto: Context): String {
        val acceso = EntryPointAccessors.fromApplication(contexto, AccesoSembrador::class.java)
        return Sembrador(acceso.baseDatos(), contexto.applicationContext, acceso.reloj()).sembrar()
    }
}
