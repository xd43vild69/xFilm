package com.example.xfilm.presentation.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xfilm.calculation.colorscience.HurterDriffield
import com.example.xfilm.rendering.gl.LutGlSurfaceView
import com.example.xfilm.rendering.lut.LUT3DGenerator

private const val PREVIEW_WIDTH = 1280
private const val PREVIEW_HEIGHT = 720

/**
 * Main camera screen: requests permission, renders a live preview graded through
 * the H&D film LUT (OpenGL ES 3.0), and overlays the metered EV plus suggested
 * Pentax K1000 configurations.
 */
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel? = null,
) {
    val context = LocalContext.current
    val actualViewModel: CameraViewModel = viewModel ?: androidx.lifecycle.viewmodel.compose.viewModel(factory = CameraViewModelFactory(context))
    val uiState by actualViewModel.uiState.collectAsState()

    // Bake the film LUT once (Tri-X 400 by default).
    val lut = remember {
        LUT3DGenerator().generate(HurterDriffield.kodakTriX400())
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var glSurfaceViewRef by remember { mutableStateOf<LutGlSurfaceView?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    LutGlSurfaceView(ctx, lut) { surfaceTexture ->
                        surfaceTexture.setDefaultBufferSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                        val surface = Surface(surfaceTexture)
                        startWithPermission(ctx, actualViewModel, surface)
                    }.also { glSurfaceView ->
                        glSurfaceViewRef = glSurfaceView
                        actualViewModel.setGlSurfaceView(glSurfaceView)
                        // Keep pinhole effects OFF by default - only Kodak Tri-X 400 grain active
                        glSurfaceView.setPinholeEffects(vignetteEnabled = false, chromaticEnabled = false, softnessEnabled = false)
                    }
                },
            )
            ExposureOverlay(uiState)

            // Shutter button for capturing photos
            ShutterButton(
                onClick = { actualViewModel.captureFrame() }
            )
        } else {
            PermissionPrompt(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}

/**
 * Lint-safe entry point: re-checks the runtime permission immediately before
 * invoking the @RequiresPermission camera start.
 */
private fun startWithPermission(
    context: Context,
    viewModel: CameraViewModel,
    surface: Surface,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        viewModel.startCamera(surface)
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "xFilm necesita acceso a la cámara",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Para medir la exposición de la escena en tiempo real",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Button(onClick = onRequest) {
            Text("Conceder permiso")
        }
    }
}

@Composable
private fun ExposureOverlay(uiState: CameraUIState) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (val state = uiState) {
            is CameraUIState.Initializing -> Text("Inicializando…")
            is CameraUIState.Loading -> Text("Cargando cámara…")
            is CameraUIState.Metering -> {
                Text(
                    text = "EV ${"%.1f".format(state.ev)}",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "ISO ${state.iso} · ${formatShutter(state.exposureSeconds)} · f/${"%.1f".format(state.aperture)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Pentax K1000 (ASA 100):",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                state.configs.forEach { config ->
                    Text(
                        text = "  • $config",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is CameraUIState.Error -> Text(
                text = "Error: ${state.message}",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatShutter(seconds: Float): String {
    if (seconds <= 0f) return "—"
    if (seconds >= 1f) return "${"%.1f".format(seconds)}\""
    val denominator = (1f / seconds).toInt()
    return "1/$denominator"
}

@Composable
private fun ShutterButton(
    onClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = Color(0xFFFFFFFF).copy(alpha = if (isPressed) 0.9f else 0.7f),
                    shape = CircleShape
                )
                .clickable(
                    enabled = true,
                    onClick = {
                        isPressed = true
                        onClick()
                        // Reset pressed state after brief delay
                        scope.launch {
                            delay(200)
                            isPressed = false
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "●",
                color = Color(0xFF333333),
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}
