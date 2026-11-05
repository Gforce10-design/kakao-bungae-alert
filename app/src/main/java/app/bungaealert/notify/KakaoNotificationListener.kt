package app.bungaealert.notify

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.bungaealert.detect.KakaoPackages
import app.bungaealert.detect.RawKakaoMessage
import app.bungaealert.detect.RawKakaoNotice

class KakaoNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        try {
            ListenerRuntime.onConnected(this)
            ListenerRuntime.ensure(this, fromWatchdog = false)
        } catch (t: Throwable) {
            Log.e(TAG, "onListenerConnected failed", t)
        }
    }

    override fun onListenerDisconnected() {
        try {
            ListenerRuntime.onDisconnected(this)
            ListenerRuntime.requestRebind(this)
        } catch (t: Throwable) {
            Log.e(TAG, "onListenerDisconnected failed", t)
        }
    }

    override fun onDestroy() {
        ListenerRuntime.onDestroyed(this)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        onNotificationPosted(sbn, null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        try {
            val packageName = sbn.packageName
            val key = sbn.key.orEmpty()
            val kakao = packageName == KakaoPackages.KAKAO_TALK
            ListenerRuntime.markCallback(this, kakao, key)
            if (!kakao) return
            val importance = rankingImportance(sbn, rankingMap)
            val room = conversationRoomName(sbn, rankingMap)
            val raw = extract(sbn, importance, room)
            val pending = sbn.notification.contentIntent
            KakaoNoticePipeline.enqueue(PostedNotice(raw, pending))
        } catch (t: Throwable) {
            Log.e(TAG, "notification snapshot failed", t)
        }
    }

    private fun rankingImportance(sbn: StatusBarNotification, rankingMap: RankingMap?): Int? {
        if (rankingMap == null) return null
        val ranking = Ranking()
        return if (rankingMap.getRanking(sbn.key, ranking)) ranking.importance else null
    }

    /**
     * KakaoTalk group notifications put the SENDER in android.title and omit
     * the conversation title; the room name only exists as the conversation
     * shortcut's label (shortcut id = room id).
     */
    private fun conversationRoomName(sbn: StatusBarNotification, rankingMap: RankingMap?): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            val ranking = Ranking()
            val found = try {
                currentRanking?.getRanking(sbn.key, ranking) == true
            } catch (t: Throwable) {
                false
            } || (rankingMap?.getRanking(sbn.key, ranking) == true)
            if (!found) {
                Log.d(DROP_TAG, "room: no ranking for key; falling back")
                return null
            }
            val shortcut = ranking.conversationShortcutInfo
            val name = (shortcut?.shortLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: shortcut?.longLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() })
            Log.d(
                DROP_TAG,
                "room: shortcut=${shortcut?.id ?: "none"} name=${name ?: "null"} " +
                    "title=${sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)}",
            )
            name
        } catch (t: Throwable) {
            Log.w(DROP_TAG, "room lookup failed: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun extract(sbn: StatusBarNotification, importance: Int?, roomName: String? = null): RawKakaoNotice {
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
            roomName = roomName,
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
        private const val DROP_TAG = "BungaeDrop"
    }
}
