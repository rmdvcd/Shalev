package cu.shalev.app.ayuda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import cu.shalev.designsystem.token.ShalevSpacing

object AyudaTags {
    fun tema(i: Int) = "ayuda.tema_$i"
    fun seccion(i: Int) = "ayuda.seccion_$i"
}

/** 0.31.0 (P7): icono de cada sección. */
private fun iconoSeccion(c: CategoriaAyuda): ImageVector = when (c) {
    CategoriaAyuda.INICIO -> ShalevIcons.Inicio
    CategoriaAyuda.INVENTARIO -> ShalevIcons.Inventario
    CategoriaAyuda.SERVICIOS -> ShalevIcons.Servicios
    CategoriaAyuda.VENTA -> ShalevIcons.Venta
    CategoriaAyuda.REGISTROS -> ShalevIcons.Registros
    CategoriaAyuda.HERRAMIENTAS -> ShalevIcons.Herramientas
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
    val modulos = cu.shalev.app.common.LocalPermisosApp.current.modulos
    val secciones = AyudaContenido.seccionesPara(modulos)

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
            secciones.forEachIndexed { indice, (categoria, ayudas) ->
                val ayuda = ayudas.first()
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
                        modifier = Modifier.testTag(AyudaTags.tema(indice))
                            .semantics { stateDescription = if (abiertoCategoria == categoria.titulo) "Abierto" else "Cerrado" },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                            Icon(iconoSeccion(categoria), contentDescription = null)
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