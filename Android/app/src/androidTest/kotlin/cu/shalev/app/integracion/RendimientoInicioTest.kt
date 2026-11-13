package cu.shalev.app.integracion

import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.money.sumOfCup
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Periodo
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.validas
import cu.shalev.domain.service.Estadisticas
import cu.shalev.domain.usecase.ObtenerGraficosPeriodo
import cu.shalev.domain.usecase.ObtenerResumenGeneral
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T1.5 (F1) — Rendimiento de Inicio con **18 meses** de datos.
 *
 * El generador de datos de prueba que usaba este test (la función «Generar datos de prueba» de Ajustes) se retiró
 * de la app: ya no existe ni en `:domain` ni en `:app`. Para que la medición siga siendo posible, **este propio
 * fichero genera el histórico** que necesita: un catálogo pequeño de artículos, un turno por día (con un descanso
 * cada siete) y varias ventas al día, escritos con los repositorios REALES (Room en memoria) por los que después
 * lee Inicio. No depende de nada que ya no exista.
 *
 * Qué mide y por qué así:
 *
 * 1. **Agregación (lo que se congelaba):** `Estadisticas.serie` sobre un año de ventas, ejecutada a propósito en el
 *    hilo del test. Es el trabajo que antes ocurría en el hilo de UI porque los casos de uso no cambiaban de
 *    contexto. Umbral 250 ms: si esto se pasa, la pantalla se nota; si el teléfono lo hace en 30 ms, hay margen.
 * 2. **Carga completa con el arreglo:** `ObtenerResumenGeneral` (30 días) y `ObtenerGraficosPeriodo` (365 días)
 *    tal como los llama Inicio, ahora en `withContext(io)`. Umbral 3000 ms (generoso a propósito: esto incluye
 *    disco cifrado con SQLCipher y en un teléfono de gama baja puede tardar; el objetivo del test es que no se
 *    degrade a segundos, no ganar una carrera).
 *
 * Se ejecuta solo con `./gradlew shalevInstrumentedTests` (necesita teléfono o emulador). El CI **no** lo ejecuta
 * todavía: en cuanto haya emulador en el CI, este es el test que hay que encender primero.
 *
 * Referencia de 0.30.0 (PC de desarrollo, emulador API 35): la agregación, entre 10 y 40 ms; el resumen, ~150 ms;
 * los gráficos del año, ~400 ms. Teléfono real: pendiente (T0.6).
 */
@RunWith(AndroidJUnit4::class)
class RendimientoInicioTest {

    private val zona: ZoneId = ZoneId.of("America/Havana")

    @Test
    fun inicioConDieciochoMesesDeDatos() = runBlocking {
        val entorno = EntornoIntegracion()
        try {
            // Fecha real del teléfono: el histórico se genera hacia atrás desde ahora (hasta ayer).
            entorno.ahora = Instant.now()
            val reloj: Clock = entorno.clock

            val generadas = generarHistorico(entorno, DIAS)
            assertTrue("no se generó historial: $generadas ventas", generadas > 0)

            val ahora = reloj.now()
            val desde365 = ahora.minus(Duration.ofDays(365))
            val rango = Periodo.Rango(desde365, ahora)

            // Datos reales de la BD para las comprobaciones.
            val ventasDelAnio = entorno.ventas.entre(desde365, ahora)
            val validas = ventasDelAnio.validas()
            assertTrue("hacen falta miles de ventas para que la prueba diga algo: ${validas.size}", validas.size > 500)

            // 1) La agregación pura, en el hilo del test (lo que antes corría en Main).
            val tAgregacion = medirSync { Estadisticas.serie(validas, desde365, ahora, zona) }

            // 2) Camino real: los dos casos de uso de Inicio, ya con withContext(io).
            val obtenerResumen = ObtenerResumenGeneral(entorno.ventas, entorno.productos, entorno.insumos, entorno.servicios, reloj, Dispatchers.IO)
            val obtenerGraficos = ObtenerGraficosPeriodo(entorno.ventas, entorno.turnos, reloj, Dispatchers.IO)
            val tResumen = medir { obtenerResumen(zona) }
            val tGraficos = medir { obtenerGraficos(rango, zone = zona) }

            val informe = """
                |RendimientoInicioTest — 18 meses (${ventasDelAnio.size} ventas en el año, ${validas.size} válidas)
                |  agregación de un año (antes en el hilo de UI): ${tAgregacion} ms  (umbral 250)
                |  ObtenerResumenGeneral (30 días):               ${tResumen} ms  (umbral 3000)
                |  ObtenerGraficosPeriodo (365 días):            ${tGraficos} ms  (umbral 3000)
            """.trimMargin()
            println(informe)

            assertTrue("la agregación de un año tardó ${tAgregacion} ms (umbral 250)", tAgregacion < 250)
            assertTrue("el resumen tardó ${tResumen} ms (umbral 3000)", tResumen < 3000)
            assertTrue("los gráficos del año tardaron ${tGraficos} ms (umbral 3000)", tGraficos < 3000)
        } finally {
            entorno.cerrar()
        }
    }

    // ------------------------------------------------------------------ generador del histórico

