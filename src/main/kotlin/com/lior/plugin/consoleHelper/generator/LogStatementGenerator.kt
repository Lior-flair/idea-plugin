package com.lior.plugin.consoleHelper.generator

import com.intellij.openapi.vfs.VirtualFile
import com.lior.plugin.consoleHelper.settings.ConsoleHelperSettings

object LogStatementGenerator {

    private val BROWSER_COLORS = listOf(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
        "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E9",
        "#82E0AA", "#F8C471", "#F1948A", "#AED6F1", "#A9DFBF"
    )

    // \x1b is the ANSI escape character; written as literal text so the generated JS source is readable
    private val ANSI_COLORS = listOf(
        "\\x1b[31m", "\\x1b[32m", "\\x1b[33m", "\\x1b[34m",
        "\\x1b[35m", "\\x1b[36m", "\\x1b[91m", "\\x1b[92m",
        "\\x1b[93m", "\\x1b[94m", "\\x1b[95m", "\\x1b[96m"
    )
    private const val ANSI_RESET = "\\x1b[0m"

    enum class Language { JAVASCRIPT, TYPESCRIPT, PYTHON, JAVA, KOTLIN, OTHER }

    fun detectLanguage(file: VirtualFile?): Language {
        return when (file?.extension?.lowercase()) {
            "js", "jsx", "mjs", "cjs" -> Language.JAVASCRIPT
            "ts", "tsx" -> Language.TYPESCRIPT
            "py" -> Language.PYTHON
            "java" -> Language.JAVA
            "kt", "kts" -> Language.KOTLIN
            else -> Language.OTHER
        }
    }

    fun generate(
        variable: String?,
        lineNumber: Int,
        file: VirtualFile?,
        indentation: String
    ): String {
        val settings = ConsoleHelperSettings.getInstance()
        val lang = detectLanguage(file)
        val fileName = file?.nameWithoutExtension ?: ""

        return when (lang) {
            Language.PYTHON -> generatePythonStatement(variable, lineNumber, fileName, indentation, settings)
            Language.JAVA -> generateJavaStatement(variable, lineNumber, fileName, indentation, settings)
            Language.KOTLIN -> generateKotlinStatement(variable, lineNumber, fileName, indentation, settings)
            else -> generateJsStatement(variable, lineNumber, fileName, indentation, settings)
        }
    }

    // ── Label builders ───────────────────────────────────────────────────────

    private fun buildLocationLabel(s: ConsoleHelperSettings, fileName: String, lineNumber: Int): String {
        val parts = mutableListOf<String>()
        if (s.prefix.isNotEmpty()) parts.add(s.prefix)
        if (s.showFileName && fileName.isNotEmpty()) parts.add(fileName)
        if (s.showLineNumber) parts.add("L${lineNumber + 1}")
        return parts.joinToString(" ~ ")
    }

    private fun buildFullLabel(s: ConsoleHelperSettings, fileName: String, lineNumber: Int, variable: String?): String {
        val parts = mutableListOf<String>()
        if (s.prefix.isNotEmpty()) parts.add(s.prefix)
        if (s.showFileName && fileName.isNotEmpty()) parts.add(fileName)
        if (s.showLineNumber) parts.add("L${lineNumber + 1}")
        if (variable != null) parts.add(variable)
        return parts.joinToString(" ~ ")
    }

    private fun wrapVariable(variable: String, s: ConsoleHelperSettings): String =
        if (s.formatComplexObjects) "JSON.stringify($variable, null, 2)" else variable

    // ── JavaScript / TypeScript ──────────────────────────────────────────────

    private fun generateJsStatement(
        variable: String?,
        lineNumber: Int,
        fileName: String,
        indentation: String,
        s: ConsoleHelperSettings
    ): String {
        val isConsole = s.logFunction.startsWith("console.")
        val useColor = isConsole && (s.useRandomColor || s.customColor.isNotEmpty())

        return when {
            useColor && s.colorTarget == "terminal" ->
                generateJsTerminalColorStatement(variable, lineNumber, fileName, indentation, s)
            useColor ->
                generateJsBrowserColorStatement(variable, lineNumber, fileName, indentation, s)
            else ->
                generateJsPlainStatement(variable, lineNumber, fileName, indentation, s)
        }
    }

