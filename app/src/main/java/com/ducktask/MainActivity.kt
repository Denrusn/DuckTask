package com.ducktask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ducktask.ui.screens.MainScreen
import com.ducktask.ui.screens.MainViewModel
import com.ducktask.ui.theme.DuckTaskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DuckTaskApp

        setContent {
            DuckTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModel.Factory(app.repository)
                    )
                    val tasks by viewModel.pendingTasks.collectAsState()

                    MainScreen(
                        viewModel = viewModel,
                        tasks = tasks
                    )
                }
            }
        }
    }
}
