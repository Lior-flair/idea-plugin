plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group   = "com.lior"
version = "1.6.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252.25557"
        }

        changeNotes = """
            <h3>v1.6.0</h3>
            <h4>i18n Helper — Tool Window 原生 Header + 启动自动加载修复</h4>
            <ul>
              <li>Tool Window 改为原生双 Content 方案：Table View / Tree View 标签直接出现在 IDE 标题栏，与平台 UI 完全融合</li>
              <li>右侧 Title Actions：搜索框、刷新按钮、设置按钮，无需展开额外面板</li>
              <li>修复：新打开文件时行尾 Inlay 与悬浮气泡须手动刷新才显示的问题——editorCreated 时若缓存未就绪，自动触发后台加载，完成后刷新所有编辑器</li>
              <li>悬浮气泡位置上移，避免与当前行代码重叠</li>
            </ul>
            <h3>v1.5.1</h3>
            <h4>设置入口迁移至【工具】</h4>
            <ul>
              <li>Console Helper 设置入口均从【编辑器】迁移至 IDE 设置 → 【工具】</li>
            </ul>
            <h3>v1.5.0</h3>
            <h4>设置入口迁移至【工具】+ i18n 项目级配置</h4>
            <ul>
              <li>i18n Helper 设置入口均从【编辑器】迁移至 IDE 设置 → 【工具】</li>
              <li>i18n 项目专属配置（语言代码、路径、文件格式等）写入 <code>.idea/i18n.xml</code>，每个项目独立保存</li>
              <li>i18n 全局配置（行尾 Inlay 开关、悬浮气泡开关）保存在 IDE 级别，所有项目共享</li>
            </ul>
            <h3>v1.4.0</h3>
            <h4>i18n Helper — 设置项完善</h4>
            <ul>
              <li>新增「文件格式」勾选组：可单独启用或禁用 .json / .yaml / .yml / .properties / .js / .ts 各后缀的扫描</li>
              <li>新增「启用鼠标悬浮气泡」独立开关，与行尾 Inlay 开关分离，可按需关闭悬浮提示</li>
            </ul>
            <h3>v1.3.1</h3>
            <h4>i18n Helper — 修复行尾 Inlay 中文乱码</h4>
            <ul>
              <li>行尾翻译渲染改用 IDE UI 标签字体（UIUtil.getLabelFont），解决代码等宽字体不含中日韩字形导致的方块乱码问题</li>
              <li>同步修正 Inlay 宽度计算，避免汉字被截断</li>
            </ul>
            <h3>v1.3.0</h3>
            <h4>i18n Helper — 行尾内联彻底修复</h4>
            <ul>
              <li>将行尾翻译渲染方案从 EditorLinePainter 切换为 InlayModel.addAfterLineEndElement()，
                  解决 IntelliJ 2025.2 中 repaint() 无法可靠触发绘制回调的问题</li>
              <li>翻译加载完成后自动刷新所有打开的编辑器，无需手动按 Alt+Shift+R</li>
              <li>文档内容变更时自动更新行尾 Inlay，与代码保持实时同步</li>
            </ul>
            <h3>v1.2.0</h3>
            <h4>i18n Helper — 鼠标悬浮翻译</h4>
            <ul>
              <li>新增鼠标悬浮气泡：光标停留在 i18n key 上时弹出气泡，同时展示所有语言的翻译对照</li>
            </ul>
            <h3>v1.1.0</h3>
            <h4>i18n Helper — 新增 Tool Window 面板</h4>
            <ul>
              <li>新增底部 i18n 面板，以 Table View / Tree View 双视图展示项目全量翻译条目</li>
              <li>Table View：Key 列 + 各语言值列，支持多语言并排对比，单击列头排序</li>
              <li>Tree View：按 key 层级展开，叶节点同步显示主语言翻译值</li>
              <li>搜索框实时过滤——同时匹配 key 名称与翻译内容，Table/Tree 联动更新</li>
            </ul>
            <h3>v1.0.0</h3>
            <h4>Console Helper — 快速日志工具</h4>
            <ul>
              <li>Alt+Shift+L：一键在光标所在行下方插入日志语句，自动提取变量名、文件名、行号</li>
              <li>Alt+Shift+D：一键清除当前文件中所有匹配函数名的日志行</li>
              <li>支持 JavaScript / TypeScript / Python / Java / Kotlin 多语言</li>
              <li>支持 console.log / console.debug / console.warn / console.error / print 等多种日志函数</li>
              <li>支持浏览器 %c CSS 彩色输出 与 终端 ANSI 彩色输出，可开启随机颜色</li>
              <li>支持 JSON.stringify 包裹复杂对象，避免 [object Object]</li>
              <li>参数数量（1/2/3）、引号类型、末尾分号均可配置</li>
            </ul>
            <h4>i18n Helper — 国际化内联注释</h4>
            <ul>
              <li>在代码行尾实时显示 i18n key 对应的翻译内容，无需跳转语言文件</li>
              <li>Alt+Shift+R：手动刷新翻译缓存；语言文件变动时自动刷新</li>
              <li>支持 i18next / vue-i18n / react-intl / ngx-translate / Flutter easy_localization 等主流框架</li>
              <li>支持 JSON / YAML / Properties / JS / TS 格式的 locale 文件</li>
              <li>支持按语言子目录（zh-CN/）与文件名（messages_zh_CN.properties）两种布局</li>
              <li>支持 Glob 路径模式，如 src/**/i18n</li>
              <li>支持文件名前缀过滤（localeFilePrefix），如只扫描 messages_*.properties</li>
              <li>自动检测项目中的可用语言列表，支持 BCP 47 语言代码（zh-CN、en-US 等）</li>
            </ul>
        """.trimIndent()
    }

    buildSearchableOptions = false
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
