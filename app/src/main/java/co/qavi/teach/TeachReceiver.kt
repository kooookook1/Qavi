package co.qavi.teach

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.qavi.QaviApp

/** 通知上的暂停、继续、结束、放弃。 */
class TeachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? QaviApp ?: return
        if (!app.teachReady) return
        when (intent.action) {
            TeachNotifier.PAUSE -> app.teach.pause()
            TeachNotifier.RESUME -> app.teach.resume()
            TeachNotifier.END -> app.teach.end()
            TeachNotifier.DISCARD -> app.teach.discard()
        }
    }
}
