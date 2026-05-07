# MineSpace Developer Tools

面向 **WebStorm / IntelliJ IDEA** 的开发效率插件，提供快速日志插入和 i18n 国际化辅助功能。

---

## 功能模块

### Console Helper — 快速日志工具

#### 快速插入日志

| 操作 | 快捷键 | 说明 |
|---|---|---|
| 插入日志语句 | `Alt+Shift+L` | 选中变量时以变量为对象；未选中时自动提取光标处的标识符 |
| 清理所有日志 | `Alt+Shift+D` | 删除当前文件中所有匹配配置函数名的日志行 |
| 右键菜单 | 编辑器右键 → **Console Helper** | 同上两个操作 |

**示例 — 选中 `userData` 后按 `Alt+Shift+L`，生成：**

```js
// paramCount = 2，双引号，带前缀和行号
console.log("🚀 ~ app ~ L42 ~ userData:", userData)

// 开启浏览器颜色样式
console.log("%c🚀 ~ app ~ L42 ~ userData:", "color: #4ECDC4; font-size: 14px; font-weight: bold", userData)

// 开启随机颜色 + 终端 ANSI
console.log(`\x1b[32m🚀 ~ app ~ L42 ~ userData:\x1b[0m`, userData)

// 开启 JSON.stringify（防止 [object Object]）
console.log("🚀 ~ app ~ L42 ~ userData:", JSON.stringify(userData, null, 2))
```

**Python / Java / Kotlin 同样支持：**

```python
# Python
print(f"🚀 ~ main ~ L10 ~ result: {result}")
```

```java
// Java
System.out.println("🚀 ~ Main ~ L10 ~ result: " + result);
```

```kotlin
// Kotlin
println("🚀 ~ Main ~ L10 ~ result: $result")
```

---

#### 日志样式配置

打开 **Settings → Editor → Console Helper** 进行配置。

##### 标签配置

| 选项 | 默认值 | 说明 |
|---|---|---|
| 前缀标识 | `🚀` | 出现在每条日志最前面，可改为 `[LOG]`、`>>` 等任意字符串 |
| 显示文件名 | 开启 | 在标签中插入当前文件名（不含扩展名） |
| 显示行号 | 开启 | 在标签中插入当前行号，格式为 `L42` |

标签各部分以 ` ~ ` 分隔，例如：`🚀 ~ myFile ~ L42 ~ myVar`

##### 颜色样式

> 颜色样式仅对 `console.*` 系列函数生效。

| 选项 | 说明 |
|---|---|
| 随机颜色 | 每次插入时从内置 15 色调色板中随机选取，方便区分不同位置的日志 |
| 文字颜色 | 固定颜色，十六进制格式，如 `#00ff00` |
| 背景颜色 | 留空则无背景色 |
| 字体大小 | 单位 px，范围 8–72 |
| 颜色输出目标 | `browser`：使用 `%c` + CSS 样式（适用于浏览器控制台）<br>`terminal`：使用 `\x1b[` ANSI 转义码（适用于 Node.js 终端） |

**浏览器 `%c` 效果：**
```js
console.log("%c🚀 ~ api ~ L88 ~ res:", "color: #FF6B6B; font-size: 14px; font-weight: bold", res)
```

**终端 ANSI 效果：**
```js
console.log(`\x1b[32m🚀 ~ api ~ L88 ~ res:\x1b[0m`, res)
```

---

#### 其他格式配置

| 选项 | 可选值 | 说明 |
|---|---|---|
| 格式化复杂对象 | 开/关 | 开启后将变量包裹为 `JSON.stringify(var, null, 2)`，避免输出 `[object Object]` |
| 参数数量 | `1` / `2` / `3` | 控制生成的日志参数结构（见下表） |
| 引号类型 | 双引号 / 单引号 | 适配不同项目的代码风格 |
| 末尾分号 | 有/无 | 适配不同项目的代码风格 |

**参数数量说明：**

| 值 | 生成格式 | 示例 |
|---|---|---|
| `1` | 纯字符串，不引用变量 | `console.log("🚀 ~ file ~ L42 ~ myVar")` |
| `2` | 标签字符串 + 变量值 | `console.log("🚀 ~ file ~ L42 ~ myVar:", myVar)` |
| `3` | 位置信息 + 变量标签 + 变量值 | `console.log("🚀 ~ file ~ L42", "myVar:", myVar)` |

---

#### 日志函数配置

| 选项 | 说明 |
|---|---|
| 当前日志函数 | 插入时使用的函数，可从下拉框选择或直接输入自定义函数名 |
| 全部日志函数 | 以逗号分隔的函数列表；**清理日志**时会匹配并删除所有包含这些函数调用的行 |

内置支持：`console.log`、`console.debug`、`console.warn`、`console.error`、`console.info`、`DEBUG_LOG`、`print`

---

### i18n Helper — 国际化辅助工具

在代码行尾自动展示当前行使用的 i18n key 所对应的翻译内容，无需跳转 locale 文件。

#### 效果预览

```ts
// displayLanguage = "zh-CN" 时，行尾自动追加翻译注释：
const title = t('home.title')            //  →  首页标题
const btn   = t('common.save')           //  →  保存
const msg   = i18n.t('error.notFound')   //  →  页面不存在
```

```html
<!-- Angular ngx-translate -->
<button>{{ 'common.submit' | translate }}</button>   <!--  →  提交  -->
```

#### 操作

| 操作 | 快捷键 | 说明 |
|---|---|---|
| 刷新翻译缓存 | `Alt+Shift+R` | 重新加载所有 locale 文件（文件变动时自动刷新） |
| 右键菜单 | 编辑器右键 → **i18n Helper** | 同上 |

#### 配置项（Settings → Editor → i18n）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `sourceLanguage` | `en` | 来源语言：locale 文件中用作翻译基准的语言代码 |
| `displayLanguage` | `en` | 显示语言：行尾注释展示哪种语言的翻译 |
| `localesPaths` | 见下 | locale 文件目录，逗号分隔，相对项目根目录 |
| `keystyle` | `nested` | 键名风格：`nested`（嵌套对象）或 `flat`（展平点号键名） |
| `annotations` | `true` | 是否启用行尾内联翻译注释 |
| `enabledFrameworks` | `auto` | 支持的框架，`auto` 则自动检测 |

**localesPaths 默认搜索路径：**
```
src/locales, locales, src/i18n, i18n, src/assets/i18n, public/locales
```
支持 Glob patterns，例如：`src/**/locales`

#### locale 文件布局

插件支持两种常见目录结构：

```
# 布局 1 — 文件命名为语言代码
locales/
  en.json
  zh-CN.json

# 布局 2 — 按语言分子目录
locales/
  en/
    common.json
    home.json
  zh-CN/
    common.json
    home.json
```

#### 支持的框架

| 框架 | 示例代码 |
|---|---|
| **i18next / react-i18next** | `t('key')` |
| **vue-i18n** | `$t('key')` · `this.$t('key')` · `v-t="'key'"` · `$i18n.t('key')` |
| **react-intl** | `intl.formatMessage({ id: 'key' })` · `<FormattedMessage id="key" />` |
| **ngx-translate** | `'key' \| translate` · `translate.instant('key')` |
| **Flutter easy_localization** | `tr('key')` |
| **通用** | `translate('key')` · `i18n.t('key')` · `gettext('key')` |

#### locale 文件格式

**JSON（nested）：**
```json
{
  "home": {
    "title": "首页标题",
    "subtitle": "欢迎使用"
  },
  "common": {
    "save": "保存",
    "cancel": "取消"
  }
}
```

**JSON（flat）：**
```json
{
  "home.title": "首页标题",
  "common.save": "保存"
}
```

**YAML：**
```yaml
home:
  title: 首页标题
  subtitle: 欢迎使用
common:
  save: 保存
```

---

## 快速开始

### 开发环境

- IntelliJ IDEA 2025.2+（或 WebStorm 2025.2+）
- JDK 21
- Gradle 9（已内置 Wrapper）

### 本地构建

```bash
# 设置 JDK 21
export JAVA_HOME=/path/to/jdk-21

# 编译
./gradlew compileKotlin

# 构建插件 ZIP
./gradlew buildPlugin
# 输出：build/distributions/plugin-1.0-SNAPSHOT.zip
```

### 在 IDE 中运行调试

```bash
./gradlew runIde
```

或在 IntelliJ IDEA 中使用 `.run/Run IDE with Plugin.run.xml` 配置直接运行。

### 安装插件

1. 执行 `./gradlew buildPlugin`
2. 打开 IntelliJ IDEA → **Settings → Plugins → ⚙️ → Install Plugin from Disk**
3. 选择 `build/distributions/plugin-1.0-SNAPSHOT.zip`

---

## 项目结构

```
src/main/kotlin/com/lior/plugin/
├── consoleHelper/
│   ├── actions/
│   │   ├── InsertLogAction.kt           # Alt+Shift+L：插入日志
│   │   └── ClearLogsAction.kt           # Alt+Shift+D：清理所有日志
│   ├── generator/
│   │   └── LogStatementGenerator.kt     # 多语言日志语句生成器
│   └── settings/
│       ├── ConsoleHelperSettings.kt
│       └── ConsoleHelperSettingsConfigurable.kt
└── i18n/
    ├── actions/
    │   └── RefreshTranslationsAction.kt  # Alt+Shift+R：刷新翻译缓存
    ├── annotation/
    │   └── I18nEditorLinePainter.kt      # 行尾翻译注释渲染
    ├── pattern/
    │   └── I18nPatternMatcher.kt         # 框架模式匹配（正则）
    ├── service/
    │   └── LocaleFileService.kt          # locale 文件读取与缓存（ProjectService）
    └── settings/
        ├── I18nSettings.kt
        └── I18nSettingsConfigurable.kt
```

---

## 相关链接

- [IntelliJ Platform SDK 文档](https://plugins.jetbrains.com/docs/intellij)
- [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [插件发布指南](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
