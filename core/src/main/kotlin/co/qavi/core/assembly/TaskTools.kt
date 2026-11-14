package co.qavi.core.assembly

import co.qavi.core.agent.CoreSections
import co.qavi.core.agent.DO_STEPS_TOOL
import co.qavi.core.cap.phone.AppPacing
import co.qavi.core.cap.phone.PHONE
import co.qavi.core.cap.phone.PhoneCapability
import co.qavi.core.cap.root.RootCapability
import co.qavi.core.cap.root.RootGroup
import co.qavi.core.cap.root.RootPort
import co.qavi.core.cap.sandbox.SandboxCapability
import co.qavi.core.cap.sandbox.SANDBOX
import co.qavi.core.cap.sandbox.SandboxGroup
import co.qavi.core.cap.sandbox.SandboxPort
import co.qavi.core.cap.web.WEB
import co.qavi.core.cap.web.WebCapability
import co.qavi.core.cap.web.WebGroup
import co.qavi.core.cap.web.WebPort
import co.qavi.core.uistate.IdleScroll
import co.qavi.core.cap.web.PagePacing
import co.qavi.core.uistate.PlacePace
import co.qavi.core.cap.mcp.McpConnection
import co.qavi.core.general.GeneralGroup
import co.qavi.core.knowledge.GuideCatalog
import co.qavi.core.memory.MemoryStore
import co.qavi.core.record.RunRecords
import co.qavi.core.memory.ProfileStore
import co.qavi.core.memory.UserUtterances
import co.qavi.core.skills.PersonalSkills
import co.qavi.core.uistate.ScreenPort
import co.qavi.core.tool.AgentTool
import co.qavi.core.tool.LegacyNames
import co.qavi.core.tool.ToolConfig
import co.qavi.core.tool.ToolDispatcher
import co.qavi.core.tool.ToolIndex
import co.qavi.core.vision.ImageOps
import co.qavi.core.vision.VisionService
import co.qavi.core.workspace.WorkspacePathCheck
import co.qavi.core.run.RunId
import co.qavi.core.run.RunSpec
import co.qavi.core.trigger.Scheduled
import co.qavi.core.trigger.TriggerDefinition
import co.qavi.core.trigger.ScheduleAdmin
import co.qavi.core.child.ChildRunHost
import co.qavi.core.userscreen.UserScreenSource

/**
 * 一个任务的工具:装上手机、网页、沙箱三种能力和核心工具。这是唯一同时认识几种能力的地方。
 * 没给端口的能力照样装上(工具都不提供),按名字查声明时它们的工具也查得到。root 命令例外:没给端口就整个不装。
 */
