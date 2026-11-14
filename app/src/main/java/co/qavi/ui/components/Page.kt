package co.qavi.ui.components

import co.qavi.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.qavi.ui.theme.QaviIcons
import co.qavi.ui.theme.QaviSpace

/**
 * 子页面的骨架:页面底 + 带返回的顶栏 + 一列可滚动的内容(左右留 [QaviSpace.Page],避开导航栏)。
 * 内容里直接放 [GroupLabel] / [GroupCard] / [FooterNote]。
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    viewportModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title, QaviIcons.Back, onBack, stringResource(R.string.back))
            Column(
                Modifier.weight(1f).fillMaxWidth().then(viewportModifier)
                    .verticalScroll(scrollState)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = QaviSpace.Page),
            ) {
                Spacer(Modifier.height(QaviSpace.S4))
                content()
                Spacer(Modifier.height(QaviSpace.S24))
            }
        }
    }
}
