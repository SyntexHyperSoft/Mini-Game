package com.example.game

import android.app.Activity
import android.app.GameManager
import android.app.GameState
import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.lang.ref.WeakReference

data class WebViewFlagState(
  val disableGpuRasterization: Boolean,
  val ignoreGpuBlocklist: Boolean,
  val vulkanPreferred: Boolean
)

object GamePerformanceOptimizer {

  private const val TAG = "GameOptimizer"
  private const val VIEW_REQUESTED_FRAME_RATE_CATEGORY_HIGH = -3f

  private var hintSession: PerformanceHintManager.Session? = null
  private var hintThreadIds: IntArray = intArrayOf()
  private var frameLoopActive = false
  private var lastChoreographerStartNs = 0L

  @Volatile
  private var lastJsWorkReportMs = 0L

  @Volatile
  private var smoothedJsWorkNs = 4_000_000L

  @Volatile
  private var lastThermalPollMs = 0L

  @Volatile
  private var thermalHeadroomSupported: Boolean? = null

  @Volatile
  private var cachedThermalLevel = 0 // 0=normal, 1=warm, 2=hot

  @Volatile
  private var cachedSystemGameMode = 1 // 1=standard, 2=performance, 3=battery

  private var thermalListener: Any? = null
  private var appContextRef: WeakReference<Context>? = null

  private val adpfFrameCallback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
      if (!frameLoopActive) return
      val nowNs = System.nanoTime()
      val nowMs = SystemClock.elapsedRealtime()

      // Prefer real JS update+draw work duration when active; fall back to light frame delta otherwise
      if (nowMs - lastJsWorkReportMs > 400L && lastChoreographerStartNs > 0L) {
        val workEstimate = ((nowNs - lastChoreographerStartNs) / 2L).coerceIn(1_000_000L, 25_000_000L)
        reportActualWorkDuration(workEstimate)
      }
      lastChoreographerStartNs = nowNs

      // Periodically sample ADPF thermal headroom (strictly >= 12s interval on single Choreographer thread)
      if (nowMs - lastThermalPollMs >= 12_000L) {
        lastThermalPollMs = nowMs
        pollThermalState()
      }

