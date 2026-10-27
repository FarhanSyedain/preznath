package com.yimberzol.preznath.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yimberzol.preznath.domain.AnalysisResult
import com.yimberzol.preznath.domain.VerdictLabel
import com.yimberzol.preznath.ui.components.PipelineTrace
import com.yimberzol.preznath.ui.theme.FraudRed
import com.yimberzol.preznath.ui.theme.Ink
import com.yimberzol.preznath.ui.theme.Ink2
import com.yimberzol.preznath.ui.theme.PantoneRed
import com.yimberzol.preznath.ui.theme.Paper
import com.yimberzol.preznath.ui.theme.Rule
import com.yimberzol.preznath.ui.theme.Surface
import com.yimberzol.preznath.ui.theme.SuspiciousRed
import com.yimberzol.preznath.ui.viewmodels.AnalyzeViewModel

private data class DemoExample(
    val title: String,
    val expected: String,
    val text: String,
    val upiId: String = "",
)

private val EXAMPLES = listOf(
    DemoExample(
        title = "SBI KYC",
        expected = "FRAUD",
        text = "Dear customer your SBI YONO account will be blocked today. Update KYC immediately: http://bit.ly/sbi-kyc-update or call 9876543210",
    ),
    DemoExample(
        title = "Hindi bijli",
        expected = "SUSPICIOUS",
        text = "आपका बिजली कनेक्शन आज रात 9:30 बजे काट दिया जाएगा। बिल अपडेट करने के लिए तुरंत कॉल करें 9123456789",
    ),
    DemoExample(
        title = "Dinner",
        expected = "SAFE",
        text = "Hey, sending you 500 for dinner, check your GPay",
        upiId = "priya.s@okicici",
    ),
)

@Composable
fun AnalyzeScreen(viewModel: AnalyzeViewModel) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("PREZNATH", style = MaterialTheme.typography.displayMedium, color = PantoneRed)
        Text(
            "Team Yimberzol · offline-first UPI SMS screen",
            style = MaterialTheme.typography.labelSmall,
            color = Ink2,
        )
        HorizontalDivider(color = Ink)

        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, color = Ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EXAMPLES.forEach { ex ->
                TextButton(onClick = { viewModel.loadExample(ex.text, ex.upiId) }) {
                    Column {
                        Text(ex.title, color = Ink)
                        Text(ex.expected, style = MaterialTheme.typography.labelSmall, color = Ink2)
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.message,
            onValueChange = viewModel::onMessageChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Message") },
            minLines = 5,
        )
        OutlinedTextField(
            value = state.upiId,
            onValueChange = viewModel::onUpiChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("UPI ID (optional)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.url,
            onValueChange = viewModel::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL (optional)") },
            singleLine = true,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = state.cloudEnabled, onCheckedChange = viewModel::onCloudToggle)
            Text(
                if (state.cloudEnabled) "Cloud models (optional)" else "Offline analyzer",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink2,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = viewModel::analyze,
                enabled = !state.analyzing && state.message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PantoneRed, contentColor = Paper),
            ) {
                Text(if (state.analyzing) "ANALYZING…" else "ANALYZE")
            }
            OutlinedButton(onClick = { viewModel.loadExample("", "") }) {
                Text("CLEAR")
            }
        }

        state.error?.let {
            Text(it, color = FraudRed, style = MaterialTheme.typography.bodyMedium)
        }

        Text("Engine: ${state.engineLabel}", style = MaterialTheme.typography.labelSmall, color = Ink2)

        PipelineTrace(stages = state.stages)

        state.result?.let { VerdictCard(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VerdictCard(result: AnalysisResult) {
    val border = when (result.verdict.label) {
        VerdictLabel.FRAUD -> PantoneRed
        VerdictLabel.SUSPICIOUS -> SuspiciousRed
        VerdictLabel.SAFE -> Ink
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border)
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("VERDICT", style = MaterialTheme.typography.labelSmall, color = Ink2)
        Text(
            result.verdict.label.name,
            style = MaterialTheme.typography.displayMedium,
            color = if (result.verdict.label == VerdictLabel.SAFE) Ink else PantoneRed,
        )
        Text(
            "Score ${result.verdict.score}/100 · ${(result.verdict.confidence * 100).toInt()}% · ${result.totalLatencyMs}ms",
            style = MaterialTheme.typography.labelLarge,
            color = Ink2,
        )
        if (result.verdict.reasons.isNotEmpty()) {
            Text("WHY", style = MaterialTheme.typography.labelSmall, color = Ink2)
            result.verdict.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Ink) }
        }
        if (result.verdict.advice.isNotEmpty()) {
            Text("WHAT TO DO", style = MaterialTheme.typography.labelSmall, color = Ink2)
            result.verdict.advice.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Ink) }
        }
    }
}