    private fun generateJsBrowserColorStatement(
        variable: String?,
        lineNumber: Int,
        fileName: String,
        indentation: String,
        s: ConsoleHelperSettings
    ): String {
        val q = s.getQuoteChar()
        val semi = s.getSemicolon()
        val fn = s.logFunction
        val color = if (s.useRandomColor) BROWSER_COLORS.random() else s.customColor
        val label = buildFullLabel(s, fileName, lineNumber, variable)

        val styleParts = mutableListOf("color: $color", "font-size: ${s.fontSize}px", "font-weight: bold")
        if (s.customBgColor.isNotEmpty() && !s.useRandomColor) {
            styleParts.add("background-color: ${s.customBgColor}")
        }
        val style = styleParts.joinToString("; ")

        return if (variable != null) {
            val varStr = wrapVariable(variable, s)
            """${indentation}${fn}(${q}%c${label}:${q}, ${q}${style}${q}, ${varStr})${semi}"""
        } else {
            """${indentation}${fn}(${q}%c${label}${q}, ${q}${style}${q})${semi}"""
        }
    }

    private fun generateJsTerminalColorStatement(
        variable: String?,
        lineNumber: Int,
        fileName: String,
        indentation: String,
        s: ConsoleHelperSettings
    ): String {
        val semi = s.getSemicolon()
        val fn = s.logFunction
        val ansiColor = ANSI_COLORS.random()
        val label = buildFullLabel(s, fileName, lineNumber, variable)

        // Use JS template literal (backtick string) so \x1b is interpreted at runtime
        return if (variable != null) {
            val varStr = wrapVariable(variable, s)
            "${indentation}${fn}(`${ansiColor}${label}:${ANSI_RESET}`, ${varStr})${semi}"
        } else {
            "${indentation}${fn}(`${ansiColor}${label}${ANSI_RESET}`)${semi}"
        }
    }

    private fun generateJsPlainStatement(
        variable: String?,
        lineNumber: Int,
        fileName: String,
        indentation: String,
        s: ConsoleHelperSettings
    ): String {
        val q = s.getQuoteChar()
        val semi = s.getSemicolon()
        val fn = s.logFunction

        return when (s.paramCount) {
            1 -> {
                val label = buildFullLabel(s, fileName, lineNumber, variable)
                "${indentation}${fn}(${q}${label}${q})${semi}"
            }
            3 -> {
                val locationLabel = buildLocationLabel(s, fileName, lineNumber)
                if (variable != null) {
                    val varStr = wrapVariable(variable, s)
                    "${indentation}${fn}(${q}${locationLabel}${q}, ${q}${variable}:${q}, ${varStr})${semi}"
                } else {
                    "${indentation}${fn}(${q}${locationLabel}${q})${semi}"
                }
            }
            else -> { // 2 (default)
                val label = buildFullLabel(s, fileName, lineNumber, variable)
                if (variable != null) {
                    val varStr = wrapVariable(variable, s)
                    "${indentation}${fn}(${q}${label}:${q}, ${varStr})${semi}"
                } else {
                    "${indentation}${fn}(${q}${label}${q})${semi}"
                }
            }
        }
    }

    // ── Python ───────────────────────────────────────────────────────────────

    private fun generatePythonStatement(
        variable: String?,
        lineNumber: Int,
        fileName: String,
        indentation: String,
        s: ConsoleHelperSettings
    ): String {
        val fn = if (s.logFunction in listOf("console.log", "console.debug", "console.warn",
                "console.error", "console.info")) "print" else s.logFunction
        val label = buildFullLabel(s, fileName, lineNumber, variable)

        return if (variable != null) {
            "${indentation}${fn}(f\"${label}: {${variable}}\")"
        } else {
            "${indentation}${fn}(\"${label}\")"
        }
    }

    // ── Java ─────────────────────────────────────────────────────────────────

    private fun generateJavaStatement(
        variable: String?,
        lineNumber: Int,
        fileName: String,
        indentation: String,
        s: ConsoleHelperSettings
    ): String {
        val fn = when (s.logFunction) {
            "console.log", "console.debug", "console.info", "print" -> "System.out.println"
            else -> s.logFunction
        }
        val label = buildFullLabel(s, fileName, lineNumber, variable)

        return if (variable != null) {
            "${indentation}${fn}(\"${label}: \" + ${variable});"
        } else {
            "${indentation}${fn}(\"${label}\");"
        }
    }

    // ── Kotlin ───────────────────────────────────────────────────────────────

    private fun generateKotlinStatement(
        variable: String?,
        lineNumber: Int,
        fileName: String,
        indentation: String,
        s: ConsoleHelperSettings
    ): String {
        val fn = if (s.logFunction == "console.log") "println" else s.logFunction
        val semi = s.getSemicolon()
        val label = buildFullLabel(s, fileName, lineNumber, variable)

        return if (variable != null) {
            "${indentation}${fn}(\"${label}: \$${variable}\")${semi}"
        } else {
            "${indentation}${fn}(\"${label}\")${semi}"
        }
    }
}
