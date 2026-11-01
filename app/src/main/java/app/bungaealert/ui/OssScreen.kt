package app.bungaealert.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OssScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 24.dp),
    ) {
        BackHeader("오픈소스", onBack)
        Spacer(Modifier.height(12.dp))
        NoticeBlock(
            "라이선스",
            "번개알림은 GPL-3.0입니다. 수정해 배포하려면 같은 라이선스의 소스가 필요합니다.",
        )
        Spacer(Modifier.height(12.dp))
        NoticeBlock(
            "OpenMinis",
            "구독 로그인은 OpenMinis(GPL-3.0) 인증 코드를 고친 것입니다. github.com/OpenMinis/OpenMinis. 이 앱의 공식 제품이 아닙니다.",
        )
        Spacer(Modifier.height(12.dp))
        NoticeBlock(
            "Pretendard",
            "글꼴 Pretendard는 SIL Open Font License 1.1입니다. 저작권은 Kil Hyung-jin에게 있습니다.",
        )
    }
}

@Composable
private fun NoticeBlock(title: String, body: String) {
    SurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
