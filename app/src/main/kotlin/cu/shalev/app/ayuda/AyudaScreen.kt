package cu.shalev.app.ayuda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

object AyudaTags {
    fun tema(i: Int) = "ayuda.tema_$i"
    fun seccion(i: Int) = "ayuda.seccion_$i"
}

/** 0.31.0 (P7): icono de cada tema del manual (el que se ve en la sección a la que pertenece). */
private fun iconoTema(titulo: String): ImageVector = when (titulo) {
    "Primeros pasos" -> ShalevIcons.Inicio
    "Abrir y cerrar el turno" -> ShalevIcons.Venta
    "Vender" -> ShalevIcons.Venta
    "Cobrar por transferencia" -> ShalevIcons.PagoElectronico
    "Ver lo vendido" -> ShalevIcons.Registros
    "Agregar productos" -> ShalevIcons.Inventario
    "Insumos y elaborados" -> ShalevIcons.Inventario
    "Servicios" -> ShalevIcons.Servicios
    "Guardar una copia" -> ShalevIcons.Respaldo
    "Licencia" -> ShalevIcons.Licencia
    "Apps vinculadas" -> ShalevIcons.Movil
    else -> ShalevIcons.Soporte
}

/** 0.31.0 (P7): icono de cada sección. */
private fun iconoSeccion(c: CategoriaAyuda): ImageVector = when (c) {
    CategoriaAyuda.EMPEZAR -> ShalevIcons.Configurar
    CategoriaAyuda.VENDER -> ShalevIcons.Venta
    CategoriaAyuda.INVENTARIO -> ShalevIcons.Inventario
    CategoriaAyuda.DATOS -> ShalevIcons.Respaldo
    CategoriaAyuda.EMPLEADOS -> ShalevIcons.Movil
    CategoriaAyuda.AYUDA -> ShalevIcons.Soporte
}

/**
 * Ajustes → Ayuda: manual por secciones y temas. Sin estado de negocio (no necesita ViewModel); solo qué tema está
 * abierto. Los esquemas se dibujan con formas del sistema de diseño, así que no hace falta guardar imágenes.
 */
@Composable
fun AyudaScreen(onBack: () -> Unit) {
    var abierto by rememberSaveable { mutableStateOf<String?>(null) }
    val secciones = AyudaContenido.seccionesPara(cu.shalev.app.common.LocalPermisosApp.current.modulos) // 0.21.0 (C12)
    // Los temas en el orden del manual, con su sección: la etiqueta de pruebas es el número de tema (como antes).
    val plan = secciones.flatMapIndexed { i, (_, temas) -> temas.mapIndexed { j, t -> Triple(i, j, t) } }
    Scaffold(topBar = { ShalevTopBar(title = "Ayuda", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            item {
                ShalevCard(tone = CardTone.Tonal) {
                    Text(
                        AyudaContenido.INTRO, style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            plan.forEachIndexed { plano, (i, j, tema) ->
                if (j == 0) {
                    val categoria = secciones[i].first
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = ShalevSpacing.md, bottom = ShalevSpacing.xs)
                                .testTag(AyudaTags.seccion(i)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                        ) {
                            Icon(iconoSeccion(categoria), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    categoria.titulo, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Text(
                                    categoria.detalle, style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(top = ShalevSpacing.xs),
                                )
                            }
                        }
                    }
                }
                item {
                    val clave = "${secciones[i].first.name}/$j"
                    val expandido = abierto == clave
                    ShalevCard(
                        onClick = { abierto = if (expandido) null else clave },
                        modifier = Modifier.testTag(AyudaTags.tema(plano))
                            .semantics { stateDescription = if (expandido) "Abierto" else "Cerrado" },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                            Icon(iconoTema(tema.titulo), contentDescription = null)
                            Text(
                                tema.titulo, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            Icon(if (expandido) ShalevIcons.Quitar else ShalevIcons.Agregar, contentDescription = null)
                        }
                        if (expandido) {
                            tema.pasos.forEachIndexed { n, paso ->
                                Text(
                                    "${n + 1}. $paso", style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(top = ShalevSpacing.xs),
                                )
                            }
                            tema.esquema?.let { Esquema(it, Modifier.padding(top = ShalevSpacing.xs)) }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ esquemas (0.31.0, P7)

/**
 * Esquema del tema: dibujado con rectángulos y texto, sin imágenes. Se usan formas del sistema de diseño para que
 * se vea igual en claro y oscuro y no haya que mantener capturas.
 */
@Composable
private fun Esquema(tipo: EsquemaAyuda, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        when (tipo) {
            EsquemaAyuda.BARRA -> Fila(
                listOf("Inicio" to true, "Inventario" to false, "Servicios" to false, "Registros" to false, "Ajustes" to false),
            )
            EsquemaAyuda.TURNO -> Flujo(
                listOf("Turno cerrado", "Abrir turno", "Vender", "Cerrar turno"),
            )
            EsquemaAyuda.VENTA -> Flujo(
                listOf("Nueva venta", "Productos o Servicios", "Efectivo o transferencia", "Confirmar"),
            )
            EsquemaAyuda.RESPALDO -> Flujo(
                listOf("Este teléfono", "Archivo .shalev cifrado", "El teléfono nuevo"),
            )
        }
    }
}

@Composable
private fun Fila(cajas: List<Pair<String, Boolean>>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        cajas.forEach { (texto, marcada) ->
            Box(
                Modifier.weight(1f)
                    .background(
                        color = if (marcada) cs.primaryContainer else cs.surfaceContainerHighest,
                        shape = RoundedCornerShape(ShalevRadius.sm),
                    )
                    .padding(vertical = ShalevSpacing.xs, horizontal = ShalevSize.strokeRegular),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    texto, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = if (marcada) cs.onPrimaryContainer else cs.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Cajas una debajo de otra con una flecha entre ellas: un camino de pocos pasos. */
@Composable
private fun Flujo(pasos: List<String>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        pasos.forEachIndexed { i, paso ->
            Box(
                Modifier.background(cs.surfaceContainerHighest, RoundedCornerShape(ShalevRadius.sm))
                    .padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
            ) {
                Text(paso, style = MaterialTheme.typography.labelLarge)
            }
            if (i < pasos.lastIndex) {
                Text("↓", style = MaterialTheme.typography.titleMedium, color = cs.primary)
            }
        }
    }
}

