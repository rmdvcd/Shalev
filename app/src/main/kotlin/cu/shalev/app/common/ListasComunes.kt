package cu.shalev.app.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.ShalevBadge
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.token.ShalevElevation
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Insumo

/**
 * Piezas repetidas de las listas con selección (Inventario, Servicios y Registros) y de la receta
 * de Productos y Servicios. Compartidas para que las tres listas se comporten igual.
 */

/** Icono de filtros con la insignia de cuántos hay activos. */
@Composable
fun BotonFiltro(
    activos: Int,
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BadgedBox(badge = {
        if (activos > 0) ShalevBadge(activos, Modifier.clearAndSetSemantics { })
    }) {
        ShalevIconAction(
            ShalevIcons.Filtrar, descripcionFiltro(texto, activos),
            onClick = onClick, selected = activos > 0, enabled = enabled, modifier = modifier,
            // 1.0.2: el texto visible es el corto; el conteo de filtros sigue en la descripción para TalkBack.
            etiqueta = texto,
        )
    }
}

/**
 * «N seleccionados» con quitar, marcar/desmarcar todo y, si la app tiene permiso, exportar y eliminar.
 * [onExportar] y [onEliminar] se omiten pasando `null` (pantalla sin esa acción); el permiso lo comprueba
 * esta barra igual que antes en las tres listas.
 */
@Composable
fun BarraSeleccion(
    texto: String,
    todosMarcados: Boolean,
    onLimpiar: () -> Unit,
    onMarcarTodo: () -> Unit,
    onExportar: (() -> Unit)? = null,
    onEliminar: (() -> Unit)? = null,
    tag: String? = null,
    tagMarcarTodo: String? = null,
    tagExportar: String? = null,
    tagEliminar: String? = null,
) {
    val permisos = LocalPermisosApp.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.xs).tag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShalevIconAction(ShalevIcons.Cancelar, "Quitar selección", onClick = onLimpiar)
        Text(texto, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        ShalevIconAction(
            ShalevIcons.Confirmar, if (todosMarcados) "Desmarcar todo" else "Marcar todo",
            onClick = onMarcarTodo, selected = todosMarcados, modifier = Modifier.tag(tagMarcarTodo),
        )
        if (onExportar != null && permisos.exportar) {
            ShalevIconAction(ShalevIcons.Exportar, "Exportar selección", onClick = onExportar, modifier = Modifier.tag(tagExportar))
        }
        if (onEliminar != null && permisos.editarInventario) {
            ShalevIconAction(ShalevIcons.Eliminar, "Eliminar selección", onClick = onEliminar, modifier = Modifier.tag(tagEliminar))
        }
    }
}

private fun Modifier.tag(tag: String?): Modifier = if (tag == null) this else testTag(tag)

/** Modo venta: n.º marcado + Continuar (ventana de cantidades y método de pago). */
@Composable
fun BarraContinuarVenta(
    texto: String,
    haySeleccion: Boolean,
    onLimpiar: () -> Unit,
    onContinuar: () -> Unit,
    tagContinuar: String? = null,
) {
    Surface(tonalElevation = ShalevElevation.tonalBar) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterHorizontally), // P28: grupo centrado
        ) {
            if (haySeleccion) ShalevIconAction(ShalevIcons.Cancelar, "Quitar selección", onClick = onLimpiar)
            Text(texto, style = MaterialTheme.typography.titleSmall)
            ShalevPrimaryButton(
                text = "Continuar", icon = ShalevIcons.Siguiente, onClick = onContinuar,
                enabled = haySeleccion, modifier = Modifier.tag(tagContinuar),
            )
        }
    }
}

/** Hoja «Agregar insumo» de la receta: buscador y lista de insumos existentes. */
@Composable
fun HojaAgregarInsumo(
    insumos: List<Insumo>,
    disponibles: List<Insumo>,
    onCerrar: () -> Unit,
    onAgregar: (Insumo) -> Unit,
) {
    var buscar by rememberSaveable { mutableStateOf("") }
    ShalevBottomSheet(onDismiss = onCerrar, title = "Agregar insumo") {
        val lista = disponibles.filter { buscar.isBlank() || it.nombre.contains(buscar.trim(), ignoreCase = true) }
        if (insumos.isEmpty()) {
            ShalevEmptyState(
                title = "Aún no tienes insumos", detail = "Créalos en Inventario → Agregar → Insumo.",
                ilustracion = ShalevIlustracion.Elaboracion,
                modifier = Modifier.heightIn(max = ShalevSize.contentMaxWidth),
            )
        } else {
            ShalevTextField(
                filtro = FiltroEntrada.BUSQUEDA, value = buscar, onValueChange = { buscar = it },
                label = "Buscar insumo", leadingIcon = ShalevIcons.Buscar, anchoCompleto = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (lista.isEmpty()) ShalevSecondaryText("Ningún insumo coincide.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            lista.forEach { i ->
                ShalevListItem(title = i.nombre, indicatorColor = null, onClick = { onAgregar(i) })
            }
        }
    }
}
