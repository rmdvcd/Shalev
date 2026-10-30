package cu.shalev.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize

/**
 * Campo de texto estándar: OutlinedTextField de 56dp, bordes redondeados (12dp; 16dp en áreas de varias líneas), borde sutil (outline ≥3:1), placeholder claro,
 * botón limpiar (X) pequeño de 18dp. Validación en tiempo real: [isError] pone el borde rojo y muestra
 * [errorText] debajo (TalkBack lo anuncia como error).
 *
 * P18 (A07), teclado:
 * - [imeAction] fija la tecla de acción: `Next` en los campos intermedios, `Done` en el último, `Search` en buscadores.
 * - Con `Next` y sin [onImeAction], el foco pasa solo al campo siguiente.
 * - [onImeAction] sustituye a la acción por defecto (por ejemplo, «Listo» = Guardar).
 *
 * P18 (A16), regla común de validación (`validarAlSalir = true` en todos los formularios):
 * - el error de un campo aparece cuando el usuario lo ha **editado y sale de él**, o cuando [forzarError] es true
 *   (tras pulsar Guardar / Siguiente con datos no válidos);
 * - a partir de ahí se actualiza en tiempo real: desaparece en cuanto el valor es válido;
 * - pasar por un campo sin escribir nada, o dejarlo vacío, no lo marca hasta pulsar Guardar.
 * Con `validarAlSalir = false` (por defecto) [isError] se muestra tal cual (filtros, buscadores).
 */
@Composable
fun ShalevTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    clearable: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction? = null,
    onImeAction: (() -> Unit)? = null,
    validarAlSalir: Boolean = false,
    forzarError: Boolean = false,
    /** P30: alineación del texto escrito (p. ej. centrado en los selectores de cantidad − [n] +). */
    textAlign: TextAlign? = null,
    /**
     * 0.21.0 (C10): filtro de entrada (qué caracteres se pueden escribir y cuántos). Si [keyboardOptions] se deja por
     * defecto, se usa también el teclado del filtro (numérico, teléfono…).
     */
    filtro: FiltroEntrada = FiltroEntrada.TEXTO,
    /**
     * 1.0.2: por defecto el campo se ajusta a su contenido (el texto y la etiqueta), con un ancho mínimo cómodo.
     * Los **buscadores** pasan `anchoCompleto = true`: ocupan todo el ancho disponible.
     */
    anchoCompleto: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val showClear = clearable && value.isNotEmpty() && enabled && !readOnly
    var editado by rememberSaveable { mutableStateOf(false) }
    var salio by rememberSaveable { mutableStateOf(false) }
    var conFoco by remember { mutableStateOf(false) }
    val errorVisible = ShalevValidacion.errorVisible(isError, validarAlSalir, forzarError, salio, vacioSinFoco = value.isEmpty() && !conFoco)
    val helper = if (errorVisible && errorText != null) errorText else supportingText
    val cambiar: (String) -> Unit = { escrito ->
        val nuevo = filtro.aceptar(value, escrito)
        if (nuevo != value) editado = true
        onValueChange(nuevo)
    }
    val teclado = if (keyboardOptions == KeyboardOptions.Default) filtro.teclado else keyboardOptions
    BloquearGestos(conFoco) // 0.27.0 (T5): escribiendo no se cambia de sección deslizando

    OutlinedTextField(
        value = value,
        onValueChange = cambiar,
        modifier = modifier
            .heightIn(min = ShalevSize.textField)
            .then(if (anchoCompleto) Modifier.fillMaxWidth() else Modifier.widthIn(min = ShalevSize.textFieldAnchoMin))
            .onFocusChanged { f ->
            salio = ShalevValidacion.salioTrasEditar(salio, conFoco, f.isFocused, editado)
            conFoco = f.isFocused
        },
        label = label?.let { { Text(it) } },
        // 0.27.0 (T6): el texto de ejemplo va centrado; el texto escrito y la etiqueta conservan su alineación.
        placeholder = placeholder?.let { { Text(it, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = if (showClear) {
            {
                IconButton(onClick = { cambiar("") }) {
                    Icon(ShalevIcons.Limpiar, contentDescription = "Limpiar ${label ?: "campo"}", modifier = Modifier.size(ShalevSize.iconSmall))
                }
            }
        } else null,
        supportingText = helper?.let { { Text(it) } },
        isError = errorVisible,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = if (imeAction != null) teclado.copy(imeAction = imeAction) else teclado,
        keyboardActions = onImeAction?.let { accion -> KeyboardActions(onDone = { accion() }, onNext = { accion() }, onGo = { accion() }, onSearch = { accion() }, onSend = { accion() }) }
            ?: keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyLarge.let { base -> if (textAlign != null) base.copy(textAlign = textAlign) else base },
        shape = RoundedCornerShape(if (singleLine) ShalevRadius.md else ShalevRadius.lg), // P24: bordes redondeados
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = cs.outline,
            focusedBorderColor = cs.primary,
            errorBorderColor = cs.error,
            unfocusedPlaceholderColor = cs.onSurfaceVariant,
            focusedPlaceholderColor = cs.onSurfaceVariant,
        ),
    )
}

/** P18 (A16): decisiones de la regla común de validación, puras para probarlas en JVM. */
object ShalevValidacion {
    /**
     * ¿Se muestra el error? Con [validarAlSalir], solo tras salir del campo editado o si se fuerza (al guardar).
     * Un campo vacío y sin foco ([vacioSinFoco]) solo se marca al forzar: así borrar un campo, o que el formulario
     * se vacíe tras guardar, no muestra errores de «obligatorio» antes de tiempo.
     */
    fun errorVisible(hayError: Boolean, validarAlSalir: Boolean, forzar: Boolean, salio: Boolean, vacioSinFoco: Boolean = false): Boolean =
        hayError && (!validarAlSalir || forzar || (salio && !vacioSinFoco))

    /** Nuevo valor de «salió tras editar» al cambiar el foco. Una vez true, se queda true. */
    fun salioTrasEditar(salio: Boolean, teniaFoco: Boolean, tieneFoco: Boolean, editado: Boolean): Boolean =
        salio || (teniaFoco && !tieneFoco && editado)
}
