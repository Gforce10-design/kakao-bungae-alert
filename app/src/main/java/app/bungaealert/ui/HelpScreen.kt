package app.bungaealert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class HelpBlock(
    val icon: ImageVector,
    val title: String,
    val lines: List<String>,
)

private val helpBlocks = listOf(
    HelpBlock(
        Icons.Rounded.Info,
        "이 앱",
        listOf("카카오톡 알림에서 번개모임과 선착순만 고릅니다."),
    ),
    HelpBlock(
        Icons.Rounded.Verified,
        "처음 켜면",
        listOf(
            "한 단계씩 나옵니다. 허용되면 다음으로 갑니다.",
            "제한된 설정, 알림 접근, 알림 표시.",
            "전체 화면, 방해금지, 배터리.",
            "이미 된 단계는 건너뜁니다. 삼성은 잠들지 않는 앱이 마지막입니다.",
        ),
    ),
    HelpBlock(
        Icons.Rounded.Bedtime,
        "삼성 잠자기",
        listOf(
            "설정 → 연결 → 삼성.",
            "잠자는 앱, 깊게 잠자는 앱에서 이 앱을 빼세요.",
            "잠들지 않는 앱에 넣으세요. 목록은 앱이 읽지 못합니다.",
        ),
    ),
    HelpBlock(
        Icons.Rounded.Tune,
        "규칙",
        listOf(
            "규칙 탭 → 규칙 추가.",
            "채팅방에 방 이름을 적고, 맞추기는 끝 · 포함 · 정확히.",
            "번개나 선착순을 켜야 저장됩니다. 알림은 한 번 또는 반복.",
        ),
    ),
    HelpBlock(
        Icons.Rounded.Home,
        "홈",
        listOf(
            "감시 중이면 알림을 듣고 있습니다.",
            "알림을 받지 않는 중이면 배너를 눌러 알림 접근을 켭니다.",
            "글은 날짜별로 모입니다. 칩은 번개 또는 선착순, 숫자는 점수입니다.",
        ),
    ),
    HelpBlock(
        Icons.Rounded.NotificationsActive,
        "알람",
        listOf(
            "반복이면 끄거나 한도가 끝날 때까지 울립니다. 처음 한도는 3분입니다.",
            "한도는 설정 → 알림 → 반복 한도.",
            "카톡 열기는 카카오톡으로 가고 꺼집니다. 끄기는 알람만 멈춥니다.",
        ),
    ),
    HelpBlock(
        Icons.Rounded.Bolt,
        "판별",
        listOf(
            "점수는 항상 이 폰에서 계산합니다. 처음 기준은 62점입니다.",
            "기준보다 많이 높으면 알리고, 많이 낮으면 알리지 않습니다.",
            "그 사이만 보조 판정에 갑니다. 없으면 점수로 결정합니다.",
            "Gemini Nano (기기 안)은 켜져 있습니다. 폰이 지원할 때만 쓰고, 글은 폰 안에 있습니다.",
            "내 키는 설정 → 판정 → 키. 스위치를 켜야 그 서비스로 글이 갑니다.",
        ),
    ),
    HelpBlock(
        Icons.Rounded.SearchOff,
        "알림이 없을 때",
        listOf(
            "홈이 감시 중인지 봅니다. 끊겼으면 설정 → 연결 → 다시 연결.",
            "카카오톡에서 그 방 알림이 켜져 있는지 봅니다.",
            "규칙이 사용 중인지, 방 이름이 맞는지 봅니다.",
        ),
    ),
    HelpBlock(
        Icons.Rounded.Lock,
        "개인정보",
        listOf(
            "알림 내용과 방 이름은 이 폰에만 둡니다.",
            "점수와 Gemini Nano는 폰 안에서 끝납니다.",
            "API 키를 켠 경우에만, 애매한 글이 그 서비스로 갑니다.",
        ),
    ),
)

@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 24.dp),
    ) {
        BackHeader("사용법", onBack)
        Spacer(Modifier.size(12.dp))
        helpBlocks.forEach { block ->
            HelpCard(block)
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun HelpCard(block: HelpBlock) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                block.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(block.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.size(8.dp))
        block.lines.forEach { line ->
            Text(
                line,
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
