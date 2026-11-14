package co.qavi.cap.phone.a11y

import co.qavi.core.uistate.UnseenField

internal object ContextPastePolicy {
    fun canProbe(enabled: Boolean, password: Boolean, inList: Boolean, role: String): Boolean =
        enabled && !password && !inList && !role.endsWith("Button")

    fun canRetry(reason: String): Boolean = reason == "input_not_sent" || reason == UnseenField.POINT_NOT_TYPED
}
