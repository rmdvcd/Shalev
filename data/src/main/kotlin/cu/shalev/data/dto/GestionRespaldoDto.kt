package cu.shalev.data.dto

import cu.shalev.data.db.entity.FichaCostoEntity
import cu.shalev.data.db.entity.LoteConsumoEntity
import cu.shalev.data.db.entity.LoteInventarioEntity
import cu.shalev.data.db.entity.MovimientoRevisionEntity
import cu.shalev.data.db.entity.RecepcionEntity
import cu.shalev.data.local.ShalevJson
import kotlinx.serialization.Serializable

@Serializable data class FichaLocalDto(val ficha: FichaCostoDto, val localId: String, val actualizadoEn: Long)
@Serializable data class RecepcionDto(val uuid: String, val localId: String, val fecha: Long, val proveedor: String, val comprobante: String, val motivo: String, val hechoPor: String, val totalCent: Long)
@Serializable data class LoteInventarioDto(val id: String, val recepcionId: String, val localId: String, val entidad: String, val entidadId: Long, val nombre: String, val unidad: String, val codigo: String, val cantidadInicialMil: Long, val disponibleMil: Long, val costoUnitarioCent: Long, val caducidad: Long? = null)
@Serializable data class ConsumoLoteDto(val id: Long, val ventaId: Long?, val loteId: String, val cantidadMil: Long, val movimientoId: Long? = null)
@Serializable data class RevisionMovimientoDto(val id: Long, val movimientoId: Long, val fecha: Long, val deltaAnterior: Long, val deltaNuevo: Long, val existenciaAnterior: Long, val existenciaNueva: Long, val hechoPor: String, val motivo: String, val local: String)
@Serializable data class FotoRespaldoDto(val nombre: String, val datos: String, val sha256: String)

fun FichaCostoEntity.toDto() = FichaLocalDto(ShalevJson.decodeFromString(FichaCostoDto.serializer(), partidasJson), localId, actualizadoEn)
fun FichaLocalDto.toEntity() = FichaCostoEntity(ficha.id, localId, ficha.nombre, ficha.margen, ficha.unidadMargen, ficha.tasa, ShalevJson.encodeToString(FichaCostoDto.serializer(), ficha), actualizadoEn)
fun RecepcionEntity.toDto() = RecepcionDto(uuid, localId, fecha, proveedor, comprobante, motivo, hechoPor, totalCent)
fun RecepcionDto.toEntity() = RecepcionEntity(uuid, localId, fecha, proveedor, comprobante, motivo, hechoPor, totalCent)
fun LoteInventarioEntity.toDto() = LoteInventarioDto(id, recepcionId, localId, entidad, entidadId, nombre, unidad, codigo, cantidadInicialMil, disponibleMil, costoUnitarioCent, caducidad)
fun LoteInventarioDto.toEntity() = LoteInventarioEntity(id, recepcionId, localId, entidad, entidadId, nombre, unidad, codigo, cantidadInicialMil, disponibleMil, costoUnitarioCent, caducidad)
fun LoteConsumoEntity.toDto() = ConsumoLoteDto(id, ventaId, loteId, cantidadMil, movimientoId)
fun ConsumoLoteDto.toEntity() = LoteConsumoEntity(id, ventaId, loteId, cantidadMil, movimientoId)
fun MovimientoRevisionEntity.toDto() = RevisionMovimientoDto(id, movimientoId, fecha, deltaAnterior, deltaNuevo, existenciaAnterior, existenciaNueva, hechoPor, motivo, local)
fun RevisionMovimientoDto.toEntity() = MovimientoRevisionEntity(id, movimientoId, fecha, deltaAnterior, deltaNuevo, existenciaAnterior, existenciaNueva, hechoPor, motivo, local)
