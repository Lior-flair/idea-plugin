package com.lior.plugin.i18n.pattern

import com.lior.plugin.i18n.settings.I18nSettings

/**
 * 单条框架模式：正则 + 捕获组索引。
 */
data class I18nPattern(
    val framework: String,
    val regex: Regex,
    val keyGroup: Int = 1
)

object I18nPatternMatcher {

    // ── 内置框架模式 ─────────────────────────────────────────────────────────

    private val PATTERNS_I18NEXT = listOf(
        // t('key') / t("key") / t(`key`) — i18next, react-i18next
        I18nPattern("i18next", Regex("""(?<![.\w${'$'}])t\(\s*['"]([^'"]+)['"]\s*[,)]"""), 1),
        // i18next.t('key')
        I18nPattern("i18next", Regex("""i18next\.t\(\s*['"]([^'"]+)['"]\s*[,)]"""), 1),
    )

    private val PATTERNS_VUE_I18N = listOf(
        // $t('key') / this.$t('key') — Vue i18n options/composition API
        I18nPattern("vue-i18n", Regex("""\${'$'}t\(\s*['"]([^'"]+)['"]\s*[,)]"""), 1),
        // useI18n().t('key') → captured by generic t() pattern above
        // v-t="'key'" directive
        I18nPattern("vue-i18n", Regex("""v-t=['"]'([^'"]+)'['"]"""), 1),
        // $i18n.t('key') — Nuxt.js
        I18nPattern("vue-i18n", Regex("""\${'$'}i18n\.t\(\s*['"]([^'"]+)['"]\s*[,)]"""), 1),
    )

    private val PATTERNS_REACT_INTL = listOf(
        // intl.formatMessage({ id: 'key' })
        I18nPattern("react-intl", Regex("""intl\.formatMessage\s*\(\s*\{[^}]*?id:\s*['"]([^'"]+)['"]"""), 1),
        // <FormattedMessage id="key" />
        I18nPattern("react-intl", Regex("""<FormattedMessage[^>]+id=['"]([^'"]+)['"]"""), 1),
        // defineMessages({ key: { id: 'key' } })
        I18nPattern("react-intl", Regex("""defineMessages\s*\(\s*\{[^}]*?id:\s*['"]([^'"]+)['"]"""), 1),
    )

    private val PATTERNS_NGX_TRANSLATE = listOf(
        // 'key' | translate — Angular template pipe
        I18nPattern("ngx-translate", Regex("""['"]([^'"]+)['"]\s*\|\s*translate"""), 1),
        // translate.instant('key') / translate.get('key') — TypeScript service
        I18nPattern("ngx-translate", Regex("""translate\.(instant|get|stream)\s*\(\s*['"]([^'"]+)['"]"""), 2),
    )

    private val PATTERNS_FLUTTER = listOf(
        // AppLocalizations.of(context)!.key — Flutter gen-l10n (key is identifier, not string literal)
        // S.of(context).key — intl_utils
        // tr('key') — easy_localization
        I18nPattern("flutter", Regex("""tr\s*\(\s*['"]([^'"]+)['"]\s*[,)]"""), 1),
    )

    private val PATTERNS_GENERIC = listOf(
        // translate('key') / gettext('key') — generic
        I18nPattern("generic", Regex("""(?:translate|gettext|_)\s*\(\s*['"]([^'"]+)['"]\s*[,)]"""), 1),
        // i18n.t('key') — generic i18n object
        I18nPattern("generic", Regex("""i18n\.t\(\s*['"]([^'"]+)['"]\s*[,)]"""), 1),
    )

    private val ALL_PATTERNS: Map<String, List<I18nPattern>> = mapOf(
        "i18next"       to PATTERNS_I18NEXT,
        "vue-i18n"      to PATTERNS_VUE_I18N,
        "react-intl"    to PATTERNS_REACT_INTL,
        "ngx-translate" to PATTERNS_NGX_TRANSLATE,
        "flutter"       to PATTERNS_FLUTTER,
        "generic"       to PATTERNS_GENERIC,
    )

    // ── 公共 API ─────────────────────────────────────────────────────────────

    /**
     * 从单行文本中提取所有 i18n key（去重，保留首次出现位置）。
     * 返回 Pair<匹配在行内的字符范围, key字符串>。
     */
    fun findKeysInLine(line: String): List<Pair<IntRange, String>> {
        val activePatterns = resolveActivePatterns()
        val result = mutableListOf<Pair<IntRange, String>>()
        val seen = mutableSetOf<String>()

        for (pattern in activePatterns) {
            for (match in pattern.regex.findAll(line)) {
                val key = match.groupValues[pattern.keyGroup]
                if (key.isNotEmpty() && seen.add(key)) {
                    result.add(match.range to key)
                }
            }
        }
        return result.sortedBy { it.first.first }
    }

    /**
     * 检测文件内容中包含哪些框架的特征代码。
     */
    fun detectFrameworks(fileContent: String): List<String> {
        return ALL_PATTERNS.entries
            .filter { (_, patterns) -> patterns.any { it.regex.containsMatchIn(fileContent) } }
            .map { it.key }
    }

    // ── 内部 ─────────────────────────────────────────────────────────────────

    private fun resolveActivePatterns(): List<I18nPattern> {
        val settings = I18nSettings.getInstance()
        val frameworkList = settings.getEnabledFrameworkList()

        return if (frameworkList.isEmpty() || frameworkList == listOf("auto")) {
            // auto: 返回所有模式，EditorLinePainter 中按实际匹配决定是否显示
            ALL_PATTERNS.values.flatten() + PATTERNS_GENERIC
        } else {
            frameworkList.flatMap { fw ->
                ALL_PATTERNS[fw] ?: emptyList()
            }.ifEmpty {
                ALL_PATTERNS.values.flatten()
            }
        }
    }
}
