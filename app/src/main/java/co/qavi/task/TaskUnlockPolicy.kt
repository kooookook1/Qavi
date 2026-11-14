package co.qavi.task

import co.qavi.core.run.ChannelPeer
import co.qavi.core.run.RunOrigin
import co.qavi.wechat.WechatChannel

/** 开跑时只为定时任务或已授权的微信任务提供解锁；实际解锁时仍重新读取微信授权。 */
internal fun taskUnlockAuthorization(
    origin: RunOrigin,
    peer: ChannelPeer?,
    canUnlockWechat: (ChannelPeer?) -> Boolean,
): (() -> Boolean)? = when {
    origin == RunOrigin.Trigger -> ({ true })
    origin == RunOrigin.Channel && peer?.channelId == WechatChannel.ID && canUnlockWechat(peer) ->
        ({ canUnlockWechat(peer) })
    else -> null
}
