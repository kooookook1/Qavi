package co.qavi.task

import co.qavi.core.run.Channel
import co.qavi.core.run.ChannelInput
import co.qavi.core.run.ChannelInputSink
import co.qavi.core.run.ChannelPeer
import co.qavi.core.run.Delivery
import co.qavi.core.run.SendOutcome

/** 手机上的任务卡片和通知：任务服务仍绘制现有界面，此适配器承接送达账本。 */
class LocalChannel(private val deliverLocal: suspend (ChannelPeer, Delivery) -> SendOutcome) : Channel {
    override val id = ID
    override val confirms = true
    private var sink: ChannelInputSink? = null

    override fun start(sink: ChannelInputSink): AutoCloseable {
        this.sink = sink
        return AutoCloseable { if (this.sink === sink) this.sink = null }
    }

    override suspend fun send(peer: ChannelPeer, delivery: Delivery): SendOutcome =
        if (delivery is Delivery.Image) SendOutcome.Failed else deliverLocal(peer, delivery)
    suspend fun receive(input: ChannelInput) { sink?.receive(input) }

    companion object { const val ID = "local" }
}
