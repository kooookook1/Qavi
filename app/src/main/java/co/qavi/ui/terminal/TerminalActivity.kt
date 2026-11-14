package co.qavi.ui.terminal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import co.qavi.QaviApp
import co.qavi.i18n.AppLanguage
import co.qavi.ui.theme.QaviTheme
import co.qavi.ui.theme.animationsOff
import co.qavi.ui.theme.appearanceDark
import co.qavi.ui.theme.systemBarsFor

/**
 * 用户终端:一个独立页面,里面是这个对话工作区的 shell。不经过模型,不写运行记录;
 * 离开页面不会结束会话(转屏、按 Home 再回来都还在),进程被杀时随 App 一起结束。
 */
class TerminalActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE)
        val app = applicationContext as QaviApp
        setContent {
            val dark = appearanceDark()
            LaunchedEffect(dark) { systemBarsFor(dark) }
            QaviTheme(dark = dark, reduceMotion = animationsOff(this)) {
                TerminalRoute(app, conversationId, title, onDone = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_CONVERSATION = "conversationId"
        const val EXTRA_TITLE = "title"
    }
}

/** 从对话菜单或设置里的工作区行打开终端。 */
object TerminalLauncher {
    fun open(context: Context, conversationId: String, title: String?) {
        val intent = Intent(context, TerminalActivity::class.java)
            .putExtra(TerminalActivity.EXTRA_CONVERSATION, conversationId)
            .putExtra(TerminalActivity.EXTRA_TITLE, title)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
