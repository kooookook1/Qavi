package co.qavi.ui

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.qavi.R
import co.qavi.core.model.ModelError
import co.qavi.core.usage.AllowanceMoney
import co.qavi.core.usage.AllowanceSource
import co.qavi.core.usage.ProviderAllowance
import co.qavi.core.usage.ProviderAllowanceClient
import co.qavi.core.usage.ResetPeriod
import co.qavi.i18n.tr
import co.qavi.i18n.uiLocale
import co.qavi.settings.AllowanceRoute
import co.qavi.settings.Provider
import co.qavi.settings.ProviderStore
import co.qavi.ui.components.GroupCard
import co.qavi.ui.components.ListRow
import co.qavi.ui.components.RowChevron
import co.qavi.ui.theme.QaviTheme
import co.qavi.ui.theme.QaviType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val allowanceClient by lazy { ProviderAllowanceClient(ProviderStore.modelHttp) }

@Composable
internal fun UsageAllowanceCard(providers: List<Provider>, store: ProviderStore, onOpenUrl: (String) -> Unit) {
    if (providers.isEmpty()) return
    val routes = remember(providers) { providers.map { it to AllowanceRoute.forProvider(it) } }
    val revisions by store.providerKeyRevisions.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    GroupCard {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.usage_allowance_title), Modifier.weight(1f).padding(vertical = 12.dp),
                style = QaviType.Body, color = QaviTheme.colors.ink)
            if (routes.any { (_, route) -> route.source != null }) {
                TextButton(onClick = { refresh++ }) { Text(tr(R.string.refresh)) }
            }
        }
        routes.forEachIndexed { index, (provider, route) ->
            key(provider.id) {
                AllowanceRow(provider, route, store, revisions[provider.id] ?: 0L, refresh, index == 0, onOpenUrl)
            }
        }
    }
}

@Composable
private fun AllowanceRow(
    provider: Provider, route: AllowanceRoute, store: ProviderStore, revision: Long,
    refresh: Int, first: Boolean, onOpenUrl: (String) -> Unit,
) {
    val source = route.source
    var value by remember(provider.baseUrl, source, revision, refresh) { mutableStateOf<ProviderAllowance?>(null) }
    var updated by remember(provider.baseUrl, source, revision) { mutableStateOf<Instant?>(null) }
    var loading by remember(provider.baseUrl, source, revision, refresh) { mutableStateOf(source != null) }
    if (source != null) {
        LaunchedEffect(provider.baseUrl, source, revision, refresh) {
            val result = try {
                withContext(Dispatchers.IO) {
                    store.allowanceKey(provider, revision)?.takeIf { it.isNotBlank() }?.let { allowanceClient.query(source, it) }
                }
            } catch (error: ModelError) {
                Log.w("QaviAllowance", "查询服务商额度失败（${source.name}，${error.javaClass.simpleName}）")
                null
            } catch (_: IllegalArgumentException) {
                Log.w("QaviAllowance", "服务商额度请求无效（${source.name}）")
                null
            }
            // 列表和密钥版本可能已更新，但界面还没来得及重组。
            if (store.matchesAllowanceConnection(provider, revision)) {
                value = result
                if (result != null) updated = Instant.now()
                loading = false
            }
        }
    }
    val link = route.dashboard?.let {
        val label = if (source == AllowanceSource.OpenRouter) R.string.usage_allowance_view_balance else R.string.chatgpt_manage_usage
        tr(label)
    }
    val current = value
    val subtitle = when {
        loading -> tr(R.string.usage_allowance_loading)
        current != null -> allowanceText(current)
        source != null -> tr(R.string.usage_allowance_unavailable)
        route.selfHosted -> tr(R.string.usage_allowance_self_hosted)
        link != null -> link
        else -> tr(R.string.usage_allowance_unsupported)
    }
    val lastUpdated = updated?.let {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(uiLocale()).withZone(ZoneId.systemDefault())
        tr(R.string.usage_allowance_updated, formatter.format(it))
    }
    val detail = listOfNotNull(lastUpdated, link?.takeIf { source != null }).joinToString("\n")
    ListRow(icon = null, title = provider.name, subtitle = subtitle, detail = detail, first = first,
        onClick = route.dashboard?.let { url -> { onOpenUrl(url) } }) {
        if (route.dashboard != null) RowChevron()
    }
}

private fun allowanceText(value: ProviderAllowance): String = when (value) {
    is ProviderAllowance.Balance -> tr(R.string.usage_allowance_balance, value.amounts.joinToString(" · ", transform = ::formatAmount))
    is ProviderAllowance.KeyLimit -> {
        val remaining = value.remaining
        if (remaining == null) tr(R.string.usage_allowance_no_key_limit) else {
            val reset = when (value.reset) {
                ResetPeriod.Daily -> tr(R.string.usage_allowance_reset_daily)
                ResetPeriod.Weekly -> tr(R.string.usage_allowance_reset_weekly)
                ResetPeriod.Monthly -> tr(R.string.usage_allowance_reset_monthly)
                null -> null
            }
            listOfNotNull(tr(R.string.usage_allowance_key_limit, formatAmount(remaining)), reset).joinToString(" · ")
        }
    }
}

private fun formatAmount(money: AllowanceMoney): String {
    val number = NumberFormat.getNumberInstance(uiLocale()).apply {
        maximumFractionDigits = money.amount.stripTrailingZeros().scale().coerceIn(2, 12)
        minimumFractionDigits = 2
    }
    return "${number.format(money.amount)} ${money.currency}"
}
