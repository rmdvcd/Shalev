package cu.shalev.app.vinculacion.qr

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Vista previa de CameraX + análisis QR con ML Kit (modelo EMPAQUETADO en el APK: funciona sin internet y no
 * descarga nada de Google Play Services). Solo vinculación: QR hardcodeado (más rápido y sin falsos positivos).
 *
 * La cámara vive EXACTAMENTE lo que este composable está en pantalla: al salir de la composición se hace
 * `unbindAll`, se cierra el detector y se apaga el hilo de análisis. Solo se entrega el PRIMER QR (AtomicBoolean).
 */
@Composable
fun CamaraEscaner(
    onQr: (String) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    descripcion: String = "Vista de la cámara. Apunta al código QR.",
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alDetectar by rememberUpdatedState(onQr)
    val alFallar by rememberUpdatedState(onError)
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }

    DisposableEffect(lifecycleOwner) {
        val entregado = AtomicBoolean(false)
        val ejecutor = Executors.newSingleThreadExecutor()
        val detector = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val futuro = ProcessCameraProvider.getInstance(context)
        var proveedor: ProcessCameraProvider? = null
        var liberado = false

        val analisis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { a -> a.setAnalyzer(ejecutor) { img -> analizar(img, detector, entregado) { c -> ContextCompat.getMainExecutor(context).execute { alDetectar(c) } } } }

        futuro.addListener({
            if (liberado) return@addListener
            try {
                val p = futuro.get().also { proveedor = it }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis)
            } catch (e: Exception) {
                // Sin detalles al usuario (Prompt 4): mensaje genérico y alternativa manual.
                Log.w("Shalev", "No se pudo abrir la cámara")
                alFallar()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            liberado = true
            analisis.clearAnalyzer()
            proveedor?.unbindAll()
            detector.close()
            ejecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.semantics { contentDescription = descripcion })
}

@SuppressLint("UnsafeOptInUsageError")
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analizar(
    img: ImageProxy,
    detector: com.google.mlkit.vision.barcode.BarcodeScanner,
    entregado: AtomicBoolean,
    onQr: (String) -> Unit,
) {
    val media = img.image
    if (media == null || entregado.get()) { img.close(); return }
    detector.process(InputImage.fromMediaImage(media, img.imageInfo.rotationDegrees))
        .addOnSuccessListener { codigos ->
            val valor = codigos.firstOrNull { !it.rawValue?.trim().isNullOrEmpty() }?.rawValue?.trim()
            if (valor != null && entregado.compareAndSet(false, true)) onQr(valor)
        }
        .addOnCompleteListener { img.close() }
}
