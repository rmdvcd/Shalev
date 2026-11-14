package cu.shalev.app.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cu.shalev.designsystem.component.ShalevBarraAcciones
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevStepper
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.TipoEntidad

/** Datos del artículo recién guardado que debe completar su recepción inicial en Gestión de inventario. */
data class RecepcionInicialAlta(val tipo: TipoEntidad, val articuloId: Long, val cantidad: String, val costo: String)

/** Transiciones acotadas compartidas por los asistentes de productos, insumos y servicios. */
object NavegacionAsistenteAlta {
    fun siguiente(actual: Int, total: Int): Int = (actual + 1).coerceIn(1, total.coerceAtLeast(1))
    fun anterior(actual: Int, total: Int): Int = (actual - 1).coerceIn(1, total.coerceAtLeast(1))

    /** Devuelve el primer paso que contiene un campo con error; null si los campos no pertenecen al asistente. */
    fun pasoConError(errores: Set<String>, camposPorPaso: List<Set<String>>): Int? {
        val indice = camposPorPaso.indexOfFirst { paso -> paso.any { it in errores } }
        return indice.takeIf { it >= 0 }?.plus(1)
    }
}

@Composable
fun EncabezadoAsistenteAlta(
    actual: Int,
    total: Int,
    titulo: String,
    modifier: Modifier = Modifier,
    onAnterior: (() -> Unit)? = null,
) {
    ShalevStepper(
        actual = actual,
        total = total,
        titulo = titulo,
        modifier = modifier.testTag("alta.paso.$actual"),
        onAtras = onAnterior,
    )
}

/** Pie fijo compartido; al crear una existencia física se puede elegir guardar y abrir una recepción inicial. */
@Composable
fun AccionesAsistenteAlta(
    actual: Int,
    total: Int,
    etiquetaGuardar: String,
    guardando: Boolean,
    onSiguiente: () -> Unit,
    onGuardar: () -> Unit,
    onGuardarYRegistrar: (() -> Unit)? = null,
) {
    ShalevBarraAcciones {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (actual < total) {
                ShalevPrimaryButton(
                    "Siguiente", onSiguiente, ShalevIcons.Siguiente,
                    modifier = Modifier.fillMaxWidth().testTag("alta.siguiente"),
                    mostrarTexto = true,
                )
            } else {
                onGuardarYRegistrar?.let { accion ->
                    ShalevSecondaryButton(
                        "Guardar y registrar compra inicial", accion, ShalevIcons.Inventario,
                        modifier = Modifier.fillMaxWidth().testTag("alta.guardar_recibir"),
                        enabled = !guardando,
                        mostrarTexto = true,
                    )
                }
                ShalevPrimaryButton(
                    etiquetaGuardar, onGuardar, ShalevIcons.Guardar,
                    modifier = Modifier.fillMaxWidth().testTag("alta.guardar"),
                    loading = guardando,
                    mostrarTexto = true,
                )
            }
        }
    }
}
