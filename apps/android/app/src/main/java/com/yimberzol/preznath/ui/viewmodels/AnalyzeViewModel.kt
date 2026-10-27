package com.yimberzol.preznath.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yimberzol.preznath.application.AnalyzeMessageUseCase
import com.yimberzol.preznath.data.AnalyzerFactory
import com.yimberzol.preznath.domain.AnalysisResult
import com.yimberzol.preznath.domain.Message
import com.yimberzol.preznath.domain.PipelineStage
import com.yimberzol.preznath.domain.StageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalyzeUiState(
    val message: String = "",
    val upiId: String = "",
    val url: String = "",
    val cloudEnabled: Boolean = false,
    val analyzing: Boolean = false,
    val stages: List<PipelineStage> = AnalyzeMessageUseCase.stagePlan(),
    val result: AnalysisResult? = null,
    val error: String? = null,
    val engineLabel: String = "offline",
)

class AnalyzeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(AnalyzeUiState())
    val state: StateFlow<AnalyzeUiState> = _state.asStateFlow()

    fun onMessageChange(value: String) = _state.update { it.copy(message = value, error = null) }
    fun onUpiChange(value: String) = _state.update { it.copy(upiId = value) }
    fun onUrlChange(value: String) = _state.update { it.copy(url = value) }
    fun onCloudToggle(enabled: Boolean) = _state.update { it.copy(cloudEnabled = enabled) }

    fun applySharedText(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(message = text, result = null, error = null) }
    }

    fun loadExample(text: String, upiId: String = "", url: String = "") {
        _state.update {
            it.copy(
                message = text,
                upiId = upiId,
                url = url,
                result = null,
                error = null,
                stages = AnalyzeMessageUseCase.stagePlan(),
            )
        }
    }

    fun analyze() {
        val current = _state.value
        val text = current.message.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(error = "Paste a message to analyze.") }
            return
        }
        _state.update {
            it.copy(
                analyzing = true,
                error = null,
                result = null,
                stages = AnalyzeMessageUseCase.stagePlan(),
            )
        }
        viewModelScope.launch {
            try {
                val message = Message(
                    text = text,
                    upiId = current.upiId.trim().ifBlank { null },
                    url = current.url.trim().ifBlank { null },
                )
                if (current.cloudEnabled) {
                    runCloud(message)
                } else {
                    runOffline(message)
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        analyzing = false,
                        error = t.message ?: "Analysis failed.",
                    )
                }
            }
        }
    }

    private suspend fun runOffline(message: Message) {
        val analyzer = withContext(Dispatchers.IO) {
            AnalyzerFactory.offline(getApplication())
        }
        withContext(Dispatchers.Default) {
            for (event in analyzer.stream(message)) {
                _state.update { state ->
                    val stages = state.stages.map { s ->
                        when {
                            s.index == event.stage.index -> event.stage
                            s.status == StageStatus.PENDING && event.stage.status == StageStatus.RUNNING &&
                                s.index < event.stage.index -> s
                            else -> s
                        }
                    }.let { list ->
                        // Keep plan rows; replace matching index
                        AnalyzeMessageUseCase.stagePlan().map { plan ->
                            list.firstOrNull { it.index == plan.index } ?: plan
                        }.map { row ->
                            when {
                                row.index == event.stage.index -> event.stage
                                event.stage.status == StageStatus.DONE && row.index < event.stage.index &&
                                    row.status == StageStatus.PENDING ->
                                    row.copy(status = StageStatus.DONE)
                                else -> row
                            }
                        }
                    }
                    state.copy(
                        stages = stages,
                        result = event.result,
                        analyzing = event.result == null,
                        engineLabel = event.result?.engine ?: "offline/${analyzer.similarityEngine}",
                    )
                }
            }
        }
        _state.update { it.copy(analyzing = false) }
    }

    private suspend fun runCloud(message: Message) {
        val result = withContext(Dispatchers.IO) {
            try {
                AnalyzerFactory.cloud().analyze(message)
            } catch (t: Throwable) {
                // Fall back to offline — never block the user.
                val offline = AnalyzerFactory.offline(getApplication(), stageDelayMs = 80L)
                offline.analyze(message).copy(
                    engine = "offline-fallback (${t.message ?: "cloud unavailable"})",
                )
            }
        }
        _state.update {
            it.copy(
                analyzing = false,
                result = result,
                stages = result.stages.ifEmpty { AnalyzeMessageUseCase.stagePlan() },
                engineLabel = result.engine,
            )
        }
    }
}
