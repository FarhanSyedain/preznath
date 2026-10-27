package com.yimberzol.preznath.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yimberzol.preznath.domain.AnalysisResult
import com.yimberzol.preznath.domain.VerdictLabel
import com.yimberzol.preznath.ui.theme.FraudRed
import com.yimberzol.preznath.ui.theme.Ink
import com.yimberzol.preznath.ui.theme.Ink2
import com.yimberzol.preznath.ui.theme.Rule
import com.yimberzol.preznath.ui.theme.SuspiciousRed

@Composable
fun ResultScreen(
    result: AnalysisResult,
    engineLabel: String,
    modifier: Modifier = Modifier,
) {
    val verdict = result.verdict
    val color = when (verdict.label) {
        VerdictLabel.SAFE -> Ink
        VerdictLabel.SUSPICIOUS -> SuspiciousRed
        VerdictLabel.FRAUD -> FraudRed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Rule)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "VERDICT",
            style = MaterialTheme.typography.labelSmall,
            color = Ink2,
        )
        Text(
            text = verdict.label.name,
            style = MaterialTheme.typography.displayLarge,
            color = color,
        )
        Text(
            text = "Score ${verdict.score} / 100 · confidence ${(verdict.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
        )
        Text(
            text = "Engine: $engineLabel · ${result.totalLatencyMs} ms · lang ${result.language}",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink2,
        )

        HorizontalDivider(color = Rule)

        Section("REASONS", verdict.reasons, Ink)
        Section("ADVICE", verdict.advice, Ink)

        if (result.entities.isNotEmpty()) {
            HorizontalDivider(color = Rule)
            Text("ENTITIES", style = MaterialTheme.typography.labelSmall, color = Ink2)
            result.entities.forEach { e ->
                val note = e.note?.let { " — $it" }.orEmpty()
                val valid = when (e.valid) {
                    true -> " ok"
                    false -> " flag"
                    null -> ""
                }
                Text(
                    text = "${e.kind.name.lowercase()} · ${e.value}$valid$note",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelSmall, color = Ink2)
    items.forEachIndexed { i, line ->
        Text(
            text = "${(i + 1).toString().padStart(2, '0')}  $line",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}
