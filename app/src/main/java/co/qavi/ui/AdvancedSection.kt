package co.qavi.ui

import co.qavi.R
import co.qavi.i18n.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import co.qavi.ui.components.QaviDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.qavi.core.cap.sandbox.SandboxStatus
import co.qavi.cap.sandbox.RootfsInstaller
import co.qavi.cap.sandbox.RuntimePreparer
import co.qavi.cap.sandbox.SandboxState
import kotlinx.coroutines.launch
import co.qavi.ui.components.GroupCard
import co.qavi.ui.components.ListRow
import co.qavi.ui.theme.QaviTheme
import co.qavi.ui.theme.QaviIcons
import co.qavi.ui.components.TileTone
import co.qavi.ui.components.QaviSwitch
import co.qavi.ui.theme.QaviType
import co.qavi.ui.components.RowValue
import co.qavi.ui.components.RowChevron
import co.qavi.ui.components.GroupLabel
import co.qavi.QaviApp
import co.qavi.cap.sandbox.SandboxDirs
import co.qavi.cap.sandbox.SandboxUsage
import co.qavi.cap.sandbox.SandboxUsageReader
import co.qavi.cap.sandbox.WorkspaceUsage
import co.qavi.ui.terminal.TerminalLauncher
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 设置 › 文件与计算环境 的文案(纯函数,便于测试)。运行环境对用户是看不见的实现细节(产品决定):
 * 字面上只说「脚本」「处理文件」「运行环境」,不出现沙箱、Linux、PRoot、Alpine 等技术名词。
 */
object SandboxCopy {
    val NETWORK_TITLE: String get() = tr(R.string.sandbox_network)
    val NETWORK_SUBTITLE: String get() = tr(R.string.sandbox_network_sub)
    val NETWORK_EXPLAIN: String get() = tr(R.string.sandbox_network_explain)
    val USAGE_TITLE: String get() = tr(R.string.sandbox_usage)
    val USAGE_NOTE: String get() = tr(R.string.sandbox_usage_note)
    val CLEAR: String get() = tr(R.string.sandbox_usage_clear)
    val CLEAR_TITLE: String get() = tr(R.string.sandbox_usage_clear_title)
    val CLEAR_ALL: String get() = tr(R.string.sandbox_usage_clear_all)
    val CLEAR_ALL_TITLE: String get() = tr(R.string.sandbox_usage_clear_all_title)
    val CLEAR_ALL_BODY: String get() = tr(R.string.sandbox_usage_clear_all_body)

    fun clearBody(w: WorkspaceUsage): String = tr(R.string.sandbox_usage_clear_body, workspaceTitle(w))

    /** 工作区那一行的名字:对话标题;对话已经删了的写明。 */
    fun workspaceTitle(w: WorkspaceUsage): String = w.title?.let(ChatCopy::plainTitle)?.takeIf(String::isNotBlank) ?: tr(R.string.sandbox_usage_deleted_chat)

    fun usageRows(u: SandboxUsage): List<Pair<String, Long>> = listOf(
        tr(R.string.sandbox_usage_total) to u.total,
        tr(R.string.sandbox_usage_runtime) to u.runtime,
        tr(R.string.sandbox_usage_skills) to u.skills,
        tr(R.string.sandbox_usage_workspaces) to u.workspacesTotal,
    )

    /** 字节数按人看得懂的单位写。 */
    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(Locale.ROOT, bytes.toDouble() / (1L shl 30))
        bytes >= 1L shl 20 -> "%.1f MB".format(Locale.ROOT, bytes.toDouble() / (1L shl 20))
        bytes >= 1L shl 10 -> "%.0f KB".format(Locale.ROOT, bytes.toDouble() / (1L shl 10))
        else -> "$bytes B"
    }
    val ISOLATION_UNAVAILABLE: String get() = tr(R.string.sandbox_isolation_unavailable)
    val RESET_TITLE: String get() = tr(R.string.sandbox_reset_title)
    val RESET_BODY: String get() = tr(R.string.sandbox_reset_body)

    /** 「重置运行环境」下面的一行:平时说明会怎样,准备中或准备失败时如实说。 */
    fun resetSubtitle(s: SandboxStatus): String = when (s) {
        is SandboxStatus.Installing -> tr(R.string.runtime_preparing)
        is SandboxStatus.Broken -> tr(R.string.sandbox_broken, s.reason)
        else -> tr(R.string.sandbox_reset_sub)
    }
}

/**
 * 接上真实状态的「文件与计算环境」页内容:联网开关读写 [SandboxState];代理统一在高级与其他页修改,这里不重复显示;
 * 已用空间进页面时在后台读一遍,清理经 [SandboxUsageReader](有任务在跑的对话不清);重置后马上在后台重新准备。
 */
@Composable
fun AdvancedSettings() {
    val context = LocalContext.current
    val state = remember { SandboxState.get(context) }
    val status by state.status.collectAsState()
    var network by remember { mutableStateOf(state.networkAllowed) }
    val app = context.applicationContext as QaviApp
    val usageReader = remember {
        SandboxUsageReader(SandboxDirs(context), { app.conversations.list(includeArchived = true) }, { app.taskState.activeConversations.value })
    }
    var usage by remember { mutableStateOf<SandboxUsage?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        usage = withContext(Dispatchers.IO) { runCatching { usageReader.read() }.getOrNull() }
    }
    val scope = rememberCoroutineScope()
    fun clearing(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
            reload++
        }
    }
    AdvancedSection(
        status = status, networkAllowed = network, isolationIssue = state.isolationIssue != null,
        onNetworkChange = { state.networkAllowed = it; network = it },
        usage = usage,
        onOpenTerminal = { w -> w.conversationId?.let { TerminalLauncher.open(context, it, w.title) } },
        onClearWorkspace = { w -> clearing { usageReader.clear(w.dirName) } },
        onClearAll = { clearing { usageReader.clearAll() } },
        onReset = {
            scope.launch {
                RootfsInstaller.create(context).reset()
                RuntimePreparer.ensure(context, retryFailed = true)
                reload++
            }
        },
    )
}

