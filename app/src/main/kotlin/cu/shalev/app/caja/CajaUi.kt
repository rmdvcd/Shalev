package cu.shalev.app.caja

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import cu.shalev.app.R
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.theme.ShalevTextos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import cu.shalev.domain.model.BilletesCUP
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevTab
import cu.shalev.designsystem.component.ShalevTabs
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Arqueo
import cu.shalev.domain.model.EstadoArqueo
import cu.shalev.domain.model.MovimientoCaja
import cu.shalev.domain.model.TipoMovimientoCaja

/** 0.25.0: textos del arqueo de caja (compartidos por Inicio, Venta, Registros y la principal). */
object TextosCaja {
    const val FONDO = "Fondo de caja (CUP)"
    const val FONDO_TITULO = "Abrir turno"
    const val FONDO_TEXTO = "Escribe el efectivo con el que empieza la caja (puede ser 0)."
    const val FONDO_SUGERIDO = "Propuesto: lo contado al cerrar el turno anterior."
    /** 0.26.0 (P73 §4): en una secundaria, el fondo lo asigna el encargado. */
    const val CONTADO = "Efectivo contado (CUP)"
    const val CUADRA_BOTON = "Cuadra (contado = esperado)"
    const val MOVIMIENTO_TITULO = "Entrada / salida de efectivo"
    const val ENTRADA = "Entrada"
    const val SALIDA = "Salida"
    const val IMPORTE = "Importe (CUP)"
    const val MOTIVO = "Motivo"
    const val MOTIVO_AYUDA = "De 3 a 60 caracteres. No se borra: un error se corrige con el movimiento contrario."
    const val IMPORTE_INVALIDO = "Escribe un importe válido (máximo 2 decimales)."
    const val SIN_ARQUEO = "Sin arqueo (turno de una versión anterior de Shalev)"
    const val MOVIMIENTO_GUARDADO = "Movimiento de caja registrado."

    fun esperado(c: Cup) = "Efectivo esperado: ${Money.format(c)}"

    fun estado(e: EstadoArqueo?): String = when (e) {
        EstadoArqueo.CUADRA -> "Cuadra"
        EstadoArqueo.SOBRANTE -> "Sobrante"
        EstadoArqueo.FALTANTE -> "Faltante"
        null -> "Sin contar"
    }

    /** «Esperado X · Contado Y · Faltante Z» (la principal antes de aprobar un cierre). */
    fun resumen(a: Arqueo): String {
        val base = "Esperado ${Money.format(a.esperado)}"
        val c = a.contado ?: return "$base · Sin contar"
        val d = a.diferencia ?: Cup.ZERO
        return "$base · Contado ${Money.format(c)} · ${estado(a.estado)}" +
            if (d.centavos != 0L) " ${Money.format(if (d.isNegative) -d else d)}" else ""
    }

    fun tipo(t: TipoMovimientoCaja) = if (t == TipoMovimientoCaja.ENTRADA) ENTRADA else SALIDA

    fun linea(m: MovimientoCaja): String = "${tipo(m.tipo)} ${Money.format(m.importe)} · ${m.motivo}"
}

object CajaTags {
    const val FONDO = "caja_fondo"
    const val CONTADO = "caja_contado"
    const val CUADRA = "caja_cuadra"
    const val IMPORTE = "caja_importe"
    const val MOTIVO = "caja_motivo"
    const val CONFIRMAR = "caja_confirmar"

    /** 0.31.0: «Omitir» el conteo por billetes y el botón para volver a él. */
    const val OMITIR = "caja_omitir"
    const val POR_BILLETES = "caja_por_billetes"

    /** 0.31.0: «Omitir» el conteo de inventario al cerrar (se cierra sin ajustar existencias). */
    const val OMITIR_INVENTARIO = "conteo_omitir"
}

/** Lógica pura de los diálogos (testeable sin Compose). */
object CajaForm {
    /** Texto inicial del fondo: el sugerido sin «CUP», o vacío (hay que escribirlo, aunque sea 0). */
    fun textoInicial(c: Cup?): String = c?.let { Money.format(it).removeSuffix(" CUP").replace(",", "") } ?: ""

    /** null = vacío o inválido; los negativos no valen. */
    fun importe(texto: String): Cup? = Money.parse(texto)?.takeUnless { it.isNegative }

    /**
     * 0.31.0: con el conteo por billetes visible, al menos un billete o moneda escritos. Declarar 0 sin contar
     * descuadraría la caja: para el cero explícito se usa el total suelto (o «Cuadra»).
     */
    fun algoContado(desglose: Map<Int, Long>, otro: Cup?): Boolean =
        desglose.values.any { it > 0L } || (otro?.centavos ?: 0L) > 0L

