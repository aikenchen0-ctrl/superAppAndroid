package com.adbcore

import android.content.Context

/**
 * 从 `:adbcore/src/main/assets/` 读取 shell 脚本并做 `{{KEY}}` 占位符替换的
 * 通用 helper。
 *
 * 用模板替换而不是 shell 环境变量,理由:
 *   - host 端字符串替换完全可控,不必担心 sh 引号 / 转义 / 元字符
 *   - server 那边 `sh -c <script>` 直接拿到拼好的字符串,行为可预测
 *
 * 占位符约定:`{{KEY}}` —— 大写下划线,两个花括号包裹。脚本里出现的所有
 * 占位符都必须在 [ pairs ] 里给值,否则模板会原样保留(运行时 sh 会把它当成
 * 不存在的字面字符串)。
 */
internal object AssetScripts {

    fun load(
        context: Context,
        assetPath: String,
        vararg pairs: Pair<String, String>
    ): String {
        var s = context.applicationContext.assets
            .open(assetPath)
            .bufferedReader()
            .use { it.readText() }
        for ((k, v) in pairs) {
            s = s.replace("{{$k}}", v)
        }
        return s
    }
}
