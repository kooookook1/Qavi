package co.qavi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import co.qavi.R
import co.qavi.core.teach.TeachPhase
import co.qavi.core.teach.TeachStep
import co.qavi.core.teach.teachGoalAccepted
import co.qavi.core.teach.teachLine
import co.qavi.teach.TeachNotice
import co.qavi.teach.TeachRuntime
import co.qavi.ui.components.ButtonKind
import co.qavi.ui.components.ButtonSize
import co.qavi.ui.components.QaviButton
import co.qavi.ui.components.TopBar
import co.qavi.ui.theme.QaviIcons
import co.qavi.ui.theme.QaviTheme
import co.qavi.ui.theme.QaviType

@Composable
fun TeachScreen(runtime: TeachRuntime, onBack: () -> Unit, onOpenDrafts: () -> Unit) {
    val ui by runtime.ui.collectAsState()
    val session = ui.session
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.teach_title), QaviIcons.Back, onBack, stringResource(R.string.back))
        Notice(ui.notice)
        when (session?.phase) {
            null, TeachPhase.Idle, TeachPhase.Ready -> GoalForm(runtime, ui.hint)
            TeachPhase.Recording, TeachPhase.Paused -> Live(runtime, session.phase, session.steps.size)
            TeachPhase.Interrupted -> Interrupted(runtime)
            TeachPhase.Reviewing, TeachPhase.Drafting -> Review(runtime, session.steps, session.phase == TeachPhase.Drafting)
            TeachPhase.Saved -> Saved(runtime, onOpenDrafts)
        }
    }
}

@Composable
private fun Notice(notice: TeachNotice?) {
    val text = when (notice) {
        TeachNotice.Busy -> stringResource(R.string.teach_busy)
        TeachNotice.NoAccessibility -> stringResource(R.string.teach_need_a11y)
        TeachNotice.NeedGoal -> stringResource(R.string.teach_need_goal)
        TeachNotice.SecretGoal -> stringResource(R.string.teach_secret_goal)
        TeachNotice.DraftFailed -> stringResource(R.string.teach_draft_failed)
        TeachNotice.NoSteps -> stringResource(R.string.teach_no_steps)
        TeachNotice.SecretNote -> stringResource(R.string.teach_sensitive_note)
        TeachNotice.NoVisibleStop -> stringResource(R.string.teach_no_visible)
        TeachNotice.WatchLost -> stringResource(R.string.teach_watch_lost)
        null -> return
    }
    Text(text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = QaviTheme.colors.warn, style = QaviType.Body)
}

@Composable
private fun GoalForm(runtime: TeachRuntime, hint: String?) {
    var goal by remember { mutableStateOf(hint.orEmpty()) }
    var armed by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf<TeachNotice?>(null) }
    LaunchedEffect(hint) { if (goal.isEmpty() && !hint.isNullOrEmpty()) goal = hint }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("teach-form"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Notice(local)
        if (!armed) {
            Text(stringResource(R.string.teach_disclosure), style = QaviType.Body, color = QaviTheme.colors.ink)
            OutlinedTextField(
                goal, { goal = it; local = null },
                Modifier.fillMaxWidth().testTag("teach-goal"),
                label = { Text(stringResource(R.string.teach_goal_label)) },
                placeholder = { Text(stringResource(R.string.teach_goal_hint)) },
            )
            QaviButton(stringResource(R.string.teach_continue), {
                local = when {
                    goal.isBlank() -> TeachNotice.NeedGoal
                    !teachGoalAccepted(goal) -> TeachNotice.SecretGoal
                    else -> null
                }
                if (local == null) armed = true
            }, kind = ButtonKind.Primary, size = ButtonSize.Medium)
        } else {
            Text(stringResource(R.string.teach_ready_body), style = QaviType.Body, color = QaviTheme.colors.ink)
            Text(goal, style = QaviType.Body, color = QaviTheme.colors.ink2)
            val context = LocalContext.current
            QaviButton(stringResource(R.string.teach_start), {
                local = runtime.start(goal)
                if (local == null) {
                    armed = false
                    (context as? Activity)?.moveTaskToBack(true)
                }
            }, kind = ButtonKind.Primary, size = ButtonSize.Medium, modifier = Modifier.testTag("teach-start"))
        }
    }
}

