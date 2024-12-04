// MainActivity.kt
package com.example.imagerecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

interface TextRecognizerProvider {
    val recognizer: TextRecognizer
}

class MainActivity : ComponentActivity(), TextRecognizerProvider{


//    val cameraController by lazy {
//        LifecycleCameraController(applicationContext).apply {
//            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
//        }
//    }
    private companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    private fun hasRequiredPermissions() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasRequiredPermissions()) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        setContent {
            WeightScannerApp()
        }
    }




}

@Composable
fun WeightScannerApp() {
    var weight by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            CameraPreview(
                onWeightDetected = { detectedWeight ->
                    weight = detectedWeight
                    isScanning = false
                }
            )


                SegmentOverlay()

        }

        WeightDisplay(weight = weight)
    }
}
@Composable
private fun CameraPreview(onWeightDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val textRecognizer = (context as TextRecognizerProvider).recognizer

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { previewView ->
        try {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(context),
                { imageProxy -> processImage(imageProxy, onWeightDetected, textRecognizer) }
            )

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

//@Composable
//private fun CameraPreview(onWeightDetected: (String) -> Unit) {
//    val context = LocalContext.current
//    val textRecognizer = (context as TextRecognizerProvider).recognizer
//    val lifecycleOwner = LocalLifecycleOwner.current
//    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
//
//    AndroidView(
//        factory = { ctx ->
//            PreviewView(ctx).apply {
//                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
//            }
//        },
//        modifier = Modifier.fillMaxSize()
//    ) { previewView ->
//        val preview = Preview.Builder().build()
//        val imageAnalysis = ImageAnalysis.Builder()
//            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//            .build()
//
//        imageAnalysis.setAnalyzer(
//            ContextCompat.getMainExecutor(context)
//        ) { imageProxy ->
//            processImage(imageProxy, onWeightDetected, textRecognizer)
//        }
//
//        try {
//            cameraProviderFuture.get().unbindAll()
//            cameraProviderFuture.get().bindToLifecycle(
//                lifecycleOwner,
//                CameraSelector.DEFAULT_BACK_CAMERA,
//                preview,
//                imageAnalysis
//            )
//            preview.setSurfaceProvider(previewView.surfaceProvider)
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//}

@OptIn(ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    onWeightDetected: (String) -> Unit,
    textRecognizer: TextRecognizer
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val weight = extractSevenSegmentNumber(visionText.text)
                if (weight.isNotEmpty()) {
                    onWeightDetected(weight)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

private fun extractSevenSegmentNumber(text: String): String {
    val segmentPattern = """(\d{1,3}(?:\.\d{1,2})?)""".toRegex()

    return text.lineSequence()
        .mapNotNull { line ->
            segmentPattern.find(line)?.value
        }
        .firstOrNull { candidate ->
            isSevenSegmentStyle(candidate)
        } ?: ""
}

private fun isSevenSegmentStyle(text: String): Boolean {
    val segmentCharacteristics = listOf(
        '0' to 6, '1' to 2, '2' to 5, '3' to 5,
        '4' to 4, '5' to 5, '6' to 6, '7' to 3,
        '8' to 7, '9' to 6, '.' to 1
    ).toMap()

    return text.all { char ->
        segmentCharacteristics.containsKey(char)
    }
}

@Composable
private fun WeightDisplay(weight: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Text(
            text = if (weight.isEmpty()) "Scanning..." else "Weight: $weight",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SegmentOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color.Green,
                style = Stroke(width = 2.dp.toPx()),
                size = Size(size.width * 0.5f, size.height * 0.2f),
                topLeft = Offset(
                    size.width * 0.25f,
                    size.height * 0.4f
                )
            )
        }
    }
}