/** 不带状态的「文件与计算环境」页内容,便于测试。不支持的设备整组不显示。 */
@Composable
fun AdvancedSection(
    status: SandboxStatus,
    networkAllowed: Boolean,
    isolationIssue: Boolean,
    onNetworkChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    /** 已用空间;还没读出来为 null(这一组先不显示)。 */
    usage: SandboxUsage? = null,
    onOpenTerminal: (WorkspaceUsage) -> Unit = {},
    onClearWorkspace: (WorkspaceUsage) -> Unit = {},
    onClearAll: () -> Unit = {},
) {
    if (status is SandboxStatus.Unsupported) return
    var confirming by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf<WorkspaceUsage?>(null) }
    var clearingAll by remember { mutableStateOf(false) }

    GroupCard {
        ListRow(QaviIcons.Link, SandboxCopy.NETWORK_TITLE, SandboxCopy.NETWORK_SUBTITLE, first = true) {
            QaviSwitch(networkAllowed, onNetworkChange, Modifier.testTag("sandbox-network"))
        }
        Note(SandboxCopy.NETWORK_EXPLAIN, if (networkAllowed) QaviTheme.colors.warn else QaviTheme.colors.ink3)
        if (isolationIssue) Note(SandboxCopy.ISOLATION_UNAVAILABLE, QaviTheme.colors.bad)
        ListRow(QaviIcons.Close, tr(R.string.sandbox_reset), SandboxCopy.resetSubtitle(status), titleColor = QaviTheme.colors.bad, tone = TileTone.Bad,
            onClick = if (status is SandboxStatus.Installing) null else ({ confirming = true }))
    }

    usage?.let { u -> UsageGroup(u, onOpenTerminal, onClear = { clearing = it }, onClearAll = { clearingAll = true }) }

    clearing?.let { w ->
        QaviDialog(
            onDismissRequest = { clearing = null },
            title = { Text(SandboxCopy.CLEAR_TITLE) },
            text = { Text(SandboxCopy.clearBody(w)) },
            confirmButton = { TextButton({ clearing = null; onClearWorkspace(w) }) { Text(SandboxCopy.CLEAR, color = QaviTheme.colors.bad) } },
            dismissButton = { TextButton({ clearing = null }) { Text(tr(R.string.cancel)) } },
        )
    }
    if (clearingAll) {
        QaviDialog(
            onDismissRequest = { clearingAll = false },
            title = { Text(SandboxCopy.CLEAR_ALL_TITLE) },
            text = { Text(SandboxCopy.CLEAR_ALL_BODY) },
            confirmButton = { TextButton({ clearingAll = false; onClearAll() }) { Text(SandboxCopy.CLEAR, color = QaviTheme.colors.bad) } },
            dismissButton = { TextButton({ clearingAll = false }) { Text(tr(R.string.cancel)) } },
        )
    }

    if (confirming) {
        QaviDialog(
            onDismissRequest = { confirming = false },
            title = { Text(SandboxCopy.RESET_TITLE) },
            text = { Text(SandboxCopy.RESET_BODY) },
            confirmButton = { TextButton({ confirming = false; onReset() }) { Text(tr(R.string.reset), color = QaviTheme.colors.bad) } },
            dismissButton = { TextButton({ confirming = false }) { Text(tr(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 10.dp), style = QaviType.Hint, color = color)
}

/**
 * 已用空间:合计、运行环境、技能、对话的工作区各占多少;下面按对话列出工作区(大的在前),点一行清掉它(先确认),
 * 有任务在跑的对话写明暂时不能清;最后一行清空所有工作区。
 */
@Composable
private fun UsageGroup(
    u: SandboxUsage,
    onOpenTerminal: (WorkspaceUsage) -> Unit,
    onClear: (WorkspaceUsage) -> Unit,
    onClearAll: () -> Unit,
) {
    val c = QaviTheme.colors
    GroupLabel(SandboxCopy.USAGE_TITLE)
    GroupCard {
        SandboxCopy.usageRows(u).forEachIndexed { i, (name, bytes) ->
            ListRow(null, name, first = i == 0) { RowValue(SandboxCopy.size(bytes)) }
        }
    }
    if (u.workspaces.isNotEmpty()) {
        GroupCard(Modifier.padding(top = 8.dp)) {
            u.workspaces.forEachIndexed { i, w ->
                ListRow(
                    null, SandboxCopy.workspaceTitle(w), if (w.busy) tr(R.string.sandbox_usage_busy) else null, first = i == 0,
                    onClick = if (w.busy) null else ({ onClear(w) }),
                ) {
                    if (w.conversationId != null) {
                        Text(
                            tr(R.string.terminal_open_action),
                            Modifier
                                .clickable { onOpenTerminal(w) }
                                .padding(end = 12.dp)
                                .testTag("sandbox-workspace-terminal"),
                            style = QaviType.Caption,
                            color = c.accent,
                        )
                    }
                    RowValue(SandboxCopy.size(w.bytes))
                }
            }
            if (u.workspaces.any { !it.busy }) {
                ListRow(null, SandboxCopy.CLEAR_ALL, titleColor = c.bad, onClick = onClearAll)
            }
        }
    }
    Text(SandboxCopy.USAGE_NOTE, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), style = QaviType.Hint, color = c.ink3)
}
