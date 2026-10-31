package app.bungaealert.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bungaealert.BungaeApp
import app.bungaealert.detect.DecisionPolicy
import app.bungaealert.detect.PostType
import app.bungaealert.detect.ScoreClassifier
import kotlinx.coroutines.launch

data class TrialResult(
    val meetup: Int,
    val giveaway: Int,
    val cutoff: Int,
    val zone: String,
    val outcome: String,
    val summary: String,
)

@Composable
fun TrialRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    var chat by remember { mutableStateOf("시험방") }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<TrialResult?>(null) }
    val scope = rememberCoroutineScope()
    TrialScreen(
        chat = chat,
        text = text,
        busy = busy,
        error = error,
        result = result,
        onChat = { chat = it },
        onText = { text = it },
        onSubmit = {
            busy = true
            error = null
            scope.launch {
                try {
                    val settings = graph.settings.current()
                    val preview = ScoreClassifier.score(text)
                    val eval = DecisionPolicy.evaluate(
                        text = text,
                        enabled = setOf(PostType.MEETUP, PostType.GIVEAWAY),
                        sensitivity = settings.sensitivity,
                        band = settings.borderlineBand,
                        judges = graph.judges(),
                        chatName = chat.ifBlank { "시험방" },
                        sender = null,
                    )
                    val kind = eval.alertType?.let { typeShort(it) }
                    result = TrialResult(
                        meetup = preview.meetup,
                        giveaway = preview.giveaway,
                        cutoff = eval.cutoff,
                        zone = zoneLabel(eval.zone),
                        outcome = if (eval.alert) listOfNotNull("알림", kind).joinToString(" · ") else "알림 없음",
                        summary = eval.summary,
                    )
                } catch (t: Throwable) {
                    result = null
                    error = t.message?.takeIf { it.isNotBlank() } ?: "판정하지 못했습니다"
                } finally {
                    busy = false
                }
            }
        },
        onBack = onBack,
    )
}

@Composable
fun TrialScreen(
    chat: String,
    text: String,
    busy: Boolean,
    error: String?,
    result: TrialResult?,
    onChat: (String) -> Unit,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 24.dp),
    ) {
        BackHeader("문장 시험", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "경계 점수만 보조 판정을 부릅니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        BungaeTextField(chat, onChat, "채팅방")
        Spacer(Modifier.height(12.dp))
        BungaeTextField(text, onText, "메시지", singleLine = false, minLines = 4)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("판정", onSubmit, Modifier.fillMaxWidth(), enabled = !busy && text.isNotBlank())
        Spacer(Modifier.height(16.dp))
        when {
            busy -> LoadingPane("판정 중", spin = true)
            error != null -> ErrorPane(title = "판정하지 못했습니다", message = error, action = "다시 시도", onAction = onSubmit)
            result != null -> TrialCard(result)
        }
    }
}

@Composable
private fun TrialCard(result: TrialResult) {
    SurfaceCard {
        Text("모임 ${result.meetup}점", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        Text("나눔 ${result.giveaway}점", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.height(8.dp))
        Text("기준 ${result.cutoff}점 · ${result.zone}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(result.outcome, style = MaterialTheme.typography.titleMedium)
        if (result.summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                result.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
