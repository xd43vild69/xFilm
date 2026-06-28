package com.example.xfilm

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xfilm.presentation.camera.CameraScreen
import com.example.xfilm.presentation.camera.CameraViewModel
import com.example.xfilm.presentation.camera.CameraViewModelFactory
import com.example.xfilm.ui.theme.XFilmTheme
class MainActivity : ComponentActivity() {
    private var cameraViewModel: CameraViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XFilmTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: CameraViewModel = viewModel(factory = CameraViewModelFactory(this))
                    cameraViewModel = viewModel
                    CameraScreen(modifier = Modifier.padding(innerPadding), viewModel = viewModel)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                cameraViewModel?.captureFrame()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    XFilmTheme {
        Text("xFilm - Analog Exposure Oracle")
    }
}