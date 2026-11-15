package com.example.game

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.util.Log

data class SoCPerformanceProfile(
  val socVendor: String,
  val gpuFamily: String,
  val gpuTier: String,
  val recommendedMaxDpr: Float,
  val recommendedParticleBudget: Int,
  val recommendedStarCount: Int,
  val lowOverdrawMode: Boolean
)

data class GraphicsEngineInfo(
  val bestEngineName: String,
  val engineType: String,
  val vulkanSupported: Boolean,
  val vulkanVersion: String?,
  val vulkanLevel: Int,
  val openGlVersion: String,
  val gpuRenderer: String,
  val gpuVendor: String,
  val isHardwareAccelerated: Boolean,
  val recommendationSummary: String,
  val technicalDetails: String,
  val socProfile: SoCPerformanceProfile
)

object GraphicsEngineDetector {

  private const val TAG = "GraphicsDetector"

  @Volatile
  private var cachedInfo: GraphicsEngineInfo? = null

  fun getGraphicsEngineInfo(context: Context): GraphicsEngineInfo {
    cachedInfo?.let { return it }

    synchronized(this) {
      cachedInfo?.let { return it }

      val pm = context.packageManager
      var vulkanVersionStr: String? = null
      var vulkanSupported = false
      var vulkanLevel = 0

      // 1. Detect Vulkan Support
      try {
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)) {
          val features = pm.systemAvailableFeatures
          for (feature in features) {
            if (feature.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION) {
              val version = feature.version
              val major = version shr 22
              val minor = (version shr 12) and 0x3FF
              val patch = version and 0xFFF
              vulkanVersionStr = "Vulkan $major.$minor.$patch"
              vulkanSupported = true
              break
            }
          }
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) {
          val features = pm.systemAvailableFeatures
          for (feature in features) {
            if (feature.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) {
              vulkanLevel = feature.version
              break
            }
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to query Vulkan features: ${e.message}")
      }

      // 2. Detect OpenGL ES Version & RAM tier
      var openGlVersion = "OpenGL ES 3.0"
      var totalRamMb = 4096L
      var isLowRamDevice = false
      try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val configInfo = am?.deviceConfigurationInfo
        if (configInfo != null && !configInfo.glEsVersion.isNullOrEmpty()) {
          openGlVersion = "OpenGL ES ${configInfo.glEsVersion}"
        }
        if (am != null) {
          isLowRamDevice = am.isLowRamDevice
          val memInfo = ActivityManager.MemoryInfo()
          am.getMemoryInfo(memInfo)
          totalRamMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(512L)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to query ActivityManager GPU/RAM info: ${e.message}")
      }

      // 3. Query GPU Renderer & Vendor via offscreen EGL context
      val (gpuRenderer, gpuVendor) = queryGpuHardwareInfo()

      // 4. Classify SoC & GPU Architecture (Qualcomm Adreno / Dimensity Mali / Unisoc PowerVR / Google Tensor / Xclipse)
      val socProfile = classifySoCProfile(
        gpuRenderer = gpuRenderer,
        gpuVendor = gpuVendor,
        vulkanSupported = vulkanSupported,
        totalRamMb = totalRamMb,
        isLowRamDevice = isLowRamDevice
      )

      // 5. Determine the BEST Engine for the device
      val bestEngineName: String
      val engineType: String
      val recommendationSummary: String
      val technicalDetails: String

      if (vulkanSupported && vulkanVersionStr != null) {
        bestEngineName = "$vulkanVersionStr · ${socProfile.gpuFamily} (${socProfile.gpuTier})"
        engineType = "Vulkan"
        recommendationSummary = "Vulkan + ${socProfile.socVendor}: Профиль максимальной частоты кадров"
        technicalDetails = "GPU $gpuRenderer ($gpuVendor). Профиль ${socProfile.gpuFamily}/${socProfile.gpuTier}, DPR=${socProfile.recommendedMaxDpr}, бюджет частиц=${socProfile.recommendedParticleBudget}."
      } else if (openGlVersion.contains("3.2") || openGlVersion.contains("3.1")) {
        bestEngineName = "$openGlVersion · ${socProfile.gpuFamily} (${socProfile.gpuTier})"
        engineType = "OpenGL ES 3.2"
        recommendationSummary = "OpenGL ES 3.2 + ${socProfile.socVendor}: Аппаратный конвейер"
        technicalDetails = "GPU $gpuRenderer ($gpuVendor). Аппаратная растеризация и кэш спрайтов под ${socProfile.gpuFamily}."
      } else {
        bestEngineName = "$openGlVersion · ${socProfile.gpuFamily}"
        engineType = "OpenGL ES"
        recommendationSummary = "OpenGL ES: Аппаратное ускорение ${socProfile.socVendor}"
        technicalDetails = "GPU $gpuRenderer ($gpuVendor). Оптимизированный режим с пониженным overdraw."
      }

      val info = GraphicsEngineInfo(
        bestEngineName = bestEngineName,
        engineType = engineType,
        vulkanSupported = vulkanSupported,
        vulkanVersion = vulkanVersionStr,
        vulkanLevel = vulkanLevel,
        openGlVersion = openGlVersion,
        gpuRenderer = gpuRenderer,
        gpuVendor = gpuVendor,
        isHardwareAccelerated = true,
        recommendationSummary = recommendationSummary,
        technicalDetails = technicalDetails,
        socProfile = socProfile
      )

      cachedInfo = info
      Log.i(
        TAG,
        "Detected GPU=$gpuRenderer ($gpuVendor), Profile=${socProfile.socVendor}/${socProfile.gpuFamily}/${socProfile.gpuTier}, DPR=${socProfile.recommendedMaxDpr}"
      )
      return info
    }
  }

