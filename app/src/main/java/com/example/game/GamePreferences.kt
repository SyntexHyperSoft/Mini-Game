package com.example.game

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.WebView

class GamePreferences(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("cosmic_battle_prefs", Context.MODE_PRIVATE)

  var fpsLimit: Int
    get() = prefs.getInt(KEY_FPS_LIMIT, DEFAULT_FPS)
    set(value) = prefs.edit().putInt(KEY_FPS_LIMIT, value).apply()

  var isNotificationsEnabled: Boolean
    get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

  var isKeepScreenOn: Boolean
    get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
    set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

  var isImmersive: Boolean
    get() = prefs.getBoolean(KEY_IMMERSIVE, true)
    set(value) = prefs.edit().putBoolean(KEY_IMMERSIVE, value).apply()

  var isLowGraphics: Boolean
    get() = prefs.getBoolean(KEY_LOW_GRAPHICS, false)
    set(value) = prefs.edit().putBoolean(KEY_LOW_GRAPHICS, value).apply()

  /**
   * FeGameX rendering engine mode: strictly "cpu" or "gpu".
   * Legacy values ("eco" -> "cpu", "perf"/"ultimate" -> "gpu") are migrated transparently.
   */
  var feGameXEngineMode: String
    get() = normalizeEngineMode(prefs.getString(KEY_FEGAMEX_ENGINE, ENGINE_GPU))
    set(value) = prefs.edit().putString(KEY_FEGAMEX_ENGINE, normalizeEngineMode(value)).apply()

  fun applyFpsSettings(activity: Activity?, webView: WebView?) {
    val targetFps = if (fpsLimit <= 0 || fpsLimit >= 240) 0 else fpsLimit.coerceAtLeast(MIN_FPS)

    GamePerformanceOptimizer.updateTargetFps(targetFps)

    activity?.let { act ->
      GamePerformanceOptimizer.applyWindowRefreshRatePolicy(act, act.window, targetFps)
    }
    GamePerformanceOptimizer.applyWebViewFrameRate(webView, targetFps)

    webView?.let { wv ->
      val js = """
        (function() {
          try {
            var target = $targetFps;
            window.__cosmicFps = target;
            if (typeof setGameFpsLimit === 'function') {
              setGameFpsLimit(target);
            } else if (typeof TARGET_FPS !== 'undefined') {
              TARGET_FPS = target;
            }
          } catch(e) {}
        })();
      """.trimIndent()
      wv.post { wv.evaluateJavascript(js, null) }
    }
  }

  fun applyLowGraphicsSettings(webView: WebView?) {
    val enabled = isLowGraphics
    webView?.let { wv ->
      val js = """
        (function() {
          try {
            window.__cosmicLowGraphics = $enabled;
            if (typeof setLowGraphicsMode === 'function') {
              setLowGraphicsMode($enabled);
            }
          } catch(e) {}
        })();
      """.trimIndent()
      wv.post { wv.evaluateJavascript(js, null) }
    }
  }

  fun syncFeGameXEngineToWebView(webView: WebView?) {
    val mode = feGameXEngineMode
    webView?.let { wv ->
      val js = """
        (function() {
          try {
            window.__cosmicFeGameXMode = '$mode';
            if (typeof setFeGameXEngineMode === 'function') {
              setFeGameXEngineMode('$mode', true);
            }
          } catch(e) {}
        })();
      """.trimIndent()
      wv.post { wv.evaluateJavascript(js, null) }
    }
  }

  companion object {
    private const val KEY_FPS_LIMIT = "fps_limit"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_IMMERSIVE = "immersive"
    private const val KEY_LOW_GRAPHICS = "low_graphics"
    private const val KEY_FEGAMEX_ENGINE = "fegamex_engine_mode"

    const val ENGINE_CPU = "cpu"
    const val ENGINE_GPU = "gpu"

    const val MIN_FPS = 20
    const val DEFAULT_FPS = 120
    val FPS_OPTIONS = listOf(20, 30, 60, 90, 120, 144, 165, 0)

    fun normalizeEngineMode(raw: String?): String {
      return when (raw?.trim()?.lowercase()) {
        ENGINE_CPU, "eco" -> ENGINE_CPU
        else -> ENGINE_GPU
      }
    }
  }
}
