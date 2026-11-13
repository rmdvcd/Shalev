package cu.shalev.domain.validation

import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.Categorias
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError.Regla
import cu.shalev.core.result.AppError.Validacion
import cu.shalev.core.validation.Phone
import cu.shalev.core.validation.Validators
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta

/**
 * Validadores de dominio. Devuelven TODOS los errores (la UI marca cada campo en tiempo real);
 * los casos de uso se quedan con el primero. Nombres de campo = nombres de propiedad del modelo.
 */
object Validadores {

    const val MAX_NOMBRE = 80
    const val MAX_CATEGORIA = 40
    /** 0.24.0: descripción resumida que distingue artículos del mismo nombre (una línea). Antes, 500. */
    const val MAX_DESCRIPCION = 40
    private val CI_CUBANO = Regex("^[0-9]{11}$")
    private val NUM_TRANSACCION = Regex("^[A-Za-z0-9]{6,30}$")
    private val CUENTA = Regex("^[0-9]{12,20}$")
    const val MIN_CONTRASENA = 8
    val RANGO_AJUSTE = -9_000..10_000 // -90 % … +100 %

    fun producto(p: Producto): List<Validacion> = buildList {
        if (p.costoAdicional < Cup.ZERO) add(Validacion("costoAdicional", Regla.RANGO))
        texto("nombre", p.nombre, MAX_NOMBRE)?.let(::add)
        texto("categoria", p.categoria, MAX_CATEGORIA)?.let(::add)
        if (Categorias.esInsumos(p.categoria)) add(Validacion("categoria", Regla.NO_PERMITIDO)) // P29: reservada
        descripcion(p.descripcion)?.let(::add)
        if (p.precioCosto.isNegative) add(Validacion("precioCosto", Regla.RANGO))
        if (p.precioVenta <= Cup.ZERO || p.precioVenta <= p.precioCosto) add(Validacion("precioVenta", Regla.RANGO))
        if (p.cantidad < 0) add(Validacion("cantidad", Regla.RANGO))
        niveles(p.nivelBajo, p.nivelCritico).forEach(::add)
        if (p.esElaborado) {
            if (p.fotoUri != null) add(Validacion("fotoUri", Regla.NO_PERMITIDO))
            if (p.fechaCaducidad != null) add(Validacion("fechaCaducidad", Regla.NO_PERMITIDO))
            if (p.cantidad != 0L) add(Validacion("cantidad", Regla.NO_PERMITIDO))
            if (p.nivelBajo != null || p.nivelCritico != null) add(Validacion("niveles", Regla.NO_PERMITIDO))
        }
    }

    fun insumo(i: Insumo): List<Validacion> = buildList {
        texto("nombre", i.nombre, MAX_NOMBRE)?.let(::add)
        if (i.precio.isNegative) add(Validacion("precio", Regla.RANGO))
        if (i.cantidad.isNegative) add(Validacion("cantidad", Regla.RANGO))
        if (i.precioVenta?.let { it <= Cup.ZERO || it <= i.precio } == true) add(Validacion("precioVenta", Regla.RANGO))
        niveles(i.nivelBajo?.milesimas, i.nivelCritico?.milesimas).forEach(::add)
    }

    /** P29: Nombre, Tipo e Importe obligatorios; insumos opcionales (cantidad > 0, sin repetir). */
    fun servicio(s: Servicio, lineas: List<RecetaLinea>): List<Validacion> = buildList {
        if (s.costoAdicional < Cup.ZERO) add(Validacion("costoAdicional", Regla.RANGO))
        texto("nombre", s.nombre, MAX_NOMBRE)?.let(::add)
        texto("tipo", s.tipo, MAX_CATEGORIA)?.let(::add)
        if (s.importe.isNegative || s.importe.centavos == 0L) add(Validacion("importe", Regla.RANGO))
        descripcion(s.descripcion)?.let(::add)
        if (lineas.any { it.cantidad <= Cantidad.ZERO }) add(Validacion("insumos", Regla.RANGO))
        if (lineas.map { it.insumoId }.toSet().size != lineas.size) add(Validacion("insumos", Regla.NO_PERMITIDO))
    }

    /** 0.24.0: una sola línea de hasta [MAX_DESCRIPCION] caracteres (null o vacía = sin descripción). */
    fun descripcion(d: String?): Validacion? = when {
        d == null -> null
        d.any { it == '\n' || it == '\r' } -> Validacion("descripcion", Regla.FORMATO)
        d.trim().length > MAX_DESCRIPCION -> Validacion("descripcion", Regla.RANGO)
        else -> null
    }

