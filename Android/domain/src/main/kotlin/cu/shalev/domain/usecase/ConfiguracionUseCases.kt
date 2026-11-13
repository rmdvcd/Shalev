package cu.shalev.domain.usecase

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.result.toResult
import cu.shalev.core.validation.Phone
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.PreciosRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.validation.Validadores
import javax.inject.Inject
import kotlinx.coroutines.flow.first

// ---------------- Perfil ----------------

/**
 * Normaliza (trim, CI en mayúsculas, cuentas solo dígitos, teléfonos E.164), valida y guarda.
 * Una selección de Pago electrónico que apunte a un elemento borrado se anula.
 */
class GuardarPerfil @Inject constructor(private val repo: PerfilRepository) {
    suspend operator fun invoke(perfil: Perfil): AppResult<Unit> {
        val tarjetas = perfil.tarjetas.map { t ->
            t.copy(numero = Validadores.normalizarCuenta(t.numero) ?: return AppResult.Err(AppError.Validacion("tarjetas")), alias = t.alias?.trim()?.ifEmpty { null })
        }
        val telefonos = perfil.telefonos.map { t ->
            t.copy(numero = Phone.normalize(t.numero) ?: return AppResult.Err(AppError.Validacion("telefonos")), alias = t.alias?.trim()?.ifEmpty { null })
        }
        val p = perfil.copy(
            nombre = perfil.nombre.trim(),
            apellidos = perfil.apellidos.trim(),
            ci = perfil.ci.trim().uppercase(),
            tarjetas = tarjetas,
            telefonos = telefonos,
            pagoTarjetaId = perfil.pagoTarjetaId?.takeIf { id -> id != 0L && tarjetas.any { it.id == id } },
            pagoTelefonoId = perfil.pagoTelefonoId?.takeIf { id -> id != 0L && telefonos.any { it.id == id } },
        )
        Validadores.perfil(p).toResult().let { if (it is AppResult.Err) return it }
        repo.guardar(p)
        return AppResult.Ok(Unit)
    }
}

class AgregarTarjeta @Inject constructor(private val repo: PerfilRepository, private val guardar: GuardarPerfil) {
    suspend operator fun invoke(numero: String, alias: String? = null): AppResult<Unit> {
        val p = repo.perfil.first()
        return guardar(p.copy(tarjetas = p.tarjetas + TarjetaBancaria(numero = numero, alias = alias)))
    }
}

class AgregarTelefono @Inject constructor(private val repo: PerfilRepository, private val guardar: GuardarPerfil) {
    suspend operator fun invoke(numero: String, alias: String? = null): AppResult<Unit> {
        val p = repo.perfil.first()
        return guardar(p.copy(telefonos = p.telefonos + Telefono(numero = numero, alias = alias)))
    }
}

class EliminarTarjeta @Inject constructor(private val repo: PerfilRepository, private val guardar: GuardarPerfil) {
    suspend operator fun invoke(id: Long): AppResult<Unit> {
        val p = repo.perfil.first()
        return guardar(p.copy(tarjetas = p.tarjetas.filterNot { it.id == id }))
    }
}

class EliminarTelefono @Inject constructor(private val repo: PerfilRepository, private val guardar: GuardarPerfil) {
    suspend operator fun invoke(id: Long): AppResult<Unit> {
        val p = repo.perfil.first()
        return guardar(p.copy(telefonos = p.telefonos.filterNot { it.id == id }))
    }
}

/** "Pago electrónico" de Inicio: teléfono de confirmación + tarjeta/cuenta que reciben transferencias. */
class SeleccionarPagoElectronico @Inject constructor(private val repo: PerfilRepository, private val guardar: GuardarPerfil) {
    suspend operator fun invoke(tarjetaId: Long?, telefonoId: Long?): AppResult<Unit> {
        val p = repo.perfil.first()
        if (tarjetaId != null && p.tarjetas.none { it.id == tarjetaId }) return AppResult.Err(AppError.NoEncontrado)
        if (telefonoId != null && p.telefonos.none { it.id == telefonoId }) return AppResult.Err(AppError.NoEncontrado)
        return guardar(p.copy(pagoTarjetaId = tarjetaId, pagoTelefonoId = telefonoId))
    }
}

/**
 * "Pago electrónico" de Inicio cuando el usuario ESCRIBE un teléfono y/o una cuenta: se normalizan, se
 * añaden a las listas del Perfil si aún no estaban (sin duplicar) y quedan seleccionados. Un campo
 * vacío/null conserva la selección actual de ese tipo.
 */
