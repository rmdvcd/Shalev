package cu.shalev.data.dto

import cu.shalev.domain.model.Anulacion
import cu.shalev.domain.model.MovimientoCaja
import cu.shalev.domain.model.TipoMovimientoCaja
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.domain.model.Servicio
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
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
import cu.shalev.data.mapper.instant
import cu.shalev.data.mapper.ms
import java.time.LocalDate
import cu.shalev.domain.model.Modulo

/**
 * Dominio ↔ DTO de respaldo. Al importar, un enum desconocido o una fecha ilegible es un archivo inválido
 * (se lanza [IllegalArgumentException] → FormatoInvalido): mejor rechazar que restaurar datos alterados.
 */
private inline fun <reified E : Enum<E>> enumStrict(v: String): E =
    enumValues<E>().firstOrNull { it.name == v } ?: throw IllegalArgumentException("valor desconocido: $v")
private fun fecha(s: String): LocalDate = runCatching { LocalDate.parse(s) }.getOrElse { throw IllegalArgumentException("fecha inválida") }

fun Producto.toDto() = ProductoDto(
    id, categoria, nombre, descripcion, fotoUri, fechaCaducidad?.toString(), precioCosto.centavos, precioVenta.centavos,
    cantidad, nivelBajo, nivelCritico, creadoEn.ms(), actualizadoEn.ms(), eliminado,
    costoAdicionalCent = costoAdicional.centavos,
)
fun ProductoDto.toDomain() = Producto(
    id, categoria, nombre, descripcion, fotoUri, fechaCaducidad?.let(::fecha), Cup(precioCostoCent), Cup(precioVentaCent),
    cantidad, nivelBajo, nivelCritico, creadoEn.instant(), actualizadoEn.instant(), eliminado,
    costoAdicional = Cup(costoAdicionalCent),
)

fun Receta.toDto() = RecetaDto(productoId, lineas.map { RecetaLineaDto(it.insumoId, it.cantidad.milesimas) })
fun RecetaDto.toDomain() = Receta(productoId, lineas.map { RecetaLinea(it.insumoId, Cantidad(it.cantidadMil)) })

fun Insumo.toDto() = InsumoDto(
    id, nombre, unidad.name, precio.centavos, cantidad.milesimas, nivelBajo?.milesimas, nivelCritico?.milesimas,
    creadoEn.ms(), actualizadoEn.ms(), precioVenta?.centavos,
)
fun InsumoDto.toDomain() = Insumo(
    id, nombre, enumStrict<UnidadMedida>(unidad), Cup(precioCent), Cantidad(cantidadMil),
    nivelBajoMil?.let(::Cantidad), nivelCriticoMil?.let(::Cantidad), creadoEn.instant(), actualizadoEn = actualizadoEn.instant(),
    precioVenta = precioVentaCent?.let(::Cup),
)

fun Servicio.toDto(insumos: List<RecetaLinea>) = ServicioDto(
    id, nombre, tipo, importe.centavos, descripcion, fotoUri, creadoEn.ms(), actualizadoEn.ms(), eliminado,
    insumos.map { RecetaLineaDto(it.insumoId, it.cantidad.milesimas) },
    costoAdicionalCent = costoAdicional.centavos,
)
fun ServicioDto.toDomain() = Servicio(
    id = id, nombre = nombre, tipo = tipo, importe = Cup(importeCent), descripcion = descripcion, fotoUri = fotoUri,
    creadoEn = creadoEn.instant(), actualizadoEn = actualizadoEn.instant(), eliminado = eliminado,
    costoAdicional = Cup(costoAdicionalCent),
) to insumos.map { RecetaLinea(it.insumoId, Cantidad(it.cantidadMil)) }

fun Turno.toDto() = TurnoDto(
    id, abiertoEn.ms(), cerradoEn?.let { it.ms() },
    resumen?.let {
        ResumenTurnoDto(it.numVentas, it.unidades, it.total.centavos, it.totalEfectivo.centavos,
            it.totalTransferencia.centavos, it.costo.centavos, it.numMovimientos,
            it.ventasEfectivo, it.ventasTransferencia, it.movimientosProducto, it.movimientosInsumo,
            it.entradasCaja.centavos, it.salidasCaja.centavos, it.numAnuladas)
    },
    abiertoPor.ifEmpty { null }, cerradoPor, empleadoId,
    fondoCent = fondo?.centavos, contadoCent = contado?.centavos,
    conteoCierre = conteoCierre?.toDto(),
)
fun TurnoDto.toDomain() = Turno(
    id, abiertoEn.instant(), cerradoEn?.let { it.instant() },
    resumen?.let {
        ResumenTurno(it.numVentas, it.unidades, Cup(it.totalCent), Cup(it.efectivoCent),
            Cup(it.transferenciaCent), Cup(it.costoCent), it.numMovimientos,
            ventasEfectivo = it.ventasEfectivo ?: 0, ventasTransferencia = it.ventasTransferencia ?: 0,
            movimientosProducto = it.movimientosProducto ?: it.numMovimientos, movimientosInsumo = it.movimientosInsumo ?: 0,
            entradasCaja = Cup(it.entradasCent ?: 0), salidasCaja = Cup(it.salidasCent ?: 0), numAnuladas = it.numAnuladas ?: 0)
    },
    abiertoPor.orEmpty(), cerradoPor, empleadoId,
    fondo = fondoCent?.let(::Cup), contado = contadoCent?.let(::Cup),
    conteoCierre = conteoCierre?.toDomain(),
)

