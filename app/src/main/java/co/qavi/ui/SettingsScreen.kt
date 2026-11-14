package co.qavi.ui

import co.qavi.R
import co.qavi.i18n.tr
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import co.qavi.settings.ProviderConnection
import android.app.ActivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import co.qavi.ui.components.QaviDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import co.qavi.BuildConfig
import co.qavi.cap.sandbox.SandboxState
import co.qavi.capsule.CapsuleMode
import co.qavi.capsule.CapsuleSetup
import co.qavi.cap.phone.a11y.ScreenAccessibility
import co.qavi.core.agent.SpeedFlags
import co.qavi.core.cap.sandbox.SandboxStatus
import co.qavi.core.model.ModelRole
import co.qavi.core.shell.BuiltinShellStatus
import co.qavi.record.RunStore
import co.qavi.settings.AccessibilityState
import co.qavi.settings.FeatureFlags
import co.qavi.settings.Prerequisites
import co.qavi.settings.Problem
import co.qavi.settings.ProviderStore
import co.qavi.settings.NetProxyStore
import co.qavi.settings.WebSearchConfig
import co.qavi.settings.WebSearchSettings
import co.qavi.cap.web.WebProxy
import co.qavi.settings.SystemStatus
import co.qavi.settings.notificationOverview
import co.qavi.task.ParallelCapability
import co.qavi.ui.components.ButtonKind
import co.qavi.ui.components.ButtonSize
import co.qavi.ui.components.GroupCard
import co.qavi.ui.components.GroupLabel
import co.qavi.ui.components.ListRow
import co.qavi.ui.components.QaviButton
import co.qavi.ui.components.QaviSwitch
import co.qavi.ui.components.PageScaffold
import co.qavi.ui.components.PillTone
import co.qavi.ui.components.RowChevron
import co.qavi.ui.components.RowValue
import co.qavi.ui.components.StateCard
import co.qavi.ui.components.StatusPill
import co.qavi.ui.components.TileTone
import co.qavi.ui.theme.QaviIcons
import co.qavi.ui.theme.QaviTheme
import co.qavi.i18n.AppLanguage
import co.qavi.wechat.WechatConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置里能进入的子页面;[Home] 是设置首页,[Advanced] 集中放置连接、维护和工程能力。 */
private enum class SettingsPage { Home, Advanced, AppAccess, PhoneOps, Calls, Wechat, DuringWork, Capsule, Language, Notifications, Recommendations, Sandbox, Recovery, SelfCheck, ComputerAccess, McpServers, Root, Developer, RunStats, Proactive }

/**
 * 设置页:总状态卡 + 常用 / 我的数据 / 其他。高级与其他只增加一层,各子页返回来时的页面。
 * 模型页由 [onOpenModels]、记忆与数据由 [onOpenMemory]、关于页由 [onOpenAbout] 交给 Activity。
 * 修复动作由 [onFix] 交给 Activity 去做(请求权限、跳转系统设置)。
 */
