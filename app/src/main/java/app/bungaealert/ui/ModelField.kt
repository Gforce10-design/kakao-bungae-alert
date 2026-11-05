package app.bungaealert.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bungaealert.ai.ModelProvider
import kotlinx.coroutines.launch

private const val CUSTOM = "직접 입력"

/**
 * Model picker: dropdown of recommended ids plus a custom entry. Nothing is stored until 저장 is
 * pressed and [validate] (a lightweight provider call) returns null.
 */
@Composable
fun ModelField(
    provider: ModelProvider,
    saved: String,
    validate: suspend (String) -> String?,
    onSaved: (String) -> Unit,
) {
    var draft by remember(saved) { mutableStateOf(saved) }
    var custom by remember(saved) { mutableStateOf(saved !in provider.recommended) }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column {
        Box {
            OutlinedTextField(
                value = if (custom) CUSTOM else draft,
                onValueChange = {},
                readOnly = true,
                label = { Text("${provider.label} 모델") },
                trailingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = "모델 목록") },
                modifier = Modifier.fillMaxWidth(),
            )
            // Transparent click target over the read-only field.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(onClickLabel = "모델 목록 열기") { expanded = true },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                provider.recommended.forEachIndexed { index, id ->
                    DropdownMenuItem(
                        text = { Text(if (index == 0) "$id (권장)" else id) },
                        onClick = {
                            draft = id
                            custom = false
                            expanded = false
                            error = ""
                            ok = ""
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(CUSTOM) },
                    onClick = {
                        custom = true
                        expanded = false
                        error = ""
                        ok = ""
                    },
                )
            }
        }
        if (custom) {
            Spacer(Modifier.height(8.dp))
            BungaeTextField(draft, { draft = it; error = ""; ok = "" }, "${provider.label} 모델 이름")
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            if (busy) "확인 중…" else "저장",
            {
                if (!busy) {
                    busy = true
                    error = ""
                    ok = ""
                    scope.launch {
                        try {
                            val result = validate(draft)
                            if (result == null) {
                                val id = draft.trim()
                                draft = id
                                onSaved(id)
                                ok = "저장했습니다"
                            } else {
                                error = result
                            }
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            Modifier.fillMaxWidth(),
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else if (ok.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(ok, style = MaterialTheme.typography.bodySmall, color = LocalBungaeExtra.current.good)
        }
    }
}