    fun receta(r: Receta?): List<Validacion> = buildList {
        if (r == null || r.lineas.isEmpty()) { add(Validacion("receta", Regla.REQUERIDO)); return@buildList }
        if (r.lineas.any { it.cantidad <= Cantidad.ZERO }) add(Validacion("receta", Regla.RANGO))
        if (r.lineas.map { it.insumoId }.toSet().size != r.lineas.size) add(Validacion("receta", Regla.NO_PERMITIDO))
    }

    fun cliente(c: DatosCliente): List<Validacion> = buildList {
        if (c.nombreApellidos.isBlank()) add(Validacion("clienteNombre", Regla.REQUERIDO))
        else if (!Validators.nombre(c.nombreApellidos)) add(Validacion("clienteNombre", Regla.FORMATO))
        if (!CI_CUBANO.matches(c.ci.trim())) add(Validacion("clienteCi", if (c.ci.isBlank()) Regla.REQUERIDO else Regla.FORMATO))
        if (Phone.normalize(c.telefono) == null) add(Validacion("clienteTelefono", if (c.telefono.isBlank()) Regla.REQUERIDO else Regla.FORMATO))
    }

    fun numeroTransaccion(n: String): Validacion? = when {
        n.isBlank() -> Validacion("numeroTransaccion", Regla.REQUERIDO)
        !NUM_TRANSACCION.matches(n.trim()) -> Validacion("numeroTransaccion", Regla.FORMATO)
        else -> null
    }

    /** Tarjeta (16 dígitos) o cuenta bancaria (12–20). Admite espacios y guiones al teclear. */
    fun normalizarCuenta(raw: String): String? = raw.filterNot { it == ' ' || it == '-' }.takeIf(CUENTA::matches)

    fun perfil(p: Perfil): List<Validacion> = buildList {
        if (p.nombre.isNotBlank() && !Validators.nombre(p.nombre)) add(Validacion("nombre", Regla.FORMATO))
        if (p.apellidos.isNotBlank() && !Validators.nombre(p.apellidos)) add(Validacion("apellidos", Regla.FORMATO))
        if (p.ci.isNotBlank() && !Validators.ci(p.ci)) add(Validacion("ci", Regla.FORMATO))
        if (p.tarjetas.any { normalizarCuenta(it.numero) != it.numero }) add(Validacion("tarjetas", Regla.FORMATO))
        if (p.telefonos.any { Phone.normalize(it.numero) != it.numero }) add(Validacion("telefonos", Regla.FORMATO))
        if (p.tarjetas.map { it.numero }.toSet().size != p.tarjetas.size) add(Validacion("tarjetas", Regla.NO_PERMITIDO))
        if (p.telefonos.map { it.numero }.toSet().size != p.telefonos.size) add(Validacion("telefonos", Regla.NO_PERMITIDO))
    }

    fun preajuste(p: PreajustePrecios): List<Validacion> = buildList {
        texto("nombre", p.nombre, MAX_NOMBRE)?.let(::add)
        if (p.puntosBasicos == 0 || p.puntosBasicos !in RANGO_AJUSTE) add(Validacion("puntosBasicos", Regla.RANGO))
        if (p.productoIds.isEmpty() && p.servicioIds.isEmpty()) add(Validacion("productoIds", Regla.REQUERIDO))
        if (p.importeMinimo != null && p.importeMinimo <= Cup.ZERO) add(Validacion("importeMinimo", Regla.RANGO))
    }

    fun contrasena(c: CharArray): Validacion? =
        if (c.size < MIN_CONTRASENA) Validacion("contrasena", Regla.RANGO) else null

    private fun texto(campo: String, v: String, max: Int): Validacion? = when {
        v.isBlank() -> Validacion(campo, Regla.REQUERIDO)
        v.trim().length > max -> Validacion(campo, Regla.RANGO)
        else -> null
    }

    /** Niveles opcionales ≥ 0 y crítico ≤ bajo. */
    private fun niveles(bajo: Long?, critico: Long?): List<Validacion> = buildList {
        if (bajo != null && bajo < 0) add(Validacion("nivelBajo", Regla.RANGO))
        if (critico != null && critico < 0) add(Validacion("nivelCritico", Regla.RANGO))
        if (bajo != null && critico != null && critico > bajo) add(Validacion("nivelCritico", Regla.RANGO))
    }
}