fun toolDispatcher(
    screen: ScreenPort,
    visionEnabled: Boolean = false,
    /** 只读的知识与技能目录;为空时不提供 knowledge_search / knowledge_read / skill_search / skill_read。 */
    skills: GuideCatalog? = null,
    /** 读屏时另截一张画面给运行记录(看不了图的模型也留画面)。 */
    recordFrames: Boolean = false,
    /** 看图模型;为空(设置里没有能看图的模型)时不提供视觉类工具。 */
    vision: VisionService? = null,
    /** 内置浏览器;为空时不提供任何网页工具,只有手机界面。 */
    web: WebPort? = null,
    /** 网页上传的工作区路径检查(规范化、符号链接解析由 app 侧做)。 */
    workspacePath: ((String) -> WorkspacePathCheck)? = null,
    /** 网页一页一页看的节奏(换页前补足停留,见 [PagePacing]);默认不等。 */
    webPacing: () -> PagePacing = { PagePacing.Off },
    /** 网站对浏览快慢的要求(按主机名);默认按 [skills] 里站点知识的 pace。 */
    webPlacePace: (String) -> PlacePace = { host -> PlacePace.of(skills?.sitePace(host)) },
    /** 网页停留时轻轻滚几下页面(见 [IdleScroll]);默认不滚。 */
    webIdleScroll: () -> IdleScroll? = { null },
    /** 在 App 里一步一步操作的节奏(改动屏幕的动作之前补足停留,见 [AppPacing]);默认不等。 */
    appPacing: () -> AppPacing = { AppPacing.Off },
    /** App 对浏览快慢的要求(按包名);默认按 [skills] 里 App 知识的 pace。 */
    appPace: (String) -> PlacePace = { pkg -> PlacePace.of(skills?.appPace(pkg)) },
    /** App 里新的一屏上的停留时轻轻滚几下(见 [IdleScroll]);默认不滚。 */
    appIdleScroll: () -> IdleScroll? = { null },
    /** 跨读屏的稳定编号:读屏给模型编号和 s<N>,动作前翻译回端口编号。 */
    stableRefs: Boolean = false,
    /** 写操作成功后等界面稳定再读一屏,把与上一屏的差异放进结果的 screen 字段;隐含 [stableRefs]。 */
    inlineScreen: Boolean = false,
    /** 先给模型元素表；只在元素信息明显不足时自动附图。动作仍带回元素变化。 */
    adaptiveVision: Boolean = false,
    /** 提速开关 deepLinks:提供打开链接的工具。 */
    deepLinks: Boolean = false,
    /** 记忆;和 [utterances] 都给了才提供 memory_search / memory_save / memory_edit。 */
    memory: MemoryStore? = null,
    profile: ProfileStore? = null,
    /** 用户本次说过的话,memory_save / memory_edit 的引文据此校验;AgentLoop 往里加。 */
    utterances: UserUtterances? = null,
    /** 本次任务所在的对话;记进记忆的出处。 */
    conversationId: String? = null,
    /** Linux 沙箱;设备支持时提供沙箱工具(没准备好时工具调用会等它)。 */
    sandbox: SandboxPort? = null,
    /** 本次任务的工作区(对话 ID)。 */
    workspace: () -> String = { "default" },
    /** 沙箱开始跑一条命令或脚本时(任务卡片显示「沙箱:…」)。 */
    onSandboxCommand: (String) -> Unit = {},
    /** 沙箱里 `qavi progress` 报的进度。 */
    onSandboxProgress: (String) -> Unit = {},
    /** 沙箱工具在等运行环境准备好(任务卡片显示「正在准备」)。 */
    onSandboxPreparing: () -> Unit = {},
    /** 开始跑技能脚本时;不给就按 [onSandboxCommand] 报。 */
    onSandboxScript: ((String) -> Unit)? = null,
    /** 发给主模型的图统一编码用;为空时原样发。 */
    imageOps: ImageOps? = vision?.ops,
    /** 开局约束允许的能力；旧调用方不传时仍得到今天的工具。 */
    runSpec: RunSpec = RunSpec(RunId("legacy"), "legacy", "legacy"),
    /** 这次任务接上的所有外部 MCP 端点(不分来源,调用方在一处拼好);每个成为一个能力,按 serverId 区分。 */
    mcpServers: List<McpConnection> = emptyList(),
    /** 存下一个定时任务,并说明能不能准时(见 [Scheduled])。 */
    scheduleTrigger: ((TriggerDefinition) -> Scheduled)? = null,
    scheduleAdmin: ScheduleAdmin? = null,
    childHost: ChildRunHost? = null,
    /** 这个对话里用户发来的屏幕;给了而这次的话或之前几轮里带着屏幕时提供 view_user_screen。 */
    userScreens: UserScreenSource? = null,
    /** 用户的个人技能;和 [skills]、[utterances] 都给了才提供 skill_save(用户要求时写下或改一份个人技能)。 */
    personalSkills: PersonalSkills? = null,
    /** 改 Qavi 自己设置的白名单入口;为空时不提供 settings_list、settings_set(见 [SelfConfig])。 */
    selfConfig: co.qavi.core.settings.SelfConfig? = null,
    /** 存下的运行记录;给了、而这个对话之前有记过运行的轮次时提供 history_read(查之前几轮读到的东西)。 */
    runRecords: RunRecords? = null,
    /** 以 root 跑命令(已 root 的手机上用户打开了「允许 Qavi 执行 root 命令」才给);为空时 root 工具不登记。 */
    root: RootPort? = null,
    /** 用户配置并启用的通用网页搜索;为空时保持原有浏览器搜索路径。 */
    webSearch: co.qavi.core.search.WebSearch? = null,
    /** 读 Qavi 自己的状态;只对手机上、有用户的 UI 主任务给。 */
    self: co.qavi.core.self.SelfPort? = null,
    /** 操作 Qavi 自己的界面/文件/通知/剪贴板;用户打开「允许 Qavi 操作自己的界面」时才给。 */
    selfOperate: co.qavi.core.self.SelfPort? = null,
    phoneCalls: co.qavi.core.calls.PhoneCallPort? = null,
    /** 跨会话任务摘要;给了才提供 task_memory_search / task_memory_timeline / task_memory_step。 */
    taskMemory: co.qavi.core.memory.TaskMemoryStore? = null,
    /** 这两天的通知原文和存着的各天、各周、各月的归纳(主动能力攒下的、够格带进任务的);给了才提供 notices_read。 */
    notices: co.qavi.core.proactive.NoticeSource? = null,
): ToolDispatcher {
    val screenshotStore = sandbox?.takeIf {
        it.status() !is co.qavi.core.cap.sandbox.SandboxStatus.Unsupported &&
            !runSpec.readOnly && runSpec.provenance.origin != co.qavi.core.run.RunOrigin.Child &&
            (runSpec.capabilities == null || "sandbox" in runSpec.capabilities)
    }?.let { co.qavi.core.cap.sandbox.WorkspaceScreenshots(it, workspace) }
    val config = ToolConfig(visionEnabled, skills, recordFrames, vision, inlineScreen, adaptiveVision, memory, profile, utterances, conversationId,
        imageOps, scheduleTrigger, scheduleAdmin, runSpec, childHost, userScreens, personalSkills, selfConfig, runRecords, webSearch, self, selfOperate,
        screenshotStore?.let { store -> { path, png -> store.save(path, png) } }, taskMemory = taskMemory, notices = notices)
    val providers: List<CapabilityProvider> = listOf(
        object : CapabilityProvider {
            override val id = PHONE
            override fun open(spec: RunSpec) = PhoneCapability(screen, deepLinks, appPacing(), appPace, appIdleScroll())
        },
        object : CapabilityProvider {
            override val id = WEB
            override fun open(spec: RunSpec) = WebCapability(web, workspacePath, webPacing(), webPlacePace, webIdleScroll())
        },
        object : CapabilityProvider {
            override val id = SANDBOX
            override fun open(spec: RunSpec) = SandboxCapability(sandbox, workspace, visionEnabled,
                onSandboxCommand, onSandboxProgress, onSandboxPreparing, onSandboxScript)
        },
    ) + listOfNotNull(root?.let { port ->
        object : CapabilityProvider {
            override val id = RootCapability.KEY.name
            override fun open(spec: RunSpec) = RootCapability(port)
        }
    }, phoneCalls?.let { port ->
        object : CapabilityProvider {
            override val id = "calls"
            override fun open(spec: RunSpec) = co.qavi.core.calls.PhoneCallsCapability(port)
        }
    }) + mcpServers.map(::McpClientProvider)
    return RunAssembler(providers).assemble(runSpec, config, stableRefs, FORMER_PREFIX, RENAMED, RETIRED, MOVED)
}

