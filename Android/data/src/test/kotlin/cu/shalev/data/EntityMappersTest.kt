package cu.shalev.data

import cu.shalev.data.db.entity.PerfilEntity
import cu.shalev.data.db.entity.TarjetaEntity
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.db.entity.VentaCompleta
import cu.shalev.data.mapper.perfilDe
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.mapper.toEntity
import cu.shalev.domain.model.Turno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntityMappersTest {

    @Test fun `producto e insumo ida y vuelta`() {
        assertEquals(PRODUCTO, PRODUCTO.toEntity().toDomain())
        assertEquals(INSUMO, INSUMO.toEntity().toDomain())
    }

    @Test fun `venta completa ida y vuelta con totales desnormalizados`() {
        val e = VENTA.toEntity()
        assertEquals(31900L, e.totalCent)
        assertEquals(9100L, e.costoCent)
        assertEquals(2L, e.unidades)
        val completa = VentaCompleta(e, VENTA.detalles.map { it.toEntity(e.id) }, VENTA.transaccion!!.toEntity(e.id))
        assertEquals(VENTA, completa.toDomain())
    }

    @Test fun `turno abierto no tiene resumen`() {
        val e = TurnoEntity(1, T.toEpochMilli(), null, null, null, null, null, null, null, null)
        assertEquals(Turno(1, T), e.toDomain())
        assertNull(Turno(1, T).toEntity().numVentas)
    }

    @Test fun `enum corrupto en BD cae a valor seguro`() {
        val e = INSUMO.toEntity().copy(unidad = "PULGADA")
        assertEquals(cu.shalev.domain.model.UnidadMedida.UNIDAD, e.toDomain().unidad)
    }

    @Test fun `perfil vacío si no hay fila`() {
        val p = perfilDe(null, emptyList(), emptyList())
        assertEquals(true, p.vacio)
        val q = perfilDe(PerfilEntity(1, "Ana", "Pérez", "85010112345", 9, null), listOf(TarjetaEntity(9, "9227069995328054", null)), emptyList())
        assertEquals("•••• 8054", q.tarjetaPago!!.enmascarado)
    }
}
