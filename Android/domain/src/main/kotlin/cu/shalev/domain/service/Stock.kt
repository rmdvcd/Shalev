package cu.shalev.domain.service

import cu.shalev.domain.model.Identificacion
import cu.shalev.domain.model.ConteoAlertas
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.TipoAlerta
import java.time.LocalDate

/**
 * Reglas de alertas (Shalev.txt). Niveles propios del artículo o, si faltan, los mínimos globales (5/1).
 * Bajo, Crítico y Sin existencia son excluyentes: con cantidad ≤ crítico cuenta solo como Crítico y con ≤ 0, solo como Sin existencia.
 * "Próximo a caducar" = caduca en ≤ N días, incluidos los ya vencidos (siguen necesitando acción).
 */
object Stock {
    /**
     * 0.21.6 — existencia que se guarda al editar una ficha abierta hace rato.
     *
     * [leida] = existencia mostrada al abrir el formulario (null = sin dato: se guarda lo escrito, como antes);
     * [enFormulario] = la que hay al pulsar ✓; [actual] = la de la base de datos en ese momento.
     * Si el usuario no tocó la cantidad se conserva [actual] (no se deshacen las ventas hechas mientras tanto);
     * si la cambió, manda el valor que escribió (suposición: lo que cuenta en el estante es lo correcto).
     */
    fun cantidadAlGuardar(leida: Long?, enFormulario: Long, actual: Long): Long =
        if (leida != null && enFormulario == leida) actual else enFormulario


    /** P26: un Elaborado no tiene existencias propias → nunca genera alertas (solo sus insumos). */
    fun nivel(p: Producto, n: NivelesMinimos): NivelStock =
        if (p.esElaborado) NivelStock.NORMAL else nivel(p.cantidad, p.nivelBajo ?: n.productoBajo, p.nivelCritico ?: n.productoCritico)

    fun nivel(i: Insumo, n: NivelesMinimos): NivelStock =
        nivel(i.cantidad.milesimas, (i.nivelBajo ?: n.insumoBajo).milesimas, (i.nivelCritico ?: n.insumoCritico).milesimas)

    /** 0.21.0 (C1): con 0 o menos el artículo está «Sin existencia» y deja de contar como crítico. */
    private fun nivel(cantidad: Long, bajo: Long, critico: Long): NivelStock = when {
        cantidad <= 0 -> NivelStock.SIN_EXISTENCIA
        cantidad <= critico -> NivelStock.CRITICO
        cantidad <= bajo -> NivelStock.BAJO
        else -> NivelStock.NORMAL
    }

    /** 0.21.0 (C1): «Sin existencia» = 0 o menos (antes, H6: solo negativas). Un Elaborado no tiene existencias propias. */
    fun sinExistencia(p: Producto): Boolean = !p.esElaborado && p.cantidad <= 0
    fun sinExistencia(i: Insumo): Boolean = i.cantidad.milesimas <= 0

    fun proximoACaducar(p: Producto, hoy: LocalDate, dias: Int): Boolean =
        p.fechaCaducidad?.let { !it.isAfter(hoy.plusDays(dias.toLong())) } == true

    fun conteo(productos: List<Producto>, insumos: List<Insumo>, n: NivelesMinimos, hoy: LocalDate, dias: Int): ConteoAlertas {
        val activos = productos.filterNot { it.eliminado }
        val np = activos.groupingBy { nivel(it, n) }.eachCount()
        val ni = insumos.groupingBy { nivel(it, n) }.eachCount()
        return ConteoAlertas(
            stockBajo = np[NivelStock.BAJO] ?: 0,
            stockCritico = np[NivelStock.CRITICO] ?: 0,
            insumoBajo = ni[NivelStock.BAJO] ?: 0,
            insumoCritico = ni[NivelStock.CRITICO] ?: 0,
            proximosACaducar = activos.count { proximoACaducar(it, hoy, dias) },
            sinExistencia = activos.count { sinExistencia(it) } + insumos.count { sinExistencia(it) },
            nombreRepetido = Identificacion.productosPorDiferenciar(activos).size,
        )
    }

    /** Filtro de Inventario al tocar una alerta de productos. */
    fun filtrarProductos(productos: List<Producto>, tipo: TipoAlerta, n: NivelesMinimos, hoy: LocalDate, dias: Int): List<Producto> {
        val repetidos = if (tipo == TipoAlerta.NOMBRE_REPETIDO) Identificacion.productosPorDiferenciar(productos) else emptySet()
        return productos.filterNot { it.eliminado }.filter {
            when (tipo) {
                TipoAlerta.STOCK_BAJO -> nivel(it, n) == NivelStock.BAJO
                TipoAlerta.STOCK_CRITICO -> nivel(it, n) == NivelStock.CRITICO
                TipoAlerta.PROXIMO_A_CADUCAR -> proximoACaducar(it, hoy, dias)
                TipoAlerta.SIN_EXISTENCIA -> sinExistencia(it)
                TipoAlerta.NOMBRE_REPETIDO -> it.id in repetidos
                TipoAlerta.INSUMO_BAJO, TipoAlerta.INSUMO_CRITICO -> false
            }
        }
    }

    fun filtrarInsumos(insumos: List<Insumo>, tipo: TipoAlerta, n: NivelesMinimos): List<Insumo> = insumos.filter {
        when (tipo) {
            TipoAlerta.INSUMO_BAJO -> nivel(it, n) == NivelStock.BAJO
            TipoAlerta.INSUMO_CRITICO -> nivel(it, n) == NivelStock.CRITICO
            TipoAlerta.SIN_EXISTENCIA -> sinExistencia(it)
            else -> false
        }
    }
}
