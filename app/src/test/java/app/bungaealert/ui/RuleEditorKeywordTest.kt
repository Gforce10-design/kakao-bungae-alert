package app.bungaealert.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.KeywordCodec
import app.bungaealert.detect.MatchKeyword
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.WatchRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class RuleEditorKeywordTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun editorListsEnabledKeywordsFromTheStoreIncludingPhrases() {
        val phrase = MatchKeyword("phrase", "오늘 저녁에 봐요")
        val hidden = MatchKeyword("hidden", "숨긴 말", enabled = false)
        rule.setContent {
            BungaeTheme {
                RuleEditor(
                    initial = WatchRule("new", "수다방", MatchMode.CONTAINS, true, true, AlertMode.ONCE),
                    isNew = true,
                    onDismiss = {},
                    onDelete = null,
                    onSave = {},
                    keywords = KeywordCodec.defaults() + phrase + hidden,
                )
            }
        }

        rule.onNodeWithText("오늘 저녁에 봐요").assertIsSelected()
        rule.onNodeWithText("번개").assertIsSelected()
        rule.onNodeWithText("선착순").assertIsSelected()
        rule.onNodeWithText("숨긴 말").assertDoesNotExist()

        rule.onNodeWithText("오늘 저녁에 봐요").performClick()
        rule.onNodeWithText("오늘 저녁에 봐요").assertIsNotSelected()
        rule.onNodeWithText("번개").assertIsSelected()
    }

    @Test
    fun editorShowsAPhraseAddedToTheStoreWhileItIsOpen() {
        var keywords by mutableStateOf(KeywordCodec.defaults())
        rule.setContent {
            BungaeTheme {
                RuleEditor(
                    initial = WatchRule("new", "수다방", MatchMode.CONTAINS, true, true, AlertMode.ONCE),
                    isNew = true,
                    onDismiss = {},
                    onDelete = null,
                    onSave = {},
                    keywords = keywords,
                )
            }
        }

        rule.onNodeWithText("오늘 저녁에 봐요").assertDoesNotExist()
        rule.runOnIdle {
            keywords = keywords + MatchKeyword("phrase", "오늘 저녁에 봐요")
        }
        rule.onNodeWithText("오늘 저녁에 봐요").assertIsSelected()
    }
}