    fun motivoValido(m: String): Boolean = m.trim().replace(Regex("\\s+"), " ").length in MovimientoCaja.MOTIVO_MIN..MovimientoCaja.MOTIVO_MAX
}

/** Recurso de miniatura oficial del billete CUP (anverso). */
@DrawableRes
fun iconoBilleteCup(denominacion: Int): Int = when (denominacion) {
    1 -> R.drawable.billete_1
    3 -> R.drawable.billete_3
    5 -> R.drawable.billete_5
    10 -> R.drawable.billete_10
    20 -> R.drawable.billete_20
    50 -> R.drawable.billete_50
    100 -> R.drawable.billete_100
    200 -> R.drawable.billete_200
    500 -> R.drawable.billete_500
    1000 -> R.drawable.billete_1000
    2000 -> R.drawable.billete_2000
    5000 -> R.drawable.billete_5000
    20000 -> R.drawable.billete_20000
    else -> R.drawable.billete_1
}

/** Miniatura enmarcada del billete CUP que sustituye a la etiqueta textual de la denominación. */
@Composable
fun MiniaturaBilleteCup(denominacion: Int, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(6.dp)
    Image(
        painter = painterResource(iconoBilleteCup(denominacion)),
        contentDescription = "Billetes de $denominacion CUP",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(width = 88.dp, height = 40.dp)
            .clip(forma)
            .border(BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant), forma),
    )
}

