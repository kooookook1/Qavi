package co.qavi.ui

import co.qavi.R
import co.qavi.i18n.tr
import co.qavi.i18n.trPlural
import co.qavi.i18n.uiLocale
import androidx.compose.ui.res.stringResource
import co.qavi.ui.components.keepsKeyboard
import co.qavi.ui.components.KeyboardDismissArea
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import co.qavi.ui.components.QaviDialog
import co.qavi.ui.components.QaviNoticeDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.qavi.core.knowledge.GuideCatalog
import co.qavi.core.memory.MemoryKind
import co.qavi.core.memory.TaskMemory
import co.qavi.memory.MemoryRuntime
import co.qavi.ui.components.ActionSheet
import co.qavi.ui.components.BarIconButton
import co.qavi.ui.components.ButtonKind
import co.qavi.ui.components.ButtonSize
import co.qavi.ui.components.EmptyNote
import co.qavi.ui.components.GroupCard
import co.qavi.ui.components.GroupLabel
import co.qavi.ui.components.ListRow
import co.qavi.ui.components.QaviButton
import co.qavi.ui.components.QaviSwitch
import co.qavi.ui.components.RowActions
import co.qavi.ui.components.RowChevron
import co.qavi.ui.components.RowValue
import co.qavi.ui.components.SearchField
import co.qavi.ui.components.SegmentedControl
import co.qavi.ui.components.SheetAction
import co.qavi.ui.components.SubPage
import co.qavi.ui.components.TileTone
import co.qavi.ui.theme.QaviIcons
import co.qavi.ui.theme.QaviTheme
import co.qavi.ui.theme.QaviType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

private val SEGMENT_TAGS = Segment.entries.map { "segment-${it.name}" }

/** 记忆按类别分组的顺序和小标题。 */
private val KIND_GROUPS get() = listOf(MemoryKind.fact to tr(R.string.memory_about_you), MemoryKind.preference to tr(R.string.memory_preferences), MemoryKind.procedure to tr(R.string.memory_procedures))
private val KIND_NAMES get() = listOf(tr(R.string.memory_fact), tr(R.string.memory_preferences), tr(R.string.memory_procedures))

private enum class MemoryDataPage { Home, WebSites, Routes }

/** 记忆和个人技能的首次加载会读盘解密。 */
@Composable
internal fun rememberMemoryViewModel(runtime: MemoryRuntime): State<MemoryViewModel?> {
    val scope = rememberCoroutineScope()
    return produceState<MemoryViewModel?>(null, runtime) {
        value = withContext(Dispatchers.IO) {
            val bundled = GuideCatalog.loadBundled()
            MemoryViewModel(
                runtime.store(), runtime.settings(), runtime.skills(), scope, runtime.file().brokenNotice,
                wipe = { runtime.scope.launch(Dispatchers.IO) { runtime.wipeAll() } },
                onForgotten = { id -> runtime.scope.launch(Dispatchers.IO) { runtime.stripMemory(setOf(id)) } },
                runnableScripts = bundled.runnableScripts(),
                appDrafts = runtime.appKnowledge(),
                builtinSkillNames = bundled.items.map { it.name }.toSet(),
                taskMemory = runtime.taskMemory(),
            )
        }
    }
}

/** 记忆与数据:保留原来的记忆 / 技能 / 待确认主页,网站登录和操作路线从这里各进一层。 */
@Composable
fun MemoryDataScreen(
    vm: MemoryViewModel,
    webSites: WebSitesState,
    routes: Int?,
    onClearRoutes: () -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(MemoryDataPage.Home) }
    val toHome = { page = MemoryDataPage.Home }
    if (page != MemoryDataPage.Home) BackHandler(onBack = toHome)
    when (page) {
        MemoryDataPage.Home -> MemoryScreen(
            vm, onBack,
            webSitesCount = webSites.sites.size, onOpenWebSites = { page = MemoryDataPage.WebSites },
            routes = routes, onOpenRoutes = { page = MemoryDataPage.Routes },
        )
        MemoryDataPage.WebSites -> WebSitesScreen(webSites, onBack = toHome)
        MemoryDataPage.Routes -> RoutesScreen(routes, onClear = onClearRoutes, onBack = toHome)
    }
}

