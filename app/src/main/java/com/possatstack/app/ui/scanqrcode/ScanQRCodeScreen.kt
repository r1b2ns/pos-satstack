package com.possatstack.app.ui.scanqrcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.possatstack.app.R
import com.possatstack.app.ui.components.PrimaryButton
import com.possatstack.app.ui.theme.BitcoinOrange
import java.util.concurrent.Executors

/**
 * Scans a QR code from the camera or pulls a string from the clipboard.
 * Whatever is captured is delivered through [onResult]; the caller is
 * responsible for popping the screen — this composable does not navigate.
 */
@Composable
fun ScanQRCodeScreen(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val hasCamera =
        remember {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        }

    if (!hasCamera) {
        NoCameraEmptyState(onResult = onResult)
        return
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasPermission = granted },
        )

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraScanner(onResult = onResult)
        } else {
            PermissionRationale(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }

        PasteFooter(
            onResult = onResult,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
        )
    }
}

@Composable
private fun CameraScanner(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val currentOnResult by rememberUpdatedState(onResult)
    var reported by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview =
                        Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                    val analysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    executor,
                                    QrCodeAnalyzer { decoded ->
                                        if (!reported) {
                                            reported = true
                                            previewView.post { currentOnResult(decoded) }
                                        }
                                    },
                                )
                            }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        ScannerOverlay(modifier = Modifier.fillMaxSize())
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }
}

/**
 * Dimmed overlay with a square cutout the user lines the QR code up with.
 */
@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
    val accent = BitcoinOrange
    val scrim = Color.Black.copy(alpha = 0.55f)

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val side = size.minDimension * 0.7f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val right = left + side
        val bottom = top + side
        val corner = 24f

        // Four scrim rectangles framing the cutout. Avoids BlendMode.Clear,
        // which needs an offscreen layer to actually punch through.
        drawRect(color = scrim, topLeft = Offset.Zero, size = Size(size.width, top))
        drawRect(
            color = scrim,
            topLeft = Offset(0f, bottom),
            size = Size(size.width, size.height - bottom),
        )
        drawRect(
            color = scrim,
            topLeft = Offset(0f, top),
            size = Size(left, side),
        )
        drawRect(
            color = scrim,
            topLeft = Offset(right, top),
            size = Size(size.width - right, side),
        )

        drawRoundRect(
            color = accent,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 4f),
        )
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = null,
            tint = BitcoinOrange,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.scan_qr_permission_title),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.scan_qr_permission_message),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton(
            label = stringResource(R.string.scan_qr_grant_permission),
            onClick = onRequest,
        )
    }
}

@Composable
private fun NoCameraEmptyState(onResult: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.NoPhotography,
                contentDescription = null,
                tint = Color(0xFF9E9E9E),
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.scan_qr_no_camera_title),
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.scan_qr_no_camera_message),
                color = Color(0xFF666666),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        PasteFooter(
            onResult = onResult,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
        )
    }
}

@Composable
private fun PasteFooter(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    PrimaryButton(
        label = stringResource(R.string.scan_qr_paste),
        onClick = {
            val pasted = clipboard.getText()?.text?.trim().orEmpty()
            if (pasted.isNotEmpty()) onResult(pasted)
        },
        modifier = modifier.fillMaxWidth(),
    )
}
