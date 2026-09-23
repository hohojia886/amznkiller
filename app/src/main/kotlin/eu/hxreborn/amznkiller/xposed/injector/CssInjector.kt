package eu.hxreborn.amznkiller.xposed.injector

import android.webkit.WebView
import eu.hxreborn.amznkiller.BuildConfig
import eu.hxreborn.amznkiller.util.Logger
import eu.hxreborn.amznkiller.xposed.hook.injectionEnabled
import eu.hxreborn.amznkiller.xposed.hook.selectors
import eu.hxreborn.amznkiller.xposed.js.ScriptId
import eu.hxreborn.amznkiller.xposed.js.ScriptRepository
import eu.hxreborn.amznkiller.xposed.js.WebViewJsExecutor
import org.json.JSONObject
import java.util.WeakHashMap

object CssInjector {
    private const val WHITELIST_RULE_COUNT = 1

    private data class InjectionKey(
        val url: String,
        val selectorsHash: Int,
    )

    private val lastInjectionByWebView = WeakHashMap<WebView, InjectionKey>()

    @Volatile private var cachedCss: String = ""

    @Volatile private var cachedHash: Int = 0

    @Volatile private var cachedRuleCount: Int = 0
    private var lastValidatedHash: Int = 0

    fun onNavigation(webView: WebView) {
        lastInjectionByWebView.remove(webView)
    }

    fun updateCache(selectors: List<String>) {
        val hash = selectors.hashCode()
        if (cachedHash == hash && cachedCss.isNotEmpty()) return
        val builder = StringBuilder(selectors.size * 32)
        for (selector in selectors) {
            builder.append(selector).append("{display:none!important;}")
        }
        builder.append(
            "#amznkiller-charts,#amznkiller-charts *" +
                "{display:block!important;visibility:visible!important;" +
                "opacity:1!important;}",
        )
        cachedCss = builder.toString()
        cachedHash = hash
        cachedRuleCount = selectors.size
    }

    fun inject(
        webView: WebView,
        url: String,
    ) {
        if (!injectionEnabled) {
            Logger.debug { "css skip reason=disabled" }
            return
        }
        val selectors = selectors
        if (selectors.isEmpty()) {
            Logger.debug { "css skip reason=empty-selectors" }
            return
        }

        updateCache(selectors)
        val hash = cachedHash
        val css = cachedCss

        lastInjectionByWebView[webView]?.let { last ->
            if (last.url == url && last.selectorsHash == hash) {
                Logger.debug { "css skip reason=already-injected" }
                return
            }
        }

        val shouldValidate = BuildConfig.DEBUG && lastValidatedHash != hash
        if (shouldValidate) lastValidatedHash = hash

        val args =
            JSONObject().apply {
                put("css", css)
                put("hash", hash)
                put("validate", shouldValidate)
                put("expectedRules", cachedRuleCount + WHITELIST_RULE_COUNT)
            }
        val script = "${
            ScriptRepository.get(
                ScriptId.AD_BLOCK,
            )
        }\nwindow.AmznKiller.blockAds($args);"
        lastInjectionByWebView[webView] = InjectionKey(url, hash)
        Logger.debug { "css inject rules=$cachedRuleCount" }

        WebViewJsExecutor.evaluate(webView, script, "CssInjector") { result ->
            if (result == null || result == "null" || result.contains("\"ok\":true")) {
                return@evaluate
            }
            Logger.debug { "css validate result=$result" }
        }
    }
}
