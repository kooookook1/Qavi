package co.qavi.core.general

import co.qavi.core.tool.AgentTool
import co.qavi.core.tool.ArgError
import co.qavi.core.tool.Args
import co.qavi.core.tool.ToolContext
import co.qavi.core.tool.ToolDef
import co.qavi.core.tool.argStr
import co.qavi.core.tool.str
import kotlinx.serialization.json.JsonObject
import co.qavi.core.uistate.focus

private const val SURFACE = "surface"

/**
 * 说的是哪块屏的通用工具(确认、接管、报障碍):本任务有两块以上的屏时多一个 surface 参数,
 * 不给时是最近一次屏幕工具的那块。分发器在调用前按它记下在操作哪块屏,工具照常用此刻那块屏。
 */
internal interface SurfaceAware : AgentTool {
    override fun def(ctx: ToolContext): ToolDef {
        val ids = ctx.focus.surfaces.map { it.id }
        if (ids.size < 2) return def
        return ToolDef(def.name, def.description, def.props + (SURFACE to str(SURFACE_NOTE, ids)), def.required)
    }

    override fun aimAt(o: JsonObject): String? = o.argStr(SURFACE)

    /** 给了 surface 时核对它是本任务的一块屏。 */
    fun checkSurface(a: Args, ctx: ToolContext) {
        val id = a.str(SURFACE) ?: return
        if (ctx.focus[id] == null) throw ArgError(SURFACE, "must be one of ${ctx.focus.surfaces.joinToString("|") { it.id }}")
    }

    companion object {
        const val SURFACE_NOTE = "Which screen this is about. Default: the screen your latest screen tool acted on."
    }
}
