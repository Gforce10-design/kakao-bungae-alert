package app.bungaealert.detect

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

interface AiJudge {
    val id: String
    suspend fun isReady(): Boolean = true
    fun timeoutMillis(): Long = 8_000
    suspend fun judge(request: JudgeRequest): JudgeVerdict?
}

class NamedJudge(
    override val id: String,
    private val timeout: Long = 1_000,
    private val ready: suspend () -> Boolean = { true },
    private val block: suspend (JudgeRequest) -> JudgeVerdict?,
) : AiJudge {
    override suspend fun isReady(): Boolean = ready()
    override fun timeoutMillis(): Long = timeout
    override suspend fun judge(request: JudgeRequest): JudgeVerdict? = block(request)
}

object DecisionPolicy {
    fun relevant(breakdown: ScoreBreakdown, enabled: Set<PostType>): Pair<PostType?, Int> {
        var bestType: PostType? = null
        var best = 0
        for (type in enabled) {
            val score = breakdown.scoreFor(type)
            if (score > best) {
                best = score
                bestType = type
            }
        }
        return bestType to best
    }

    suspend fun evaluate(
        text: String,
        enabled: Set<PostType>,
        sensitivity: Int,
        band: Int,
        judges: List<AiJudge>,
        chatName: String,
        sender: String?,
    ): Evaluation {
        val breakdown = ScoreClassifier.score(text)
        val cutoff = Sensitivity.cutoff(sensitivity)
        val safeBand = band.coerceIn(0, 40)
        if (breakdown.mediaOrEmpty || enabled.isEmpty()) {
            return Evaluation(
                breakdown = breakdown,
                relevantScore = 0,
                relevantType = null,
                cutoff = cutoff,
                zone = ScoreZone.CLEAR_REJECT,
                verdict = null,
                alert = false,
                alertType = null,
                summary = breakdown.reasons.joinToString(" · "),
            )
        }
        val (type, score) = relevant(breakdown, enabled)
        val low = cutoff - safeBand
        val high = (cutoff + safeBand).coerceAtMost(100)
        if (type == null || score < low) {
            return finish(breakdown, score, type, cutoff, ScoreZone.CLEAR_REJECT, null, false, null)
        }
        if (score >= high) {
            return finish(breakdown, score, type, cutoff, ScoreZone.CLEAR_MATCH, null, true, type)
        }
        val request = JudgeRequest(text, chatName, sender, enabled, score, breakdown.reasons)
        var verdict: JudgeVerdict? = null
        for (judge in judges) {
            val ready = try {
                judge.isReady()
            } catch (_: Exception) {
                false
            }
            if (!ready) continue
            verdict = try {
                withTimeout(judge.timeoutMillis().coerceIn(1_000L, 120_000L)) { judge.judge(request) }
            } catch (_: TimeoutCancellationException) {
                null
            } catch (_: Exception) {
                null
            }
            if (verdict != null) break
        }
        if (verdict == null) {
            val alert = score >= cutoff
            return finish(
                breakdown, score, type, cutoff, ScoreZone.BORDERLINE, null, alert, if (alert) type else null,
            )
        }
        val judgedType = verdict.type?.takeIf { it in enabled }
            ?: type.takeIf { verdict.isMatch && verdict.type == null && it in enabled }
        val alert = verdict.isMatch && judgedType != null
        return finish(
            breakdown, score, type, cutoff, ScoreZone.BORDERLINE, verdict, alert, if (alert) judgedType else null,
        )
    }

    private fun finish(
        breakdown: ScoreBreakdown,
        score: Int,
        type: PostType?,
        cutoff: Int,
        zone: ScoreZone,
        verdict: JudgeVerdict?,
        alert: Boolean,
        alertType: PostType?,
    ): Evaluation {
        val summary = buildString {
            append(breakdown.reasons.joinToString(" · "))
            if (verdict != null) {
                append(" · 보조판정 ")
                append(verdict.backendId)
                append(if (verdict.isMatch) " 일치" else " 아님")
                if (verdict.reason.isNotBlank()) {
                    append(": ")
                    append(verdict.reason)
                }
            } else if (zone == ScoreZone.BORDERLINE) {
                append(" · 보조판정 없음, 점수와 기준 비교")
            }
        }
        return Evaluation(breakdown, score, type, cutoff, zone, verdict, alert, alertType, summary)
    }
}