class IntroducirPagoElectronico @Inject constructor(private val repo: PerfilRepository, private val guardar: GuardarPerfil) {
    suspend operator fun invoke(telefono: String?, tarjeta: String?): AppResult<Unit> {
        val tel = telefono?.takeIf { it.isNotBlank() }?.let {
            Phone.normalize(it) ?: return AppResult.Err(AppError.Validacion("telefonos"))
        }
        val cta = tarjeta?.takeIf { it.isNotBlank() }?.let {
            Validadores.normalizarCuenta(it) ?: return AppResult.Err(AppError.Validacion("tarjetas"))
        }
        var p = repo.perfil.first()
        val ampliado = p.copy(
            telefonos = if (tel != null && p.telefonos.none { it.numero == tel }) p.telefonos + Telefono(numero = tel) else p.telefonos,
            tarjetas = if (cta != null && p.tarjetas.none { it.numero == cta }) p.tarjetas + TarjetaBancaria(numero = cta) else p.tarjetas,
        )
        if (ampliado != p) {
            guardar(ampliado).let { if (it is AppResult.Err) return it }
            p = repo.perfil.first() // los nuevos ya tienen id
        }
        return guardar(
            p.copy(
                pagoTelefonoId = tel?.let { n -> p.telefonos.first { it.numero == n }.id } ?: p.pagoTelefonoId,
                pagoTarjetaId = cta?.let { n -> p.tarjetas.first { it.numero == n }.id } ?: p.pagoTarjetaId,
            ),
        )
    }
}

/** Edita número y alias de una tarjeta/cuenta de la lista de Ajustes. Un número repetido es Duplicado. */
class EditarTarjeta @Inject constructor(private val repo: PerfilRepository, private val guardar: GuardarPerfil) {
    suspend operator fun invoke(id: Long, numero: String, alias: String?): AppResult<Unit> {
        val p = repo.perfil.first()
        if (p.tarjetas.none { it.id == id }) return AppResult.Err(AppError.NoEncontrado)
        val n = Validadores.normalizarCuenta(numero) ?: return AppResult.Err(AppError.Validacion("tarjetas"))
        if (p.tarjetas.any { it.id != id && it.numero == n }) return AppResult.Err(AppError.Duplicado("tarjetas"))
        return guardar(p.copy(tarjetas = p.tarjetas.map { if (it.id == id) it.copy(numero = n, alias = alias) else it }))
    }
}

/** Edita número y alias de un teléfono de la lista de Ajustes. Un número repetido es Duplicado. */
class EditarTelefono @Inject constructor(private val repo: PerfilRepository, private val guardar: GuardarPerfil) {
    suspend operator fun invoke(id: Long, numero: String, alias: String?): AppResult<Unit> {
        val p = repo.perfil.first()
        if (p.telefonos.none { it.id == id }) return AppResult.Err(AppError.NoEncontrado)
        val n = Phone.normalize(numero) ?: return AppResult.Err(AppError.Validacion("telefonos"))
        if (p.telefonos.any { it.id != id && it.numero == n }) return AppResult.Err(AppError.Duplicado("telefonos"))
        return guardar(p.copy(telefonos = p.telefonos.map { if (it.id == id) it.copy(numero = n, alias = alias) else it }))
    }
}

// ---------------- Precios ----------------

class GuardarPreajuste @Inject constructor(private val repo: PreciosRepository) {
    suspend operator fun invoke(p: PreajustePrecios): AppResult<Long> {
        val n = p.copy(nombre = p.nombre.trim())
        Validadores.preajuste(n).toResult().let { if (it is AppResult.Err) return it }
        return repo.guardarPreajuste(n)
    }
}

/** Interruptor de la lista de Precios: activa o pausa un preajuste sin borrarlo. */
class ActivarPreajuste @Inject constructor(private val repo: PreciosRepository) {
    suspend operator fun invoke(id: Long, activo: Boolean): AppResult<Unit> {
        val p = repo.observarPreajustes().first().firstOrNull { it.id == id } ?: return AppResult.Err(AppError.NoEncontrado)
        if (p.activo == activo) return AppResult.Ok(Unit)
        return when (val r = repo.guardarPreajuste(p.copy(activo = activo))) {
            is AppResult.Ok -> AppResult.Ok(Unit)
            is AppResult.Err -> r
        }
    }
}

// ---------------- Preferencias ----------------

class GuardarNiveles @Inject constructor(private val repo: PreferenciasRepository) {
    suspend operator fun invoke(n: NivelesMinimos): AppResult<Unit> {
        if (n.productoCritico < 0 || n.productoBajo < n.productoCritico) return AppResult.Err(AppError.Validacion("productoCritico", AppError.Regla.RANGO))
        if (n.insumoCritico.isNegative || n.insumoBajo < n.insumoCritico) return AppResult.Err(AppError.Validacion("insumoCritico", AppError.Regla.RANGO))
        repo.guardarNiveles(n)
        return AppResult.Ok(Unit)
    }
}

// ---------------- Formato monetario ----------------