    /**
     * Escribe [dias] días de historial con los repositorios reales: un catálogo de [CATALOGO] artículos, un turno
     * por día laborable (descansa uno de cada siete) y de 6 a 12 ventas al día con 1–3 líneas. Una de cada
     * tres ventas es por transferencia (para que también haya transacciones y clientes). Devuelve cuántas ventas
     * se registraron.
     */
    private suspend fun generarHistorico(entorno: EntornoIntegracion, dias: Int): Int {
        val base = LocalDate.ofInstant(entorno.clock.now(), zona).minusDays(dias.toLong())
        val creadoEn = base.atTime(8, 0).atZone(zona).toInstant()
        val articulos = CATALOGO.mapIndexed { i, (nombre, categoria) ->
            val id = ok(
                entorno.productos.crear(
                    Producto(
                        categoria = categoria, nombre = nombre,
                        precioCosto = Cup.ofPesos(PRECIO_COSTO + i * 5L), precioVenta = Cup.ofPesos(PRECIO_VENTA + i * 10L),
                        cantidad = EXISTENCIAS, creadoEn = creadoEn,
                    ), null,
                ),
            )
            entorno.productos.obtener(id)!!
        }

        var ventas = 0
        for (d in 0 until dias) {
            if (d % 7 == 6) continue // un día de descanso a la semana
            val fecha = base.plusDays(d.toLong())
            val apertura = fecha.atTime(8, 0).atZone(zona).toInstant()
            val cierre = fecha.atTime(20, 0).atZone(zona).toInstant()
            val vendedor = VENDEDORES[(d / 14) % VENDEDORES.size]
            val turno = ok(entorno.turnos.abrir(apertura, vendedor))
            val nVentas = 6 + d % 7
            for (n in 0 until nVentas) {
                val fechaVenta = apertura.plusSeconds((n + 1) * 3600L)
                val detalles = (0 until 1 + n % 3).map { l ->
                    val p = articulos[(d * 3 + n + l) % articulos.size]
                    detalle(p, 1L + (n + l) % 3)
                }
                val transferencia = n % 3 == 0
                val metodo = if (transferencia) MetodoPago.TRANSFERENCIA else MetodoPago.EFECTIVO
                val tx = if (transferencia) {
                    Transaccion(
                        fecha = fechaVenta,
                        importe = detalles.sumOfCup { it.subtotal },
                        numero = "BR" + d.toString().padStart(3, '0') + n.toString().padStart(2, '0'),
                        cliente = CLIENTES[(d + n) % CLIENTES.size],
                        clienteFijo = (d + n) % 2 == 0,
                    )
                } else null
                ok(entorno.ventas.registrar(Venta(turnoId = turno.id, fecha = fechaVenta, metodoPago = metodo, detalles = detalles, transaccion = tx)))
                ventas++
            }
            ok(entorno.turnos.cerrar(cierre, vendedor))
        }
        return ventas
    }

    private fun detalle(p: Producto, cantidad: Long) = DetalleVenta(
        productoId = p.id, nombre = p.nombre, categoria = p.categoria, cantidad = cantidad,
        precioBase = p.precioVenta, precioUnitario = p.precioVenta, costoUnitario = p.precioCosto,
    )

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")

    // ------------------------------------------------------------------ datos fijos del histórico

    /** Tiempo en milisegundos de [bloque]; se calienta una vez para no medir el primer acceso a disco. */
    private fun <T> medirSync(bloque: () -> T): Long {
        bloque()
        val inicio = System.nanoTime()
        bloque()
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis()
    }

    private suspend fun <T> medir(bloque: suspend () -> T): Long {
        bloque()
        val inicio = System.nanoTime()
        bloque()
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis()
    }

    companion object {
        /** 18 meses, como el histórico de referencia que usaba el generador retirado. */
        const val DIAS = 548
        const val PRECIO_COSTO = 60L
        const val PRECIO_VENTA = 100L
        /** Existencias amplias: el histórico no debe quedarse sin stock a mitad. */
        const val EXISTENCIAS = 1_000_000L

        /** Nombre → categoría del catálogo mínimo del histórico. */
        val CATALOGO = listOf(
            "Arroz" to "Granos y cereales",
            "Frijoles negros" to "Granos y cereales",
            "Aceite de oliva" to "Aceites y grasas",
            "Azúcar" to "Panadería y dulcería",
            "Leche entera" to "Lácteos",
            "Queso blanco" to "Lácteos",
            "Refresco de cola" to "Bebidas",
            "Jugo de mango" to "Bebidas",
            "Detergente en polvo" to "Limpieza del hogar",
            "Jabón de baño" to "Aseo personal",
            "Café molido" to "Alimentos",
            "Café en grano" to "Alimentos",
        )

        /** Se turnan cada 14 días, como los vendedores de la app. */
        val VENDEDORES = listOf("Ana Pérez", "Luis Fernández")

        val CLIENTES = listOf(
            DatosCliente("Ana Díaz Rey", "90020212345", "+5353000000"),
            DatosCliente("Luis Pérez Soto", "89051523456", "+5352111111"),
            DatosCliente("Marta Cruz Vega", "88031045678", "+5353222222"),
        )
    }
}
