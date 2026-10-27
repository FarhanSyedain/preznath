package com.yimberzol.preznath.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yimberzol.preznath.domain.PipelineStage
import com.yimberzol.preznath.domain.StageStatus
import com.yimberzol.preznath.ui.theme.Ink
import com.yimberzol.preznath.ui.theme.Ink2
import com.yimberzol.preznath.ui.theme.PantoneRed
import com.yimberzol.preznath.ui.theme.Paper
import com.yimberzol.preznath.ui.theme.Rule

@Composable
fun PipelineTrace(
    stages: List<PipelineStage>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Rule)
            .background(Paper)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "PIPELINE",
            style = MaterialTheme.typography.labelSmall,
            color = Ink2,
        )
        stages.forEach { stage ->
            StageRow(stage)
        }
    }
}

@Composable
private fun StageRow(stage: PipelineStage) {
    val index = stage.index.toString().padStart(2, '0')
    val statusColor = when (stage.status) {
        StageStatus.RUNNING -> PantoneRed
        StageStatus.DONE -> Ink
        StageStatus.FAILED -> PantoneRed
        else -> Ink2
    }
    val statusLabel = when (stage.status) {
        StageStatus.PENDING -> "····"
        StageStatus.RUNNING -> "RUN "
        StageStatus.DONE -> "DONE"
        StageStatus.SKIPPED -> "SKIP"
        StageStatus.FAILED -> "FAIL"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = index,
            style = MaterialTheme.typography.labelLarge,
            color = statusColor,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stage.label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )
            val detail = stage.signal?.evidence ?: stage.error
            if (!detail.isNullOrBlank() && stage.status == StageStatus.DONE) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink2,
                    maxLines = 2,
                )
            }
        }
        Box {
            Text(
                text = if (stage.status == StageStatus.DONE && stage.latencyMs > 0) {
                    "${stage.latencyMs}ms"
                } else {
                    statusLabel
                },
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
        }
    }
}