/** 改名前各能力工具名的前缀:手机的没有前缀,网页的是 browser_。 */
private val FORMER_PREFIX = mapOf(PHONE to "", WEB to "browser_")
/** 屏幕工具第二次改名时动词部分的变化(以前 → 现在),手机、网页两套一样。 */
private val SCREEN_VERBS_MOVED = mapOf(
    "dump_elements" to "list_elements", "locate_on_screen" to "find_visually",
    "screen_description" to "describe", "check_screen" to "diagnose",
)

/** 除了前缀,其余部分也改过的(现在的名字 → 最早的名字;第二次改名的屏幕工具最早是几块屏共用的那个名字)。 */
private val RENAMED = mapOf("web_wait_for" to "browser_wait") +
    SCREEN_VERBS_MOVED.flatMap { (old, now) -> listOf(PHONE, WEB).map { "${it}_$now" to old } }

/**
 * 整个换了名字的(以前的名字 → 现在的名字)。一个拆成知识、技能两个的列两个:模型调旧名时按参数挑(见 [LegacyNames.replacements]),
 * 文字里的旧名两个都写上。
 */
private val MOVED: Map<String, List<String>> = mapOf(
    "find_skills" to listOf("knowledge_search", "skill_search"),
    "skill_view" to listOf("knowledge_read", "skill_read"),
    "run_script" to listOf("run_bundled_script"),
    "report_barrier" to listOf("handle_obstacle"),
    "report_failure" to listOf("end_not_done"),
    "request_input" to listOf("ask_user"),
    "user_takeover" to listOf("hand_to_user"),
    "recall_screenshot" to listOf("view_past_screens"),
) + SCREEN_VERBS_MOVED.flatMap { (old, now) -> listOf(PHONE, WEB).map { "${it}_$old" to listOf("${it}_$now") } }

/** 去掉了、没有替代工具的(以前的名字 → 现在怎么做):以前用工具交结果,现在不带调用的回复就是回答。 */
private val RETIRED = mapOf(
    "finish" to "reply to the user with your answer as text, without calling any tool",
    "phone_ask_app_ai" to "read the app's own screens yourself (asking an app's built-in AI assistant is no longer available)",
)

/** 改名前的工具名 → 现在的名字(读旧的路线、运行记录、个人技能时用)。 */
fun legacyNames(): LegacyNames =
    LegacyNames(knownTools(), FORMER_PREFIX, RENAMED, retired = RETIRED, moved = MOVED)

/** 读运行记录用的工具声明:改名前记下的旧名也认成现在的工具(动作、能力相同),旧的记录和新的评分一样。 */
fun recordedTools(): ToolIndex = legacyNames().let { legacy -> ToolIndex(allTools()) { legacy.resolve(it) } }

/** Qavi 所有可能提供的工具,按发给模型的顺序。 */
internal fun allTools(): List<AgentTool> =
    GeneralGroup.tools + PhoneCapability.tools() + WebGroup.tools + SandboxGroup.tools + RootGroup.tools + co.qavi.core.calls.callAgentTools

/** Qavi 所有可能提供的工具的声明。 */
fun knownTools(): ToolIndex = ToolIndex(allTools())

/** Qavi 所有可能提供的工具名(技能文档里提到的工具名据此校验)。 */
fun knownToolNames(): Set<String> = knownTools().names + DO_STEPS_TOOL
