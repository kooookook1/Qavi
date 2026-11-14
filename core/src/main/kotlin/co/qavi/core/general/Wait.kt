package co.qavi.core.general

import co.qavi.core.prompt.Prompts
import co.qavi.core.tool.AgentTool
import co.qavi.core.tool.ArgError
import co.qavi.core.tool.Args
import co.qavi.core.tool.Risk
import co.qavi.core.tool.ToolContext
import co.qavi.core.tool.ToolDef
import co.qavi.core.tool.ToolExecution
import co.qavi.core.tool.Verb
import co.qavi.core.tool.ok
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object Wait : AgentTool {
    override val verb = Verb.Wait
    override val batchable = true
    override val label = "等待"
    override val risk = Risk.Read
    override val logged = true
    override val yieldsToSteer = true
    override val def = ToolDef("wait", Prompts.tool("wait"),
        mapOf("seconds" to buildJsonObject { put("type", "number"); put("description", "0.2-10 seconds."); put("minimum", 0.2); put("maximum", 10) }),
        listOf("seconds"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val s = a.number("seconds")!!
        if (s < 0.2 || s > 10) throw ArgError("seconds", "must be between 0.2 and 10")
        delay((s * 1000).toLong())
        return ok { put("waitedSeconds", s) }
    }
}
