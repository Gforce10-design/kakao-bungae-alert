package app.bungaealert.notify

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.bungaealert.BungaeApp
import app.bungaealert.alert.dispatchAlert
import app.bungaealert.data.DetectionRecord
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.ChatMatcher
import app.bungaealert.detect.DecisionPolicy
import app.bungaealert.detect.KakaoNoticeParser
import app.bungaealert.detect.KakaoPackages
import app.bungaealert.detect.RawKakaoMessage
import app.bungaealert.detect.RawKakaoNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class KakaoNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        onNotificationPosted(sbn, null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        try {
            handle(sbn, rankingMap)
        } catch (t: Throwable) {
            Log.e(TAG, "notification handling failed", t)
        }
    }

    private fun handle(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val importance = rankingImportance(sbn, rankingMap)
        if (!KakaoPackages.shouldRead(sbn.packageName, importance)) return
        val raw = extract(sbn, importance)
        val messages = KakaoNoticeParser.parse(raw)
        val app = BungaeApp.instance
        val rules = app.graph.rules.current().filter { it.enabled }
        if (rules.isEmpty()) return
        for (message in messages) {
            if (message.text.isBlank()) continue
            val matched = rules.filter { ChatMatcher.matches(it, message.chatName) }
            if (matched.isEmpty()) continue
            if (!app.graph.dedupe.shouldProcess(message.chatName, message.text)) continue
            val types = matched.flatMap { it.types }.toSet()
            if (types.isEmpty()) continue
            val mode = if (matched.any { it.alertMode == AlertMode.REPEAT }) AlertMode.REPEAT else AlertMode.ONCE
            val kakaoIntent = sbn.notification.contentIntent
            scope.launch {
                val settings = app.graph.settings.current()
                val judges = app.graph.judges()
                val evaluation = DecisionPolicy.evaluate(
                    text = message.text,
                    enabled = types,
                    sensitivity = settings.sensitivity,
                    band = settings.borderlineBand,
                    judges = judges,
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
                    val id = UUID.randomUUID().toString()
                    val snippet = message.text.take(180)
                    dispatchAlert(
                        context = app,
                        id = id,
                        chatName = message.chatName,
                        body = snippet,
                        type = evaluation.alertType,
                        mode = mode,
                        seconds = settings.maxRingSeconds,
                        kakao = kakaoIntent,
                    )
                }
            }
        }
    }

    private fun rankingImportance(sbn: StatusBarNotification, rankingMap: RankingMap?): Int? {
        if (rankingMap == null) return null
        val ranking = Ranking()
        return if (rankingMap.getRanking(sbn.key, ranking)) ranking.importance else null
    }

    private fun extract(sbn: StatusBarNotification, importance: Int?): RawKakaoNotice {
        val extras = sbn.notification.extras
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it?.toString().orEmpty() }
            .orEmpty()
        val messages = buildList {
            val parcels = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return@buildList
            for (item in parcels) {
                val bundle = item as? Bundle ?: continue
                val text = bundle.getCharSequence("text")?.toString().orEmpty()
                val sender = bundle.getCharSequence("sender")?.toString()
                    ?: personName(bundle)
                if (text.isNotEmpty()) add(RawKakaoMessage(sender, text))
            }
        }
        return RawKakaoNotice(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            textLines = lines,
            messages = messages,
            isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            importance = importance,
            postedAtMillis = sbn.postTime,
        )
    }

    private fun personName(bundle: Bundle): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        val person = if (Build.VERSION.SDK_INT >= 33) {
            bundle.getParcelable("sender_person", Person::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable("sender_person") as? Person
        }
        return person?.name?.toString()
    }

    companion object {
        private const val TAG = "KakaoListener"
    }
}
