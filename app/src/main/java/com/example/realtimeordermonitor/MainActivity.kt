package com.example.realtimeordermonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.realtimeordermonitor.ui.OrderScreen
import com.example.realtimeordermonitor.viewmodel.OrderViewModel
import com.example.realtimeordermonitor.ui.theme.RealtimeOrderMonitorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: OrderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RealtimeOrderMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OrderScreen(viewModel = viewModel)
                }
            }
        }
    }
}