package cu.shalev.data.repository

import android.content.Context
import androidx.room.withTransaction
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.abortar
import cu.shalev.data.db.dao.idActivo
import cu.shalev.data.db.dao.localActivo
import cu.shalev.data.db.entity.FichaCostoEntity
import cu.shalev.data.db.tx
import cu.shalev.data.dto.FichaCostoDto
import cu.shalev.data.dto.toDomain
import cu.shalev.data.dto.toDto
import cu.shalev.data.local.ShalevJson
import cu.shalev.data.sync.ConfigSync
import cu.shalev.domain.fichacosto.CategoriaCosto
import cu.shalev.domain.fichacosto.DestinoFicha
import cu.shalev.domain.fichacosto.FichaCostoGuardada
import cu.shalev.domain.fichacosto.FichaCostoRepository
import cu.shalev.domain.fichacosto.MonedaCosto
import cu.shalev.domain.fichacosto.ReglasFichaCosto
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.Identificacion
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.usecase.GuardarInsumo
import cu.shalev.domain.usecase.GuardarProducto
import cu.shalev.domain.usecase.GuardarServicio
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

@Singleton
class FichaCostoRepositoryImpl @Inject constructor(
    private val db: ShalevDatabase,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val config: ConfigSync,
    private val insumos: InsumoRepositoryImpl,
    private val productos: ProductoRepositoryImpl,
    private val servicios: ServicioRepositoryImpl,
) : FichaCostoRepository {
    override fun observar() = flow {
        migrarFichasAntiguas(context, db, clock)
        emitAll(db.gestionDao().observarFichas().map { xs -> xs.map { it.aFicha() } })
    }

    override suspend fun guardar(ficha: FichaCostoGuardada): AppResult<Unit> = db.tx {
        if (!ReglasFichaCosto.validar(ficha)) abortar(AppError.Validacion("ficha"))
        val localId = db.almacenLocalDao().idActivo()
        val previa = db.gestionDao().ficha(ficha.id)
        if (previa != null && previa.localId != localId) abortar(AppError.SinPermiso)
        db.gestionDao().guardarFicha(ficha.aEntity(localId, clock.now().toEpochMilli()))
    }

    override suspend fun eliminar(id: Long): AppResult<Unit> = db.tx {
        if (db.gestionDao().borrarFicha(id) == 0) abortar(AppError.NoEncontrado)
    }

    override suspend fun exportar(ficha: FichaCostoGuardada, destino: DestinoFicha): AppResult<Long> {
        // No se simula una transacción repartida entre dos teléfonos: la ficha se exporta en la principal.
        if (config.tipo.first() != TipoApp.PRINCIPAL) return AppResult.Err(AppError.SinPermiso)
        return db.tx {
            if (!ReglasFichaCosto.validar(ficha)) abortar(AppError.Validacion("ficha"))
            val tasa = cu.shalev.domain.fichacosto.CalculoFichaCosto.numero(ficha.tasaCupPorUsd)
            val precio = ReglasFichaCosto.precio(ficha) ?: abortar(AppError.Validacion("tasa"))
            var adicional = Cup.ZERO
            val lineas = mutableMapOf<Long, Long>()
            val candidatos = insumos.todos().toMutableList() // solo el local de esta operación
            for (p in ficha.partidas) {
                val importe = ReglasFichaCosto.importeCup(p, tasa) ?: abortar(AppError.Validacion("tasa"))
                if (p.categoria != CategoriaCosto.MATERIAS) { adicional += importe; continue }
                val unidad = ReglasFichaCosto.unidad(p.unidad) ?: abortar(AppError.Validacion("unidad"))
                val clave = Identificacion.clave(p.descripcion)
                val coincidencias = candidatos.filter { Identificacion.clave(it.nombre) == clave }
                if (coincidencias.size > 1) abortar(AppError.Duplicado("insumo"))
                val i = coincidencias.singleOrNull() ?: run {
                    val unitario = if (p.moneda == MonedaCosto.CUP) p.costoUnitario
                        else p.costoUnitario.multiply(tasa ?: abortar(AppError.Validacion("tasa")))
                    val nuevo = Insumo(nombre = p.descripcion.trim(), unidad = unidad, precio = Cup.of(unitario), cantidad = Cantidad.ZERO, creadoEn = clock.now())
                    val id = GuardarInsumo(insumos, productos, clock)(nuevo).valorOAbortar()
                    nuevo.copy(id = id).also { candidatos += it }
                }
                val cantidad = ReglasFichaCosto.convertir(p.cantidad, unidad, i.unidad) ?: abortar(AppError.Validacion("unidad"))
                lineas[i.id] = Math.addExact(lineas[i.id] ?: 0, cantidad.milesimas)
            }
            val receta = lineas.map { (id, mil) -> RecetaLinea(id, Cantidad(mil)) }
            when (destino) {
                DestinoFicha.INVENTARIO -> {
                    if (receta.isEmpty()) abortar(AppError.Validacion("receta", AppError.Regla.REQUERIDO))
                    GuardarProducto(productos, insumos, clock)(Producto(
                        categoria = Categorias.ELABORADO, nombre = ficha.nombre.trim(), precioCosto = Cup.ZERO,
                        precioVenta = precio, cantidad = 0, creadoEn = clock.now(), costoAdicional = adicional,
                    ), Receta(0, receta)).valorOAbortar()
                }
                DestinoFicha.SERVICIO -> GuardarServicio(servicios, clock)(Servicio(
                    nombre = ficha.nombre.trim(), tipo = "General", importe = precio, creadoEn = clock.now(), costoAdicional = adicional,
                ), receta).valorOAbortar()
            }
        }
    }
}

private fun <T> AppResult<T>.valorOAbortar(): T = when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> abortar(error)
}

internal fun FichaCostoGuardada.aEntity(localId: String, ahora: Long) = FichaCostoEntity(
    id, localId, nombre, margen, unidadMargen.name, tasaCupPorUsd,
    ShalevJson.encodeToString(FichaCostoDto.serializer(), toDto()), ahora,
)
internal fun FichaCostoEntity.aFicha(): FichaCostoGuardada = ShalevJson.decodeFromString(FichaCostoDto.serializer(), partidasJson).toDomain()

/** Una sola migración, idempotente y sin descartar el JSON original si hay un fallo. También se llama al respaldar. */
internal suspend fun migrarFichasAntiguas(context: Context, db: ShalevDatabase, clock: Clock) {
    val prefs = context.getSharedPreferences("fichas_costo", Context.MODE_PRIVATE)
    val texto = prefs.getString("lista", null) ?: return
    val fichas = ShalevJson.decodeFromString(ListSerializer(FichaCostoDto.serializer()), texto).map { it.toDomain() }
    db.withTransaction {
        val local = db.almacenLocalDao().idActivo()
        for (f in fichas) if (db.gestionDao().ficha(f.id) == null) {
            db.gestionDao().guardarFicha(f.aEntity(local, clock.now().toEpochMilli()))
        }
    }
    check(prefs.edit().remove("lista").commit()) { "No se pudo completar la migración de fichas." }
}