/** 设置 →「记忆与数据」:看得见、改得了、删得掉。分三段:记忆、技能(内容在 [SkillsSection])、待确认。 */
@Composable
fun MemoryScreen(
    vm: MemoryViewModel,
    onBack: () -> Unit,
    webSitesCount: Int? = null,
    onOpenWebSites: () -> Unit = {},
    routes: Int? = null,
    onOpenRoutes: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val ui by vm.ui.collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    val searchable = ui.segment != Segment.Skills

    SubPage(
        stringResource(R.string.settings_memory_data), onBack,
        right = if (searchable) ({
            BarIconButton(QaviIcons.Search, stringResource(R.string.search), { searching = !searching; if (!searching) vm.search("") })
        }) else null,
    ) {
        ui.brokenNotice?.let {
            GroupCard { Text(stringResource(R.string.memory_unreadable), Modifier.padding(16.dp), style = QaviType.Caption, color = QaviTheme.colors.warn) }
        }

        val pendingLabel = if (ui.pendingCount > 0) stringResource(R.string.memory_pending_n, ui.pendingCount) else stringResource(R.string.memory_pending)
        SegmentedControl(
            listOf(stringResource(R.string.memory_memory), stringResource(R.string.memory_skills), pendingLabel), Segment.entries.indexOf(ui.segment), { vm.select(Segment.entries[it]) },
            tags = SEGMENT_TAGS,
        )
        if (searchable && (searching || ui.query.isNotEmpty())) {
            SearchField(ui.query, vm::search, stringResource(R.string.search), Modifier.testTag("memory-search"))
        }

        when (ui.segment) {
            Segment.Memories -> MemoryEntries(vm, ui)
            Segment.Skills -> SkillsSection(vm, ui.skills, pending = false)
            Segment.Pending -> {
                if (ui.pendingCount == 0) {
                    GroupLabel("")
                    EmptyNote(stringResource(R.string.memory_nothing_pending))
                }
                if (ui.pending.isNotEmpty()) {
                    GroupLabel(stringResource(R.string.memory_pending_title))
                    GroupCard {
                        ui.pending.forEachIndexed { i, r ->
                            Column {
                                MemoryRow(r, first = i == 0, onClick = null)
                                RowActions {
                                    QaviButton(stringResource(R.string.ok_yes), { vm.approve(r.id) }, size = ButtonSize.Small)
                                    QaviButton(stringResource(R.string.no_thanks), { vm.reject(r.id) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                                }
                            }
                        }
                    }
                }
                SkillsSection(vm, ui.skills, pending = true)
            }
        }

        GroupLabel(stringResource(R.string.memory_switches))
        GroupCard {
            SwitchRow(stringResource(R.string.memory_use), stringResource(R.string.memory_use_sub), ui.switches.useMemory, first = true) { on -> vm.setSwitches { it.copy(useMemory = on) } }
            SwitchRow(stringResource(R.string.memory_auto), stringResource(R.string.memory_auto_sub), ui.switches.autoRemember) { on -> vm.setSwitches { it.copy(autoRemember = on) } }
            SwitchRow(stringResource(R.string.memory_infer), stringResource(R.string.memory_infer_sub), ui.switches.inferMemories) { on -> vm.setSwitches { it.copy(inferMemories = on) } }
            SwitchRow(stringResource(R.string.memory_learn), stringResource(R.string.memory_learn_sub), ui.switches.learnSkills) { on -> vm.setSwitches { it.copy(learnSkills = on) } }
            SwitchRow(stringResource(R.string.memory_tasks), stringResource(R.string.memory_tasks_sub), ui.switches.rememberTasks) { on -> vm.setSwitches { it.copy(rememberTasks = on) } }
        }
        TaskMemoriesSection(vm)
        if (webSitesCount != null || routes != null) {
            GroupLabel(stringResource(R.string.settings_saved_data))
            GroupCard {
                var first = true
                fun take(): Boolean = first.also { first = false }
                webSitesCount?.let {
                    ListRow(QaviIcons.Globe, stringResource(R.string.settings_web_logins), first = take(), onClick = onOpenWebSites) {
                        RowValue(trPlural(R.plurals.settings_count_sites, it))
                        RowChevron()
                    }
                }
                routes?.let {
                    ListRow(QaviIcons.Route, stringResource(R.string.settings_routes), first = take(), onClick = onOpenRoutes) {
                        RowValue(trPlural(R.plurals.settings_count_routes, it))
                        RowChevron()
                    }
                }
            }
        }
        GroupCard {
            ListRow(QaviIcons.Close, stringResource(R.string.routes_clear_all), stringResource(R.string.memory_clear_sub), first = true, titleColor = QaviTheme.colors.bad, tone = TileTone.Bad, onClick = vm::requestClear)
        }
    }

    MemoryMessage(vm, ui.message)
    if (ui.clearStage > 0) {
        QaviDialog(
            onDismissRequest = vm::cancelClear,
            title = { Text(if (ui.clearStage == 1) stringResource(R.string.memory_clear_q) else stringResource(R.string.memory_clear_sure)) },
            text = { Text(if (ui.clearStage == 1) stringResource(R.string.memory_clear_body) else stringResource(R.string.memory_clear_final)) },
            confirmButton = { TextButton(vm::confirmClear, Modifier.testTag("confirm-clear")) { Text(if (ui.clearStage == 1) stringResource(R.string.chat_continue) else stringResource(R.string.routes_clear_all)) } },
            dismissButton = { TextButton(vm::cancelClear) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** 跨会话任务摘要(任务结束自动记的):看得见、删得掉。没有就不占地方。 */
@Composable
private fun TaskMemoriesSection(vm: MemoryViewModel) {
    val tasks by vm.tasks.collectAsState()
    if (tasks.isEmpty()) return
    var acting by remember { mutableStateOf<TaskMemory?>(null) }
    GroupLabel(stringResource(R.string.memory_tasks_title))
    GroupCard {
        tasks.forEachIndexed { i, t ->
            val subtitle = listOfNotNull(
                SimpleDateFormat("yyyy-MM-dd HH:mm", uiLocale()).format(Date(t.createdAt)),
                t.conclusion.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            ListRow(null, t.goal, subtitle, first = i == 0, onClick = { acting = t })
        }
    }
    acting?.let { t ->
        ActionSheet(
            t.goal,
            listOf(SheetAction(stringResource(R.string.confirm_delete), destructive = true) { vm.deleteTask(t.id); acting = null }),
            onDismiss = { acting = null },
        )
    }
}

@Composable
internal fun MemoryEntries(vm: MemoryViewModel, ui: MemoryUi, allowAdd: Boolean = true) {
    var editing by remember { mutableStateOf<Row?>(null) }
    var acting by remember { mutableStateOf<Row?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (ui.rows.isEmpty()) {
        GroupLabel("")
        EmptyNote(if (ui.query.isBlank()) stringResource(R.string.memory_empty) else stringResource(R.string.not_found))
    }
    KIND_GROUPS.forEach { (kind, label) ->
        val rows = ui.rows.filter { it.kind == kind }
        if (rows.isNotEmpty()) {
            GroupLabel(label)
            GroupCard {
                rows.forEachIndexed { i, r ->
                    MemoryRow(r, first = i == 0, onClick = { acting = r })
                }
            }
        }
    }
    if (allowAdd) {
        GroupLabel("")
        GroupCard {
            ListRow(QaviIcons.Plus, stringResource(R.string.memory_add), first = true, titleColor = QaviTheme.colors.accent, tone = TileTone.Accent, onClick = { adding = true })
        }
    }

    acting?.let { r ->
        ActionSheet(
            r.content,
            listOf(
                SheetAction(stringResource(R.string.edit_it)) { editing = r },
                SheetAction(if (r.pinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) { vm.setPinned(r.id, !r.pinned) },
                SheetAction(stringResource(R.string.confirm_delete), destructive = true) { vm.delete(r.id) },
            ),
            onDismiss = { acting = null },
        )
    }
    editing?.let { r ->
        EditDialog(stringResource(R.string.edit_it), r.content, r.keywords, onDismiss = { editing = null }) { content, keywords, _ -> vm.edit(r.id, content, keywords); editing = null }
    }
    if (adding) {
        EditDialog(stringResource(R.string.memory_add), "", emptyList(), onDismiss = { adding = false }, withKind = true) { content, keywords, kind -> vm.add(kind, content, keywords); adding = false }
    }
}

@Composable
internal fun MemoryMessage(vm: MemoryViewModel, message: String?) {
    message?.let { m ->
        QaviNoticeDialog(m, onDismiss = vm::dismissMessage)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, on: Boolean, first: Boolean = false, onChange: (Boolean) -> Unit) {
    ListRow(null, title, subtitle, first = first) { QaviSwitch(on, onChange) }
}

@Composable
private fun MemoryRow(r: Row, first: Boolean, onClick: (() -> Unit)?) {
    val subtitle = listOfNotNull(
        (if (r.pinned) stringResource(R.string.memory_pinned_prefix) else "") + r.source,
        r.replaces?.let { stringResource(R.string.memory_was, it) },
    ).joinToString("\n")
    androidx.compose.foundation.layout.Box(Modifier.testTag("memory-row")) {
        ListRow(null, r.content, subtitle, first = first, onClick = onClick)
    }
}

@Composable
internal fun Link(text: String, onClick: () -> Unit) {
    Text(text, Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp), style = QaviType.Hint, fontWeight = FontWeight.Medium, color = QaviTheme.colors.accent)
}

@Composable
private fun EditDialog(
    title: String, content: String, keywords: List<String>, onDismiss: () -> Unit,
    withKind: Boolean = false, onSave: (String, List<String>, MemoryKind) -> Unit,
) {
    var kind by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf(content) }
    var words by remember { mutableStateOf(keywords.joinToString(" ")) }
    QaviDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            KeyboardDismissArea { Column {
                if (withKind) SegmentedControl(KIND_NAMES, kind, { kind = it }, Modifier.padding(bottom = 8.dp), tags = listOf("memory-kind-fact", "memory-kind-preference", "memory-kind-procedure"))
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().keepsKeyboard().testTag("memory-content"), label = { Text(stringResource(R.string.memory_content)) })
                OutlinedTextField(words, { words = it }, Modifier.fillMaxWidth().padding(top = 8.dp).keepsKeyboard().testTag("memory-keywords"), label = { Text(stringResource(R.string.memory_keywords)) })
            } }
        },
        confirmButton = { TextButton({ onSave(text, words.split(Regex("\\s+")).filter { it.isNotBlank() }, KIND_GROUPS[kind].first) }, Modifier.testTag("memory-save")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
