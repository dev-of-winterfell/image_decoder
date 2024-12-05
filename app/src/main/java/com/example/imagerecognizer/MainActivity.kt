// MainActivity.kt
package com.example.imagerecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.camera.core.ExperimentalGetImage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*

import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory


import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.example.imagerecognizer.ui.theme.ScanRegion
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

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
    var scanRegion by remember { mutableStateOf<ScanRegion?>(null) }
    var lastUpdateTime by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            initializeCamera()
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "Scale Reader",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Camera Preview with Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    color = Color.Black
                ) {
                    Box {
                        CameraPreview(
                            controller = cameraController,
                            scanRegion = scanRegion,
                            onWeightDetected = { detectedWeight ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastUpdateTime > 1000L) {
                                    weight = detectedWeight
                                    isScanning = false
                                    lastUpdateTime = currentTime
                                }
                            }
                        )

                        SegmentOverlay(onOverlayPositioned = { region ->
                            scanRegion = region
                        })

                        // Scanning indicator
                        if (isScanning) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "Scanning...",
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Weight Display
            WeightDisplay(weight = weight, isScanning = isScanning)
        }
    }
}
@Composable
private fun CameraPreview(
    controller: LifecycleCameraController,
    scanRegion: ScanRegion?,
    onWeightDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val textRecognizer = (context as TextRecognizerProvider).recognizer

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                // Add touch listener for manual focus
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            try {
                                val factory = SurfaceOrientedMeteringPointFactory(
                                    view.width.toFloat(),
                                    view.height.toFloat()
                                )
                                val point = factory.createPoint(event.x, event.y)
                                val action = FocusMeteringAction.Builder(point)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                controller.cameraControl?.startFocusAndMetering(action)
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "Error setting focus: ${e.message}")
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { previewView ->
        // Configure camera controller
        controller.apply {
            try {
                // Set initial center focus
                val factory = SurfaceOrientedMeteringPointFactory(
                    previewView.width.toFloat(),
                    previewView.height.toFloat()
                )
                val centerX = previewView.width / 2f
                val centerY = previewView.height / 2f
                val centerPoint = factory.createPoint(centerX, centerY)
                val action = FocusMeteringAction.Builder(centerPoint)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                cameraControl?.startFocusAndMetering(action)
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error setting initial focus: ${e.message}")
            }
        }

        controller.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context)
        ) { imageProxy ->
            processImage(imageProxy, scanRegion, onWeightDetected, textRecognizer)
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
private fun LifecycleCameraController.initializeCamera() {
    cameraControl?.let { control ->
        try {
            // Set camera control settings
            control.enableTorch(false)
            control.setLinearZoom(0.0f)

            // Configure camera for better text recognition
            val previewBuilder = Preview.Builder()
            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)

            // Apply camera characteristics using Camera2Interop
            val camera2Interop = Camera2Interop.Extender(imageCaptureBuilder)

            camera2Interop.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            camera2Interop.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            // Add auto-white balance
            camera2Interop.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO
            )
            // Set focus distance
            camera2Interop.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                0.0f  // Set to minimum focus distance for close-up shots
            )

            // Build the configuration
            val imageCapture = imageCaptureBuilder.build()

        } catch (e: Exception) {
            Log.e("Camera", "Error initializing camera: ${e.message}")
        }
    }
}
@OptIn(ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    scanRegion: ScanRegion?,
    onWeightDetected: (String) -> Unit,
    textRecognizer: TextRecognizer
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && scanRegion != null) {
        // Handle rotation correctly
        val rotation = imageProxy.imageInfo.rotationDegrees
        val isPortrait = rotation == 90 || rotation == 270

        // Adjust scale factors based on rotation
        val scaleX = if (isPortrait) {
            mediaImage.height.toFloat() / imageProxy.height
        } else {
            mediaImage.width.toFloat() / imageProxy.width
        }

        val scaleY = if (isPortrait) {
            mediaImage.width.toFloat() / imageProxy.width
        } else {
            mediaImage.height.toFloat() / imageProxy.height
        }

        // Calculate scan region coordinates with proper rotation handling
        val (scanLeft, scanTop, scanWidth, scanHeight) = when (rotation) {
            90 -> {
                val left = (scanRegion.top * scaleX).toInt()
                val top = (imageProxy.width - scanRegion.left - scanRegion.width) * scaleY
                val width = (scanRegion.height * scaleX).toInt()
                val height = (scanRegion.width * scaleY).toInt()
                listOf(left, top.toInt(), width, height)
            }
            270 -> {
                val left = (imageProxy.height - scanRegion.top - scanRegion.height) * scaleX
                val top = (scanRegion.left * scaleY).toInt()
                val width = (scanRegion.height * scaleX).toInt()
                val height = (scanRegion.width * scaleY).toInt()
                listOf(left.toInt(), top, width, height)
            }
            180 -> {
                val left = (imageProxy.width - scanRegion.left - scanRegion.width) * scaleX
                val top = (imageProxy.height - scanRegion.top - scanRegion.height) * scaleY
                val width = (scanRegion.width * scaleX).toInt()
                val height = (scanRegion.height * scaleY).toInt()
                listOf(left.toInt(), top.toInt(), width, height)
            }
            else -> {
                val left = (scanRegion.left * scaleX).toInt()
                val top = (scanRegion.top * scaleY).toInt()
                val width = (scanRegion.width * scaleX).toInt()
                val height = (scanRegion.height * scaleY).toInt()
                listOf(left, top, width, height)
            }
        }

        // Create input image
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        // Create the bounding box for text detection
        val cropRect = Rect(scanLeft, scanTop, scanLeft + scanWidth, scanTop + scanHeight)

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Log all detected text for debugging
                Log.d("TextRecognition", "All detected text: ${visionText.text}")

                // Filter text blocks that intersect with our region of interest
                val filteredText = visionText.textBlocks
                    .filter { block ->
                        val blockRect = block.boundingBox
                        if (blockRect != null) {
                            onWeightDetected(block.text)
                            val intersects = blockRect.intersect(cropRect)
                            Log.d("TextRecognition", "Block: ${block.text}, Intersects: $intersects")
                            intersects

                        } else false
                    }
                    .joinToString("\n") { it.text }

                Log.d("TextRecognition", "Filtered text: $filteredText")


                val weight = extractSevenSegmentNumber(filteredText)
                Log.d("TextRecognition", "Extracted weight: '$weight'")

                if (weight.isNotEmpty()) {
                    Log.d("TextRecognition", "Calling onWeightDetected with weight: '$weight'")
                    onWeightDetected(weight)
                }
            }
            .addOnFailureListener { e ->
                Log.e("TextRecognition", "Text recognition failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

private fun extractSevenSegmentNumber(text: String): String {
    val segmentPattern = """(\d+\.?\d*)""".toRegex() // More lenient pattern

    return text.lineSequence()
        .mapNotNull { line ->
            segmentPattern.find(line)?.value
        }
        .firstOrNull { candidate ->
            isSevenSegmentStyle(candidate)
            // Make validation more lenient - just check if it's a valid number
            candidate.toDoubleOrNull() != null
        } ?: ""
}

// Remove the seven-segment validation for now
private fun isSevenSegmentStyle(text: String): Boolean {
    // Characteristics of seven-segment displays
    val segmentCharacteristics = mapOf(
        '0' to 6, // Uses 6 segments
        '1' to 2, // Uses 2 segments
        '2' to 5, // Uses 5 segments
        '3' to 5, // Uses 5 segments
        '4' to 4, // Uses 4 segments
        '5' to 5, // Uses 5 segments
        '6' to 6, // Uses 6 segments
        '7' to 3, // Uses 3 segments
        '8' to 7, // Uses 7 segments
        '9' to 6, // Uses 6 segments
        '.' to 1  // Uses 1 segment
    )

    // Additional seven-segment characteristics
    val isValidFormat = text.matches("""^\d+\.?\d*$""".toRegex()) // Only numbers and optional decimal
    val hasReasonableLength = text.length <= 8 // Seven segment displays typically show limited digits
    val hasConsistentHeight = true // You could add actual height validation using boundingBox

    return text.all { char ->
        segmentCharacteristics.containsKey(char)
    } && isValidFormat && hasReasonableLength && hasConsistentHeight
}



@Composable
private fun WeightDisplay(weight: String, isScanning: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Weight Reading",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (weight.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = weight,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "kg",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                Text(
                    text = if (isScanning) "Align the scale display within the green box"
                    else "No weight detected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
@Composable
private fun SegmentOverlay(onOverlayPositioned: (ScanRegion) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width * 0.7f  // Increased width for better visibility
            val height = size.height * 0.15f
            val left = size.width * 0.15f
            val top = size.height * 0.4f

            // Draw outer stroke
            drawRect(
                color = Color(0xFF4CAF50),  // Material Green
                style = Stroke(width = 4.dp.toPx()),
                size = Size(width, height),
                topLeft = Offset(left, top)
            )

            // Draw corner indicators
            val cornerSize = 20.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val corners = listOf(
                Pair(left to top, Pair(0f to cornerSize, cornerSize to 0f)),
                Pair((left + width) to top, Pair(-cornerSize to 0f, 0f to cornerSize)),
                Pair(left to (top + height), Pair(0f to -cornerSize, cornerSize to 0f)),
                Pair((left + width) to (top + height), Pair(-cornerSize to 0f, 0f to -cornerSize))
            )

            corners.forEach { (point, lines) ->
                drawLine(
                    color = Color(0xFF4CAF50),
                    start = Offset(point.first, point.second),
                    end = Offset(point.first + lines.first.first, point.second + lines.first.second),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color(0xFF4CAF50),
                    start = Offset(point.first, point.second),
                    end = Offset(point.first + lines.second.first, point.second + lines.second.second),
                    strokeWidth = strokeWidth
                )
            }

            onOverlayPositioned(
                ScanRegion(
                    left = left,
                    top = top,
                    width = width,
                    height = height
                )
            )
        }
    }
}