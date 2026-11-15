package com.example.game

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

class AndroidBridge(private val context: Context) {

  private val vibrator: Vibrator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager =
        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vibratorManager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  @JavascriptInterface
  fun getAppVersion(): String = CURRENT_VERSION

  @JavascriptInterface
  fun isHardwareAccelerated(): Boolean =
    GamePreferences(context).feGameXEngineMode == GamePreferences.ENGINE_GPU

  @JavascriptInterface
  fun getFeGameXEngineMode(): String = GamePreferences(context).feGameXEngineMode

  @JavascriptInterface
  fun getWebViewFlagsJson(): String {
    val mode = GamePreferences(context).feGameXEngineMode
    val flags = GamePerformanceOptimizer.getActiveWebViewFlags(mode)
    return JSONObject().apply {
      put("engineMode", mode)
      put("disableGpuRasterization", flags.disableGpuRasterization)
      put("ignoreGpuBlocklist", flags.ignoreGpuBlocklist)
      put("vulkanPipelineActive", flags.vulkanPreferred && mode == GamePreferences.ENGINE_GPU)
      put("androidApiLevel", Build.VERSION.SDK_INT)
      put("androidRelease", Build.VERSION.RELEASE ?: "8.1+")
    }.toString()
  }

  @JavascriptInterface
  fun getGraphicsEngineInfoJson(): String {
    return try {
      val info = GraphicsEngineDetector.getGraphicsEngineInfo(context)
      val mode = GamePreferences(context).feGameXEngineMode
      val flags = GamePerformanceOptimizer.getActiveWebViewFlags(mode)
      JSONObject().apply {
        put("bestEngine", info.bestEngineName)
        put("engineType", info.engineType)
        put("vulkanSupported", info.vulkanSupported)
        put("vulkanVersion", info.vulkanVersion ?: "N/A")
        put("openGlVersion", info.openGlVersion)
        put("gpuRenderer", info.gpuRenderer)
        put("gpuVendor", info.gpuVendor)
        put("socVendor", info.socProfile.socVendor)
        put("gpuFamily", info.socProfile.gpuFamily)
        put("gpuTier", info.socProfile.gpuTier)
        put("recommendedMaxDpr", info.socProfile.recommendedMaxDpr.toDouble())
        put("recommendedParticleBudget", info.socProfile.recommendedParticleBudget)
        put("recommendedStarCount", info.socProfile.recommendedStarCount)
        put("lowOverdrawMode", info.socProfile.lowOverdrawMode)
        put("thermalLevel", GamePerformanceOptimizer.getThermalLevel())
        put("systemGameMode", GamePerformanceOptimizer.getSystemGameMode())
        put("isHardwareAccelerated", mode == GamePreferences.ENGINE_GPU)
        put("feGameXMode", mode)
        put("disableGpuRasterization", flags.disableGpuRasterization)
        put("ignoreGpuBlocklist", flags.ignoreGpuBlocklist)
        put("recommendationSummary", info.recommendationSummary)
        put("technicalDetails", info.technicalDetails)
        put("appVersion", CURRENT_VERSION)
        put("androidApiLevel", Build.VERSION.SDK_INT)
      }.toString()
    } catch (_: Exception) {
      "{ \"bestEngine\": \"Hardware GPU Pipeline\", \"appVersion\": \"$CURRENT_VERSION\", \"isHardwareAccelerated\": true, \"feGameXMode\": \"gpu\", \"gpuFamily\": \"GENERIC\", \"gpuTier\": \"HIGH\", \"recommendedMaxDpr\": 1.4, \"recommendedParticleBudget\": 200, \"recommendedStarCount\": 85, \"lowOverdrawMode\": false }"
    }
  }

  @JavascriptInterface
  fun getThermalLevel(): Int = GamePerformanceOptimizer.getThermalLevel()

  @JavascriptInterface
  fun reportFrameWorkNs(workNs: Long) {
    GamePerformanceOptimizer.reportJsFrameWorkNs(workNs)
  }

