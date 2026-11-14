package cu.shalev.data.seed

import android.content.Context
import androidx.room.withTransaction
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.AlmacenLocalEntity
import cu.shalev.data.db.entity.ClienteFijoEntity
import cu.shalev.data.db.entity.DetalleVentaEntity
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.db.entity.InsumoEntity
import cu.shalev.data.db.entity.MovimientoCajaEntity
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.db.entity.PerfilEntity
import cu.shalev.data.db.entity.PreajusteEntity
import cu.shalev.data.db.entity.PreajusteProductoEntity
import cu.shalev.data.db.entity.PreajusteServicioEntity
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
 * **Solo builds de depuración.** Carga datos de demostración en la base de la app instalada.
 *
 * Cubre **todos los módulos** de la app actual (v18):
 * - **2 años de uso** (730 días) en el local principal: turnos, ventas por efectivo y transferencia, caja,
 *   movimientos de existencias, anulaciones y correcciones de venta.
 * - **Locales con catálogo e historial propios** (v18): el principal y dos más, cada uno con sus productos,
 *   sus servicios, sus empleados asignados y su propio histórico de turnos y ventas.
 * - **Inventario**: productos, insumos, elaborados con receta, consumos de servicios y ajustes de
 *   existencias con motivo (merma, consumo interno, corrección de conteo y devolución).
 * - **Servicios**, **clientes fijos**, **preajustes de precio** (de productos y de servicios), **perfil y
 *   cobro electrónico** y **apps secundarias** vinculadas con sus permisos y su local.
 *
 * No entra en el APK de release: vive en `data/src/debug` (D-05, auditoría de la 0.32.0). Borra los datos
 * operativos, de configuración y de gestión antes de sembrar, así que se puede ejecutar varias veces.
 */
