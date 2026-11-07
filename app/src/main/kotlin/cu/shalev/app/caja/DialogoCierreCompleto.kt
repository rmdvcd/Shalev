package cu.shalev.app.caja

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Arqueo
import cu.shalev.domain.model.ConteoCierre
import cu.shalev.domain.model.TipoEntidad

/** Tres pasos: efectivo, conteo físico y revisión explícita antes de declarar/cerrar. */
@Composable
fun DialogoCierreCompleto(
    titulo: String, texto: String, confirmar: String, arqueo: Arqueo?, trabajando: Boolean,
    onConfirmar: (Cup) -> Unit, onDismiss: () -> Unit, dismissDescription: String = "Cancelar",
    viewModel: CierreConteoViewModel? = null,
) {
    // La ruta inyecta el ViewModel; el contenido puro de las pruebas no depende de Hilt.
    if (viewModel == null) {
        DialogoContado(titulo, texto, confirmar, arqueo, trabajando, onConfirmar, onDismiss, dismissDescription)
        return
    }
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var omitido by rememberSaveable { mutableStateOf(false) }
    var billetes by rememberSaveable { mutableStateOf(hashMapOf<Int, Long>()) }
    var otro by rememberSaveable { mutableLongStateOf(0L) }
    var motivo by rememberSaveable { mutableStateOf("") }
    var cantidades by rememberSaveable { mutableStateOf(listOf<String>()) }
    val lineas by viewModel.lineas.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val guardando by viewModel.guardando.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.cargar() }
    val estadoEfectivo = rememberSaveableStateHolder()
    val ls = lineas
    LaunchedEffect(ls) { cantidades = ls?.map { "" }.orEmpty() }
    if (paso == 0) {
        estadoEfectivo.SaveableStateProvider("efectivo") { DialogoContado(titulo, texto, "Continuar al inventario", arqueo, trabajando, onConfirmar, onDismiss, dismissDescription,
            onDesglose = { _, b, o -> billetes = HashMap(b); otro = o.centavos; paso = 1 }) }
        return
    }
    fun cantidad(i: Int): Long? {
        val l = ls?.getOrNull(i) ?: return null
        val t = cantidades.getOrNull(i).orEmpty()
        return ConteoForm.cantidad(t, l.entidad)
    }
    val completo = ls != null && ls.indices.all { cantidad(it) != null }
    // 0.32.0: el conteo solo incluye lo que cambió en el turno; productos e insumos van juntos en una misma lista.
    // 0.31.0: «Omitir» cierra sin ajustar existencias: se guarda el conteo con lo esperado (sin diferencias) y el
    // motivo lo deja dicho en la revisión, la ficha del turno y los exportados.
    val inventario = when {
        omitido -> ls?.map { it.copy(contado = it.esperado) }.orEmpty()
        completo -> ls!!.mapIndexed { i, l -> l.copy(contado = cantidad(i)!!) }
        else -> emptyList()
    }
    val conteo = ConteoCierre(billetes, Cup(otro), inventario, if (omitido) MOTIVO_OMITIDO else motivo.trim())
    ShalevDialog(
        title = if (paso == 1) "Conteo de inventario" else "Revisar cierre",
        onDismiss = { if (!guardando) onDismiss() },
        onConfirm = { if (paso == 1) paso = 2 else viewModel.guardar(conteo) { onConfirmar(conteo.efectivo) } },
        confirmDescription = if (paso == 1) "Revisar diferencias" else confirmar,
        confirmEnabled = (if (paso == 1) completo else conteo.valido()) && !guardando && !trabajando,
        confirmLoading = guardando || trabajando, dismissDescription = dismissDescription,
        // 1.0.4: «Atrás» y «Omitir» comparten fila centrada con la confirmación. Antes iban dentro del contenido
        // —que se desplaza— y con el texto largo quedaban flotando en otra fila, lejos del ✓.
        secundarias = {
            ShalevTextButton("Atrás", onClick = { omitido = false; paso-- }, icon = ShalevIcons.Atras, enabled = !guardando)
            if (paso == 1) ShalevTextButton(
                "Omitir", onClick = { omitido = true; paso = 2 }, icon = ShalevIcons.AhoraNo, enabled = ls != null && !guardando && !trabajando,
                modifier = Modifier.testTag(CajaTags.OMITIR_INVENTARIO),
            )
            // 1.0.4: la recuperación del inventario también es una acción del pie (antes, un icono suelto en el contenido).
            if (error != null) ShalevTextButton(
                "Volver a cargar inventario", onClick = { paso = 1; viewModel.cargar() }, icon = ShalevIcons.Listo,
            )
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            Text(if (paso == 1) "Cuenta solo lo que cambió durante el turno. Los elaborados se concilian mediante sus insumos." else "Revisa las diferencias antes de confirmar. Solo la principal ajustará las existencias.", style = MaterialTheme.typography.bodyMedium)
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (ls == null) Text("Cargando inventario…", style = MaterialTheme.typography.bodyMedium)
            else if (paso == 1) {
                if (ls.isEmpty()) {
                    Text("Durante este turno no cambió ningún artículo: no hay nada que contar.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    // Productos e insumos en una misma lista, sin pestañas por categoría.
                    ls.forEachIndexed { i, l ->
                        ShalevTextField(
                            value = cantidades.getOrNull(i).orEmpty(),
                            onValueChange = { nuevo -> if (nuevo.length <= 16 && nuevo.all { it.isDigit() || it == '.' || it == ',' }) {
                                omitido = false
                                cantidades = cantidades.toMutableList().also { valores -> if (i < valores.size) valores[i] = nuevo }
                            } },
                            label = "${l.nombre} (${l.unidad})",
                            supportingText = "Esperado: ${ConteoForm.formato(l.esperado, l.entidad)} ${l.unidad}",
                            keyboardOptions = KeyboardOptions(keyboardType = if (l.entidad == TipoEntidad.PRODUCTO) KeyboardType.Number else KeyboardType.Decimal),
                            modifier = Modifier,
                        )
                    }
                }
            } else {
                Text("Efectivo contado: ${cu.shalev.core.money.Money.format(conteo.efectivo)}", style = MaterialTheme.typography.bodyMedium)
                if (omitido) Text("Conteo de inventario omitido: se cierra sin ajustar existencias.", style = MaterialTheme.typography.bodyMedium)
                else if (!conteo.tieneDiferencias) Text("El inventario cuadra.", style = MaterialTheme.typography.bodyMedium)
                inventario.filter { it.diferencia != 0L }.forEach { l ->
                    Text("${l.nombre}: esperado ${ConteoForm.formato(l.esperado, l.entidad)}, contado ${ConteoForm.formato(l.contado, l.entidad)}, diferencia ${ConteoForm.formato(l.diferencia, l.entidad)} ${l.unidad}",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (conteo.tieneDiferencias) ShalevTextField(
                    value = motivo, onValueChange = { motivo = it.take(160) }, label = "Motivo del ajuste *",
                    supportingText = "De 3 a 160 caracteres. Los ajustes quedarán registrados.", modifier = Modifier,
                )
            }
            // 1.0.4: las acciones del asistente («Atrás», «Omitir») se pasan al pie del diálogo (ver `secundarias`).
        }
    }
}

/** 0.31.0: motivo con el que se guarda un conteo de inventario omitido (queda en la ficha, el resumen y los exportados). */
private const val MOTIVO_OMITIDO = "Conteo de inventario omitido"
