package cu.shalev.data

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.data.dto.PreferenciasDto
import cu.shalev.data.dto.RespaldoDto
import cu.shalev.data.dto.TurnoDto
import cu.shalev.data.dto.VentaDto
import cu.shalev.data.dto.toDomain
import cu.shalev.data.dto.toDto
import cu.shalev.data.local.ShalevJson
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.model.Venta
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

val T: Instant = Instant.parse("2026-09-01T12:00:00Z")

val PRODUCTO = Producto(7, "Bebidas", "Refresco ñandú", "lata 355 ml", "content://x", LocalDate.of(2026, 12, 31),
    Cup(4550), Cup(14500), 12, 5, 1, T, T.plusSeconds(60), false)
val INSUMO = Insumo(3, "Harina", UnidadMedida.KILOGRAMO, Cup(12000), Cantidad(2750), Cantidad(1000), null, T, T)
val VENTA = Venta(
    id = 11, turnoId = 2, fecha = T, metodoPago = MetodoPago.TRANSFERENCIA,
    detalles = listOf(DetalleVenta(21, 11, 7, "Refresco ñandú", "Bebidas", 2, Cup(14500), Cup(15950), Cup(4550))),
    transaccion = Transaccion(31, 11, T, Cup(31900), "KW12345", DatosCliente("Ana Pérez", "85010112345", "+5351234567"), "9227069995328054", null),
)

class DtoRoundTripTest {

    private inline fun <reified A> json(a: A, ser: kotlinx.serialization.KSerializer<A>): A =
        ShalevJson.decodeFromString(ser, ShalevJson.encodeToString(ser, a))

    @Test fun `respaldo completo ida y vuelta por JSON conserva todo`() {
        val perfil = Perfil("Ana", "Pérez", "85010112345", listOf(TarjetaBancaria(1, "9227069995328054", "BPA")),
            listOf(Telefono(2, "+5351234567")), 1, 2)
        val pre = PreajustePrecios(4, "Transferencia +10 %", 1000, setOf(7L), MetodoPago.TRANSFERENCIA, Cup(100000), true)
        val turno = Turno(2, T, T.plusSeconds(3600), ResumenTurno(1, 2, Cup(31900), Cup.ZERO, Cup(31900), Cup(9100), 1))
        val mov = MovimientoInventario(5, T, TipoMovimiento.VENTA, TipoEntidad.PRODUCTO, 7, "Refresco ñandú", -2, 10, 2, 11, null)
        val receta = Receta(7, listOf(RecetaLinea(3, Cantidad(250))))
        val prefs = Preferencias(NivelesMinimos(6, 2, Cantidad(4500), Cantidad(500)))

        val dto = RespaldoDto(
            creadoEn = T.toEpochMilli(),
            productos = listOf(PRODUCTO.toDto()), recetas = listOf(receta.toDto()), insumos = listOf(INSUMO.toDto()),
            turnos = listOf(turno.toDto()), ventas = listOf(VENTA.toDto()), movimientos = listOf(mov.toDto()),
            perfil = perfil.toDto(), preajustes = listOf(pre.toDto()), preferencias = prefs.toDto(),
        )
        val back = json(dto, RespaldoDto.serializer())
        assertEquals(dto, back)
        assertEquals(PRODUCTO, back.productos.single().toDomain())
        assertEquals(INSUMO, back.insumos.single().toDomain())
        assertEquals(VENTA, back.ventas.single().toDomain())
        assertEquals(turno, back.turnos.single().toDomain())
        assertEquals(mov, back.movimientos.single().toDomain())
        assertEquals(receta, back.recetas.single().toDomain())
        assertEquals(perfil, back.perfil!!.toDomain())
        assertEquals(pre.copy(importeMinimo = null), back.preajustes.single().toDomain())
        assertEquals(prefs, back.preferencias!!.toDomain())
    }

    @Test fun `campos desconocidos se ignoran (compatibilidad hacia delante)`() {
        // También los campos retirados en v3 (tipo, tasas, caducidades) que traiga un JSON antiguo.
        val raw = """{"formato":"spvi-respaldo","version":3,"tipo":"COMPLETO","tasas":[],"caducidades":[],"creadoEn":1,"campoFuturo":{"a":1}}"""
        val dto = ShalevJson.decodeFromString(RespaldoDto.serializer(), raw)
        assertEquals(3, dto.version)
        assertEquals(emptyList<Any>(), dto.productos)
    }

    @Test fun `enum desconocido en respaldo es error de formato`() {
        val mala = VENTA.toDto().copy(metodoPago = "BITCOIN")
        assertThrows(IllegalArgumentException::class.java) { mala.toDomain() }
    }

    @Test fun `JSON roto lanza SerializationException`() {
        assertThrows(SerializationException::class.java) { ShalevJson.decodeFromString(RespaldoDto.serializer(), "{\"tipo\":") }
    }

    @Test fun `preferencias fuera de rango se acotan`() {
        val p = PreferenciasDto(productoBajo = -3).toDomain()
        assertEquals(0L, p.niveles.productoBajo)
    }

    @Test fun `campos antiguos de preferencias se ignoran al leer`() {
        // `consultasEnLinea` era del escáner en línea (retirado): un JSON viejo con ese campo se importa igual.
        val json = """{"productoBajo":6,"consultasEnLinea":"DENEGADO","diasAvisoCaducidad":10,"timeoutRedSegundos":12,"tema":"OSCURO"}"""
        assertEquals(6L, ShalevJson.decodeFromString(PreferenciasDto.serializer(), json).toDomain().niveles.productoBajo)
    }

    @Test fun `preferencias por defecto coinciden con Shalev_txt`() {
        assertEquals(Preferencias(), PreferenciasDto().toDomain())
    }

    @Test fun `marcas de sincronizacion del respaldo van y vuelven y faltan en respaldos anteriores`() {
        val t = TurnoDto(id = 2, abiertoEn = 1, empleadoId = 4, uuid = "t-uuid", sincronizado = true)
        assertEquals(t, json(t, TurnoDto.serializer()))
        val v = VENTA.toDto().copy(uuid = "v-uuid", empleadoId = 4, sincronizado = true)
        assertEquals(v, json(v, VentaDto.serializer()))
        // Respaldo 0.18.x: sin los campos nuevos → null/false (la venta queda como de la principal, como antes).
        val viejo = ShalevJson.decodeFromString(TurnoDto.serializer(), """{"id":2,"abiertoEn":1}""")
        assertEquals(null, viejo.uuid); assertEquals(false, viejo.sincronizado)
        val vieja = ShalevJson.decodeFromString(VentaDto.serializer(), ShalevJson.encodeToString(VentaDto.serializer(), VENTA.toDto()))
        assertEquals(null, vieja.uuid); assertEquals(null, vieja.empleadoId)
    }
}
