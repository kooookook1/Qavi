package co.qavi.cap.sandbox

import co.qavi.core.workspace.NioFdSys
import co.qavi.core.workspace.WorkspaceFileGate

/** JVM 测试里的关口(按路径的近似;手机上是 AndroidFdSys.gate)。 */
internal val JVM_GATE = WorkspaceFileGate(NioFdSys)
