package com.yimberzol.preznath.data

import com.yimberzol.preznath.domain.AnalysisResult
import com.yimberzol.preznath.domain.AnalyzerPort
import com.yimberzol.preznath.domain.Entity
import com.yimberzol.preznath.domain.EntityKind
import com.yimberzol.preznath.domain.Message
import com.yimberzol.preznath.domain.PipelineStage
import com.yimberzol.preznath.domain.Signal
import com.yimberzol.preznath.domain.StageEvent
import com.yimberzol.preznath.domain.StageStatus
import com.yimberzol.preznath.domain.Verdict
import com.yimberzol.preznath.domain.VerdictLabel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional cloud analyzer. Feature-flagged off by default.
 * Emulator host loopback: http://10.0.2.2:8000/api/analyze
 * Never blocks the offline path — call only when the user enables Cloud models.
 */
class CloudAnalyzer(
    private val baseUrl: String = DEFAULT_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) : AnalyzerPort {

    override fun analyze(message: Message): AnalysisResult {
        val body = JSONObject()
            .put("text", message.text)
            .put("upi_id", message.upiId)
            .put("url", message.url)
            .toString()
        val request = Request.Builder()
            .url(baseUrl)
            .post(body.toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Cloud analyze failed: HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            return parseResult(message, json)
        }
    }

    override fun stream(message: Message): Sequence<StageEvent> = sequence {
        val result = analyze(message)
        for (stage in result.stages) {
            yield(StageEvent(stage.copy(status = StageStatus.RUNNING)))
            yield(StageEvent(stage))
        }
        yield(StageEvent(result.stages.last(), result))
    }

    private fun parseResult(message: Message, json: JSONObject): AnalysisResult {
        val verdictObj = json.getJSONObject("verdict")
        val verdict = Verdict(
            label = VerdictLabel.valueOf(verdictObj.getString("label")),
            score = verdictObj.getInt("score"),
            confidence = verdictObj.getDouble("confidence"),
            reasons = verdictObj.getJSONArray("reasons").toStringList(),
            advice = verdictObj.getJSONArray("advice").toStringList(),
        )
        val entities = json.optJSONArray("entities")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Entity(
                            kind = parseEntityKind(o.getString("kind")),
                            value = o.getString("value"),
                            valid = if (o.has("valid") && !o.isNull("valid")) o.getBoolean("valid") else null,
                            note = o.optString("note").ifBlank { null },
                        ),
                    )
                }
            }
        }.orEmpty()

        val signals = json.optJSONArray("signals")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Signal(
                            name = o.getString("name"),
                            label = o.getString("label"),
                            score = o.getDouble("score"),
                            weight = o.getDouble("weight"),
                            evidence = o.getString("evidence"),
                            latencyMs = o.optInt("latency_ms"),
                        ),
                    )
                }
            }
        }.orEmpty()

        val stages = json.optJSONArray("stages")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PipelineStage(
                            index = o.getInt("index"),
                            name = o.getString("name"),
                            label = o.getString("label"),
                            status = StageStatus.valueOf(o.getString("status").uppercase()),
                            latencyMs = o.optInt("latency_ms"),
                        ),
                    )
                }
            }
        }.orEmpty()

        return AnalysisResult(
            message = message,
            language = json.optString("language", "en"),
            languageConfidence = json.optDouble("language_confidence", 0.0),
            translatedText = json.optString("translated_text").ifBlank { null },
            entities = entities,
            signals = signals,
            stages = stages.ifEmpty {
                listOf(
                    PipelineStage(8, "fusion", "Risk fusion", StageStatus.DONE, 0, null),
                )
            },
            verdict = verdict,
            totalLatencyMs = json.optInt("total_latency_ms"),
            engine = "cloud",
        )
    }

    private fun JSONArray.toStringList(): List<String> =
        buildList { for (i in 0 until length()) add(getString(i)) }

    private fun parseEntityKind(raw: String): EntityKind =
        when (raw.lowercase()) {
            "upi_vpa" -> EntityKind.UPI_VPA
            "phone" -> EntityKind.PHONE
            "url" -> EntityKind.URL
            "amount" -> EntityKind.AMOUNT
            "credential" -> EntityKind.CREDENTIAL
            "bank" -> EntityKind.BANK
            "app" -> EntityKind.APP
            else -> EntityKind.BANK
        }

    companion object {
        const val DEFAULT_URL = "http://10.0.2.2:8000/api/analyze"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
