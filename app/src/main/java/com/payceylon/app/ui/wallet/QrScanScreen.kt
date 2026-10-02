package com.payceylon.app.ui.wallet

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.payceylon.app.R
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.components.staggeredEntrance
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.PayCeylonPrimary
import java.util.concurrent.Executors

private val ScanWindowRadius = 20.dp
private val ScanWindowMaxSize = 340.dp

@Composable
fun QrScanScreen(
    /** The signed-in user's handle; scanning their own code is rejected. */
    ownHandle: String,
    onBack: () -> Unit,
    onRecipientFound: (PaymentRequest) -> Unit,
    onSendToUsername: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(scanMessage) {
        if (scanMessage != null) {
            kotlinx.coroutines.delay(2_500)
            scanMessage = null
        }
    }
    LightStatusBarIcons()

    val showCamera = hasCameraPermission && !cameraUnavailable

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val windowSize = minOf(ScanWindowMaxSize, maxWidth - 72.dp)

        if (showCamera) {
            QrCameraPreview(
                onCodeScanned = { raw ->
                    val request = parsePaymentRequest(raw)
                    if (request?.handle.equals(ownHandle, ignoreCase = true)) {
                        scanMessage = "That's your own PayCeylon code."
                        false
                    } else if (request != null) {
                        onRecipientFound(request)
                        true
                    } else {
                        scanMessage = "That QR code isn't a PayCeylon payment code."
                        false
                    }
                },
                onCameraUnavailable = { cameraUnavailable = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Figma's camera placeholder, shown until a live preview is available.
            Image(
                painter = painterResource(R.drawable.qr_scan_backdrop),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        ScanOverlay(windowSize = windowSize)

        // Equal-weight halves keep the scan window at the exact centre, matching ScanOverlay.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = "Send Money",
                    modifier = Modifier.staggeredEntrance(0),
                    style = TrimmedText,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(15.dp))
                Text(
                    text = "Scan QR to send instantly",
                    modifier = Modifier.staggeredEntrance(1),
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(39.dp))
            }
            Box(
                modifier = Modifier.size(windowSize),
                contentAlignment = Alignment.Center,
            ) {
                if (!showCamera) {
                    ScanPill(
                        label = if (cameraUnavailable) "Camera unavailable" else "Allow camera access",
                        onClick = {
                            if (!cameraUnavailable) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                ScanPill(
                    label = "Send to username instead",
                    onClick = onSendToUsername,
                    modifier = Modifier.staggeredEntrance(2),
                )
                Spacer(Modifier.height(12.dp))
                AnimatedVisibility(
                    visible = scanMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = scanMessage.orEmpty(),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = Color.White,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        CircleBackButton(
            onClick = onBack,
            background = Color.White.copy(alpha = 0.05f),
            iconTint = Color.White,
            modifier = Modifier
                .statusBarsPadding()
                .padding(20.dp),
        )
    }
}

@Composable
private fun ScanOverlay(windowSize: Dp) {
    // The window eases open on entry, then the frame breathes softly while scanning.
    val openProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        openProgress.animateTo(1f, tween(durationMillis = 520, easing = PayCeylonEasing))
    }
    val breathing = rememberInfiniteTransition(label = "scan frame")
    val frameAlpha by breathing.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan frame alpha",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = windowSize.toPx() * (0.9f + 0.1f * openProgress.value)
        val radius = ScanWindowRadius.toPx()
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val windowRect = RoundRect(
            left = topLeft.x,
            top = topLeft.y,
            right = topLeft.x + side,
            bottom = topLeft.y + side,
            cornerRadius = CornerRadius(radius),
        )
        // Dimmed surround with a rounded cut-out (Figma "Subtract" layer, black 80%).
        val scrim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
            addRoundRect(windowRect)
        }
        drawPath(scrim, Color.Black.copy(alpha = 0.8f))

        // 5dp inside stroke in the primary colour.
        val stroke = 5.dp.toPx()
        drawRoundRect(
            color = PayCeylonPrimary.copy(alpha = frameAlpha * openProgress.value),
            topLeft = topLeft + Offset(stroke / 2f, stroke / 2f),
            size = Size(side - stroke, side - stroke),
            cornerRadius = CornerRadius(radius - stroke / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
    }
}

@Composable
private fun ScanPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .height(40.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 15.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = InterFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(
    onCodeScanned: (String) -> Boolean,
    onCameraUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
    val currentOnCameraUnavailable by rememberUpdatedState(onCameraUnavailable)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var isDisposed = false
        var isHandled = false
        var lastRejected: String? = null

        providerFuture.addListener({
            if (isDisposed) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || isHandled) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                    scanner.process(input)
                        // ML Kit delivers results on the main thread.
                        .addOnSuccessListener { barcodes ->
                            val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
                            if (raw != null && !isHandled && !isDisposed && raw != lastRejected) {
                                isHandled = currentOnCodeScanned(raw)
                                if (!isHandled) lastRejected = raw
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (_: Exception) {
                currentOnCameraUnavailable()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            isDisposed = true
            cameraProvider?.unbindAll()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** The scanner is always dark, so status bar icons switch to light while it is shown. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = wasLight }
    }
}