@Composable
fun SettingsScreen(
    store: ProviderStore,
    status: SystemStatus,
    androidRelease: String,
    onFix: (Fix) -> Unit,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenMemory: (() -> Unit)? = null,
    /** 任务运行里的「自主模式」:当前值和换它的办法。 */
    autonomous: Boolean = false,
    onAutonomous: (Boolean) -> Unit = {},
    /** 查到的新版本号;有它时「关于 Qavi」一行写「x 可更新」带红点。 */
    updateVersion: String? = null,
    onOpenAbout: () -> Unit = {},
    /** 外部 MCP server 设置(outbound);为空时不显示入口。 */
    mcpServers: co.qavi.settings.McpServerStore? = null,
) {
    val configs by store.configs.collectAsState()
    val roles by store.roles.collectAsState()
    // 开发者选项里的「看图定位已验证」跟着看图模型(看图用主模型时是主模型那条配置)
    val visionConfig = remember(configs, roles) { store.configFor(ModelRole.Vision) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    var explaining by remember { mutableStateOf<Problem?>(null) }
    // 「完全退出」先问一句:任务、胶囊和通知都会停
    var confirmingQuit by remember { mutableStateOf(false) }

    fun fix(f: Fix) = if (f == Fix.AddModel) onOpenModels() else onFix(f)
    val onProblem: (Problem) -> Unit = { p -> StatusCopy.directFix(p)?.let(::fix) ?: run { explaining = p } }

    // 并行任务的上限只在实验开关 parallelTasks 打开时出现;设备能力按此刻算,第二块屏建失败过就按失败算
    val context = LocalContext.current
    val recommendedTasks = remember { (context.applicationContext as co.qavi.QaviApp).recommendedTasks }
    val root = remember { co.qavi.cap.phone.PhoneServices.of(context).shellManager.root }
    val rootState by root.state.collectAsState()
    LaunchedEffect(Unit) { root.refresh() }
    val wechat = remember { (context.applicationContext as co.qavi.QaviApp).wechat }
    val wechatState by wechat.state.collectAsState()
    val flags = remember { FeatureFlags.get(context) }
    var personalStart by remember { mutableStateOf(flags.personalStart()) }
    var parallelMax by remember { mutableIntStateOf(flags.parallelMax()) }
    var keepScreenOn by remember { mutableStateOf(flags.keepScreenOn()) }
    var muteTarget by remember { mutableStateOf(flags.muteTarget()) }
    var operatingPace by remember { mutableStateOf(flags.operatingPace()) }
    var stepLimit by remember { mutableStateOf(flags.taskStepLimit()) }
    var tokenBudget by remember { mutableStateOf(flags.taskTokenBudget()) }
    var selfConfig by remember { mutableStateOf(flags.selfConfig()) }
    var selfOperate by remember { mutableStateOf(flags.selfOperate()) }
    var pipOperate by remember { mutableStateOf(flags.pipOperate()) }
    var externalSchedule by remember { mutableStateOf(flags.externalSchedule()) }
    var autoArchiveDays by remember { mutableStateOf(flags.autoArchiveDays()) }
    var editingAutoArchive by remember { mutableStateOf(false) }
    val capsuleMode by remember(flags) { flags.capsuleModeChanges() }.collectAsState(flags.capsuleMode())
    var capsuleIdleOpacity by remember { mutableStateOf(flags.capsuleIdleOpacity()) }
    var capsuleCardScale by remember { mutableStateOf(flags.capsuleCardScale()) }
    // 换语言时界面会重建,这里每次进来读一次即可
    val language = remember { AppLanguage.current(context) }
    val runtimeLimit by (context.applicationContext as co.qavi.QaviApp).taskState.parallelLimit.collectAsState()
    var speed by remember { mutableStateOf(flags.speedFlags()) }
    val scope = rememberCoroutineScope()
    val sandboxStatus by remember { SandboxState.get(context) }.status.collectAsState()
    // 网络代理:只在这里改;读设置要解密、看浏览器支不支持要加载 WebView,都放到后台
    val proxies = remember { NetProxyStore.get(context) }
    var proxy by remember { mutableStateOf<String?>(null) }
    var browserProxy by remember { mutableStateOf<WebProxy.Mode?>(null) }
    var editingProxy by remember { mutableStateOf(false) }
    LaunchedEffect(proxy) {
        if (proxy == null) proxy = withContext(Dispatchers.IO) { proxies.get() }
        browserProxy = withContext(Dispatchers.IO) { WebProxy.mode() }
    }
    val searchSettings = remember { WebSearchSettings.get(context) }
    var webSearch by remember { mutableStateOf<WebSearchConfig?>(null) }
    var editingSearch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { webSearch = withContext(Dispatchers.IO) { searchSettings.get() } }
    val mcp = remember { (context.applicationContext as co.qavi.QaviApp).mcp }
    val mcpNetworkOn by mcp.settings.enabled.collectAsState()
    val mcpUsbOn by mcp.usb.enabled.collectAsState()
    val mcpOn = mcpNetworkOn || mcpUsbOn
    val parallel = if (!speed.parallelTasks) null else ParallelSetting(
        parallelMax,
        ParallelCapability.detect(
            status.sdkInt, context.getSystemService(ActivityManager::class.java).isLowRamDevice, parallelMax,
            secondDisplayFailed = runtimeLimit.reason == ParallelCapability.SECOND_DISPLAY_FAILED,
        ),
    )
    // 开发者选项只在调试版和性能测试版里列出:发布版的各开关按评测结果统一定默认值
    val showDeveloper = co.qavi.record.DetailedTraces.supportedBuild
    val toHome = { page = SettingsPage.Home }
    val toAdvanced = { page = SettingsPage.Advanced }
    val back = {
        page = when (page) {
            SettingsPage.RunStats, SettingsPage.Proactive -> SettingsPage.Developer
            SettingsPage.SelfCheck -> SettingsPage.Recovery
            SettingsPage.Sandbox, SettingsPage.Recovery, SettingsPage.ComputerAccess,
            SettingsPage.McpServers, SettingsPage.Root, SettingsPage.Developer -> SettingsPage.Advanced
            else -> SettingsPage.Home
        }
    }

    if (page != SettingsPage.Home) BackHandler { back() }

    val visiblePage = when {
        !showDeveloper && (page == SettingsPage.Developer || page == SettingsPage.RunStats || page == SettingsPage.Proactive) -> SettingsPage.Home
        page == SettingsPage.Root && !rootState.suPresent -> SettingsPage.Advanced
        else -> page
    }
    when (visiblePage) {
        SettingsPage.Home -> SettingsContent(
            status = status, androidRelease = androidRelease,
            mainModel = roles.main?.let { id -> configs.firstOrNull { it.id == id } }?.let(ProviderConnection::displayName),
            onBack = onBack, onProblem = onProblem, onOpenModels = onOpenModels,
            onOpenPhoneOps = { page = SettingsPage.PhoneOps },
            onOpenCalls = { page = SettingsPage.Calls },
            wechatState = wechatState, wechatBound = wechat.store.account() != null,
            onOpenWechat = { page = SettingsPage.Wechat },
            onOpenAppAccess = { page = SettingsPage.AppAccess },
            onOpenDuringWork = { page = SettingsPage.DuringWork },
            capsuleMode = capsuleMode, onOpenCapsule = { page = SettingsPage.Capsule },
            onOpenNotifications = { page = SettingsPage.Notifications },
            language = language, onOpenLanguage = { page = SettingsPage.Language },
            personalStart = personalStart,
            onPersonalStart = { flags.setPersonalStart(it); personalStart = it },
            autoArchiveDays = autoArchiveDays,
            onOpenAutoArchive = { editingAutoArchive = true },
            onOpenMemory = onOpenMemory,
            onOpenRecommendations = { page = SettingsPage.Recommendations },
            onOpenAdvanced = { page = SettingsPage.Advanced },
            versionName = BuildConfig.VERSION_NAME, updateVersion = updateVersion, onOpenAbout = onOpenAbout,
        )
        SettingsPage.Advanced -> AdvancedSettingsContent(
            proxy = proxy, proxyNote = proxy?.let { NetProxyCopy.subtitle(it, browserProxy) }, onOpenProxy = { editingProxy = true },
            webSearch = webSearch, onOpenWebSearch = { editingSearch = true },
            sandbox = sandboxStatus, onOpenSandbox = { page = SettingsPage.Sandbox },
            showRecovery = status.sdkInt >= Prerequisites.MIN_SDK, onOpenRecovery = { page = SettingsPage.Recovery },
            computerAccess = mcpOn, onOpenComputerAccess = { page = SettingsPage.ComputerAccess },
            mcpServersCount = mcpServers?.servers?.value?.size, onOpenMcpServers = { page = SettingsPage.McpServers },
            showRoot = rootState.suPresent, rootCommands = rootState.commands, onOpenRoot = { page = SettingsPage.Root },
            showDeveloper = showDeveloper, onOpenDeveloper = { page = SettingsPage.Developer },
            onBack = toHome,
            onQuit = { confirmingQuit = true },
        )
        SettingsPage.AppAccess -> AppAccessScreen(onBack = toHome)
        SettingsPage.Wechat -> WechatSettingsScreen(wechat, onBack = toHome)
        SettingsPage.PhoneOps -> PhoneOpsScreen(
            status, androidRelease, onProblem,
            onRepair = { onFix(Fix.RepairPhoneHelper) }, onUnpair = { onFix(Fix.UnpairPhoneHelper) }, onBack = toHome,
        )
        SettingsPage.Calls -> CallSettingsScreen((context.applicationContext as co.qavi.QaviApp).calls, onBack = toHome)
        SettingsPage.Notifications -> NotificationsScreen(status, onFix = ::fix, onBack = back)
        SettingsPage.DuringWork -> DuringWorkScreen(
            keepScreenOn, onKeepScreenOn = { flags.setKeepScreenOn(it); keepScreenOn = it },
            muteTarget, onMuteTarget = { flags.setMuteTarget(it); muteTarget = it },
            parallel, onParallelMax = { flags.setParallelMax(it); parallelMax = it }, onBack = toHome,
            autonomous = autonomous, onAutonomous = onAutonomous,
            operatingPace = operatingPace, onOperatingPace = { flags.setOperatingPace(it); operatingPace = it },
            stepLimit = stepLimit, onStepLimit = { flags.setTaskStepLimit(it); stepLimit = it },
            tokenBudget = tokenBudget, onTokenBudget = { flags.setTaskTokenBudget(it); tokenBudget = it },
            selfConfig = selfConfig, onSelfConfig = { flags.setSelfConfig(it); selfConfig = it },
            selfOperate = selfOperate, onSelfOperate = { flags.setSelfOperate(it); selfOperate = it },
            pipOperate = pipOperate, onPipOperate = { flags.setPipOperate(it); pipOperate = it },
            externalSchedule = externalSchedule, onExternalSchedule = { flags.setExternalSchedule(it); externalSchedule = it },
        )
        SettingsPage.Language -> LanguageScreen(language, onLanguage = { AppLanguage.set(context, it) }, onBack = toHome)
        SettingsPage.Recommendations -> RecommendedTasksScreen(recommendedTasks, onBack = toHome)
        SettingsPage.Capsule -> CapsuleScreen(
            capsuleMode, onMode = {
                flags.setCapsuleMode(it)
                if (CapsuleSetup.needsAccessibility(it, ScreenAccessibility.instance.value != null)) onFix(Fix.OpenAccessibility)
            }, onBack = toHome,
            idleOpacity = capsuleIdleOpacity,
            onIdleOpacity = { flags.setCapsuleIdleOpacity(it); capsuleIdleOpacity = it },
            cardScale = capsuleCardScale,
            onCardScale = { flags.setCapsuleCardScale(it); capsuleCardScale = it },
        )
        SettingsPage.Sandbox -> SandboxScreen(onBack = toAdvanced)
        SettingsPage.Recovery -> RecoveryScreen(onBack = toAdvanced, onOpenSelfCheck = { page = SettingsPage.SelfCheck })
        SettingsPage.SelfCheck -> SelfCheckScreen(status, onBack = back)
        SettingsPage.ComputerAccess -> McpScreen(mcp, onBack = toAdvanced)
        SettingsPage.McpServers -> mcpServers?.let { McpServersScreen(it, onBack = toAdvanced) }
        SettingsPage.Root -> RootScreen(helperRow = status.sdkInt >= Prerequisites.MIN_SDK, onBack = toAdvanced)
        SettingsPage.Developer -> DeveloperOptionsScreen(
            speed, onSpeed = { flags.setSpeed(it); speed = it }, onBack = toAdvanced,
            grounding = visionConfig?.let { GroundingSetting(it.model, it.capabilities.groundingVerified) },
            onGrounding = { verified -> scope.launch { withContext(Dispatchers.IO) { store.setVisionGroundingVerified(verified) } } },
            onOpenRunStats = { page = SettingsPage.RunStats },
            onOpenProactive = { page = SettingsPage.Proactive },
        )
        SettingsPage.RunStats -> RunStatsScreen(remember { RunStore(context).metrics }, onBack = back)
        SettingsPage.Proactive -> co.qavi.proactive.ProactiveScreen(onBack = back)
    }

    if (editingProxy) NetProxyDialog(
        initial = proxy.orEmpty(),
        onDismiss = { editingProxy = false },
        onSave = { v ->
            scope.launch {
                withContext(Dispatchers.IO) { proxies.set(v) }
                proxy = v.trim()
                editingProxy = false
            }
        },
    )

    if (editingSearch) WebSearchDialog(
        initial = webSearch ?: WebSearchConfig(),
        onDismiss = { editingSearch = false },
        onSave = { config ->
            scope.launch {
                withContext(Dispatchers.IO) { searchSettings.set(config) }
                webSearch = searchSettings.get()
                editingSearch = false
            }
        },
    )

    explaining?.let { p ->
        val e = StatusCopy.explain(p, androidRelease)
        QaviDialog(
            onDismissRequest = { explaining = null },
            title = { Text(e.title) },
            text = {
                Column {
                    Text(e.body)
                    e.secondaryAction?.let { (label, destination) ->
                        TextButton(onClick = {
                            explaining = null
                            fix(destination)
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {
                if (e.action != null && e.fix != null) {
                    TextButton({ explaining = null; fix(e.fix) }) { Text(e.action) }
                } else {
                    TextButton({ explaining = null }) { Text(stringResource(R.string.ok_got_it)) }
                }
            },
            dismissButton = if (e.fix != null) ({ TextButton({ explaining = null }) { Text(stringResource(R.string.cancel)) } }) else null,
        )
    }

    if (confirmingQuit) {
        AlertDialog(
            onDismissRequest = { confirmingQuit = false },
            title = { Text(stringResource(R.string.quit_title)) },
            text = { Text(stringResource(R.string.quit_confirm_body)) },
            confirmButton = {
                TextButton({ confirmingQuit = false; co.qavi.quit.QaviQuit.quit(context) }) {
                    Text(stringResource(R.string.quit_confirm_action), color = QaviTheme.colors.bad)
                }
            },
            dismissButton = { TextButton({ confirmingQuit = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (editingAutoArchive) {
        AutoArchiveDialog(
            current = autoArchiveDays,
            onPick = { days ->
                flags.setAutoArchiveDays(days)
                autoArchiveDays = days
                editingAutoArchive = false
                // 选完马上按新设置跑一次,老对话立刻进归档
                (context.applicationContext as co.qavi.QaviApp).autoArchiveNow()
            },
            onDismiss = { editingAutoArchive = false },
        )
    }
}

/** 不带状态的设置首页,便于测试。首页只列常用入口,高级能力统一从 [onOpenAdvanced] 进入。 */
@Composable
fun SettingsContent(
    status: SystemStatus,
    androidRelease: String,
    /** 当前主模型的名字;没有可用的主模型时为空。 */
    mainModel: String?,
    onBack: () -> Unit,
    onProblem: (Problem) -> Unit,
    onOpenModels: () -> Unit,
    onOpenPhoneOps: () -> Unit = {},
    onOpenCalls: () -> Unit = {},
    wechatState: WechatConnectionState = WechatConnectionState.NeedsBinding,
    wechatBound: Boolean = false,
    onOpenWechat: () -> Unit = {},
    onOpenAppAccess: () -> Unit = {},
    onOpenDuringWork: () -> Unit = {},
    /** 悬浮胶囊此刻的选择,写在「悬浮胶囊」一行的右边。 */
    capsuleMode: CapsuleMode = CapsuleMode.DEFAULT,
    onOpenCapsule: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    /** 此刻选的界面语言,写在「语言与外观」一行的右边。 */
    language: AppLanguage = AppLanguage.System,
    onOpenLanguage: () -> Unit = {},
    onOpenMemory: (() -> Unit)? = null,
    onOpenRecommendations: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    versionName: String = "",
    updateVersion: String? = null,
    onOpenAbout: () -> Unit = {},
    personalStart: Boolean = false,
    onPersonalStart: ((Boolean) -> Unit)? = null,
    /** 自动归档:多久没有新消息的对话自动归档;0 表示关闭。 */
    autoArchiveDays: Int = 0,
    onOpenAutoArchive: () -> Unit = {},
) {
    val check = Prerequisites.check(status, hasMainModel = mainModel != null)
    PageScaffold(stringResource(R.string.settings_title), onBack) {
        StatusSummary(check, onProblem)

        GroupLabel(stringResource(R.string.settings_basic))
        GroupCard {
            ListRow(QaviIcons.Spark, stringResource(R.string.settings_model), stringResource(R.string.settings_model_sub), first = true, onClick = onOpenModels) {
                RowValue(mainModel ?: stringResource(R.string.settings_not_set))
                RowChevron()
            }
            ListRow(QaviIcons.Phone, stringResource(R.string.settings_phone_ops), onClick = onOpenPhoneOps) {
                PhoneOpsPill(status)
                RowChevron()
            }
            ListRow(QaviIcons.Phone, stringResource(R.string.calls_title), stringResource(R.string.calls_entry_sub), onClick = onOpenCalls) { RowChevron() }
            ListRow(QaviIcons.Chats, stringResource(R.string.wechat_title), stringResource(R.string.wechat_settings_sub), onClick = onOpenWechat) {
                WechatEntryStatus(wechatState, wechatBound)
                RowChevron()
            }
            ListRow(QaviIcons.Moon, stringResource(R.string.settings_during_work), stringResource(R.string.settings_during_work_sub), onClick = onOpenDuringWork) { RowChevron() }
            ListRow(QaviIcons.Message, CapsuleSettingsCopy.TITLE, CapsuleSettingsCopy.SUBTITLE, onClick = onOpenCapsule) {
                RowValue(CapsuleSettingsCopy.label(capsuleMode))
                RowChevron()
            }
            ListRow(QaviIcons.Globe, stringResource(R.string.settings_language_appearance), onClick = onOpenLanguage) {
                RowValue(languageLabel(language))
                RowChevron()
            }
            onPersonalStart?.let { change ->
                val label = stringResource(R.string.settings_personal_start)
                ListRow(QaviIcons.Spark, label, stringResource(R.string.settings_personal_start_sub)) {
                    QaviSwitch(personalStart, change, Modifier.semantics { contentDescription = label })
                }
            }
            val (notifText, notifTone) = StatusCopy.notificationSummary(status.notificationOverview().summary)
            ListRow(QaviIcons.Bell, StatusCopy.NOTIFICATIONS, stringResource(R.string.settings_notif_sub), onClick = onOpenNotifications) {
                RowValue(notifText, notifTone)
                RowChevron()
            }
        }

        GroupLabel(stringResource(R.string.settings_privacy))
        GroupCard {
            onOpenMemory?.let {
                ListRow(QaviIcons.Heart, stringResource(R.string.settings_memory_data), stringResource(R.string.settings_memory_sub), first = true, onClick = it) { RowChevron() }
            }
            ListRow(
                QaviIcons.Archive,
                stringResource(R.string.settings_auto_archive),
                stringResource(R.string.settings_auto_archive_sub),
                first = onOpenMemory == null,
                onClick = onOpenAutoArchive,
            ) {
                RowValue(
                    if (autoArchiveDays <= 0) stringResource(R.string.state_off)
                    else pluralStringResource(R.plurals.settings_auto_archive_days, autoArchiveDays, autoArchiveDays),
                )
                RowChevron()
            }
            ListRow(
                QaviIcons.Message,
                stringResource(R.string.recommendations_title),
                stringResource(R.string.recommendations_sub),
                onClick = onOpenRecommendations,
            ) { RowChevron() }
            ListRow(QaviIcons.Shield, stringResource(R.string.appaccess_title), stringResource(R.string.appaccess_row_sub), onClick = onOpenAppAccess) { RowChevron() }
        }

        GroupLabel(stringResource(R.string.settings_other))
        GroupCard {
            ListRow(QaviIcons.Wrench, stringResource(R.string.settings_advanced), stringResource(R.string.settings_advanced_sub), first = true, onClick = onOpenAdvanced) { RowChevron() }
        }
        GroupCard {
            ListRow(QaviIcons.Info, stringResource(R.string.settings_about), first = true, onClick = onOpenAbout) {
                TrailingValue(AboutCopy.settingsValue(updateVersion, versionName))
                RowChevron()
            }
        }
    }
}

/** 高级与其他:网络与连接、维护、开发者三组;各能力仍使用原来的完整子页。 */
@Composable
fun AdvancedSettingsContent(
    onBack: () -> Unit,
    proxy: String? = null,
    proxyNote: String? = null,
    onOpenProxy: () -> Unit = {},
    webSearch: WebSearchConfig? = null,
    onOpenWebSearch: () -> Unit = {},
    sandbox: SandboxStatus? = null,
    onOpenSandbox: () -> Unit = {},
    showRecovery: Boolean = true,
    onOpenRecovery: () -> Unit = {},
    computerAccess: Boolean? = null,
    onOpenComputerAccess: () -> Unit = {},
    mcpServersCount: Int? = null,
    onOpenMcpServers: () -> Unit = {},
    showRoot: Boolean = false,
    rootCommands: Boolean = false,
    onOpenRoot: () -> Unit = {},
    showDeveloper: Boolean = false,
    onOpenDeveloper: () -> Unit = {},
    onQuit: () -> Unit = {},
) {
    PageScaffold(stringResource(R.string.settings_advanced), onBack) {
        val sandboxRow = sandbox?.takeIf { it !is SandboxStatus.Unsupported }
        if (proxy != null || webSearch != null || sandboxRow != null || computerAccess != null || mcpServersCount != null || showRoot) {
            GroupLabel(stringResource(R.string.settings_connections))
            GroupCard {
                var first = true
                fun take(): Boolean = first.also { first = false }
                proxy?.let {
                    ListRow(QaviIcons.Link, NetProxyCopy.TITLE, proxyNote, first = take(), onClick = onOpenProxy) {
                        RowValue(NetProxyCopy.value(it))
                        RowChevron()
                    }
                }
                webSearch?.let { config ->
                    ListRow(QaviIcons.Globe, stringResource(R.string.web_search_title), stringResource(R.string.web_search_row_sub), first = take(), onClick = onOpenWebSearch) {
                        RowValue(WebSearchCopy.value(config)); RowChevron()
                    }
                }
                if (sandboxRow != null) {
                    ListRow(QaviIcons.Terminal, stringResource(R.string.settings_sandbox), first = take(), onClick = onOpenSandbox) {
                        RowValue(sandboxValue(sandboxRow)); RowChevron()
                    }
                }
                computerAccess?.let { on ->
                    ListRow(QaviIcons.Desktop, stringResource(R.string.mcp_title), first = take(), onClick = onOpenComputerAccess) {
                        RowValue(stringResource(if (on) R.string.state_on else R.string.state_off)); RowChevron()
                    }
                }
                mcpServersCount?.let { count ->
                    ListRow(QaviIcons.Link, stringResource(R.string.mcp_servers_title), first = take(), onClick = onOpenMcpServers) {
                        RowValue(stringResource(R.string.mcp_servers_count, count)); RowChevron()
                    }
                }
                if (showRoot) {
                    ListRow(QaviIcons.Key, PhoneSetupCopy.ROOT_GROUP, PhoneSetupCopy.rootEntryLine(rootCommands), first = take(), onClick = onOpenRoot) {
                        RowChevron()
                    }
                }
            }
        }
        if (showRecovery) {
            GroupLabel(stringResource(R.string.settings_maintenance))
            GroupCard {
                ListRow(QaviIcons.Wrench, stringResource(R.string.settings_recovery), first = true, onClick = onOpenRecovery) { RowChevron() }
            }
        }
        if (showDeveloper) {
            GroupLabel(stringResource(R.string.settings_developer_group))
            GroupCard {
                ListRow(QaviIcons.Code, stringResource(R.string.settings_developer), first = true, onClick = onOpenDeveloper) { RowChevron() }
            }
        }

        // 完全退出:和无障碍服务不同,它只是让 Qavi 安静下来;下次打开 App 就回来
        GroupCard {
            ListRow(
                QaviIcons.Power, stringResource(R.string.quit_title), stringResource(R.string.quit_row_sub),
                first = true, titleColor = QaviTheme.colors.bad, onClick = onQuit,
            ) { RowChevron() }
        }
    }
}

/** 「手机操作」一行尾部的状态胶囊:无障碍和内置助手都就绪才是「可用」。 */
@Composable
private fun PhoneOpsPill(status: SystemStatus) {
    when {
        status.sdkInt < Prerequisites.MIN_SDK -> StatusPill(stringResource(R.string.pill_unsupported), PillTone.Neutral)
        status.builtinShell == BuiltinShellStatus.Running && status.accessibility == AccessibilityState.On -> StatusPill(stringResource(R.string.pill_available), PillTone.Ok)
        else -> StatusPill(stringResource(R.string.pill_needs_attention), PillTone.Warn)
    }
}

private fun sandboxValue(s: SandboxStatus): String = when (s) {
    is SandboxStatus.Ready -> tr(R.string.sandbox_ready)
    is SandboxStatus.Installing -> tr(R.string.sandbox_preparing)
    is SandboxStatus.Broken -> tr(R.string.pill_needs_attention)
    else -> tr(R.string.sandbox_not_prepared)
}

/**
 * 页面顶上的总状态:一切正常是「一切就绪」;否则变成第一个问题的提示,带修复按钮。
 * 焦点提醒改不了,只在手机操作页的「系统版本」一行里提示,不占这里。
 */
@Composable
private fun StatusSummary(check: Prerequisites.Check, onProblem: (Problem) -> Unit) {
    val problem = check.problems.firstOrNull { it != Problem.FocusMayLeak }
    if (problem == null) {
        StateCard(QaviIcons.Check, stringResource(R.string.all_ready), stringResource(R.string.settings_all_ready_detail), TileTone.Ok, Modifier.testTag("settings-status"))
    } else {
        val copy = StatusCopy.banner(problem)
        StateCard(
            QaviIcons.Exclaim, copy.title, copy.detail,
            if (problem in check.blockers) TileTone.Bad else TileTone.Warn,
            Modifier.testTag("settings-status"),
        ) {
            QaviButton(copy.link, { onProblem(problem) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
        }
    }
}
