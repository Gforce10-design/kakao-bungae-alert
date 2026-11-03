package app.bungaealert.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class ChipLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun labelIsCenteredInAShortPillWithA48DpTouchTarget() {
        var clicks = 0
        rule.setContent {
            BungaeTheme {
                BungaeChip(label = "전체", selected = true, onClick = { clicks += 1 })
            }
        }

        val touch = rule.onNodeWithTag("bungaeChipTouch").assertIsSelected().getUnclippedBoundsInRoot()
        val pill = rule.onNodeWithTag("bungaeChipPill", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val text = rule.onNodeWithText("전체", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val touchHeight = touch.bottom - touch.top
        val touchWidth = touch.right - touch.left
        val pillHeight = pill.bottom - pill.top

        assertTrue("touch height $touchHeight", touchHeight >= 48.dp)
        assertTrue("touch width $touchWidth", touchWidth >= 48.dp)
        assertTrue("pill height $pillHeight", pillHeight >= 36.dp && pillHeight <= 40.dp)

        val textMidY = (text.top + text.bottom) * 0.5f
        val textMidX = (text.left + text.right) * 0.5f
        val pillMidY = (pill.top + pill.bottom) * 0.5f
        val pillMidX = (pill.left + pill.right) * 0.5f
        val touchMidY = (touch.top + touch.bottom) * 0.5f
        assertTrue("text vs pill y $textMidY $pillMidY", abs(textMidY.value - pillMidY.value) < 1f)
        assertTrue("text vs pill x $textMidX $pillMidX", abs(textMidX.value - pillMidX.value) < 1f)
        assertTrue("pill vs touch y $pillMidY $touchMidY", abs(pillMidY.value - touchMidY.value) < 1f)

        rule.onNodeWithTag("bungaeChipTouch").performClick()
        rule.runOnIdle { assertTrue(clicks == 1) }
    }
}