      Choreographer.getInstance().postFrameCallback(this)
    }
  }

  fun getActiveWebViewFlags(mode: String): WebViewFlagState {
    val normalized = GamePreferences.normalizeEngineMode(mode)
    return if (normalized == GamePreferences.ENGINE_CPU) {
      WebViewFlagState(
        disableGpuRasterization = true,
        ignoreGpuBlocklist = false,
        vulkanPreferred = false
      )
    } else {
      WebViewFlagState(
        disableGpuRasterization = false,
        ignoreGpuBlocklist = true,
        vulkanPreferred = true
      )
    }
  }

  /**
   * Synchronizes Chromium WebView command-line switches for the selected FeGameX mode:
   * - CPU: enables --disable-gpu-rasterization and clears GPU flags
   * - GPU: clears --disable-gpu-rasterization and enables --ignore-gpu-blocklist + GPU/OOP rasterization + zero-copy
   */
  fun applyChromiumCommandLineFlags(mode: String) {
    val flags = getActiveWebViewFlags(mode)
    try {
      val cmdClass = Class.forName("org.chromium.base.CommandLine")
      val isInitialized = runCatching {
        cmdClass.getMethod("isInitialized").invoke(null) as? Boolean
      }.getOrDefault(false) ?: false

      if (!isInitialized) {
        val initArgs = if (flags.disableGpuRasterization) {
          arrayOf("cosmic_battle", "--disable-gpu-rasterization")
        } else {
          arrayOf(
            "cosmic_battle",
            "--ignore-gpu-blocklist",
            "--enable-gpu-rasterization",
            "--enable-accelerated-2d-canvas",
            "--disable-background-timer-throttling",
            "--disable-renderer-backgrounding"
          )
        }
        runCatching {
          cmdClass.getMethod("init", Array<String>::class.java).invoke(null, initArgs as Any)
        }
      }

      val instance = runCatching {
        cmdClass.getMethod("getInstance").invoke(null)
      }.getOrNull() ?: return

      val appendSwitch = runCatching {
        cmdClass.getMethod("appendSwitch", String::class.java)
      }.getOrNull()
      val removeSwitch = runCatching {
        cmdClass.getMethod("removeSwitch", String::class.java)
      }.getOrNull()

      // Ensure experimental black-screen prone switches are always cleared
      removeSwitch?.invoke(instance, "enable-oop-rasterization")
      removeSwitch?.invoke(instance, "enable-zero-copy")

      if (flags.disableGpuRasterization) {
        removeSwitch?.invoke(instance, "ignore-gpu-blocklist")
        removeSwitch?.invoke(instance, "enable-gpu-rasterization")
        removeSwitch?.invoke(instance, "enable-accelerated-2d-canvas")
        removeSwitch?.invoke(instance, "disable-background-timer-throttling")
        removeSwitch?.invoke(instance, "disable-renderer-backgrounding")
        appendSwitch?.invoke(instance, "disable-gpu-rasterization")
      } else {
        removeSwitch?.invoke(instance, "disable-gpu-rasterization")
        appendSwitch?.invoke(instance, "ignore-gpu-blocklist")
        appendSwitch?.invoke(instance, "enable-gpu-rasterization")
        appendSwitch?.invoke(instance, "enable-accelerated-2d-canvas")
        appendSwitch?.invoke(instance, "disable-background-timer-throttling")
        appendSwitch?.invoke(instance, "disable-renderer-backgrounding")
      }
      Log.i(
        TAG,
        "FeGameX Chromium flags applied (mode=$mode, disable-gpu-rasterization=${flags.disableGpuRasterization}, ignore-gpu-blocklist=${flags.ignoreGpuBlocklist})"
      )
    } catch (e: Throwable) {
      Log.d(TAG, "Chromium CommandLine reflection skipped: ${e.message}")
    }
  }

  /**
   * Collects persistent UI + RenderThread + in-process Chromium compositor/GPU thread IDs
   * inside our own process so ADPF PerformanceHintManager boosts all rendering pipelines.
   */
  private fun collectRenderThreadIds(): IntArray {
    val tids = linkedSetOf(Process.myTid())
    try {
      val taskDir = File("/proc/self/task")
      val children = taskDir.listFiles()
      if (children != null) {
        for (entry in children) {
          val tid = entry.name.toIntOrNull() ?: continue
          val commFile = File(entry, "comm")
          val name = runCatching { commFile.readText().trim() }.getOrDefault("")
          if (
            name.contains("RenderThread", ignoreCase = true) ||
            name.contains("hwuiTask", ignoreCase = true) ||
            name.contains("CrRendererMain", ignoreCase = true) ||
            name.contains("VizCompositor", ignoreCase = true) ||
            name.contains("Chrome_InProcGpu", ignoreCase = true) ||
            name.contains("Compositor", ignoreCase = true)
          ) {
            tids.add(tid)
          }
          if (tids.size >= 10) break
        }
      }
    } catch (_: Throwable) {}
    return tids.toIntArray()
  }

  private fun initOrUpdateHintSession(context: Context, targetFrameTimeNs: Long) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
      val tids = collectRenderThreadIds()
      if (hintSession == null) {
        val hintManager = context.getSystemService(PerformanceHintManager::class.java) ?: return
        hintSession = runCatching {
          hintManager.createHintSession(tids, targetFrameTimeNs)
        }.getOrNull() ?: runCatching {
          hintManager.createHintSession(intArrayOf(Process.myTid()), targetFrameTimeNs)
        }.getOrNull()
        hintThreadIds = tids
        hintSession?.updateTargetWorkDuration(targetFrameTimeNs)
        Log.i(TAG, "ADPF HintSession created (tids=${tids.size}, targetNs=$targetFrameTimeNs)")
      } else {
        hintSession?.updateTargetWorkDuration(targetFrameTimeNs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !tids.contentEquals(hintThreadIds)) {
          runCatching {
            val method = hintSession!!::class.java.getMethod("setThreads", IntArray::class.java)
            method.invoke(hintSession, tids)
            hintThreadIds = tids
          }
        }
      }

      // Android 15+ (API 35+): disable power-efficiency preference unless system GameMode is Battery
      if (Build.VERSION.SDK_INT >= 35 && hintSession != null) {
        val preferEfficiency = cachedSystemGameMode == 3 // GameManager.GAME_MODE_BATTERY
        runCatching {
          val method = hintSession!!::class.java.getMethod(
            "setPreferPowerEfficiency",
            Boolean::class.javaPrimitiveType
          )
          method.invoke(hintSession, preferEfficiency)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "PerformanceHintManager init/update error: ${e.message}")
    }
  }

  fun refreshHintSessionThreads(context: Context, fpsLimit: Int) {
    initOrUpdateHintSession(context, targetNsForFps(fpsLimit))
  }

  /**
   * Applies maximum OS/Vulkan/ADPF/ARR optimizations across Android 8.0 (API 26) -> Android 17 (API 37).
   */
  fun applyGameOptimizations(activity: Activity) {
    appContextRef = WeakReference(activity.applicationContext)
    val window = activity.window
    val prefs = GamePreferences(activity)
    applyChromiumCommandLineFlags(prefs.feGameXEngineMode)

    try {
      Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to elevate thread priority: ${e.message}")
    }

    // Query Android 12+ GameMode and update Android 13+ GameState
    querySystemGameMode(activity)
    updateGameState(activity, isPlaying = false, isLoading = false)

    // Register Android 10+ thermal status listener once
    registerThermalListener(activity.applicationContext)

    // Android 12+ (API 31+) ADPF Performance Hint Session across UI + RenderThread + Chromium threads
    initOrUpdateHintSession(activity, targetNsForFps(prefs.fpsLimit))

    // Window flags for zero-latency fullscreen, display cutouts, high-Hz, ARR bypass, and hardware accel
    try {
      if (prefs.isKeepScreenOn) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.attributes = window.attributes.apply {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes = window.attributes.apply {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
      }

      applyWindowRefreshRatePolicy(activity, window, prefs.fpsLimit)
      applyAndroid15ArrBypass(window, prefs.fpsLimit)

      WindowCompat.setDecorFitsSystemWindows(window, false)
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      if (prefs.isImmersive) {
        insetsController.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
      }

      window.setFlags(
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
      )

      // Android 11+ (API 30 - 37): Request low-latency Display HAL scanout without post-processing filters
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { window.setPreferMinimalPostProcessing(true) }
      }

      // Only engage SustainedPerformanceMode under severe thermal load (never throttle cool hardware)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        window.setSustainedPerformanceMode(cachedThermalLevel >= 2)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Window flags setup error: ${e.message}")
    }

    if (prefs.feGameXEngineMode == GamePreferences.ENGINE_GPU) {
      startFrameHintLoop()
    } else {
      stopFrameHintLoop()
    }
  }

  /**
   * Matches native physical display resolution before selecting highest supported refresh rate,
   * preventing unintended resolution downscaling on Samsung/Xiaomi/OnePlus/Pixel panels.
   */
  fun applyWindowRefreshRatePolicy(activity: Activity, window: Window, fpsLimit: Int) {
    try {
      val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
      } else {
        @Suppress("DEPRECATION")
        window.windowManager.defaultDisplay
      } ?: return

      val currentMode = display.mode
      val modes = display.supportedModes
      val sameResModes = modes?.filter {
        it.physicalWidth == currentMode.physicalWidth &&
          it.physicalHeight == currentMode.physicalHeight
      }?.ifEmpty { modes.toList() } ?: emptyList()

      val wantMaxHz = fpsLimit <= 0 || fpsLimit > 60
      val bestMode = if (wantMaxHz) {
        if (fpsLimit > 60 && fpsLimit < 240) {
          sameResModes.minByOrNull { kotlin.math.abs(it.refreshRate - fpsLimit.toFloat()) }
            ?: sameResModes.maxByOrNull { it.refreshRate }
        } else {
          sameResModes.maxByOrNull { it.refreshRate }
        }
      } else {
        null
      }

      val curAttrs = window.attributes
      val targetModeId = if (bestMode != null && bestMode.refreshRate > 60f) bestMode.modeId else curAttrs.preferredDisplayModeId
      val targetRefreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        when {
          fpsLimit <= 0 || fpsLimit >= 240 -> bestMode?.refreshRate ?: 165f
          fpsLimit > 60 -> fpsLimit.toFloat()
          else -> 0f
        }
      } else {
        0f
      }
      val needsModeChange = curAttrs.preferredDisplayModeId != targetModeId ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && kotlin.math.abs(curAttrs.preferredRefreshRate - targetRefreshRate) > 0.5f)
      if (needsModeChange) {
        window.attributes = curAttrs.apply {
          preferredDisplayModeId = targetModeId
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            preferredRefreshRate = targetRefreshRate
          }
        }
      }
    } catch (e: Exception) {
      Log.d(TAG, "Display mode refresh policy skipped: ${e.message}")
    }
  }

  /**
   * Android 15+ (API 35 - 37) Adaptive Refresh Rate (ARR) & Touch Boost APIs.
   */
  private fun applyAndroid15ArrBypass(window: Window, fpsLimit: Int) {
    if (Build.VERSION.SDK_INT < 35) return
    val highFps = fpsLimit <= 0 || fpsLimit > 60
    runCatching {
      val balanceMethod = Window::class.java.getMethod(
        "setFrameRatePowerSavingsBalanced",
        Boolean::class.javaPrimitiveType
      )
      balanceMethod.invoke(window, !highFps && cachedSystemGameMode == 3)
    }
    runCatching {
      val touchBoostMethod = Window::class.java.getMethod(
        "setFrameRateBoostOnTouchEnabled",
        Boolean::class.javaPrimitiveType
      )
      touchBoostMethod.invoke(window, true)
    }
  }

  fun applyWebViewFrameRate(webView: WebView?, fpsLimit: Int) {
    if (webView == null || Build.VERSION.SDK_INT < 35) return
    val requestedRate = when {
      fpsLimit <= 0 || fpsLimit > 60 -> VIEW_REQUESTED_FRAME_RATE_CATEGORY_HIGH
      else -> fpsLimit.toFloat()
    }
    runCatching {
      val m = View::class.java.getMethod(
        "setRequestedFrameRate",
        Float::class.javaPrimitiveType
      )
      m.invoke(webView, requestedRate)
    }
  }

  private fun querySystemGameMode(context: Context): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      runCatching {
        val gm = context.getSystemService(GameManager::class.java)
        if (gm != null) {
          cachedSystemGameMode = gm.gameMode
        }
      }
    }
    return cachedSystemGameMode
  }

  fun getSystemGameMode(): Int = cachedSystemGameMode

  fun getThermalLevel(): Int = cachedThermalLevel

  private fun registerThermalListener(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || thermalListener != null) return
    runCatching {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
      val listener = PowerManager.OnThermalStatusChangedListener { status ->
        cachedThermalLevel = when {
          status >= PowerManager.THERMAL_STATUS_SEVERE -> 2
          status >= PowerManager.THERMAL_STATUS_MODERATE -> 1
          else -> 0
        }
      }
      pm.addThermalStatusListener(listener)
      thermalListener = listener
      cachedThermalLevel = when {
        pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> 2
        pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 1
        else -> 0
      }
    }
  }

  private fun pollThermalState() {
    val ctx = appContextRef?.get() ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    try {
      val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
      val status = pm.currentThermalStatus
      if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
        cachedThermalLevel = 2
        return
      } else if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
        cachedThermalLevel = 1
        return
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && thermalHeadroomSupported != false) {
        val headroom = pm.getThermalHeadroom(30)
        if (thermalHeadroomSupported == null) {
          thermalHeadroomSupported = !headroom.isNaN() && headroom > 0f
        }
        if (thermalHeadroomSupported == true && !headroom.isNaN()) {
          cachedThermalLevel = when {
            headroom >= 0.96f -> 2
            headroom >= 0.85f -> 1
            else -> 0
          }
        }
      }
    } catch (_: Exception) {}
  }

  fun updateGameState(context: Context, isPlaying: Boolean, isLoading: Boolean = false) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val gameManager = context.getSystemService(GameManager::class.java) ?: return
        val mode = if (isPlaying) {
          GameState.MODE_GAMEPLAY_INTERRUPTIBLE
        } else {
          GameState.MODE_CONTENT
        }
        gameManager.setGameState(GameState(isLoading, mode))
      }
    } catch (e: Exception) {
      Log.d(TAG, "GameManager.setGameState skipped: ${e.message}")
    }
  }

  fun startFrameHintLoop() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    if (frameLoopActive) return
    try {
      appContextRef?.get()?.let { ctx ->
        refreshHintSessionThreads(ctx, GamePreferences(ctx).fpsLimit)
      }
      frameLoopActive = true
      lastChoreographerStartNs = 0L
      Choreographer.getInstance().postFrameCallback(adpfFrameCallback)
    } catch (_: Exception) {
      frameLoopActive = false
    }
  }

  fun stopFrameHintLoop() {
    frameLoopActive = false
    lastChoreographerStartNs = 0L
    try {
      Choreographer.getInstance().removeFrameCallback(adpfFrameCallback)
    } catch (_: Exception) {}
  }

  private fun targetNsForFps(fps: Int): Long {
    val effective = when {
      fps <= 0 || fps >= 240 -> 144
      else -> fps.coerceIn(GamePreferences.MIN_FPS, 240)
    }
    return (1_000_000_000L / effective).coerceIn(4_000_000L, 50_000_000L)
  }

  fun updateTargetFps(fps: Int) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hintSession?.updateTargetWorkDuration(targetNsForFps(fps))
      }
    } catch (_: Exception) {}
  }

  fun reportJsFrameWorkNs(workNs: Long) {
    lastJsWorkReportMs = SystemClock.elapsedRealtime()
    val clamped = workNs.coerceIn(400_000L, 45_000_000L)
    smoothedJsWorkNs = (smoothedJsWorkNs * 3L + clamped) / 4L
    reportActualWorkDuration(smoothedJsWorkNs)
  }

  fun reportActualWorkDuration(actualDurationNs: Long) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hintSession?.reportActualWorkDuration(actualDurationNs)
      }
    } catch (_: Exception) {}
  }

  fun prepareFeastGameLaunch(activity: Activity?, fpsLimit: Int) {
    activity ?: return
    updateGameState(activity, isPlaying = true, isLoading = true)
    refreshHintSessionThreads(activity, fpsLimit)
    if (GamePreferences(activity).feGameXEngineMode == GamePreferences.ENGINE_GPU) {
      startFrameHintLoop()
    }
  }

  /**
   * Switches WebView between FeGameX CPU (--disable-gpu-rasterization)
   * and GPU (--ignore-gpu-blocklist + hardware/Vulkan pipeline) using safe LAYER_TYPE_NONE
   * window-compositing so AndroidView never blanks canvas or video surfaces.
   */
  @Suppress("DEPRECATION")
  fun applyFeGameXMode(activity: Activity?, webView: WebView?, rawMode: String) {
    val mode = GamePreferences.normalizeEngineMode(rawMode)
    applyChromiumCommandLineFlags(mode)
    val isCpu = mode == GamePreferences.ENGINE_CPU

    if (isCpu) {
      stopFrameHintLoop()
    } else {
      startFrameHintLoop()
    }

    webView?.let { wv ->
      val fps = GamePreferences(wv.context).fpsLimit
      wv.post {
        try {
          if (wv.layerType != View.LAYER_TYPE_NONE) {
            wv.setLayerType(View.LAYER_TYPE_NONE, null)
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
          }
          wv.settings.apply {
            setRenderPriority(
              if (isCpu) WebSettings.RenderPriority.NORMAL else WebSettings.RenderPriority.HIGH
            )
            offscreenPreRaster = !isCpu
          }
          applyWebViewFrameRate(wv, fps)
          refreshHintSessionThreads(wv.context, fps)
          wv.invalidate()
        } catch (e: Exception) {
          Log.w(TAG, "applyFeGameXMode error: ${e.message}")
        }
      }
    }

    activity?.let { act ->
      updateGameState(act, isPlaying = false, isLoading = false)
    }
  }

  @Suppress("DEPRECATION")
  fun optimizeWebView(webView: WebView, engineMode: String = GamePreferences.ENGINE_GPU) {
    val mode = GamePreferences.normalizeEngineMode(engineMode)
    applyChromiumCommandLineFlags(mode)
    val isCpu = mode == GamePreferences.ENGINE_CPU
    val fps = GamePreferences(webView.context).fpsLimit

    try {
      webView.setLayerType(View.LAYER_TYPE_NONE, null)
      webView.isScrollbarFadingEnabled = true
      webView.isVerticalScrollBarEnabled = false
      webView.isHorizontalScrollBarEnabled = false
      webView.isHorizontalFadingEdgeEnabled = false
      webView.isVerticalFadingEdgeEnabled = false
      webView.isNestedScrollingEnabled = false
      webView.overScrollMode = View.OVER_SCROLL_NEVER

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
      }

      applyWebViewFrameRate(webView, fps)

      webView.settings.apply {
        setRenderPriority(
          if (isCpu) WebSettings.RenderPriority.NORMAL else WebSettings.RenderPriority.HIGH
        )
        offscreenPreRaster = !isCpu
        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        setGeolocationEnabled(false)
        setNeedInitialFocus(false)
        domStorageEnabled = true
        databaseEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        allowFileAccess = true
        allowContentAccess = true
        loadsImagesAutomatically = true
        blockNetworkImage = false
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
      }

      try {
        CookieManager.getInstance().setAcceptCookie(true)
      } catch (_: Exception) {}
    } catch (e: Exception) {
      Log.w(TAG, "WebView hardware optimization error: ${e.message}")
    }
  }

  fun closeSession() {
    stopFrameHintLoop()
    try {
      val ctx = appContextRef?.get()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ctx != null && thermalListener != null) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let {
          pm?.removeThermalStatusListener(it)
        }
        thermalListener = null
      }
    } catch (_: Exception) {}
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hintSession?.close()
        hintSession = null
        hintThreadIds = intArrayOf()
      }
    } catch (_: Exception) {}
  }
}
