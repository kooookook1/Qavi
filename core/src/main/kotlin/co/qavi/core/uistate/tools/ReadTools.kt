package co.qavi.core.uistate.tools

import co.qavi.core.gate.IrreversibleDetector
import co.qavi.core.prompt.Feedback
import co.qavi.core.uistate.ScreenFamily
import co.qavi.core.tool.Args
import co.qavi.core.tool.HINT_PROTECTED
import co.qavi.core.tool.HINT_DISPLAY_CAPTURE
import co.qavi.core.uistate.ScreenDiagnosis
import co.qavi.core.visual.Recall
import co.qavi.core.tool.Risk
import co.qavi.core.tool.ToolContext
import co.qavi.core.tool.ToolDef
import co.qavi.core.tool.ToolExecution
import co.qavi.core.tool.Verb
import co.qavi.core.tool.fail
import co.qavi.core.tool.okObj
import co.qavi.core.tool.str
import co.qavi.core.workspace.SandboxError
import co.qavi.core.uistate.screenText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import co.qavi.core.uistate.lastSnapshot
import co.qavi.core.uistate.screen
import co.qavi.core.uistate.session

/** 读这块屏:元素表(observe 按配置补截图)、单独截图。 */
internal class Observe(f: ScreenFamily) : ScreenTool(f, "observe") {
    override val verb = Verb.Observe
    override val label = "看屏幕"
    override val risk = Risk.Read
    override val def by lazy { ToolDef(name, describe()) }

    override suspend fun execute(a: Args, ctx: ToolContext) = ctx.session.observe(
        withImage = ctx.config.visionEnabled && !ctx.config.adaptiveVision,
        adaptiveImage = ctx.config.visionEnabled && ctx.config.adaptiveVision,
    )
}

internal class ListElements(f: ScreenFamily) : ScreenTool(f, "list_elements") {
    override val verb = Verb.ListElements
    override val label = "读取界面元素"
    override val risk = Risk.Read
    override val def by lazy { ToolDef(name, describe()) }

    override suspend fun execute(a: Args, ctx: ToolContext) = ctx.session.observe(withImage = false, recordFrame = false)
}

/** Seeing models receive the image; any task with a workspace can save it for delivery to the user. */
internal class Screenshot(f: ScreenFamily) : ScreenTool(f, "screenshot") {
    override val verb = Verb.Screenshot
    override val label = "截图"
    override val risk = Risk.Read
    override val def by lazy { ToolDef(name, describe(), mapOf(
        "savePath" to str("Optional new PNG path under /workspace. Save the screenshot there, then use share_file to give it to the user."))) }

    override fun usable(ctx: ToolContext) = ctx.config.visionEnabled || ctx.config.saveScreenshot != null
    override fun readOnly(args: JsonObject) = "savePath" !in args

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val savePath = a.str("savePath", max = 4096)
        if (savePath == null && !ctx.config.visionEnabled)
            return fail("save_path_required", "This model cannot view images. Set savePath to a new .png path under /workspace to capture a screenshot for the user.")
        if (savePath != null && ctx.config.saveScreenshot == null)
            return fail("screenshot_save_unavailable", "Saving screenshots is not available for this task.")
        val screen = ctx.screen
        if (screen.isProtected()) return ToolExecution(buildJsonObject {
            put("ok", false); put("error", "protected_content"); put("protected", true)
            put("message", HINT_PROTECTED); put("recoverable", true)
        })
        val png = screen.screenshotPng()
        if (png == null) {
            val captureError = screen.peek().captureError
            if (captureError == ScreenDiagnosis.DISPLAY_CAPTURE_UNAVAILABLE) {
                return fail(captureError, HINT_DISPLAY_CAPTURE, recoverable = false)
            }
            return fail("screenshot_unavailable", Feedback["screenshot_unavailable"])
        }
        // Recheck before writing a persistent image if the screen changed during capture.
        if (savePath != null && screen.isProtected()) return fail("protected_content", HINT_PROTECTED)
        val saved = if (savePath == null) null else try { ctx.config.saveScreenshot!!.invoke(savePath, png) }
            catch (e: SandboxError) { return fail(e.code, e.message ?: e.code, e.recoverable) }
        val s = ctx.lastSnapshot
        val shots = ctx.shots
        // 和读屏一样登记进回看,旧图被裁掉后还能按 screenshotId 找回;付款页的图不留
        val shotId = shots.next()
        shots.remember(Recall(shotId, ctx.currentStep, s?.packageName, png, s?.let(::screenText).orEmpty()))
        return ToolExecution(okObj {
            s?.let { put("width", it.width); put("height", it.height) }; put("screenshotId", shotId)
            saved?.let { put("path", it); put("size", png.size); put("hint", "Use share_file with this path to deliver the screenshot to the user.") }
        }, image = png.takeIf { ctx.config.visionEnabled }, imageIds = if (ctx.config.visionEnabled) listOf(shotId) else emptyList(),
            followUpTools = if (saved == null) emptyList() else listOf(Verb.ShareFile))
    }
}