class Sembrador(
    private val db: ShalevDatabase,
    private val contexto: Context,
    private val clock: Clock,
) {
    private val azar = Random(20261022)
    private val zona: ZoneId = ZoneId.systemDefault()

    /** Histórico de demostración: 2 años de uso (730 días hacia atrás desde hoy) en el local principal. */
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
        Insumo("Jamón en lonchas", "GRAMO", 18),
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
    // Locales de demostración: cada uno es un negocio completo (catálogo, existencias e historial propios)
    // ---------------------------------------------------------------------------------------------

    private data class LocalDemo(
        val nombre: String,
        /** Índices del catálogo global que usa este local. */
        val productos: List<Int>,
        val servicios: List<Int>,
        /** Días de histórico hacia atrás. */
        val dias: Int,
        /** Días de la semana que abre. */
        val diasSemana: Set<DayOfWeek>,
        /** Empleados (índice en [nombresEmpleados]) asignados a este local. */
        val empleados: List<Int>,
        /** Turno del dueño (null si el local solo lo llevan empleados). */
        val turnoDueño: Pair<LocalTime, LocalTime>?,
        val ventasDueño: IntRange,
        val ventasEmpleado: IntRange,
        /** Turnos de empleado por día abierto. */
        val turnosEmpleado: IntRange,
    )

    private val locales = listOf(
        LocalDemo(
            nombre = "Bodega",
            productos = productos.indices.toList(),
            servicios = servicios.indices.toList(),
            dias = diasHistorico,
            diasSemana = DayOfWeek.values().toSet() - DayOfWeek.SUNDAY,
            empleados = listOf(0, 1, 2, 3),
            turnoDueño = LocalTime.of(9, 0) to LocalTime.of(18, 30),
            ventasDueño = 2..5,
            ventasEmpleado = 1..3,
            turnosEmpleado = 0..2,
        ),
        LocalDemo(
            nombre = "Casa",
            productos = listOf(0, 3, 5, 7, 12, 14, 16, 20, 25, 26, 30, 32, 33, 34, 35, 27, 28, 29),
            servicios = listOf(10, 11, 12, 13, 14, 15, 16, 19),
            dias = 400,
            diasSemana = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            empleados = listOf(4, 5),
            turnoDueño = null,
            ventasDueño = 0..0,
            ventasEmpleado = 1..4,
            turnosEmpleado = 1..1,
        ),
        LocalDemo(
            nombre = "Kiosco",
            productos = listOf(1, 6, 8, 15, 18, 19, 21, 22, 23, 24, 26, 27, 28, 29, 31),
            servicios = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 18),
            dias = 220,
            diasSemana = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            empleados = listOf(6, 7),
            turnoDueño = null,
            ventasDueño = 0..0,
            ventasEmpleado = 1..4,
            turnosEmpleado = 1..1,
        ),
    )

    /** Motivos con los que el dueño anula una venta (2 % de las ventas). */
    private val motivosAnulacion = listOf(
        "El cliente devolvió el producto",
        "Error en el cobro",
        "Se registró por duplicado",
        "El cliente cambió de opinión",
    )

    /** Motivos de corrección: la venta original se anula y entra otra que la corrige. */
    private val motivosCorreccion = listOf(
        "Se corrigió la cantidad",
        "Se cambió el método de pago",
        "Se cambió un artículo por otro",
    )

    /** Ajustes de existencias con motivo (como los que registra la app en Inventario). */
    private data class AjusteDemo(val motivo: String, val detalle: String, val cantidad: Long)

    private val ajustesDemo = listOf(
        AjusteDemo("MERMA", "cajas mojadas en el almacén", -2L),
        AjusteDemo("MERMA", "envases rotos en el mostrador", -1L),
        AjusteDemo("CONSUMO_INTERNO", "café del personal", -1L),
        AjusteDemo("CONSUMO_INTERNO", "agua para el equipo", -2L),
        AjusteDemo("CORRECCION", "diferencia en el conteo", -1L),
        AjusteDemo("CORRECCION", "se contaron dos cajas de más", 2L),
        AjusteDemo("DEVOLUCION", "devolución del proveedor", 3L),
    )

    // ---------------------------------------------------------------------------------------------
    // Planes (en memoria) y consumo
    // ---------------------------------------------------------------------------------------------

    private data class LineaPlan(val clase: String, val idx: Int, val cantidad: Long)

    /** Anulación de una venta: cuándo, por qué y quién. */
    private data class AnulacionPlan(val en: Long, val motivo: String, val por: String)

    private data class VentaPlan(
        val en: Long,
        val metodo: String,
        val lineas: List<LineaPlan>,
        val cliente: Int?,
        /** Anulación con motivo; null si la venta se queda. */
        val anulacion: AnulacionPlan?,
        /** La venta se corrige con otra (la original queda anulada). */
        val correccion: Boolean,
        /** Motivo de la corrección (el texto final lo arma la app). */
        val motivoCorreccion: String?,
    )

    private data class CajaPlan(val en: Long, val tipo: String, val importeCent: Long, val motivo: String)

    /** Ajuste de existencias con motivo, de un local (sin turno: se registra fuera de la jornada). */
    private data class AjustePlan(
        val local: String,
        val en: Long,
        val idx: Int,
        val delta: Long,
        val motivo: String,
        val detalle: String,
    )

    private data class TurnoPlan(
        val local: String,
        val empleado: Int?,
        val abierto: Long,
        val cerrado: Long,
        val fondoCent: Long,
        val ventas: List<VentaPlan>,
        val caja: List<CajaPlan>,
    )

    /** Todo lo que el plan sembró: turnos por local y ajustes de existencias. */
    private data class Plan(
        val turnos: List<TurnoPlan>,
        val ajustes: List<AjustePlan>,
    )

    suspend fun sembrar(): String {
        val inicio = clock.now()
        val desde = inicio.atZone(zona).toLocalDate().minusDays(diasHistorico.toLong())
        val plan = planificar(desde, inicio)
        return ejecutar(plan, desde, inicio)
    }

    /** Recorre el histórico de cada local y arma turnos, ventas, caja y ajustes de existencias. */
    private fun planificar(desde: LocalDate, ahora: Instant): Plan {
        val turnos = mutableListOf<TurnoPlan>()
        val ajustes = mutableListOf<AjustePlan>()
        val hoy = ahora.atZone(zona).toLocalDate()
        locales.forEach { local ->
            var dia = hoy.minusDays(local.dias.toLong())
            while (!dia.isAfter(hoy)) {
                if (dia.dayOfWeek in local.diasSemana && azar.nextInt(100) < 93) {
                    val delDia = mutableListOf<TurnoPlan>()
                    local.turnoDueño?.let { (apertura, cierre) ->
                        delDia += turno(
                            local, dia, null, apertura, cierre,
                            azar.nextInt(local.ventasDueño.first, local.ventasDueño.last + 1),
                        )
                    }
                    val nEmpleados = when {
                        local.turnoDueño == null -> local.turnosEmpleado.first
                        azar.nextInt(100) < 35 -> 2
                        azar.nextInt(100) < 60 -> 1
                        else -> 0
                    }.coerceIn(local.turnosEmpleado)
                    repeat(nEmpleados) {
                        val e = local.empleados[azar.nextInt(local.empleados.size)]
                        delDia += turno(
                            local, dia, e, LocalTime.of(10, 0), LocalTime.of(17, 0),
                            azar.nextInt(local.ventasEmpleado.first, local.ventasEmpleado.last + 1),
                        )
                    }
                    // Un ajuste de existencias al abrir el local (12 % de los días abiertos).
                    if (azar.nextInt(100) < 12) {
                        val ajuste = ajustesDemo[azar.nextInt(ajustesDemo.size)]
                        ajustes += AjustePlan(
                            local = local.nombre,
                            en = dia.atTime(8, 30).atZone(zona).toInstant().toEpochMilli(),
                            idx = local.productos[azar.nextInt(local.productos.size)],
                            delta = ajuste.cantidad,
                            motivo = ajuste.motivo,
                            detalle = ajuste.detalle,
                        )
                    }
                    turnos += delDia
                }
                dia = dia.plusDays(1)
            }
        }
        turnos.sortBy { it.abierto }
        return Plan(turnos, ajustes)
    }

    private fun turno(
        local: LocalDemo,
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
                    azar.nextInt(100) < 55 -> lineas += LineaPlan("PRODUCTO", elegirProducto(local.productos), (1 + azar.nextInt(3)).toLong())
                    azar.nextInt(100) < 30 -> lineas += LineaPlan("SERVICIO", elegirServicio(local.servicios), (1 + azar.nextInt(2)).toLong())
                    else -> lineas += LineaPlan("PRODUCTO", elegirProducto(local.productos), 1)
                }
            }
            val metodo = if (azar.nextInt(100) < 55) "EFECTIVO" else "TRANSFERENCIA"
            val cliente = if (metodo == "TRANSFERENCIA") azar.nextInt(clientes.size) else null
            val anula = azar.nextInt(100) < 2
            val corrige = !anula && azar.nextInt(100) < 1
            val anulacion = if (anula || corrige) {
                AnulacionPlan(
                    en = minOf(t + (30 + azar.nextInt(150)) * 60_000L, cierra),
                    motivo = if (corrige) motivosCorreccion[azar.nextInt(motivosCorreccion.size)]
                    else motivosAnulacion[azar.nextInt(motivosAnulacion.size)],
                    por = if (empleado == null) "Ronnie" else nombresEmpleados[empleado],
                )
            } else {
                null
            }
            ventas += VentaPlan(
                en = t, metodo = metodo, lineas = lineas, cliente = cliente,
                anulacion = anulacion, correccion = corrige,
                motivoCorreccion = if (corrige) anulacion?.motivo else null,
            )
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
            local = local.nombre,
            empleado = empleado,
            abierto = abre,
            cerrado = cierra,
            fondoCent = (500 + azar.nextInt(10) * 100) * 100L,
            ventas = ventas,
            caja = caja,
        )
    }

    /** Elige un producto del local con peso (los baratos y los elaborados salen más). */
    private fun elegirProducto(catalogo: List<Int>): Int {
        val total = catalogo.sumOf { productos[it].peso }
        var r = azar.nextInt(total)
        for (i in catalogo) {
            r -= productos[i].peso
            if (r < 0) return i
        }
        return catalogo.first()
    }

    private fun elegirServicio(catalogo: List<Int>): Int = catalogo[azar.nextInt(catalogo.size)]

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

    /** Líneas de la venta que corrige a otra: una unidad menos de la última línea (o esa línea fuera). */
    private fun lineasCorregidas(lineas: List<LineaPlan>): List<LineaPlan> {
        if (lineas.isEmpty()) return lineas
        val ultima = lineas.last()
        return when {
            ultima.cantidad > 1 -> lineas.dropLast(1) + ultima.copy(cantidad = ultima.cantidad - 1)
            lineas.size > 1 -> lineas.dropLast(1)
            else -> lineas
        }
    }

    /**
     * Registra el consumo para calcular existencias iniciales. Una venta corregida consume lo que
     * consume la venta que la corrige (la original se anula y devuelve las existencias).
     */
    private fun acumularConsumo(
        l: LineaPlan,
        local: String,
        prod: MutableMap<String, LongArray>,
        ins: MutableMap<String, LongArray>,
    ) {
        when (l.clase) {
            "SERVICIO" -> consumoServicio[l.idx].orEmpty().forEach { (i, mil) -> ins[local]!![i] += mil * l.cantidad }
            else -> {
                val p = productos[l.idx]
                if (p.elaborado) {
                    recetas[l.idx].orEmpty().forEach { (i, mil) -> ins[local]!![i] += mil * l.cantidad }
                } else {
                    prod[local]!![l.idx] += l.cantidad
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Inserción
    // ---------------------------------------------------------------------------------------------

    /**
     * Catálogo de un local: sus insumos, sus productos, sus servicios y el alta de existencias con el
     * histórico de consumo ya descontado (como en una compra de apertura).
     */
    private suspend fun sembrarLocal(
        local: LocalDemo,
        creadoEn: Long,
        idsProducto: MutableMap<String, LongArray>,
        idsInsumo: MutableMap<String, LongArray>,
        idsServicio: MutableMap<String, LongArray>,
        existenciasProducto: MutableMap<String, LongArray>,
        existenciasInsumo: MutableMap<String, LongArray>,
        consumoProd: Map<String, LongArray>,
        consumoIns: Map<String, LongArray>,
        ajustesProd: Map<String, LongArray>,
        altas: MutableList<MovimientoEntity>,
    ) {
        val nombre = local.nombre
        val ins = LongArray(insumos.size)
        val prods = LongArray(productos.size)
        val servs = LongArray(servicios.size)

        // --- Insumos (cada local lleva su propio inventario) -------------------------------------
        insumos.forEachIndexed { i, insumo ->
            val unidades = if (insumo.unidad == "UNIDAD") 120L + azar.nextInt(180) else 800L + azar.nextInt(2200)
            val resto = unidades * 1000L
            // El alta es lo que había al abrir: lo que se consumió más lo que queda más los ajustes del período.
            existenciasInsumo[nombre]!![i] = consumoIns[nombre]!![i] + resto
            ins[i] = db.insumoDao().insertar(
                InsumoEntity(
                    nombre = insumo.nombre, unidad = insumo.unidad, precioCent = insumo.precioCent,
                    cantidadMil = resto,
                    nivelBajoMil = resto / 4, nivelCriticoMil = resto / 10,
                    creadoEn = creadoEn, actualizadoEn = creadoEn, precioVentaCent = null, local = nombre,
                ),
            )
            altas += MovimientoEntity(
                fecha = creadoEn, tipo = "ALTA", entidad = "INSUMO", entidadId = ins[i],
                nombre = insumo.nombre, delta = existenciasInsumo[nombre]!![i], existencia = existenciasInsumo[nombre]!![i],
                turnoId = null, ventaId = null, nota = null, hechoPor = "", local = nombre,
            )
        }

        // --- Productos ---------------------------------------------------------------------------
        local.productos.forEach { i ->
            val p = productos[i]
            val resto = if (p.elaborado) 0L else (12 + azar.nextInt(45)).toLong()
            val costoElaborado = recetas[i].orEmpty().sumOf { (j, mil) -> insumos[j].precioCent * mil / 1000 }
            // El alta descuenta los ajustes del período (una merma de 2 unidades exige 2 unidades más al abrir).
            existenciasProducto[nombre]!![i] = consumoProd[nombre]!![i] - ajustesProd[nombre]!![i] + resto
            prods[i] = db.productoDao().insertar(
                ProductoEntity(
                    categoria = p.categoria, nombre = p.nombre,
                    descripcion = if (p.elaborado) "Preparado en el local" else null, fotoUri = foto("prod", i),
                    fechaCaducidad = if (p.caduca) LocalDate.now(zona).plusDays(2L + azar.nextInt(25)).toEpochDay() else null,
                    precioCostoCent = if (p.elaborado) costoElaborado else p.costo, precioVentaCent = p.venta,
                    cantidad = resto,
                    nivelBajo = 10, nivelCritico = 4,
                    creadoEn = creadoEn, actualizadoEn = creadoEn, eliminado = false,
                    local = nombre,
                ),
            )
            if (!p.elaborado) {
                altas += MovimientoEntity(
                    fecha = creadoEn, tipo = "ALTA", entidad = "PRODUCTO", entidadId = prods[i],
                    nombre = p.nombre, delta = existenciasProducto[nombre]!![i], existencia = existenciasProducto[nombre]!![i],
                    turnoId = null, ventaId = null, nota = null, hechoPor = "", local = nombre,
                )
            }
        }

        // --- Servicios ---------------------------------------------------------------------------
        local.servicios.forEach { i ->
            val s = servicios[i]
            servs[i] = db.servicioDao().insertar(
                ServicioEntity(
                    nombre = s.nombre, tipo = s.tipo, importeCent = s.importeCent,
                    descripcion = null, fotoUri = foto("serv", i),
                    creadoEn = creadoEn, actualizadoEn = creadoEn, eliminado = false, local = nombre,
                ),
            )
            consumoServicio[i]?.let { lineas ->
                db.servicioDao().insertarInsumos(lineas.map { (j, mil) -> ServicioInsumoEntity(servs[i], ins[j], mil) })
            }
        }

        // --- Recetas (solo hay elaborados en el local principal) ---------------------------------
        local.productos.filter { productos[it].elaborado }.forEach { i ->
            recetas[i]?.let { lineas ->
                db.recetaDao().insertarTodas(lineas.map { (j, mil) -> RecetaLineaEntity(prods[i], ins[j], mil) })
            }
        }

        idsProducto[nombre] = prods
        idsInsumo[nombre] = ins
        idsServicio[nombre] = servs
    }

    private suspend fun ejecutar(plan: Plan, desde: LocalDate, ahora: Instant): String {
        // --- Consumo y ajustes por local ---------------------------------------------------------
        val consumoProd = mutableMapOf<String, LongArray>()
        val consumoIns = mutableMapOf<String, LongArray>()
        val ajustesProd = mutableMapOf<String, LongArray>()
        locales.forEach { l ->
            consumoProd[l.nombre] = LongArray(productos.size)
            consumoIns[l.nombre] = LongArray(insumos.size)
            ajustesProd[l.nombre] = LongArray(productos.size)
        }
        plan.turnos.forEach { tp ->
            tp.ventas.forEach { v ->
                val lineas = if (v.correccion) lineasCorregidas(v.lineas) else v.lineas
                lineas.forEach { l -> acumularConsumo(l, tp.local, consumoProd, consumoIns) }
            }
        }
        val ajustes = plan.ajustes
        ajustes.forEach { a -> ajustesProd[a.local]!![a.idx] += a.delta }

        val creadoEn = desde.minusDays(15).atStartOfDay(zona).toInstant().toEpochMilli()
        val existenciasProducto = mutableMapOf<String, LongArray>()
        val existenciasInsumo = mutableMapOf<String, LongArray>()
        val idsProducto = mutableMapOf<String, LongArray>()
        val idsInsumo = mutableMapOf<String, LongArray>()
        val idsServicio = mutableMapOf<String, LongArray>()
        locales.forEach { l ->
            existenciasProducto[l.nombre] = LongArray(productos.size)
            existenciasInsumo[l.nombre] = LongArray(insumos.size)
        }

        var ventasAnuladas = 0
        var ventasCorregidas = 0
        var totalVentas = 0
        var totalMovimientos = 0
        val idsEmpleado = LongArray(nombresEmpleados.size)

        db.withTransaction {
            // --- Vaciado: operativo, configuración y gestión (semilla repetible) -------------------
            db.mantenimientoDao().borrarOperacion()
            db.mantenimientoDao().borrarConfiguracion()
            db.gestionDao().borrarConsumos()
            db.gestionDao().borrarLotes()
            db.gestionDao().borrarRecepciones()
            db.gestionDao().borrarFichas()
            db.gestionDao().borrarRevisiones()
            db.gestionDao().borrarConfiguracion()
            db.mantenimientoDao().borrarRecibosComandos()
            // Semilla repetible: cada local tiene su propio catálogo y su propio historial.
            db.almacenLocalDao().borrar()
            // Cada local corresponde a un Inventario y Servicios propios (la misma ventana para ambos);
            // el primero queda activo.
            val idLocal = mutableMapOf<String, String>()
            locales.forEach { l ->
                val entidad = AlmacenLocalEntity(nombre = l.nombre)
                db.almacenLocalDao().insertar(entidad)
                idLocal[l.nombre] = entidad.id
            }
            db.almacenLocalDao().inventario(locales.first().nombre)
            db.almacenLocalDao().servicios(locales.first().nombre)
            val localBodega = locales.first().nombre
            // Las apps vinculadas no las borra el vaciado de respaldo (no viajan en él): se limpian aquí.
            db.openHelper.writableDatabase.execSQL("DELETE FROM empleado")

            // --- Perfil, cobro y clientes ---------------------------------------------------------
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

            // --- Catálogo y existencias por local --------------------------------------------------
            val altas = mutableListOf<MovimientoEntity>()
            locales.forEach { local ->
                sembrarLocal(
                    local, creadoEn, idsProducto, idsInsumo, idsServicio,
                    existenciasProducto, existenciasInsumo, consumoProd, consumoIns, ajustesProd, altas,
                )
            }
            db.movimientoDao().insertarTodos(altas)

            // --- Empleados (apps secundarias de prueba), cada uno en su local ---------------------
            nombresEmpleados.forEachIndexed { i, nombre ->
                val local = locales.firstOrNull { i in it.empleados } ?: locales.first()
                val vinculado = ahora.minusSeconds((7L + i * 9) * 86_400L).toEpochMilli()
                idsEmpleado[i] = db.syncDao().insertarEmpleado(
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
                        localId = idLocal[local.nombre],
                    ),
                )
            }

            // --- Turnos, ventas y movimientos ------------------------------------------------------
            val movimientos = mutableListOf<MovimientoEntity>()
            val ajustesAplicados = mutableSetOf<String>()
            plan.turnos.forEach { tp ->
                val local = tp.local
                val abiertoPor = tp.empleado?.let { nombresEmpleados[it] } ?: "Ronnie"
                val empleadoId = tp.empleado?.let { idsEmpleado[it] }
                var unidadesTurno = 0L
                var totalTurno = 0L
                var costoTurno = 0L
                var efectivoTurno = 0L
                var transferenciaTurno = 0L
                var ventasEfectivoTurno = 0
                var ventasTransferenciaTurno = 0
                var anuladasTurno = 0
                val salidas = tp.caja.filter { it.tipo == "SALIDA" }.sumOf { it.importeCent }
                val entradas = tp.caja.filter { it.tipo == "ENTRADA" }.sumOf { it.importeCent }

                // --- Ajustes de existencias del día (08:30, antes de abrir) -----------------------
                ajustes.filter { a ->
                    a.local == local && a.en < tp.abierto && !ajustesAplicados.contains(a.local + a.en + a.idx)
                }.forEach { a ->
                    ajustesAplicados += a.local + a.en + a.idx
                    val idEntidad = idsProducto[local]!![a.idx]
                    existenciasProducto[local]!![a.idx] += a.delta
                    val existencia = existenciasProducto[local]!![a.idx]
                    movimientos += MovimientoEntity(
                        fecha = a.en, tipo = "AJUSTE", entidad = "PRODUCTO", entidadId = idEntidad,
                        nombre = productos[a.idx].nombre, delta = a.delta, existencia = existencia,
                        turnoId = null, ventaId = null,
                        nota = "${motivoTitulo(a.motivo)} · ${a.detalle}", hechoPor = "Ronnie", local = local,
                        motivo = a.motivo,
                    )
                }

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
                        sincronizado = true, fondoCent = tp.fondoCent, local = local,
                        empleadoId = empleadoId,
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
                    val ventaId = escribirVenta(
                        vp, turnoId, local, abiertoPor, empleadoId,
                        idsProducto[local]!!, idsInsumo[local]!!, idsServicio[local]!!,
                        existenciasProducto[local]!!, existenciasInsumo[local]!!,
                        movimientos, clientes,
                    )
                    totalVentas++
                    if (vp.anulacion == null) {
                        // Venta vigente: cuenta en los totales del turno.
                        unidadesTurno += unidadesDe(vp)
                        totalTurno += totalDe(vp)
                        costoTurno += costoDe(vp)
                        if (vp.metodo == "EFECTIVO") {
                            efectivoTurno += totalDe(vp); ventasEfectivoTurno++
                        } else {
                            transferenciaTurno += totalDe(vp); ventasTransferenciaTurno++
                        }
                    } else {
                        ventasAnuladas++
                        anuladasTurno++
                        if (vp.correccion) {
                            ventasCorregidas++
                            // La venta que corrige: misma fecha un minuto después, con un motivo que la app rehace.
                            val correccion = vp.copy(
                                en = vp.anulacion.en + 60_000L,
                                lineas = lineasCorregidas(vp.lineas),
                                metodo = if (vp.motivoCorreccion == "Se cambió el método de pago") {
                                    if (vp.metodo == "EFECTIVO") "TRANSFERENCIA" else "EFECTIVO"
                                } else {
                                    vp.metodo
                                },
                                anulacion = null, correccion = false, motivoCorreccion = null,
                            )
                            val nuevaId = escribirVenta(
                                correccion, turnoId, local, abiertoPor, empleadoId,
                                idsProducto[local]!!, idsInsumo[local]!!, idsServicio[local]!!,
                                existenciasProducto[local]!!, existenciasInsumo[local]!!,
                                movimientos, clientes, corrigeVentaId = ventaId,
                            )
                            db.ventaDao().cambiarMotivo(
                                ventaId,
                                "Modificada → venta #$nuevaId · ${vp.motivoCorreccion.orEmpty()}".take(120),
                            )
                            totalVentas++
                            unidadesTurno += unidadesDe(correccion)
                            totalTurno += totalDe(correccion)
                            costoTurno += costoDe(correccion)
                            if (correccion.metodo == "EFECTIVO") {
                                efectivoTurno += totalDe(correccion); ventasEfectivoTurno++
                            } else {
                                transferenciaTurno += totalDe(correccion); ventasTransferenciaTurno++
                            }
                        }
                    }
                }

                db.syncDao().actualizarTurno(TurnoEntity(
                    id = turnoId, abiertoEn = tp.abierto, cerradoEn = tp.cerrado,
                    numVentas = ventasEfectivoTurno + ventasTransferenciaTurno, unidades = unidadesTurno,
                    totalCent = totalTurno, efectivoCent = efectivoTurno, transferenciaCent = transferenciaTurno,
                    costoCent = costoTurno, numMovimientos = movimientos.count { it.turnoId == turnoId },
                    abiertoPor = abiertoPor, cerradoPor = abiertoPor,
                    ventasEfectivo = ventasEfectivoTurno, ventasTransferencia = ventasTransferenciaTurno,
                    movimientosProducto = movimientos.count { it.turnoId == turnoId && it.entidad == "PRODUCTO" },
                    movimientosInsumo = movimientos.count { it.turnoId == turnoId && it.entidad == "INSUMO" },
                    uuid = UUID.randomUUID().toString(), empleadoId = empleadoId, sincronizado = true,
                    fondoCent = tp.fondoCent, local = local,
                    contadoCent = tp.fondoCent + efectivoTurno + entradas - salidas,
                    entradasCent = if (entradas == 0L) null else entradas,
                    salidasCent = if (salidas == 0L) null else salidas,
                    numAnuladas = anuladasTurno,
                ))
            }
            movimientos.chunked(500).forEach { db.movimientoDao().insertarTodos(it) }
            totalMovimientos = movimientos.size + altas.size
        }

        return buildString {
            appendLine("Datos de demostración cargados")
            locales.forEach { l ->
                val turnos = plan.turnos.count { it.local == l.nombre }
                val ventasLocal = plan.turnos.filter { it.local == l.nombre }.sumOf { it.ventas.size }
                appendLine(
                    "Local ${l.nombre}: ${l.dias} días · $turnos turnos · $ventasLocal ventas · " +
                        "${l.productos.size} productos · ${l.servicios.size} servicios",
                )
            }
            appendLine("Ventas: $totalVentas ($ventasAnuladas anuladas, $ventasCorregidas corregidas)")
            appendLine("Movimientos de existencias: $totalMovimientos · Ajustes con motivo: ${ajustes.size}")
            appendLine("Preajustes de precio: 2 de productos · 1 de servicios")
            appendLine("Apps secundarias: ${nombresEmpleados.size} (cada una con su local)")
            appendLine("Desde: ${desde} · hasta: ${ahora.atZone(zona).toLocalDate()}")
        }
    }

    /** Escribe una venta con sus detalles, su transacción y sus movimientos de existencias. */
    private suspend fun escribirVenta(
        vp: VentaPlan,
        turnoId: Long,
        local: String,
        hechoPor: String,
        empleadoId: Long?,
        idsProducto: LongArray,
        idsInsumo: LongArray,
        idsServicio: LongArray,
        existenciasProducto: LongArray,
        existenciasInsumo: LongArray,
        movimientos: MutableList<MovimientoEntity>,
        clientes: List<Cliente>,
        corrigeVentaId: Long? = null,
    ): Long {
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
                uuid = UUID.randomUUID().toString(), empleadoId = empleadoId, sincronizado = true,
                local = local, corrigeVentaId = corrigeVentaId,
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
                    local = local,
                ),
            )
        }
        // Salidas de existencias: un movimiento por producto y otro por cada insumo consumido.
        val salidas = mutableListOf<Triple<String, Int, Long>>()
        vp.lineas.forEach { l ->
            if (l.clase == "PRODUCTO" && !productos[l.idx].elaborado) {
                existenciasProducto[l.idx] -= l.cantidad
                salidas += Triple("PRODUCTO", l.idx, l.cantidad)
                movimientos += MovimientoEntity(
                    fecha = vp.en, tipo = "VENTA", entidad = "PRODUCTO", entidadId = idsProducto[l.idx],
                    nombre = productos[l.idx].nombre, delta = -l.cantidad,
                    existencia = existenciasProducto[l.idx], turnoId = turnoId, ventaId = ventaId,
                    nota = null, hechoPor = hechoPor, local = local,
                )
            }
            val lineas = if (l.clase == "SERVICIO") consumoServicio[l.idx].orEmpty()
            else if (productos[l.idx].elaborado) recetas[l.idx].orEmpty() else emptyList()
            lineas.forEach { (i, mil) ->
                val delta = -(mil * l.cantidad)
                existenciasInsumo[i] += delta
                salidas += Triple("INSUMO", i, mil * l.cantidad)
                movimientos += MovimientoEntity(
                    fecha = vp.en, tipo = "CONSUMO", entidad = "INSUMO", entidadId = idsInsumo[i],
                    nombre = insumos[i].nombre, delta = delta,
                    existencia = existenciasInsumo[i], turnoId = turnoId, ventaId = ventaId,
                    nota = null, hechoPor = hechoPor, local = local,
                )
            }
        }
        // Anulación: la app devuelve las existencias con un movimiento ANULACION por cada salida.
        vp.anulacion?.let { anulacion ->
            salidas.forEach { (entidad, idx, cantidad) ->
                val existencia = if (entidad == "PRODUCTO") {
                    existenciasProducto[idx] += cantidad
                    existenciasProducto[idx]
                } else {
                    existenciasInsumo[idx] += cantidad
                    existenciasInsumo[idx]
                }
                val idEntidad = if (entidad == "PRODUCTO") idsProducto[idx] else idsInsumo[idx]
                val nombre = if (entidad == "PRODUCTO") productos[idx].nombre else insumos[idx].nombre
                movimientos += MovimientoEntity(
                    fecha = anulacion.en, tipo = "ANULACION", entidad = entidad, entidadId = idEntidad,
                    nombre = nombre, delta = cantidad, existencia = existencia,
                    turnoId = turnoId, ventaId = ventaId,
                    nota = "Venta anulada · ${anulacion.motivo}".take(160), hechoPor = anulacion.por, local = local,
                )
            }
            db.ventaDao().anular(ventaId, anulacion.en, anulacion.motivo.trim(), anulacion.por, bajar = false)
        }
        return ventaId
    }

    private fun totalDe(vp: VentaPlan): Long = vp.lineas.sumOf { precioLinea(it) * it.cantidad }

    private fun costoDe(vp: VentaPlan): Long = vp.lineas.sumOf { costoLinea(it) * it.cantidad }

    private fun unidadesDe(vp: VentaPlan): Long = vp.lineas.sumOf { it.cantidad }

    /** Título legible del motivo de ajuste (como en la app: «Merma · cajas mojadas en el almacén»). */
    private fun motivoTitulo(motivo: String): String = when (motivo) {
        "MERMA" -> "Merma"
        "CONSUMO_INTERNO" -> "Consumo interno"
        "CORRECCION" -> "Corrección de conteo"
        "DEVOLUCION" -> "Devolución"
        else -> motivo
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