fun Venta.toDto() = VentaDto(
    id, turnoId, fecha.ms(), metodoPago.name,
    detalles.map {
        DetalleVentaDto(it.id, it.productoId, it.nombre, it.categoria, it.cantidad,
            it.precioBase.centavos, it.precioUnitario.centavos, it.costoUnitario.centavos, it.clase.name)
    },
    transaccion?.let {
        TransaccionDto(it.id, it.fecha.ms(), it.importe.centavos, it.numero, it.cliente.nombreApellidos,
            it.cliente.ci, it.cliente.telefono, it.tarjetaCobro, it.telefonoCobro, clienteFijo = it.clienteFijo, referenciaDuplicada = it.referenciaDuplicada)
    },
    anuladaEn = anulacion?.en?.let { it.ms() }, motivoAnulacion = anulacion?.motivo, anuladaPor = anulacion?.por,
    corrigeVentaId = corrigeVentaId,
)
fun VentaDto.toDomain() = Venta(
    id, turnoId, fecha.instant(), enumStrict<MetodoPago>(metodoPago),
    detalles.map {
        DetalleVenta(it.id, id, it.productoId, it.nombre, it.categoria, it.cantidad,
            Cup(it.precioBaseCent), Cup(it.precioUnitarioCent), Cup(it.costoUnitarioCent), enumStrict<ClaseArticulo>(it.clase))
    },
    transaccion?.let {
        Transaccion(it.id, id, it.fecha.instant(), Cup(it.importeCent), it.numero,
            DatosCliente(it.clienteNombre, it.clienteCi, it.clienteTelefono), it.tarjetaCobro, it.telefonoCobro,
            clienteFijo = it.clienteFijo, referenciaDuplicada = it.referenciaDuplicada)
    },
    anulacion = anuladaEn?.let { Anulacion(it.instant(), motivoAnulacion.orEmpty(), anuladaPor.orEmpty()) },
    corrigeVentaId = corrigeVentaId,
)

fun MovimientoCaja.toDto() = MovimientoCajaDto(id, turnoId, fecha.ms(), tipo.name, importe.centavos, motivo, hechoPor, uuid)
fun MovimientoCajaDto.toDomain() = MovimientoCaja(
    id, turnoId, fecha.instant(), enumStrict<TipoMovimientoCaja>(tipo), Cup(importeCent), motivo, hechoPor, uuid,
)

fun MovimientoInventario.toDto() = MovimientoDto(
    id, fecha.ms(), tipo.name, entidad.name, entidadId, nombre, delta, existenciaResultante, turnoId, ventaId, nota, hechoPor,
)
fun MovimientoDto.toDomain() = MovimientoInventario(
    id, fecha.instant(), enumStrict<TipoMovimiento>(tipo), enumStrict<TipoEntidad>(entidad), entidadId, nombre,
    delta, existencia, turnoId, ventaId, nota, hechoPor,
)

fun Perfil.toDto() = PerfilDto(
    nombre, apellidos, ci, tarjetas.map { TarjetaDto(it.id, it.numero, it.alias) },
    telefonos.map { TelefonoDto(it.id, it.numero, it.alias) }, pagoTarjetaId, pagoTelefonoId,
)
fun PerfilDto.toDomain() = Perfil(
    nombre, apellidos, ci, tarjetas.map { TarjetaBancaria(it.id, it.numero, it.alias) },
    telefonos.map { Telefono(it.id, it.numero, it.alias) }, pagoTarjetaId, pagoTelefonoId,
)

fun PreajustePrecios.toDto() = PreajusteDto(
    id = id, nombre = nombre, puntosBasicos = puntosBasicos, productoIds = productoIds.sorted(), servicioIds = servicioIds.sorted(),
    metodoPago = metodoPago?.name, importeMinimoCent = null, activo = activo,
)
fun PreajusteDto.toDomain() = PreajustePrecios(
    id = id, nombre = nombre, puntosBasicos = puntosBasicos, productoIds = productoIds.toSet(), servicioIds = servicioIds.toSet(),
    metodoPago = metodoPago?.let { enumStrict<MetodoPago>(it) }, importeMinimo = null, activo = activo,
)

fun Preferencias.toDto() = PreferenciasDto(
    niveles.productoBajo, niveles.productoCritico, niveles.insumoBajo.milesimas, niveles.insumoCritico.milesimas,
    modulos = modulos.map { it.name }.sorted(),
    empleadosPrevistos = empleadosPrevistos,
)

/** Tolerante (DataStore / respaldo): valores fuera de rango se acotan; `consultasEnLinea` de versiones anteriores se ignora. */
fun PreferenciasDto.toDomain(onboardingCompletado: Boolean = false) = Preferencias(
    niveles = NivelesMinimos(
        productoBajo = productoBajo.coerceAtLeast(0), productoCritico = productoCritico.coerceAtLeast(0),
        insumoBajo = Cantidad(insumoBajoMil.coerceAtLeast(0)), insumoCritico = Cantidad(insumoCriticoMil.coerceAtLeast(0)),
    ),
    onboardingCompletado = onboardingCompletado,
    modulos = Modulo.deNombres(modulos),
    empleadosPrevistos = empleadosPrevistos.coerceIn(0, cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX),
)
