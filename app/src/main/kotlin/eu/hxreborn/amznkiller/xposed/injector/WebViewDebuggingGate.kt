package eu.hxreborn.amznkiller.xposed.injector

import android.util.Log
import android.webkit.WebView
import eu.hxreborn.amznkiller.util.Logger
import eu.hxreborn.amznkiller.xposed.hook.webviewDebugging
import java.util.concurrent.atomic.AtomicBoolean

object WebViewDebuggingGate {
    private val enabled = AtomicBoolean(false)

    fun tryEnable() {
        if (!webviewDebugging) {
            if (enabled.compareAndSet(true, false)) {
                runCatching {
                    WebView.setWebContentsDebuggingEnabled(false)
                }.onSuccess {
                    Logger.debug { "webview debug disabled" }
                }.onFailure {
                    Logger.log(Log.ERROR, "webview debug disable fail", it)
                }
            }
            return
        }
        if (!enabled.compareAndSet(false, true)) return
        runCatching {
            WebView.setWebContentsDebuggingEnabled(true)
        }.onSuccess {
            Logger.debug { "webview debug enabled" }
        }.onFailure {
            Logger.log(Log.ERROR, "webview debug fail", it)
        }
    }
}
