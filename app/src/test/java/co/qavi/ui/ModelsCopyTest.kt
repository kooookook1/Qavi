package co.qavi.ui

import org.junit.Rule
import co.qavi.i18n.ChineseTexts
import co.qavi.core.model.Capabilities
import co.qavi.core.model.ListedModel
import co.qavi.core.model.ModelRole
import co.qavi.settings.Provider
import co.qavi.settings.ProviderConfig
import co.qavi.settings.ProviderPresets
import co.qavi.settings.RoleChoice
import co.qavi.settings.RoleChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模型页的文字:用途卡、服务商一行、删除提示,以及列表排序。 */
class ModelsCopyTest {
    @get:Rule val texts = ChineseTexts()

    private val caps = Capabilities(toolCalls = true, vision = false, contextTokens = 64_000, reasoning = false)
    private val ds = Provider("p1", "DeepSeek", ProviderConfig.Kind.OpenAiCompat, "https://api.deepseek.com",
        listOf(ListedModel("deepseek-chat"), ListedModel("deepseek-reasoner"), ListedModel("deepseek-coder")))
    private val qw = Provider("p2", "通义千问", ProviderConfig.Kind.OpenAiCompat, "https://dashscope.aliyuncs.com/compatible-mode/v1",
        listOf(ListedModel("qwen-vl", vision = true)))
    private val main = ProviderConfig("c1", ds.kind, ds.baseUrl, "deepseek-reasoner", caps, ds.name, ds.id)
    private val vl = ProviderConfig("c2", qw.kind, qw.baseUrl, "qwen-vl", caps, qw.name, qw.id)

    private fun ui(roles: RoleChoices, providers: List<Provider> = listOf(ds, qw), configs: List<ProviderConfig> = listOf(main, vl)) =
        ModelSetupUi(AddForm.of(ProviderPresets.all.first()), providers = providers, configs = configs, roles = roles)

    @Test fun inUseModelsComeFirstAndPickedIgnoresSameAsMain() {
        val u = ui(RoleChoices(main = "c1", vision = RoleChoice.SameAsMain))
        assertEquals(listOf("deepseek-reasoner", "deepseek-chat", "deepseek-coder"), u.sortedOptions(ds).map { it.id })
        assertEquals(listOf(ModelRole.Main, ModelRole.Vision, ModelRole.Light), u.rolesOf(ds.id, "deepseek-reasoner"))
        assertTrue(u.picked(ModelRole.Main, ds.id, "deepseek-reasoner"))
        assertFalse("用主模型不算明确选了它", u.picked(ModelRole.Vision, ds.id, "deepseek-reasoner"))
    }

    @Test fun pickerFixedChoicesPerRole() {
        assertEquals(emptyList<RoleChoice>(), fixedChoices(ModelRole.Main))
        assertEquals(listOf(RoleChoice.SameAsMain, RoleChoice.Off), fixedChoices(ModelRole.Vision))
        assertEquals(listOf(RoleChoice.SameAsMain), fixedChoices(ModelRole.Light))
    }
}