  private fun classifySoCProfile(
    gpuRenderer: String,
    gpuVendor: String,
    vulkanSupported: Boolean,
    totalRamMb: Long,
    isLowRamDevice: Boolean
  ): SoCPerformanceProfile {
    val r = gpuRenderer.lowercase()
    val v = gpuVendor.lowercase()
    val hw = Build.HARDWARE.lowercase()
    val board = Build.BOARD.lowercase()
    val brand = Build.BRAND.lowercase()
    val model = Build.MODEL.lowercase()
    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      "${Build.SOC_MODEL} ${Build.SOC_MANUFACTURER}".lowercase()
    } else {
      ""
    }
    val combined = "$r $v $hw $board $soc"
    val isPixelTensor = (brand.contains("google") || model.contains("pixel") || combined.contains("tensor") || combined.contains("gs101") || combined.contains("gs201") || combined.contains("zuma"))
    val isUnisoc = combined.contains("unisoc") || combined.contains("sprd") || combined.contains("ums") || combined.contains("sc98") || combined.contains("t6") || combined.contains("t7") || combined.contains("t8")
    val isMediaTek = combined.contains("mt") || combined.contains("dimensity") || combined.contains("helio") || combined.contains("mediatek")

    // Parse numeric GPU model when present (e.g., Adreno 740, Mali-G715, PowerVR GE8320)
    val modelNum = Regex("(\\d{3,4})").find(r)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    // 1. PowerVR / Imagination / Unisoc
    if (r.contains("powervr") || v.contains("imagination") || r.contains("rogue") || r.contains("img ") || (isUnisoc && !r.contains("mali"))) {
      // Modern PowerVR DXT / B-series / Tensor G5 IMG vs older Rogue GE8xxx / GM9xxx
      val isHighPowerVr = r.contains("dxt") || r.contains("cxt") || r.contains("bxs") || isPixelTensor
      val tier = if (isHighPowerVr && !isLowRamDevice) "HIGH" else if (modelNum >= 9000 && totalRamMb >= 3500) "MID" else "LOW"
      return SoCPerformanceProfile(
        socVendor = if (isPixelTensor) "GOOGLE_TENSOR" else "UNISOC_POWERVR",
        gpuFamily = "POWERVR",
        gpuTier = tier,
        recommendedMaxDpr = if (tier == "HIGH") 1.25f else 1.0f,
        recommendedParticleBudget = if (tier == "HIGH") 180 else 85,
        recommendedStarCount = if (tier == "HIGH") 75 else 50,
        lowOverdrawMode = true
      )
    }