/** Color del estado del arqueo: faltante en rojo (error), el resto normal. */
@Composable
fun colorArqueo(e: EstadoArqueo?): Color =
    if (e == EstadoArqueo.FALTANTE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

/**
 * 0.25.0 (§5.2): diálogo único de apertura (Inicio y «Abrir turno» al vender). El fondo es obligatorio (puede ser 0);
 * se propone [sugerido] (lo contado al cerrar el turno anterior de esta app).
 *
 * 0.32.0: el fondo lo escribe siempre quien abre el turno, también en una app secundaria.
 */
@Composable
fun DialogoFondo(
    sugerido: Cup?,
    trabajando: Boolean,
    onConfirmar: (Cup) -> Unit,
    onDismiss: () -> Unit,
) {
    var texto by rememberSaveable(sugerido) { androidx.compose.runtime.mutableStateOf(CajaForm.textoInicial(sugerido)) }
    val valor = CajaForm.importe(texto)
    ShalevDialog(
        title = TextosCaja.FONDO_TITULO,
        text = TextosCaja.FONDO_TEXTO,
        onDismiss = onDismiss,
        onConfirm = { valor?.let(onConfirmar) },
        confirmDescription = "Abrir turno",
        confirmEnabled = valor != null && !trabajando,
        confirmLoading = trabajando,
        confirmTag = CajaTags.CONFIRMAR,
    ) {
        ShalevTextField(
            value = texto, onValueChange = { texto = it.take(16) }, label = "${TextosCaja.FONDO} *",
            supportingText = if (sugerido != null) TextosCaja.FONDO_SUGERIDO else null,
            isError = texto.isNotBlank() && valor == null, errorText = TextosCaja.IMPORTE_INVALIDO,
            filtro = FiltroEntrada.DINERO, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(CajaTags.FONDO),
        )
    }
}

/**
 * 0.25.0 (§5.2): conteo al cerrar (o al pedir el cierre en una secundaria). Muestra en vivo el esperado y la diferencia;
 * «Cuadra» rellena el contado con el esperado. No se confirma con el campo vacío. [arqueo] null = turno sin fondo
 * (anterior a 0.25.0): se pide igualmente el contado, sin diferencia.
 *
 * 0.31.0: el conteo **por billetes** se muestra de entrada y «Omitir» (esquina inferior derecha) pasa al total suelto.
 */
@Composable
fun DialogoContado(
    titulo: String,
    texto: String,
    confirmar: String,
    arqueo: Arqueo?,
    trabajando: Boolean,
    onConfirmar: (Cup) -> Unit,
    onDismiss: () -> Unit,
    dismissDescription: String = "Cancelar",
    onDesglose: ((Cup, Map<Int, Long>, Cup) -> Unit)? = null,
) {
    var valorTexto by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    // 0.31.0: por billetes de entrada (antes quedaba oculto tras un botón de solo icono).
    var porBilletes by rememberSaveable { mutableStateOf(true) }
    var cantidades by rememberSaveable { mutableStateOf(List(BilletesCUP.denominaciones.size) { "" }) }
    var otroTexto by rememberSaveable { mutableStateOf("0") }
    val otro = CajaForm.importe(otroTexto)
    val desglose = BilletesCUP.denominaciones.mapIndexed { i, d -> d to (cantidades[i].toLongOrNull() ?: 0L) }.toMap()
    val contado = if (porBilletes) otro?.let { runCatching { BilletesCUP.total(desglose, it) }.getOrNull() } else CajaForm.importe(valorTexto)
    // 0.31.0: «Omitir» el conteo de billetes (no se cuentan uno a uno): se escribe el total contado.
    val omitir: (@Composable () -> Unit)? = if (!porBilletes) null else {
        {
            ShalevTextButton(
                "Omitir el conteo de billetes", onClick = { porBilletes = false }, icon = ShalevIcons.AhoraNo,
                modifier = Modifier.testTag(CajaTags.OMITIR),
            )
        }
    }
    ShalevDialog(
        title = titulo,
        text = texto,
        onDismiss = onDismiss,
        onConfirm = { contado?.let { c ->
            if (onDesglose != null) onDesglose(c, if (porBilletes) desglose else emptyMap(), if (porBilletes) otro!! else c)
            else onConfirmar(c)
        } },
        confirmDescription = confirmar,
        // Con los billetes a la vista hace falta algo contado; con el total suelto, el 0 es una decisión explícita.
        confirmEnabled = contado != null && (!porBilletes || CajaForm.algoContado(desglose, otro)) && !trabajando,
        confirmLoading = trabajando,
        confirmTag = CajaTags.CONFIRMAR,
        dismissDescription = dismissDescription,
        esquina = omitir,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            arqueo?.let { a ->
                // 0.28.0: los importes («Efectivo esperado: 1 250.00 CUP») van en seminegrita.
                Text(
                    ShalevTextos.resaltar(TextosCaja.esperado(a.esperado), Money.format(a.esperado)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ShalevSecondaryText(
                    ShalevTextos.resaltar(
                        "Fondo ${Money.format(a.fondo)} + efectivo ${Money.format(a.ventasEfectivo)} + entradas ${Money.format(a.entradas)} − salidas ${Money.format(a.salidas)}",
                        Money.format(a.fondo), Money.format(a.ventasEfectivo), Money.format(a.entradas), Money.format(a.salidas),
                    ),
                    maxLines = 3,
                )
            }
            // 0.31.0: desde el total suelto se puede volver a contar billete a billete.
            if (!porBilletes) ShalevTextButton(
                "Contar billetes por denominación", onClick = { porBilletes = true }, icon = ShalevIcons.Efectivo,
                modifier = Modifier.testTag(CajaTags.POR_BILLETES),
            )
            if (porBilletes) {
                BilletesCUP.denominaciones.forEachIndexed { i, denominacion ->
                    val actual = cantidades[i].toLongOrNull() ?: 0L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MiniaturaBilleteCup(denominacion)
                        ShalevIconAction(
                            ShalevIcons.Disminuir,
                            "Un billete menos de $denominacion CUP",
                            onClick = {
                                val sig = (actual - 1L).coerceAtLeast(0L)
                                cantidades = cantidades.toMutableList().also { it[i] = if (sig == 0L) "" else sig.toString() }
                            },
                            enabled = actual > 0L,
                            style = IconActionStyle.Tonal,
                            modifier = Modifier.testTag("billetes_menos_$denominacion"),
                        )
                        ShalevTextField(
                            value = cantidades[i],
                            onValueChange = { nuevo ->
                                if (nuevo.length <= 6 && nuevo.all { it in '0'..'9' }) {
                                    cantidades = cantidades.toMutableList().also { it[i] = nuevo }
                                }
                            },
                            label = null,
                            placeholder = "0",
                            clearable = false,
                            textAlign = TextAlign.Center,
                            filtro = FiltroEntrada.ENTERO,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("billetes_$denominacion"),
                        )
                        ShalevIconAction(
                            ShalevIcons.Aumentar,
                            "Un billete más de $denominacion CUP",
                            onClick = {
                                val sig = (actual + 1L).coerceAtMost(999_999L)
                                cantidades = cantidades.toMutableList().also { it[i] = sig.toString() }
                            },
                            enabled = actual < 999_999L,
                            style = IconActionStyle.Tonal,
                            modifier = Modifier.testTag("billetes_mas_$denominacion"),
                        )
                    }
                }
                ShalevTextField(value = otroTexto, onValueChange = { otroTexto = it.take(16) }, label = "Monedas / otro efectivo (CUP)",
                    filtro = FiltroEntrada.DINERO, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Text("Total contado: ${contado?.let(Money::format) ?: "Importe inválido"}", style = ShalevTextos.datoEn(MaterialTheme.typography.bodyMedium))
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                ShalevTextField(
                    value = valorTexto, onValueChange = { valorTexto = it.take(16) }, label = "${TextosCaja.CONTADO} *",
                    isError = valorTexto.isNotBlank() && contado == null, errorText = TextosCaja.IMPORTE_INVALIDO,
                    filtro = FiltroEntrada.DINERO, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag(CajaTags.CONTADO),
                )
                if (arqueo != null) ShalevTextButton(
                    TextosCaja.CUADRA_BOTON, onClick = { valorTexto = CajaForm.textoInicial(arqueo.esperado) }, icon = ShalevIcons.Listo,
                    modifier = Modifier.testTag(CajaTags.CUADRA),
                )
            }
            if (arqueo != null && contado != null) {
                val a = arqueo.copy(contado = contado)
                val d = a.diferencia ?: Cup.ZERO
                // 0.28.0: el importe («Faltante: 25.00 CUP») va en seminegrita.
                val texto = "${TextosCaja.estado(a.estado)}${if (d.centavos != 0L) ": " + Money.format(if (d.isNegative) -d else d) else ""}"
                Text(
                    ShalevTextos.resaltar(texto, if (d.centavos != 0L) Money.format(if (d.isNegative) -d else d) else ""),
                    color = colorArqueo(a.estado), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** 0.25.0 (§5.2): entrada o salida de efectivo (pestañas Tipo, Importe y Motivo). */
@Composable
fun DialogoMovimientoCaja(
    trabajando: Boolean,
    onConfirmar: (TipoMovimientoCaja, Cup, String) -> Unit,
    onDismiss: () -> Unit,
    efectivoDisponible: Cup? = null,
) {
    var tipo by rememberSaveable { mutableIntStateOf(1) }
    var importe by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var motivo by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val t = if (tipo == 0) TipoMovimientoCaja.ENTRADA else TipoMovimientoCaja.SALIDA
    val valor = CajaForm.importe(importe)?.takeIf { it.centavos > 0 }
    val superaCaja = t == TipoMovimientoCaja.SALIDA && efectivoDisponible != null && valor != null && valor > efectivoDisponible
    val ok = valor != null && !superaCaja && CajaForm.motivoValido(motivo) && !trabajando
    ShalevDialog(
        title = TextosCaja.MOVIMIENTO_TITULO,
        onDismiss = onDismiss,
        onConfirm = { if (valor != null && !superaCaja) onConfirmar(t, valor, motivo) },
        confirmDescription = "Registrar ${TextosCaja.tipo(t).lowercase()}",
        confirmEnabled = ok,
        confirmLoading = trabajando,
        confirmTag = CajaTags.CONFIRMAR,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            ShalevTabs(listOf(ShalevTab(TextosCaja.ENTRADA), ShalevTab(TextosCaja.SALIDA)), tipo, { tipo = it })
            ShalevTextField(
                value = importe, onValueChange = { importe = it.take(16) }, label = "${TextosCaja.IMPORTE} *",
                isError = (importe.isNotBlank() && valor == null) || superaCaja,
                errorText = if (superaCaja && efectivoDisponible != null) {
                    "Supera el efectivo disponible en caja (${Money.format(efectivoDisponible)})."
                } else TextosCaja.IMPORTE_INVALIDO,
                supportingText = if (t == TipoMovimientoCaja.SALIDA && efectivoDisponible != null) {
                    "Disponible en caja: ${Money.format(efectivoDisponible)}"
                } else null,
                filtro = FiltroEntrada.DINERO, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag(CajaTags.IMPORTE),
            )
            ShalevTextField(
                value = motivo, onValueChange = { motivo = it.take(MovimientoCaja.MOTIVO_MAX) }, label = "${TextosCaja.MOTIVO} *",
                supportingText = TextosCaja.MOTIVO_AYUDA,
                modifier = Modifier.fillMaxWidth().testTag(CajaTags.MOTIVO),
            )
        }
    }
}

/** Filas «Fondo · Ventas en efectivo · Entradas · Salidas · Esperado · Contado · Diferencia» (detalle del turno). */
@Composable
fun TablaArqueo(a: Arqueo?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        if (a == null) { ShalevSecondaryText(TextosCaja.SIN_ARQUEO, maxLines = 2); return@Column }
        Fila("Fondo", Money.format(a.fondo))
        Fila("Ventas en efectivo", Money.format(a.ventasEfectivo))
        Fila("Entradas", Money.format(a.entradas))
        Fila("Salidas", Money.format(a.salidas))
        Fila("Esperado", Money.format(a.esperado))
        Fila("Contado", a.contado?.let(Money::format) ?: "—")
        val d = a.diferencia
        Fila(TextosCaja.estado(a.estado), d?.let { Money.format(if (it.isNegative) -it else it) } ?: "—", colorArqueo(a.estado))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Fila(etiqueta: String, valor: String, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    // 0.27.1 (T2, capturas al 200 %): ni el importe («12,35 / 0.00 / CUP») ni la etiqueta («Esperad / o») se parten.
    // Si los dos no caben en una línea, el importe baja a la siguiente.
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = ShalevSpacing.xs))
        Text(valor, style = ShalevTextos.datoEn(MaterialTheme.typography.bodyMedium), color = color, softWrap = false, maxLines = 1)
    }
}