  @JavascriptInterface
  fun getLocalIpAddress(): String {
    return try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val intf = interfaces.nextElement()
        val addrs = intf.inetAddresses
        while (addrs.hasMoreElements()) {
          val addr = addrs.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            return addr.hostAddress ?: "127.0.0.1"
          }
        }
      }
      "127.0.0.1"
    } catch (_: Exception) {
      "127.0.0.1"
    }
  }

  @JavascriptInterface
  fun getDeviceLoadInfo(): String {
    return try {
      val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      val memInfo = ActivityManager.MemoryInfo()
      actManager?.getMemoryInfo(memInfo)
      val totalMb = (memInfo.totalMem / (1024 * 1024)).coerceAtLeast(1)
      val availMb = memInfo.availMem / (1024 * 1024)
      val usedPercent = (((totalMb - availMb).toFloat() / totalMb) * 100).toInt().coerceIn(0, 100)
      val cores = Runtime.getRuntime().availableProcessors()
      JSONObject().apply {
        put("totalRamMb", totalMb)
        put("availRamMb", availMb)
        put("availMemMb", availMb)
        put("ramUsedPercent", usedPercent)
        put("cpuCores", cores)
        put("cores", cores)
        put("lowMemory", memInfo.lowMemory)
      }.toString()
    } catch (_: Exception) {
      "{ \"ramUsedPercent\": 45, \"cpuCores\": 8, \"cores\": 8, \"totalRamMb\": 4096, \"availRamMb\": 2048, \"availMemMb\": 2048 }"
    }
  }

  private var onOpenSettingsRequested: (() -> Unit)? = null
  private var onFpsLimitChanged: ((Int) -> Unit)? = null
  private var onLowGraphicsChanged: ((Boolean) -> Unit)? = null
  private var onImmersiveChanged: ((Boolean) -> Unit)? = null
  private var onKeepScreenOnChanged: ((Boolean) -> Unit)? = null
  private var onNotificationsChanged: ((Boolean) -> Unit)? = null
  private var onShareApkRequested: (() -> Unit)? = null
  private var onDownloadApkRequested: (() -> Unit)? = null
  private var onRenderEngineChanged: ((String) -> Unit)? = null
  private var onGameplayStateChanged: ((Boolean) -> Unit)? = null
  private var onFeastOptimizationChanged: ((Boolean) -> Unit)? = null

  fun setOnOpenSettingsListener(listener: () -> Unit) {
    onOpenSettingsRequested = listener
  }

  fun setOnFpsLimitListener(listener: (Int) -> Unit) {
    onFpsLimitChanged = listener
  }

  fun setOnLowGraphicsListener(listener: (Boolean) -> Unit) {
    onLowGraphicsChanged = listener
  }

  fun setOnImmersiveListener(listener: (Boolean) -> Unit) {
    onImmersiveChanged = listener
  }

  fun setOnKeepScreenOnListener(listener: (Boolean) -> Unit) {
    onKeepScreenOnChanged = listener
  }

  fun setOnNotificationsListener(listener: (Boolean) -> Unit) {
    onNotificationsChanged = listener
  }

  fun setOnShareApkListener(listener: () -> Unit) {
    onShareApkRequested = listener
  }

  fun setOnDownloadApkListener(listener: () -> Unit) {
    onDownloadApkRequested = listener
  }

  fun setOnRenderEngineListener(listener: (String) -> Unit) {
    onRenderEngineChanged = listener
  }

  fun setOnGameplayStateListener(listener: (Boolean) -> Unit) {
    onGameplayStateChanged = listener
  }

  fun setOnFeastOptimizationListener(listener: (Boolean) -> Unit) {
    onFeastOptimizationChanged = listener
  }

  @JavascriptInterface
  fun setRenderEngine(mode: String) {
    onRenderEngineChanged?.invoke(GamePreferences.normalizeEngineMode(mode))
  }

  @JavascriptInterface
  fun setGameplayActive(isActive: Boolean) {
    onGameplayStateChanged?.invoke(isActive)
  }

  @JavascriptInterface
  fun startFeastOptimization() {
    onFeastOptimizationChanged?.invoke(true)
  }

  @JavascriptInterface
  fun finishFeastOptimization() {
    onFeastOptimizationChanged?.invoke(false)
  }

  @JavascriptInterface
  fun openNativeSettings() {
    onOpenSettingsRequested?.invoke()
  }

  @JavascriptInterface
  fun setFpsLimit(fps: Int) {
    onFpsLimitChanged?.invoke(fps)
  }

  @JavascriptInterface
  fun setLowGraphics(enabled: Boolean) {
    onLowGraphicsChanged?.invoke(enabled)
  }

  @JavascriptInterface
  fun setImmersive(enabled: Boolean) {
    onImmersiveChanged?.invoke(enabled)
  }

  @JavascriptInterface
  fun setImmersiveMode(enabled: Boolean) {
    onImmersiveChanged?.invoke(enabled)
  }

  @JavascriptInterface
  fun setKeepScreenOn(enabled: Boolean) {
    onKeepScreenOnChanged?.invoke(enabled)
  }

  @JavascriptInterface
  fun setNotificationsEnabled(enabled: Boolean) {
    onNotificationsChanged?.invoke(enabled)
  }

  @JavascriptInterface
  fun setNotifications(enabled: Boolean) {
    onNotificationsChanged?.invoke(enabled)
  }

  @JavascriptInterface
  fun shareApk() {
    onShareApkRequested?.invoke()
  }

  @JavascriptInterface
  fun downloadApk() {
    onDownloadApkRequested?.invoke()
  }

  @JavascriptInterface
  fun downloadApkToDownloads() {
    onDownloadApkRequested?.invoke()
  }

  @JavascriptInterface
  fun vibrate(type: String?) {
    val vib = vibrator ?: return
    if (!vib.hasVibrator()) return

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = when (type) {
          "heavy", "error" -> VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
          "medium" -> VibrationEffect.createOneShot(25, (VibrationEffect.DEFAULT_AMPLITUDE * 0.7f).toInt().coerceIn(1, 255))
          "success" -> VibrationEffect.createWaveform(longArrayOf(0, 15, 40, 20), -1)
          else -> VibrationEffect.createOneShot(12, (VibrationEffect.DEFAULT_AMPLITUDE * 0.4f).toInt().coerceIn(1, 255))
        }
        vib.vibrate(effect)
      } else {
        @Suppress("DEPRECATION")
        val duration = when (type) {
          "heavy", "error" -> 45L
          "medium" -> 25L
          else -> 12L
        }
        @Suppress("DEPRECATION")
        vib.vibrate(duration)
      }
    } catch (_: Exception) {}
  }

  companion object {
    const val CURRENT_VERSION = "1.0.5"
  }
}