@Composable
private fun Live(runtime: TeachRuntime, phase: TeachPhase, steps: Int) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(if (phase == TeachPhase.Recording) R.string.teach_learning else R.string.teach_paused),
            style = QaviType.Title, color = QaviTheme.colors.ink,
        )
        Text(stringResource(R.string.teach_notification), style = QaviType.Body, color = QaviTheme.colors.ink2)
        Text(steps.toString(), style = QaviType.Hint, color = QaviTheme.colors.ink3)
        val context = LocalContext.current
        if (phase == TeachPhase.Recording) QaviButton(stringResource(R.string.teach_pause), runtime::pause, size = ButtonSize.Medium)
        else QaviButton(stringResource(R.string.teach_resume), {
            if (runtime.resume() == null) (context as? Activity)?.moveTaskToBack(true)
        }, kind = ButtonKind.Primary, size = ButtonSize.Medium)
        QaviButton(stringResource(R.string.teach_end), runtime::end, kind = ButtonKind.Primary, size = ButtonSize.Medium)
        QaviButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Tonal, size = ButtonSize.Medium)
    }
}

@Composable
private fun Interrupted(runtime: TeachRuntime) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val context = LocalContext.current
        Text(stringResource(R.string.teach_interrupted), style = QaviType.Body, color = QaviTheme.colors.ink)
        QaviButton(stringResource(R.string.teach_resume), {
            if (runtime.resume() == null) (context as? Activity)?.moveTaskToBack(true)
        }, kind = ButtonKind.Primary, size = ButtonSize.Medium)
        QaviButton(stringResource(R.string.teach_end), runtime::end, size = ButtonSize.Medium)
        QaviButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Tonal, size = ButtonSize.Medium)
    }
}

@Composable
private fun Review(runtime: TeachRuntime, steps: List<TeachStep>, drafting: Boolean) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.teach_review), style = QaviType.Title, color = QaviTheme.colors.ink)
        if (steps.isEmpty()) Text(stringResource(R.string.teach_no_steps), color = QaviTheme.colors.ink2)
        steps.forEach { step -> StepEditor(runtime, step) }
        QaviButton(
            stringResource(R.string.teach_generate),
            runtime::generate,
            enabled = steps.isNotEmpty() && !drafting,
            kind = ButtonKind.Primary,
            size = ButtonSize.Medium,
            modifier = Modifier.testTag("teach-generate"),
        )
        if (confirm) {
            Text(stringResource(R.string.teach_discard_body), color = QaviTheme.colors.warn)
            QaviButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Tonal, size = ButtonSize.Medium)
        } else {
            QaviButton(stringResource(R.string.teach_discard), { confirm = true }, kind = ButtonKind.Tonal, size = ButtonSize.Medium)
        }
    }
}

@Composable
private fun StepEditor(runtime: TeachRuntime, step: TeachStep) {
    var correction by remember(step.id, step.correction) { mutableStateOf(step.correction.orEmpty()) }
    var explanation by remember(step.id, step.explanation) { mutableStateOf(step.explanation.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(teachLine(step), style = QaviType.Body, color = QaviTheme.colors.ink)
        if (step.insufficient) Text(stringResource(R.string.teach_insufficient_note), style = QaviType.Hint, color = QaviTheme.colors.ink3)
        if (step.sensitiveOmitted) Text(stringResource(R.string.teach_sensitive_note), style = QaviType.Hint, color = QaviTheme.colors.ink3)
        OutlinedTextField(
            correction,
            {
                correction = it
                runtime.annotate(step.id, it, explanation)?.let { }
            },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.teach_correct)) },
        )
        if (step.needsClarification) {
            OutlinedTextField(
                explanation,
                { explanation = it; runtime.annotate(step.id, correction, it) },
                Modifier.fillMaxWidth().testTag("teach-clarify"),
                label = { Text(stringResource(R.string.teach_clarify)) },
            )
        }
        Row {
            QaviButton(stringResource(R.string.teach_remove), { runtime.remove(step.id) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
        }
    }
}

@Composable
private fun Saved(runtime: TeachRuntime, onOpenDrafts: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.teach_draft_ready), style = QaviType.Body, color = QaviTheme.colors.ink)
        QaviButton(stringResource(R.string.teach_open_drafts), onOpenDrafts, kind = ButtonKind.Primary, size = ButtonSize.Medium)
        QaviButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Tonal, size = ButtonSize.Medium)
    }
}
