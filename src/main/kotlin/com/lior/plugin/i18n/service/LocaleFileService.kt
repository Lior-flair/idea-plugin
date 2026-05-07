package com.lior.plugin.i18n.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.lior.plugin.i18n.settings.I18nSettings
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class LocaleFileService(private val project: Project) {

    private val log = logger<LocaleFileService>()

    /** language-code → flat key-value map */
    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    init {
        // 订阅 VFS 变更：locale 文件变动时自动清除缓存并刷新编辑器
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { isLocaleExtension(it.path) }) {
                        invalidateCache()
                        ApplicationManager.getApplication().invokeLater {
                            FileEditorManager.getInstance(project)
                                .allEditors.forEach { it.component.repaint() }
                        }
                    }
                }
            }
        )
    }

    // ── 公共 API ─────────────────────────────────────────────────────────────

    fun getTranslation(key: String): String? {
        val lang = I18nSettings.getInstance().displayLanguage
        return getTranslationsForLanguage(lang)[key]
    }

    fun getTranslationsForLanguage(language: String): Map<String, String> =
        cache.getOrPut(language) { loadLanguage(language) }

    /** 清除缓存，下次访问时重新读取文件 */
    fun invalidateCache() = cache.clear()

    /** 返回当前 displayLanguage 下所有的翻译 key */
    fun allKeys(): Set<String> =
        getTranslationsForLanguage(I18nSettings.getInstance().displayLanguage).keys

    // ── 文件查找 ─────────────────────────────────────────────────────────────

    fun findLocaleFilesForLanguage(language: String): List<VirtualFile> {
        val basePath = project.basePath?.let { Paths.get(it) } ?: return emptyList()
        val settings = I18nSettings.getInstance()
        val result = mutableListOf<VirtualFile>()

        for (rawPath in settings.getLocalesPathList()) {
            val resolvedDirs = if (isGlob(rawPath)) {
                findGlobMatches(basePath, rawPath)
            } else {
                listOf(basePath.resolve(rawPath)).filter { Files.isDirectory(it) }
            }
            for (dir in resolvedDirs) {
                collectLocaleFiles(dir, language, result)
            }
        }
        return result
    }

    // ── 内部：路径解析 ────────────────────────────────────────────────────────

    private fun isGlob(path: String) =
        path.contains('*') || path.contains('?') || path.contains('{')

    private fun findGlobMatches(base: Path, pattern: String): List<Path> {
        return try {
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            Files.walk(base)
                .filter { Files.isDirectory(it) && matcher.matches(base.relativize(it)) }
                .toList()
        } catch (e: Exception) {
            log.warn("i18n: glob error for pattern '$pattern': ${e.message}")
            emptyList()
        }
    }

    /**
     * 在目录中查找匹配指定语言的文件，支持两种布局：
     *   1. {dir}/{language}.json|yaml|yml
     *   2. {dir}/{language}/{any}.json|yaml|yml
     */
    private fun collectLocaleFiles(dir: Path, language: String, result: MutableList<VirtualFile>) {
        val lfs = LocalFileSystem.getInstance()
        // Layout 1: 直接命名文件
        for (ext in listOf("json", "yaml", "yml")) {
            val candidate = dir.resolve("$language.$ext")
            if (Files.isRegularFile(candidate)) {
                lfs.findFileByNioFile(candidate)?.let { result.add(it) }
            }
        }
        // Layout 2: 语言子目录
        val langDir = dir.resolve(language)
        if (Files.isDirectory(langDir)) {
            Files.walk(langDir)
                .filter { Files.isRegularFile(it) && isLocaleExtension(it.toString()) }
                .forEach { p -> lfs.findFileByNioFile(p)?.let { result.add(it) } }
        }
    }

    private fun isLocaleExtension(path: String): Boolean =
        path.substringAfterLast('.').lowercase() in setOf("json", "yaml", "yml")

    // ── 内部：翻译加载 ────────────────────────────────────────────────────────

    private fun loadLanguage(language: String): Map<String, String> {
        val files = findLocaleFilesForLanguage(language)
        if (files.isEmpty()) {
            log.info("i18n: no locale files found for language '$language'")
        }
        return buildMap {
            for (file in files) {
                try {
                    putAll(parseFile(file))
                } catch (e: Exception) {
                    log.warn("i18n: failed to parse '${file.path}': ${e.message}")
                }
            }
        }
    }

    private fun parseFile(file: VirtualFile): Map<String, String> {
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        return when (file.extension?.lowercase()) {
            "json"       -> parseJson(content)
            "yaml", "yml" -> parseYaml(content)
            else          -> emptyMap()
        }
    }

    // ── JSON 解析（使用 IntelliJ 平台内置 Gson）────────────────────────────────

    private fun parseJson(content: String): Map<String, String> {
        return try {
            val obj = com.google.gson.Gson()
                .fromJson(content, com.google.gson.JsonObject::class.java)
            buildMap { flattenJsonObject(obj, "", this as MutableMap<String, String>) }
        } catch (e: Exception) {
            log.warn("i18n: JSON parse error: ${e.message}")
            emptyMap()
        }
    }

    private fun flattenJsonObject(
        obj: com.google.gson.JsonObject,
        prefix: String,
        out: MutableMap<String, String>
    ) {
        for ((k, v) in obj.entrySet()) {
            val fullKey = if (prefix.isEmpty()) k else "$prefix.$k"
            when {
                v.isJsonObject    -> flattenJsonObject(v.asJsonObject, fullKey, out)
                v.isJsonArray     -> out[fullKey] = v.asJsonArray
                    .joinToString(", ") { if (it.isJsonPrimitive) it.asString else it.toString() }
                v.isJsonPrimitive -> out[fullKey] = v.asString
            }
        }
    }

    // ── YAML 解析（使用 IntelliJ 平台内置 SnakeYAML）──────────────────────────

    private fun parseYaml(content: String): Map<String, String> {
        return try {
            val raw = org.yaml.snakeyaml.Yaml().load<Any>(content)
            buildMap { flattenYamlNode(raw, "", this as MutableMap<String, String>) }
        } catch (e: Exception) {
            log.warn("i18n: YAML parse error: ${e.message}")
            emptyMap()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenYamlNode(node: Any?, prefix: String, out: MutableMap<String, String>) {
        when (node) {
            is Map<*, *> -> (node as Map<String, Any?>).forEach { (k, v) ->
                flattenYamlNode(v, if (prefix.isEmpty()) k else "$prefix.$k", out)
            }
            is List<*>   -> out[prefix] = node.joinToString(", ") { it?.toString() ?: "" }
            null          -> { /* skip */ }
            else          -> out[prefix] = node.toString()
        }
    }

    // ── 伴生对象 ─────────────────────────────────────────────────────────────

    companion object {
        fun getInstance(project: Project): LocaleFileService =
            project.getService(LocaleFileService::class.java)
    }
}
