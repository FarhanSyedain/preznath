package com.yimberzol.preznath.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yimberzol.preznath.ui.screens.AnalyzeScreen
import com.yimberzol.preznath.ui.theme.Paper
import com.yimberzol.preznath.ui.theme.PreznathTheme
import com.yimberzol.preznath.ui.viewmodels.AnalyzeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AnalyzeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            PreznathTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
                    AnalyzeScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            viewModel.applySharedText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        }
    }
}