    // 2. Qualcomm Adreno
    if (r.contains("adreno") || v.contains("qualcomm") || combined.contains("qcom") || combined.contains("snapdragon")) {
      val tier = when {
        isLowRamDevice || totalRamMb < 2800 -> "LOW"
        modelNum >= 730 || r.contains("adreno (tm) 8") || r.contains("adreno 8") -> "ULTRA"
        modelNum >= 640 || (vulkanSupported && totalRamMb >= 6000 && modelNum >= 600) -> "HIGH"
        modelNum in 500..639 || modelNum == 0 -> "MID"
        else -> "LOW"
      }
      return SoCPerformanceProfile(
        socVendor = "QUALCOMM",
        gpuFamily = "ADRENO",
        gpuTier = tier,
        recommendedMaxDpr = when (tier) {
          "ULTRA" -> 1.5f
          "HIGH" -> 1.5f
          "MID" -> 1.25f
          else -> 1.0f
        },
        recommendedParticleBudget = when (tier) {
          "ULTRA" -> 300
          "HIGH" -> 230
          "MID" -> 145
          else -> 85
        },
        recommendedStarCount = when (tier) {
          "ULTRA", "HIGH" -> 95
          "MID" -> 70
          else -> 50
        },
        lowOverdrawMode = tier == "LOW"
      )
    }

    // 3. Samsung Xclipse (RDNA)
    if (r.contains("xclipse") || r.contains("amd") || r.contains("samsung")) {
      val tier = if (isLowRamDevice) "MID" else "ULTRA"
      return SoCPerformanceProfile(
        socVendor = "SAMSUNG_EXYNOS",
        gpuFamily = "XCLIPSE",
        gpuTier = tier,
        recommendedMaxDpr = 1.5f,
        recommendedParticleBudget = 280,
        recommendedStarCount = 95,
        lowOverdrawMode = false
      )
    }

    // 4. ARM Mali / Immortalis (MediaTek Dimensity, Google Tensor, Exynos, Unisoc Mali-G57)
    if (r.contains("mali") || r.contains("immortalis") || v.contains("arm") || isMediaTek || isPixelTensor) {
      val isImmortalis = r.contains("immortalis")
      val tier = when {
        isLowRamDevice || totalRamMb < 2800 -> "LOW"
        isImmortalis || modelNum >= 715 || modelNum == 78 || modelNum == 77 -> "ULTRA"
        modelNum >= 610 || modelNum in 71..76 || modelNum == 68 -> "HIGH"
        modelNum in 52..69 -> "MID"
        else -> if (vulkanSupported && totalRamMb >= 4000) "MID" else "LOW"
      }
      val socVendor = when {
        isPixelTensor -> "GOOGLE_TENSOR"
        isUnisoc -> "UNISOC_MALI"
        isMediaTek -> "MEDIATEK_MALI"
        combined.contains("exynos") -> "SAMSUNG_EXYNOS"
        else -> "ARM_MALI"
      }
      return SoCPerformanceProfile(
        socVendor = socVendor,
        gpuFamily = "MALI",
        gpuTier = tier,
        recommendedMaxDpr = when (tier) {
          "ULTRA" -> 1.5f
          "HIGH" -> 1.35f
          "MID" -> 1.15f
          else -> 1.0f
        },
        recommendedParticleBudget = when (tier) {
          "ULTRA" -> 260
          "HIGH" -> 195
          "MID" -> 125
          else -> 75
        },
        recommendedStarCount = when (tier) {
          "ULTRA", "HIGH" -> 85
          "MID" -> 65
          else -> 45
        },
        // Mali & Tensor benefit strongly from trimmed alpha overdraw
        lowOverdrawMode = tier != "ULTRA"
      )
    }

