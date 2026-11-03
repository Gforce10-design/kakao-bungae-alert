package app.bungaealert

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.bungaealert.ai.JudgeFactory
import app.bungaealert.ai.NtfySubscribeService
import app.bungaealert.ai.NtfyWatchdogWorker
import app.bungaealert.ai.ReplyHub
import app.bungaealert.auth.AppVisibility
import app.bungaealert.data.DetectionLogRepository
import app.bungaealert.data.RulesRepository
import app.bungaealert.data.SecretStore
import app.bungaealert.data.SettingsRepository
import app.bungaealert.detect.AiJudge
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.DedupeWindow
import app.bungaealert.notify.ListenerRuntime
import app.bungaealert.notify.ListenerWatchdogWorker
import app.bungaealert.update.UpdateCheckWorker

class BungaeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
        AppVisibility.install()
        NtfyWatchdogWorker.schedule(this)
        ListenerWatchdogWorker.schedule(this)
        UpdateCheckWorker.schedule(this)
        ListenerRuntime.ensure(this, fromWatchdog = false)
        val settings = graph.settings.current()
        if (BackendIds.GROK_WEBHOOK in settings.enabledBackends &&
            graph.secrets.get(SecretStore.GROK_URL).isNotBlank()
        ) {
            NtfySubscribeService.start(this)
        }
    }

    companion object {
        lateinit var instance: BungaeApp
            private set
    }
}

class AppGraph(private val app: Context) {
    val settings = SettingsRepository(app)
    val secrets = SecretStore(app)
    val rules = RulesRepository(app)
    val log = DetectionLogRepository(app)
    val dedupe = DedupeWindow()
    val replies = ReplyHub()

    fun judges(): List<AiJudge> = JudgeFactory.build(app, settings.current(), secrets, replies)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        try {
            ListenerRuntime.ensure(appContext, fromWatchdog = false)
        } catch (t: Throwable) {
            Log.e("KakaoListener", "rebind after boot failed", t)
        }
        val app = appContext as? BungaeApp ?: return
        val settings = app.graph.settings.current()
        if (BackendIds.GROK_WEBHOOK in settings.enabledBackends &&
            app.graph.secrets.get(SecretStore.GROK_URL).isNotBlank()
        ) {
            NtfySubscribeService.start(appContext)
        }
    }
}
