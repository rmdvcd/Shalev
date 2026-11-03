package cu.shalev.app.ayuda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.app.ayuda.AyudaVentana
import cu.shalev.domain.model.Modulo

object AyudaTags {
    fun tema(i: Int) = "ayuda.tema_$i"
    fun seccion(i: Int) = "ayuda.seccion_$i"
}

/** 0.31.0 (P7): icono de cada tema del manual (el que se ve en la sección a la que pertenece). */
private fun iconoTema(titulo: String): ImageVector = when (titulo) {
    "Primeros pasos", "Consultar el negocio" -> ShalevIcons.Inicio
    "Abrir y cerrar el turno" -> ShalevIcons.Venta
    "Vender" -> ShalevIcons.Venta
    "Cobrar por transferencia" -> ShalevIcons.PagoElectronico
    "Ver lo vendido", "Corregir operaciones" -> ShalevIcons.Registros
    "Agregar productos", "Buscar y filtrar el inventario" -> ShalevIcons.Inventario
    "Insumos y elaborados" -> ShalevIcons.Inventario
    "Servicios" -> ShalevIcons.Servicios
    "Guardar una copia", "Restaurar una copia" -> ShalevIcons.Respaldo
    "Licencia" -> ShalevIcons.Licencia
    "Apps vinculadas" -> ShalevIcons.Movil
    "Configurar el negocio" -> ShalevIcons.Configurar
    "Pago electrónico" -> ShalevIcons.PagoElectronico
    "Precios" -> ShalevIcons.Precios
    else -> ShalevIcons.Soporte
}

/** 0.31.0 (P7): icono de cada sección. */
private fun iconoSeccion(c: CategoriaAyuda): ImageVector = when (c) {
    CategoriaAyuda.INICIO -> ShalevIcons.Inicio
    CategoriaAyuda.INVENTARIO -> ShalevIcons.Inventario
    CategoriaAyuda.SERVICIOS -> ShalevIcons.Servicios
    CategoriaAyuda.VENTA -> ShalevIcons.Venta
    CategoriaAyuda.REGISTROS -> ShalevIcons.Registros
    CategoriaAyuda.AJUSTES -> ShalevIcons.Configurar
    CategoriaAyuda.RESPALDO -> ShalevIcons.Respaldo
    CategoriaAyuda.LICENCIA -> ShalevIcons.Licencia
    CategoriaAyuda.EMPLEADOS -> ShalevIcons.Movil
    CategoriaAyuda.SOPORTE -> ShalevIcons.Soporte
}

/**
 * Ajustes → Manual de usuario: resumen por ventanas y temas. Sin estado de negocio (no necesita ViewModel); 
 * solo qué tema está abierto.
 */
@Composable
fun AyudaScreen(onBack: () -> Unit) {
    var abierto by rememberSaveable { mutableStateOf<String?>(null) }
    val modulos = cu.shalev.app.common.LocalPermisosApp.current.modulos

    Scaffold(topBar = { ShalevTopBar(title = "Manual de usuario", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            item {
                ShalevCard(tone = CardTone.Tonal) {
                    Text(
                        AyudaContenido.INTRO,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // Iterar sobre cada categoría de ayuda
            for (categoria in CategoriaAyuda.values()) {
                val ayuda = AyudaContenido.ayudaPara(categoria)
                // Mostrar la sección de la categoría
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = ShalevSpacing.md, bottom = ShalevSpacing.xs)
                            .testTag(AyudaTags.seccion(categoria.ordinal)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                    ) {
                        Icon(iconoSeccion(categoria), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                categoria.titulo,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                categoria.detalle,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth().padding(top = ShalevSpacing.xs),
                            )
                        }
                    }
                }
                // Mostrar los temas/ventanas de ayuda para esta categoría
                item {
                    var abiertoCategoria by rememberSaveable { mutableStateOf<String?>(null) }
                    ShalevCard(
                        onClick = { abiertoCategoria = if (abiertoCategoria == categoria.titulo) null else categoria.titulo },
                        modifier = Modifier.testTag(AyudaTags.seccion(categoria.ordinal))
                            .semantics { stateDescription = if (abiertoCategoria == categoria.titulo) "Abierto" else "Cerrado" },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                            Icon(iconoTema(ayuda.titulo), contentDescription = null)
                            Text(
                                ayuda.titulo,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            Icon(
                                if (abiertoCategoria == categoria.titulo) ShalevIcons.Quitar else ShalevIcons.Agregar,
                                contentDescription = null,
                            )
                        }
                        if (abiertoCategoria == categoria.titulo) {
                            AyudaContenido.INTRO.let { intro ->
                                Text(intro, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = ShalevSpacing.xs))
                            }
                            ayuda.pasos.forEachIndexed { n, paso ->
                                Text(
                                    "${n + 1}. $paso",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = ShalevSpacing.xs, bottom = ShalevSpacing.xs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}