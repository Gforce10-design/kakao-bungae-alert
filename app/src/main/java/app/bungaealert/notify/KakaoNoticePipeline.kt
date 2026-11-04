package app.bungaealert.notify

import android.app.PendingIntent
import android.util.Log
import app.bungaealert.BungaeApp
import app.bungaealert.alert.dispatchAlert
import app.bungaealert.data.DetectionRecord
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.ChatMatcher
import app.bungaealert.detect.DecisionPolicy
import app.bungaealert.detect.KakaoNoticeParser
import app.bungaealert.detect.KakaoPackages
import app.bungaealert.detect.KeywordMatcher
import app.bungaealert.detect.RawKakaoNotice
import app.bungaealert.detect.RuleKeywords
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

data class PostedNotice(
    val raw: RawKakaoNotice,
    val contentIntent: PendingIntent?,
)

object KakaoNoticePipeline {
    private const val TAG = "KakaoListener"
    private val scope = CoroutineScope(
        SupervisorJob() +
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "kakao-notice").apply { isDaemon = true }
            }.asCoroutineDispatcher() +
            CoroutineExceptionHandler { _, error ->
                Log.e(TAG, "pipeline failed", error)
            },
    )

    fun enqueue(notice: PostedNotice) {
        scope.launch {
            try {
                consume(notice)
            } catch (t: Throwable) {
                Log.e(TAG, "notification handling failed", t)
            }
        }
    }

    private suspend fun consume(notice: PostedNotice) {
        if (!KakaoPackages.shouldRead(notice.raw.packageName, notice.raw.importance)) return
        val app = BungaeApp.instance
        val keywords = app.graph.keywords.current()
        val body = KeywordMatcher.notificationText(notice.raw)
        val rules = app.graph.rules.current().filter { it.enabled }
        val interested = rules.filter { rule ->
            KeywordMatcher.matchesAny(
                RuleKeywords.applicable(rule, keywords),
                notice.raw.title,
                body,
            )
        }
        if (interested.isEmpty()) return
        val messages = KakaoNoticeParser.parse(notice.raw)
        for (message in messages) {
            if (message.text.isBlank()) continue
            val matched = interested.filter { ChatMatcher.matches(it, message.chatName) }
            if (matched.isEmpty()) continue
            if (!app.graph.dedupe.shouldProcess(message.chatName, message.text)) continue
            val types = matched.flatMap { rule ->
                RuleKeywords.postTypes(RuleKeywords.applicable(rule, keywords))
            }.toSet()
            if (types.isEmpty()) continue
            val mode = if (matched.any { it.alertMode == AlertMode.REPEAT }) AlertMode.REPEAT else AlertMode.ONCE
            val settings = app.graph.settings.current()
            val evaluation = DecisionPolicy.evaluate(
                text = message.text,
                enabled = types,
                sensitivity = settings.sensitivity,
                band = settings.borderlineBand,
                judges = app.graph.judges(),
                chatName = message.chatName,
                sender = message.sender,
            )
            if (!evaluation.breakdown.mediaOrEmpty) {
                app.graph.log.add(
                    DetectionRecord(
                        id = UUID.randomUUID().toString(),
                        atMillis = System.currentTimeMillis(),
                        chatName = message.chatName,
                        sender = message.sender,
                        text = message.text,
                        score = evaluation.relevantScore,
                        meetupScore = evaluation.breakdown.meetup,
                        giveawayScore = evaluation.breakdown.giveaway,
                        alerted = evaluation.alert,
                        alertType = evaluation.alertType?.wire,
                        summary = evaluation.summary,
                    ),
                )
            }
            if (evaluation.alert) {
                dispatchAlert(
                    context = app,
                    id = UUID.randomUUID().toString(),
                    chatName = message.chatName,
                    body = message.text.take(180),
                    type = evaluation.alertType,
                    mode = mode,
                    seconds = settings.maxRingSeconds,
                    kakao = notice.contentIntent,
                )
            }
        }
    }
}
