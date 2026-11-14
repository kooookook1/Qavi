package co.qavi

import co.qavi.core.action.ActionExecutor
import co.qavi.core.action.ActionRecorder
import co.qavi.core.action.Origin
import co.qavi.core.action.execution
import co.qavi.core.guard.OperationGuards
import co.qavi.core.model.Part
import co.qavi.core.record.RunSink
import co.qavi.core.tool.ToolDispatcher
import co.qavi.core.tool.ToolExecution

/** 设备测试里直接做一个工具调用:和产品一样经执行入口(不配确认关卡,不等锁屏)。 */
suspend fun ToolDispatcher.runAction(call: Part.ToolCall): ToolExecution =
    ActionExecutor(this, OperationGuards(), null, gate = {}, recorder = ActionRecorder(RunSink.NONE)).run(call, Origin.Model(0)).execution()
