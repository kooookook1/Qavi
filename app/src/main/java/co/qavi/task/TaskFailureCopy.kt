package co.qavi.task

import co.qavi.R
import co.qavi.core.agent.FailureCode
import co.qavi.core.agent.TaskState
import co.qavi.i18n.tr

/** Translate structured failures before publishing or saving the result. */
internal fun localizeTaskFailure(state: TaskState): TaskState {
    if (state !is TaskState.Failed) return state
    val reason = when (state.code) {
        FailureCode.ModelRateLimited -> tr(R.string.task_model_rate_limited)
        null -> return state
    }
    return state.copy(reason = reason)
}
