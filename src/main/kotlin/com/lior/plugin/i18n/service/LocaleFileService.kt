package com.lior.plugin.i18n.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.lior.plugin.i18n.settings.I18nSettings
import java.io.StringReader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class LocaleFileService(private val project: Project) {

    private val log = logger<LocaleFileService>()
    private val cache = ConcurrentHashMap<String, Map<String, String>>()
    private val loading = AtomicBoolean(false)

    init {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { isLocaleExtension(it.path) }) {
                        invalidateCache()
                        repaintAllEditors()
                    }
                }
            }
        )
    }

    // ── 公共 API ─────────────────────────────────────────────────────────────

    fun getTranslation(key: String): String? {
        val lang = I18nSettings.getInstance().displayLanguage
        return cache[lang]?.get(key)
    }

    fun isCached(language: String): Boolean = cache.containsKey(language)

    fun loadInBackground(language: String = I18nSettings.getInstance().displayLanguage) {
        if (isCached(language) || !loading.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                cache[language] = loadLanguage(language)
            } finally {
                loading.set(false)
            }
            repaintAllEditors()
        }
    }

    fun invalidateCache() = cache.clear()

    fun getTranslationsForLanguage(language: String): Map<String, String> =
        cache.getOrPut(language) { loadLanguage(language) }

    /** EDT 安全：仅读取缓存中已存在的语言列表，不触发 IO */
    fun getCachedLanguages(): List<String> = cache.keys.toList().sorted()

    /** EDT 安全：直接从缓存读取翻译，不触发 IO */
    fun getCachedTranslation(language: String, key: String): String? = cache[language]?.get(key)

    // ── 语言自动检测 ──────────────────────────────────────────────────────────

    fun detectAvailableLanguages(): List<String> {
        val basePath = project.basePath?.let { Paths.get(it) } ?: return emptyList()
        val settings = I18nSettings.getInstance()
        val prefixes = settings.getLocaleFilePrefixList()
        val languages = sortedSetOf<String>()

        for (rawPath in settings.getLocalesPathList()) {
            val dirs = resolveDirs(basePath, rawPath)
            for (dir in dirs) {
                try {
                    Files.list(dir).use { stream ->
                        stream.forEach { path ->
                            val name = path.fileName.toString()
                            when {
                                Files.isDirectory(path) ->
                                    normalizeLanguageCode(name)
                                        ?.let { languages.add(it) }

                                Files.isRegularFile(path) && isLocaleExtension(name) -> {
                                    val stem = name.substringBeforeLast('.')
                                    if (matchesPrefix(stem, prefixes)) {
                                        extractLanguageCode(stem)
                                            ?.let { languages.add(it) }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.warn("i18n: detectAvailableLanguages error at '$dir': ${e.message}")
                }
            }
        }
        return languages.toList()
    }

    // ── 文件查找 ─────────────────────────────────────────────────────────────

    fun findLocaleFilesForLanguage(language: String): List<VirtualFile> {
        val basePath = project.basePath?.let { Paths.get(it) } ?: return emptyList()
        val settings = I18nSettings.getInstance()
        val prefixes = settings.getLocaleFilePrefixList()
        val result = mutableListOf<VirtualFile>()
        val normalizedLang = normalizeLanguageCode(language) ?: language

        for (rawPath in settings.getLocalesPathList()) {
            resolveDirs(basePath, rawPath).forEach { dir ->
                collectLocaleFiles(dir, normalizedLang, prefixes, result)
            }
        }
        return result
    }

    // ── 内部：路径解析 ────────────────────────────────────────────────────────

    private fun resolveDirs(base: Path, pattern: String): List<Path> =
        if (isGlob(pattern)) findGlobMatches(base, pattern)
        else listOf(base.resolve(pattern)).filter { Files.isDirectory(it) }

    private fun isGlob(path: String) =
        path.contains('*') || path.contains('?') || path.contains('{')

    private fun findGlobMatches(base: Path, pattern: String): List<Path> {
        return try {
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            Files.walk(base)
                .filter { Files.isDirectory(it) && matcher.matches(base.relativize(it)) }
                .toList()
        } catch (e: Exception) {
            log.warn("i18n: glob error for '$pattern': ${e.message}")
            emptyList()
        }
    }

    /**
     * 统一扫描目录，支持四种文件布局：
     *
     *   L1  {language}.json / {language}.yaml / {language}.properties
     *   L2  {language}/任意文件名  (子目录，文件名任意)
     *   L3  {language}-{namespace}.json  (语言代码作前缀，连字符分隔)
     *   L4  {namespace}_{language}.properties  (Java 惯例，语言代码作后缀)
     */
    private fun collectLocaleFiles(
        dir: Path,
        normalizedLang: String,
        prefixes: List<String>,
        result: MutableList<VirtualFile>
    ) {
        val lfs = LocalFileSystem.getInstance()

        // L2 — 语言子目录（同时尝试 zh-CN 和 zh_CN 两种写法）
        for (dirName in listOf(normalizedLang, normalizedLang.replace('-', '_'))) {
            val langDir = dir.resolve(dirName)
            if (Files.isDirectory(langDir)) {
                try {
                    Files.walk(langDir)
                        .filter { Files.isRegularFile(it) && isLocaleExtension(it.toString()) }
                        .forEach { p -> lfs.findFileByNioFile(p)?.let { if (it !in result) result.add(it) } }
                } catch (e: Exception) {
                    log.warn("i18n: L2 walk error at '$langDir': ${e.message}")
                }
            }
        }

        // L1 / L3 / L4 — 目录内的文件，通过文件名提取语言代码进行匹配
        try {
            Files.list(dir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && isLocaleExtension(it.toString()) }
                    .forEach { file ->
                        val stem = file.fileName.toString().substringBeforeLast('.')
                        if (matchesPrefix(stem, prefixes) && extractLanguageCode(stem) == normalizedLang) {
                            lfs.findFileByNioFile(file)
                                ?.let { if (it !in result) result.add(it) }
                        }
                    }
            }
        } catch (e: Exception) {
            log.warn("i18n: L1/L3/L4 scan error at '$dir': ${e.message}")
        }
    }

    private fun isLocaleExtension(path: String): Boolean =
        path.substringAfterLast('.').lowercase() in LOCALE_EXTENSIONS

    /**
     * 判断文件名 stem 是否符合前缀过滤条件。
     * prefixes 为空时不过滤（全部通过）。
     * 对于每个前缀 p，匹配：stem == p（基础文件）或 stem.startsWith("${p}_") 或 stem.startsWith("${p}-")
     */
    private fun matchesPrefix(stem: String, prefixes: List<String>): Boolean {
        if (prefixes.isEmpty()) return true
        return prefixes.any { p ->
            stem == p || stem.startsWith("${p}_") || stem.startsWith("${p}-")
        }
    }

    // ── 内部：语言代码识别 ────────────────────────────────────────────────────

    /**
     * 判断是否为合法 BCP 47 语言代码（简化规则）。
     * 支持：en, fr, zh, zh-CN, zh_CN, zh-Hant, pt-BR, zh-Hant-TW
     * 拒绝：modules, common, home, messages（长度或格式不符）
     */
    private fun isLangCode(name: String): Boolean {
        val parts = name.split('-', '_')
        val primary = parts[0]
        // 主标签：2-3 个小写字母（ISO 639-1 / 639-2）
        if (primary.length !in 2..3 || !primary.all { it.isLowerCase() }) return false
        // 附加标签：地区码 (CN/US)、文字码 (Hant/Hans)，或可选的数字变体
        return parts.drop(1).all { tag ->
            (tag.length == 2 && tag.all { it.isUpperCase() }) ||              // 地区: CN US TW
            (tag.length == 4 && tag[0].isUpperCase() && tag.drop(1).all { it.isLowerCase() }) || // 文字: Hant Hans
            (tag.all { it.isDigit() })                                        // 数字变体
        }
    }

    /**
     * 将语言代码统一为连字符格式（zh_CN → zh-CN），非法输入返回 null。
     */
    private fun normalizeLanguageCode(code: String): String? =
        if (isLangCode(code)) code.replace('_', '-') else null

    /**
     * 从文件名 stem 中提取并规范化语言代码：
     *   "en"              → "en"       (L1)
     *   "zh-CN"           → "zh-CN"    (L1)
     *   "en-modules"      → "en"       (L3: 语言前缀)
     *   "messages_en"     → "en"       (L4: Java 惯例)
     *   "messages_zh_CN"  → "zh-CN"    (L4 + 地区)
     */
    private fun extractLanguageCode(stem: String): String? {
        // 直接匹配（L1: en, zh-CN）
        normalizeLanguageCode(stem)?.let { return it }

        // Java 下划线惯例：messages_zh_CN → 从末尾依次尝试拼接
        val uParts = stem.split("_")
        for (len in uParts.size downTo 1) {
            val candidate = uParts.takeLast(len).joinToString("_")
            normalizeLanguageCode(candidate)?.let { return it }
        }

        // 连字符命名空间前缀：en-modules → 从头依次缩短
        val hParts = stem.split("-")
        for (len in 1 until hParts.size) {
            val candidate = hParts.take(len).joinToString("-")
            normalizeLanguageCode(candidate)?.let { return it }
        }

        return null
    }

    // ── 内部：翻译加载与解析 ──────────────────────────────────────────────────

    private fun loadLanguage(language: String): Map<String, String> {
        val files = findLocaleFilesForLanguage(language)
        if (files.isEmpty()) log.info("i18n: no locale files for '$language'")
        return buildMap {
            files.forEach { file ->
                try { putAll(parseFile(file)) }
                catch (e: Exception) { log.warn("i18n: parse error '${file.path}': ${e.message}") }
            }
        }
    }

    private fun parseFile(file: VirtualFile): Map<String, String> {
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        return when (file.extension?.lowercase()) {
            "json"        -> parseJson(content)
            "yaml", "yml" -> parseYaml(content)
            "properties"  -> parseProperties(content)
            "js", "ts"    -> parseJs(content)
            else          -> emptyMap()
        }
    }

    // ── JSON ─────────────────────────────────────────────────────────────────

    private fun parseJson(content: String): Map<String, String> {
        return try {
            val obj = com.google.gson.Gson()
                .fromJson(content, com.google.gson.JsonObject::class.java)
            buildMap { flattenJsonObject(obj, "", this as MutableMap<String, String>) }
        } catch (e: Exception) {
            log.warn("i18n: JSON parse error: ${e.message}"); emptyMap()
        }
    }

    private fun flattenJsonObject(
        obj: com.google.gson.JsonObject,
        prefix: String,
        out: MutableMap<String, String>
    ) {
        for ((k, v) in obj.entrySet()) {
            val key = if (prefix.isEmpty()) k else "$prefix.$k"
            when {
                v.isJsonObject    -> flattenJsonObject(v.asJsonObject, key, out)
                v.isJsonArray     -> out[key] = v.asJsonArray
                    .joinToString(", ") { if (it.isJsonPrimitive) it.asString else it.toString() }
                v.isJsonPrimitive -> out[key] = v.asString
            }
        }
    }

    // ── YAML ─────────────────────────────────────────────────────────────────

    private fun parseYaml(content: String): Map<String, String> {
        return try {
            val raw = org.yaml.snakeyaml.Yaml().load<Any>(content)
            buildMap { flattenYamlNode(raw, "", this as MutableMap<String, String>) }
        } catch (e: Exception) {
            log.warn("i18n: YAML parse error: ${e.message}"); emptyMap()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenYamlNode(node: Any?, prefix: String, out: MutableMap<String, String>) {
        when (node) {
            is Map<*, *> -> (node as Map<String, Any?>).forEach { (k, v) ->
                flattenYamlNode(v, if (prefix.isEmpty()) k else "$prefix.$k", out)
            }
            is List<*>   -> out[prefix] = node.joinToString(", ") { it?.toString() ?: "" }
            null          -> Unit
            else          -> out[prefix] = node.toString()
        }
    }

    // ── Properties ───────────────────────────────────────────────────────────

    /**
     * 解析 Java .properties 文件。
     * 支持 UTF-8 编码，键值均保留原始格式（不做额外展平，因为 .properties 天然是 flat 结构）。
     */
    private fun parseProperties(content: String): Map<String, String> {
        return try {
            val props = Properties()
            props.load(StringReader(content))
            props.stringPropertyNames().associateWith { props.getProperty(it) }
        } catch (e: Exception) {
            log.warn("i18n: .properties parse error: ${e.message}"); emptyMap()
        }
    }

    // ── JS / TS ──────────────────────────────────────────────────────────────

    /**
     * 解析 JS/TS locale 文件。
     * 支持常见写法：
     *   export default { ... }
     *   module.exports = { ... }
     *   export const messages = { ... }
     * 处理：无引号 key、单引号字符串、模板字符串、尾逗号、行注释、块注释。
     */
    private fun parseJs(content: String): Map<String, String> {
        return try {
            parseJson(normalizeJsToJson(content))
        } catch (e: Exception) {
            log.warn("i18n: JS/TS parse error: ${e.message}"); emptyMap()
        }
    }

    private fun normalizeJsToJson(content: String): String {
        var text = content

        // 1. 去掉行注释和块注释（先块后行，避免块注释内有 // 干扰）
        text = text.replace(Regex("""/\*[\s\S]*?\*/"""), "")
        text = text.replace(Regex("""//[^\n\r]*"""), "")

        // 2. 剥离模块导出包装，只保留对象字面量
        text = text.replace(
            Regex("""^\s*(?:export\s+default|module\.exports\s*=|export\s+const\s+\w[\w$]*\s*=)\s*"""),
            ""
        ).trim().trimEnd(';').trim()

        // 3. 提取最外层 { }
        val start = text.indexOf('{')
        val end   = text.lastIndexOf('}')
        if (start < 0 || end <= start) return "{}"
        text = text.substring(start, end + 1)

        // 4. 给未加引号的 key 加双引号（key: → "key":）
        //    负向前瞻排除已是 "key": 或 'key': 的情况
        text = Regex("""(?<=[{,\n\r]\s{0,200})([a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*)(\s*):(?!\s*/)""")
            .replace(text) { m -> "\"${m.groupValues[1]}\"${m.groupValues[2]}:" }

        // 5. 单引号字符串 → 双引号（先转义内部 "，再还原 \'）
        text = Regex("""'((?:[^'\\]|\\.)*)'""").replace(text) { m ->
            val inner = m.groupValues[1]
                .replace("\\\"", " DQ ")
                .replace("\"", "\\\"")
                .replace("\\'", "'")
                .replace(" DQ ", "\\\"")
            "\"$inner\""
        }

        // 6. 模板字符串 → 双引号（丢弃 ${} 插值，保留静态文本）
        text = Regex("""`((?:[^`\\]|\\.)*)`""").replace(text) { m ->
            val inner = m.groupValues[1]
                .replace(Regex("""\$\{[^}]*\}"""), "")
                .replace("\"", "\\\"")
            "\"$inner\""
        }

        // 7. 去掉尾逗号
        text = text.replace(Regex(""",(\s*[}\]])"""), "$1")

        return text
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    private fun repaintAllEditors() {
        // 翻译加载完成后，通过 InlayHintManager 直接将 Inlay 写入各编辑器，
        // 不依赖 EditorLinePainter 的绘制回调（后者在 2025.2 中无法被 repaint() 可靠触发）
        ApplicationManager.getApplication().invokeLater {
            com.lior.plugin.i18n.annotation.I18nInlayHintManager.refreshAllEditors(project)
        }
    }

    companion object {
        private val LOCALE_EXTENSIONS = setOf("json", "yaml", "yml", "properties", "js", "ts")

        fun getInstance(project: Project): LocaleFileService =
            project.getService(LocaleFileService::class.java)
    }
}
