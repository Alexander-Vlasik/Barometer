package com.barometer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.barometer.data.AppEventType
import com.barometer.data.GraphProvider
import com.barometer.data.db.AppEventEntity
import com.barometer.ui.MainScreen
import com.barometer.ui.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ProcessSessionTracker.markUiStarted()) {
            lifecycleScope.launch {
                GraphProvider.get(applicationContext).pressureRepository.insertEvent(
                    AppEventEntity(
                        timestampUtcMillis = System.currentTimeMillis(),
                        type = AppEventType.APP_START.name,
                        detail = "main_activity_on_create",
                    ),
                )
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
            ),
        )
        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModel.factory(application))
                MainScreen(viewModel = vm)
            }
        }
    }
}