    // 5. Generic / Unknown / Emulator fallback
    val genericTier = when {
      isLowRamDevice || totalRamMb < 2800 -> "LOW"
      vulkanSupported && totalRamMb >= 6000 -> "HIGH"
      else -> "MID"
    }
    return SoCPerformanceProfile(
      socVendor = "GENERIC",
      gpuFamily = "GENERIC",
      gpuTier = genericTier,
      recommendedMaxDpr = if (genericTier == "HIGH") 1.4f else if (genericTier == "MID") 1.15f else 1.0f,
      recommendedParticleBudget = if (genericTier == "HIGH") 210 else if (genericTier == "MID") 130 else 80,
      recommendedStarCount = if (genericTier == "HIGH") 85 else 60,
      lowOverdrawMode = genericTier == "LOW"
    )
  }

  /**
   * Safely probes the actual GPU Renderer and Vendor strings using a lightweight offscreen EGL context.
   */
  private fun queryGpuHardwareInfo(): Pair<String, String> {
    var renderer = "GPU Hardware Accelerated"
    var vendor = "Standard Mobile GPU"

    try {
      val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      if (display == EGL14.EGL_NO_DISPLAY) {
        return fallbackGpu()
      }

      val version = IntArray(2)
      if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
        return fallbackGpu()
      }

      val attribList = intArrayOf(
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
        EGL14.EGL_NONE
      )

      val configs = arrayOfNulls<EGLConfig>(1)
      val numConfigs = IntArray(1)
      EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)

      val config = configs[0]
      if (config != null) {
        val contextAttribs = intArrayOf(
          EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
          EGL14.EGL_NONE
        )
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val pbufferAttribs = intArrayOf(
          EGL14.EGL_WIDTH, 1,
          EGL14.EGL_HEIGHT, 1,
          EGL14.EGL_NONE
        )
        val surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)

        if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) {
          EGL14.eglMakeCurrent(display, surface, surface, context)

          val glRenderer = GLES20.glGetString(GLES20.GL_RENDERER)
          val glVendor = GLES20.glGetString(GLES20.GL_VENDOR)

          if (!glRenderer.isNullOrBlank()) renderer = glRenderer
          if (!glVendor.isNullOrBlank()) vendor = glVendor

          EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
          EGL14.eglDestroySurface(display, surface)
          EGL14.eglDestroyContext(display, context)
        }
      }
      EGL14.eglTerminate(display)
    } catch (e: Exception) {
      Log.w(TAG, "EGL probe failed, using fallback: ${e.message}")
      return fallbackGpu()
    }

    return Pair(renderer, vendor)
  }

  private fun fallbackGpu(): Pair<String, String> {
    val hardware = Build.HARDWARE
    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else hardware
    val vendor = when {
      hardware.contains("qcom", ignoreCase = true) || soc.contains("snapdragon", ignoreCase = true) -> "Qualcomm Adreno"
      hardware.contains("exynos", ignoreCase = true) || hardware.contains("mali", ignoreCase = true) -> "ARM Mali"
      hardware.contains("mt", ignoreCase = true) || soc.contains("dimensity", ignoreCase = true) -> "MediaTek / ARM Mali"
      hardware.contains("sprd", ignoreCase = true) || hardware.contains("ums", ignoreCase = true) -> "Unisoc / PowerVR"
      hardware.contains("kirin", ignoreCase = true) -> "HiSilicon / ARM Mali"
      else -> "Hardware Accelerated GPU"
    }
    return Pair(soc.ifBlank { "Mobile GPU" }, vendor)
  }
}
