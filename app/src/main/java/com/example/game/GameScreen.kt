package com.example.game

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CosmicDark
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GlowGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StarSilver
import com.example.ui.theme.StarWhite
import kotlinx.coroutines.delay

private const val GAME_URL = "file:///android_asset/game/index.html"
private const val TAG = "CosmicBattle"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CosmicBattleScreen(
  onExitApp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val lifecycleOwner = LocalLifecycleOwner.current
  val gamePreferences = remember { GamePreferences(context) }

  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var reloadTrigger by remember { mutableIntStateOf(0) }
  var isLoading by remember { mutableStateOf(true) }
  var loadingProgress by remember { mutableIntStateOf(0) }
  var hasError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var showExitDialog by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showClearDataDialog by remember { mutableStateOf(false) }

  var currentFps by remember { mutableIntStateOf(gamePreferences.fpsLimit) }
  var isKeepScreenOn by remember { mutableStateOf(gamePreferences.isKeepScreenOn) }
  var isImmersive by remember { mutableStateOf(gamePreferences.isImmersive) }
  var isNotificationsEnabled by remember { mutableStateOf(gamePreferences.isNotificationsEnabled) }
  var isLowGraphics by remember { mutableStateOf(gamePreferences.isLowGraphics) }
  var feGameXEngineMode by remember { mutableStateOf(gamePreferences.feGameXEngineMode) }
  var isFeastOptimizing by remember { mutableStateOf(false) }

  // Custom Fullscreen View support (WebChromeClient)
  var customView by remember { mutableStateOf<View?>(null) }
  var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

  // Safety timeout for FeastOptimizingService overlay in case WebView navigates/reloads mid-launch
  LaunchedEffect(isFeastOptimizing) {
    if (isFeastOptimizing) {
      GamePerformanceOptimizer.prepareFeastGameLaunch(activity, currentFps)
      delay(4800)
      isFeastOptimizing = false
      GamePerformanceOptimizer.updateGameState(context, isPlaying = true, isLoading = false)
    }
  }

  // Notification permission launcher for Android 13+ (API 33+)
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      Toast.makeText(context, "Уведомления включены!", Toast.LENGTH_SHORT).show()
      NotificationHelper.sendBattleNotification(
        context,
        context.getString(R.string.test_notification_title),
        context.getString(R.string.test_notification_body)
      )
    } else {
      Toast.makeText(context, "Разрешение на уведомления отклонено", Toast.LENGTH_SHORT).show()
      isNotificationsEnabled = false
      gamePreferences.isNotificationsEnabled = false
    }
  }

  // Loading splash dismiss + safety unstick watchdog (only triggers if video genuinely failed/stalled)
  LaunchedEffect(reloadTrigger) {
    NotificationHelper.createNotificationChannel(context)
    delay(220)
    isLoading = false

    delay(4500)
    webViewInstance?.evaluateJavascript(
      """
      (function() {
        try {
          if (typeof window.__finishIntroIfStalled === 'function') {
            window.__finishIntroIfStalled();
          }
        } catch(e) {}
      })();
      """.trimIndent(),
      null
    )
  }

  // Android 8.1 - 17 WebView lifecycle & background audio hardening
  DisposableEffect(lifecycleOwner, webViewInstance) {
    val observer = LifecycleEventObserver { _, event ->
      val wv = webViewInstance ?: return@LifecycleEventObserver
      when (event) {
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
          wv.onPause()
          wv.evaluateJavascript(
            "(function(){try{if(typeof window.__onAndroidAppPause==='function')window.__onAndroidAppPause();}catch(e){}})();",
            null
          )
        }
        Lifecycle.Event.ON_RESUME -> {
          wv.onResume()
          GamePerformanceOptimizer.applyFeGameXMode(
            activity,
            wv,
            gamePreferences.feGameXEngineMode
          )
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // Handle keep screen on
  DisposableEffect(isKeepScreenOn) {
    if (isKeepScreenOn) {
      activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    onDispose {
      activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  // Handle Fullscreen Immersive Mode
  LaunchedEffect(isImmersive) {
    activity?.let { act ->
      val window = act.window
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      if (isImmersive) {
        insetsController.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
      } else {
        insetsController.show(WindowInsetsCompat.Type.systemBars())
      }
    }
  }

  // Apply FPS changes dynamically
  LaunchedEffect(currentFps, webViewInstance) {
    if (webViewInstance != null) {
      gamePreferences.fpsLimit = currentFps
      gamePreferences.applyFpsSettings(activity, webViewInstance)
    }
  }

  // Handle Android Back Navigation (API 27 - API 37 BackHandler / Predictive Back)
  BackHandler(enabled = true) {
    val wv = webViewInstance
    if (customView != null) {
      customViewCallback?.onCustomViewHidden()
    } else if (wv != null) {
      wv.evaluateJavascript(
        """
        (function() {
          if (typeof window.handleAndroidBack === 'function') {
            return window.handleAndroidBack();
          }
          return false;
        })();
        """.trimIndent()
      ) { result ->
        if (result != "true") {
          showExitDialog = true
        }
      }
    } else {
      showExitDialog = true
    }
  }

  // Ensure cookies flushed on disposal
  DisposableEffect(Unit) {
    onDispose {
      CookieManager.getInstance().flush()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(CosmicDark)
  ) {
    if (customView != null) {
      AndroidView(
        factory = {
          FrameLayout(it).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(customView)
          }
        },
        modifier = Modifier.fillMaxSize()
      )
    } else {
      androidx.compose.runtime.key(reloadTrigger) {
        AndroidView(
          modifier = Modifier
            .fillMaxSize()
            .testTag("game_webview"),
          factory = { ctx ->
            // Synchronize Chromium command-line flags BEFORE creating WebView
            val initialEngineMode = gamePreferences.feGameXEngineMode
            GamePerformanceOptimizer.applyChromiumCommandLineFlags(initialEngineMode)

            WebView(ctx).apply {
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )

              setBackgroundColor(android.graphics.Color.parseColor("#030308"))
              GamePerformanceOptimizer.optimizeWebView(this, initialEngineMode)
              isFocusable = true
              isFocusableInTouchMode = true
              requestFocus()

              val cookieManager = CookieManager.getInstance()
              cookieManager.setAcceptCookie(true)
              cookieManager.setAcceptThirdPartyCookies(this, true)

              val gameWebView = this
              val bridge = AndroidBridge(ctx).apply {
                setOnOpenSettingsListener {
                  (ctx as? Activity)?.runOnUiThread {
                    showSettingsDialog = true
                  }
                }
                setOnFpsLimitListener { fps ->
                  (ctx as? Activity)?.runOnUiThread {
                    currentFps = fps
                    gamePreferences.fpsLimit = fps
                    gamePreferences.applyFpsSettings(activity, gameWebView)
                  }
                }
                setOnLowGraphicsListener { enabled ->
                  (ctx as? Activity)?.runOnUiThread {
                    isLowGraphics = enabled
                    gamePreferences.isLowGraphics = enabled
                  }
                }
                setOnImmersiveListener { enabled ->
                  (ctx as? Activity)?.runOnUiThread {
                    isImmersive = enabled
                    gamePreferences.isImmersive = enabled
                  }
                }
                setOnKeepScreenOnListener { enabled ->
                  (ctx as? Activity)?.runOnUiThread {
                    isKeepScreenOn = enabled
                    gamePreferences.isKeepScreenOn = enabled
                  }
                }
                setOnNotificationsListener { enabled ->
                  (ctx as? Activity)?.runOnUiThread {
                    isNotificationsEnabled = enabled
                    gamePreferences.isNotificationsEnabled = enabled
                  }
                }
                setOnShareApkListener {
                  (ctx as? Activity)?.runOnUiThread {
                    ApkShareHelper.shareApk(ctx)
                  }
                }
                setOnDownloadApkListener {
                  (ctx as? Activity)?.runOnUiThread {
                    ApkShareHelper.saveApkToDownloads(ctx)
                  }
                }
                setOnRenderEngineListener { mode ->
                  val normalized = GamePreferences.normalizeEngineMode(mode)
                  (ctx as? Activity)?.runOnUiThread {
                    feGameXEngineMode = normalized
                    gamePreferences.feGameXEngineMode = normalized
                    GamePerformanceOptimizer.applyFeGameXMode(activity, gameWebView, normalized)
                  }
                }
                setOnGameplayStateListener { isPlaying ->
                  GamePerformanceOptimizer.updateGameState(ctx, isPlaying, isLoading = false)
                }
                setOnFeastOptimizationListener { active ->
                  (ctx as? Activity)?.runOnUiThread {
                    isFeastOptimizing = active
                    if (active) {
                      GamePerformanceOptimizer.prepareFeastGameLaunch(activity, currentFps)
                    } else {
                      GamePerformanceOptimizer.updateGameState(ctx, isPlaying = true, isLoading = false)
                    }
                  }
                }
              }
              addJavascriptInterface(bridge, "AndroidBridge")

              @Suppress("DEPRECATION")
              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = false
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true
                blockNetworkImage = false

                // Prevent OS Dark Mode from inverting game canvas on Android 10 - Android 17
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  isAlgorithmicDarkeningAllowed = false
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                  forceDark = WebSettings.FORCE_DARK_OFF
                }

                textZoom = 100
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  safeBrowsingEnabled = false
                }

                val defaultUa = userAgentString
                if (defaultUa.contains("; wv")) {
                  userAgentString = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
                }
              }

              webChromeClient = object : WebChromeClient() {
                // Prevent ugly gray play icon flash before intro.mp4 renders first frame
                override fun getDefaultVideoPoster(): Bitmap {
                  return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.BLACK)
                  }
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                  loadingProgress = newProgress
                  if (newProgress >= 90) {
                    isLoading = false
                  }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                  consoleMessage?.let {
                    Log.d(TAG, "[WebView] ${it.message()} (line ${it.lineNumber()})")
                  }
                  return true
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                  customView = view
                  customViewCallback = callback
                }

                override fun onHideCustomView() {
                  customView = null
                  customViewCallback = null
                }

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                  result?.confirm()
                  return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                  result?.confirm()
                  return true
                }

                override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                  result?.confirm(defaultValue ?: "")
                  return true
                }
              }

              webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  super.onPageStarted(view, url, favicon)
                  hasError = false
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                  super.onPageCommitVisible(view, url)
                  isLoading = false
                  injectPageEnhancements(view, activity, gamePreferences)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  isLoading = false
                  CookieManager.getInstance().flush()
                  activity?.let { act ->
                    GamePerformanceOptimizer.refreshHintSessionThreads(act, gamePreferences.fpsLimit)
                  }
                  GamePerformanceOptimizer.applyWebViewFrameRate(view, gamePreferences.fpsLimit)
                  injectPageEnhancements(view, activity, gamePreferences)
                }

                override fun onRenderProcessGone(
                  view: WebView?,
                  detail: RenderProcessGoneDetail?
                ): Boolean {
                  Log.w(TAG, "WebView RenderProcessGone! Did crash: ${detail?.didCrash()}")
                  view?.destroy()
                  webViewInstance = null
                  reloadTrigger++
                  return true
                }

                override fun onReceivedError(
                  view: WebView?,
                  request: WebResourceRequest?,
                  error: WebResourceError?
                ) {
                  super.onReceivedError(view, request, error)
                  if (request?.isForMainFrame == true) {
                    val code = error?.errorCode ?: 0
                    if (code != ERROR_FILE_NOT_FOUND) {
                      hasError = true
                      isLoading = false
                      errorMessage = error?.description?.toString() ?: "Load Error"
                    }
                  }
                }

                override fun shouldOverrideUrlLoading(
                  view: WebView?,
                  request: WebResourceRequest?
                ): Boolean {
                  return false
                }
              }

              loadUrl(GAME_URL)
              webViewInstance = this
            }
          },
          update = { webView ->
            webViewInstance = webView
          }
        )
      }
    }

    if (isLoading && !hasError) {
      LinearProgressIndicator(
        progress = { (loadingProgress.coerceAtLeast(10)) / 100f },
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp)
          .align(Alignment.TopCenter),
        color = NeonCyan,
        trackColor = CosmicDark.copy(alpha = 0.5f)
      )
    }

    AnimatedVisibility(
      visible = isLoading && !hasError,
      enter = fadeIn(),
      exit = fadeOut(animationSpec = tween(durationMillis = 250)),
      modifier = Modifier.fillMaxSize()
    ) {
      CosmicLoadingOverlay(progress = loadingProgress)
    }

    AnimatedVisibility(
      visible = isFeastOptimizing && !hasError,
      enter = fadeIn(animationSpec = tween(durationMillis = 180)),
      exit = fadeOut(animationSpec = tween(durationMillis = 260)),
      modifier = Modifier.fillMaxSize()
    ) {
      FeastOptimizingOverlay(engineMode = feGameXEngineMode)
    }

    if (hasError) {
      CosmicErrorOverlay(
        errorMessage = errorMessage,
        onRetry = {
          hasError = false
          isLoading = true
          reloadTrigger++
        }
      )
    }

    if (showSettingsDialog) {
      CosmicSettingsDialog(
        currentFps = currentFps,
        feGameXEngineMode = feGameXEngineMode,
        isImmersive = isImmersive,
        isKeepScreenOn = isKeepScreenOn,
        isNotificationsEnabled = isNotificationsEnabled,
        isLowGraphics = isLowGraphics,
        onSelectEngineMode = { mode ->
          val normalized = GamePreferences.normalizeEngineMode(mode)
          feGameXEngineMode = normalized
          gamePreferences.feGameXEngineMode = normalized
          GamePerformanceOptimizer.applyFeGameXMode(activity, webViewInstance, normalized)
          gamePreferences.syncFeGameXEngineToWebView(webViewInstance)
          val msg = if (normalized == GamePreferences.ENGINE_CPU) {
            "FeGameX CPU: disable-gpu-rasterization включён"
          } else {
            "FeGameX GPU: ignore-gpu-blocklist + Vulkan включён"
          }
          Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        },
        onSelectFps = { fps ->
          currentFps = fps
          gamePreferences.fpsLimit = fps
          gamePreferences.applyFpsSettings(activity, webViewInstance)
          val toastMsg = if (fps == 0) "FPS: MAX (без ограничений)" else "FPS лимит: $fps FPS"
          Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        },
        onToggleImmersive = {
          val newVal = !isImmersive
          isImmersive = newVal
          gamePreferences.isImmersive = newVal
        },
        onToggleKeepScreenOn = {
          val newVal = !isKeepScreenOn
          isKeepScreenOn = newVal
          gamePreferences.isKeepScreenOn = newVal
        },
        onToggleNotifications = {
          val newVal = !isNotificationsEnabled
          isNotificationsEnabled = newVal
          gamePreferences.isNotificationsEnabled = newVal
          if (newVal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
              ) != PackageManager.PERMISSION_GRANTED
            ) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
              Toast.makeText(context, "Уведомления активны", Toast.LENGTH_SHORT).show()
            }
          }
        },
        onSendTestNotification = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
          ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            val sent = NotificationHelper.sendBattleNotification(
              context,
              context.getString(R.string.test_notification_title),
              context.getString(R.string.test_notification_body)
            )
            if (sent) {
              Toast.makeText(context, "Уведомление отправлено!", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(context, "Проверьте разрешения на уведомления", Toast.LENGTH_SHORT).show()
            }
          }
        },
        onToggleLowGraphics = {
          val newVal = !isLowGraphics
          isLowGraphics = newVal
          gamePreferences.isLowGraphics = newVal
          gamePreferences.applyLowGraphicsSettings(webViewInstance)
          Toast.makeText(
            context,
            if (newVal) "Низкая графика включена (Blur отключен)" else "Низкая графика выключена",
            Toast.LENGTH_SHORT
          ).show()
        },
        onReload = {
          showSettingsDialog = false
          hasError = false
          isLoading = true
          reloadTrigger++
        },
        onClearData = {
          showSettingsDialog = false
          showClearDataDialog = true
        },
        onDismiss = { showSettingsDialog = false }
      )
    }

    if (showExitDialog) {
      AlertDialog(
        onDismissRequest = { showExitDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.RocketLaunch,
              contentDescription = null,
              tint = NeonCyan,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.exit_title),
              color = StarWhite,
              fontWeight = FontWeight.Bold
            )
          }
        },
        text = {
          Text(
            text = stringResource(R.string.exit_message),
            color = StarSilver
          )
        },
        containerColor = CosmicSurface,
        confirmButton = {
          Button(
            onClick = {
              showExitDialog = false
              onExitApp()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = AlertRed,
              contentColor = StarWhite
            ),
            modifier = Modifier
              .defaultMinSize(minHeight = 48.dp)
              .testTag("confirm_exit_button")
          ) {
            Text(stringResource(R.string.exit_confirm))
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showExitDialog = false },
            modifier = Modifier
              .defaultMinSize(minHeight = 48.dp)
              .testTag("cancel_exit_button")
          ) {
            Text(stringResource(R.string.cancel), color = NeonCyan)
          }
        }
      )
    }

    if (showClearDataDialog) {
      AlertDialog(
        onDismissRequest = { showClearDataDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = AlertRed,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Очистить данные игры?",
              color = StarWhite,
              fontWeight = FontWeight.Bold
            )
          }
        },
        text = {
          Text(
            text = "Это удалит все локальные сохранения, очки и кэшированные файлы игры в WebView.",
            color = StarSilver
          )
        },
        containerColor = CosmicSurface,
        confirmButton = {
          Button(
            onClick = {
              showClearDataDialog = false
              webViewInstance?.let { wv ->
                wv.clearCache(true)
                wv.clearFormData()
                wv.clearHistory()
                android.webkit.WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                reloadTrigger++
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = AlertRed,
              contentColor = StarWhite
            ),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
          ) {
            Text("Очистить и перезапустить")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showClearDataDialog = false },
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
          ) {
            Text("Отмена", color = NeonCyan)
          }
        }
      )
    }
  }
}

private fun injectPageEnhancements(
  view: WebView?,
  activity: Activity?,
  prefs: GamePreferences
) {
  if (view == null) return

  val engineMode = prefs.feGameXEngineMode
  val script = """
    (function() {
      try {
        window.__cosmicFeGameXMode = '$engineMode';
        if (window.Telegram && window.Telegram.WebApp) {
          var twa = window.Telegram.WebApp;
          if (!twa.requestFullscreen) twa.requestFullscreen = function() {};
          if (!twa.exitFullscreen) twa.exitFullscreen = function() {};
        }
      } catch(e) {
        console.warn('[CosmicBattle] Injection error: ' + e);
      }
    })();
  """.trimIndent()

  view.evaluateJavascript(script, null)
  prefs.syncFeGameXEngineToWebView(view)
  prefs.applyFpsSettings(activity, view)
  prefs.applyLowGraphicsSettings(view)
}

@Composable
private fun CosmicLoadingOverlay(
  progress: Int
) {
  val infiniteTransition = rememberInfiniteTransition(label = "rocket_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.06f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.25f,
    targetValue = 0.75f,
    animationSpec = infiniteRepeatable(
      animation = tween(1100, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(CosmicDark, Color(0xFF0C1126), CosmicDark)
        )
      ),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(24.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(92.dp)
          .scale(pulseScale)
      ) {
        Box(
          modifier = Modifier
            .size(82.dp)
            .clip(CircleShape)
            .background(NeonCyan.copy(alpha = glowAlpha * 0.35f))
        )
        Surface(
          modifier = Modifier.size(68.dp),
          shape = CircleShape,
          color = CosmicSurfaceVariant,
          border = BorderStroke(2.dp, NeonCyan)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.RocketLaunch,
              contentDescription = "Cosmic Battle Loading",
              tint = NeonCyan,
              modifier = Modifier.size(34.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "COSMIC BATTLE",
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.5.sp,
        color = StarWhite
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "SYNTEXHYPERSOFT ENGINE",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = NeonPurple
      )

      Spacer(modifier = Modifier.height(14.dp))

      CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        color = NeonCyan,
        strokeWidth = 3.dp
      )

      if (progress > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "$progress%",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = NeonCyan
        )
      }
    }
  }
}

@Composable
private fun FeastOptimizingOverlay(
  engineMode: String
) {
  val infiniteTransition = rememberInfiniteTransition(label = "feast_optimizing")

  // Discord-style update icon spin: smooth eased 360° flip with crisp pause settle
  val iconRotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1350
        0f at 0 using CubicBezierEasing(0.68f, -0.20f, 0.32f, 1.20f)
        360f at 980
        360f at 1350
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "discord_icon_spin"
  )

  val iconPulse by infiniteTransition.animateFloat(
    initialValue = 0.96f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(675, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "discord_icon_pulse"
  )

  val textShimmerOffset by infiniteTransition.animateFloat(
    initialValue = -220f,
    targetValue = 900f,
    animationSpec = infiniteRepeatable(
      animation = tween(1500, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "feast_text_shimmer"
  )

  val textBreathAlpha by infiniteTransition.animateFloat(
    initialValue = 0.72f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(750, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "feast_text_alpha"
  )

  // Bottom bar: line flies left -> right in 1000ms, pauses 1000ms, then repeats
  val barSweepProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 2000
        0f at 0 using FastOutSlowInEasing
        1f at 1000
        1f at 2000
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "feast_bar_sweep"
  )

  val interactionSource = remember { MutableInteractionSource() }
  val isGpuMode = GamePreferences.normalizeEngineMode(engineMode) != GamePreferences.ENGINE_CPU

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(Color(0xFF060814), Color(0xFF0E1330), Color(0xFF050712))
        )
      )
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {}
      )
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(horizontal = 24.dp, vertical = 28.dp)
      .testTag("feast_optimizing_overlay")
  ) {
    // Upper/Center-top: Spinning app icon (Discord checking-for-updates feel)
    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(bottom = 56.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(132.dp)
      ) {
        Box(
          modifier = Modifier
            .size(126.dp)
            .scale(iconPulse)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                colors = listOf(
                  NeonCyan.copy(alpha = 0.38f),
                  NeonPurple.copy(alpha = 0.16f),
                  Color.Transparent
                )
              )
            )
        )

        Surface(
          modifier = Modifier
            .size(94.dp)
            .graphicsLayer {
              rotationZ = iconRotation
              scaleX = iconPulse
              scaleY = iconPulse
            },
          shape = CircleShape,
          color = Color(0xFF0B0D1B),
          border = BorderStroke(
            width = 2.5.dp,
            brush = Brush.linearGradient(colors = listOf(NeonCyan, NeonPurple))
          ),
          shadowElevation = 14.dp
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_cosmic_icon),
            contentDescription = "Иконка приложения",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .clip(CircleShape)
          )
        }
      }

      Spacer(modifier = Modifier.height(34.dp))

      // Middle: Animated text "Оптимизация игры с FeastOptimizingService..."
      Text(
        text = "Оптимизация игры с FeastOptimizingService...",
        textAlign = TextAlign.Center,
        style = TextStyle(
          brush = Brush.linearGradient(
            colors = listOf(
              StarWhite.copy(alpha = textBreathAlpha),
              NeonCyan,
              Color(0xFFD8B4FE),
              StarWhite.copy(alpha = textBreathAlpha)
            ),
            start = androidx.compose.ui.geometry.Offset(textShimmerOffset - 220f, 0f),
            end = androidx.compose.ui.geometry.Offset(textShimmerOffset + 220f, 80f)
          ),
          fontSize = 18.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.6.sp,
          lineHeight = 25.sp
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("feast_optimizing_text")
      )

      Spacer(modifier = Modifier.height(10.dp))

      Surface(
        shape = RoundedCornerShape(999.dp),
        color = CosmicSurfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.32f))
      ) {
        Text(
          text = if (isGpuMode) {
            "FeGameX GPU • Vulkan / Adreno / Mali Pipeline"
          } else {
            "FeGameX CPU • Software Rasterizer Pipeline"
          },
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = NeonCyan.copy(alpha = 0.9f),
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
      }
    }

    // Bottom: Animated progress bar (sweeps left->right in 1s, waits 1s, repeats)
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(bottom = 18.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .height(7.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(Color(0xFF161D3A))
          .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
      ) {
        val barWidth = (maxWidth * 0.36f).coerceAtLeast(72.dp)
        if (barSweepProgress < 0.995f) {
          val offsetX = -barWidth + (maxWidth + barWidth) * barSweepProgress
          Box(
            modifier = Modifier
              .offset(x = offsetX)
              .width(barWidth)
              .height(7.dp)
              .clip(RoundedCornerShape(999.dp))
              .background(
                Brush.horizontalGradient(
                  colors = listOf(
                    Color.Transparent,
                    NeonCyan,
                    Color.White,
                    NeonPurple,
                    Color.Transparent
                  )
                )
              )
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "Подготовка шейдеров, спрайтов и ADPF-потоков",
        fontSize = 11.sp,
        color = StarSilver.copy(alpha = 0.78f),
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
private fun CosmicErrorOverlay(
  errorMessage: String,
  onRetry: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(CosmicDark.copy(alpha = 0.95f))
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = CosmicSurface,
      border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Surface(
          shape = CircleShape,
          color = AlertRed.copy(alpha = 0.15f),
          modifier = Modifier.size(56.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.WifiOff,
              contentDescription = null,
              tint = AlertRed,
              modifier = Modifier.size(28.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = stringResource(R.string.connection_error_title),
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = StarWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = stringResource(R.string.connection_error_desc),
          fontSize = 13.sp,
          color = StarSilver,
          textAlign = TextAlign.Center
        )

        if (errorMessage.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = errorMessage,
            fontSize = 11.sp,
            color = AlertRed.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = onRetry,
          colors = ButtonDefaults.buttonColors(
            containerColor = NeonCyan,
            contentColor = CosmicDark
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("retry_connection_button")
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.retry),
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CosmicSettingsDialog(
  currentFps: Int,
  feGameXEngineMode: String,
  isImmersive: Boolean,
  isKeepScreenOn: Boolean,
  isNotificationsEnabled: Boolean,
  isLowGraphics: Boolean,
  onSelectEngineMode: (String) -> Unit,
  onSelectFps: (Int) -> Unit,
  onToggleImmersive: () -> Unit,
  onToggleKeepScreenOn: () -> Unit,
  onToggleNotifications: () -> Unit,
  onToggleLowGraphics: () -> Unit,
  onSendTestNotification: () -> Unit,
  onReload: () -> Unit,
  onClearData: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val graphicsInfo = remember { GraphicsEngineDetector.getGraphicsEngineInfo(context) }
  val activeFlags = remember(feGameXEngineMode) {
    GamePerformanceOptimizer.getActiveWebViewFlags(feGameXEngineMode)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = CircleShape,
          color = NeonCyan.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
          modifier = Modifier.size(38.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = null,
              tint = NeonCyan,
              modifier = Modifier.size(20.dp)
            )
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "Настройки Cosmic Battle",
            color = StarWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
          )
          Text(
            text = "v${AndroidBridge.CURRENT_VERSION} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            color = StarSilver,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // --- Hardware / Vulkan Card ---
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = CosmicSurfaceVariant.copy(alpha = 0.9f)
          ),
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
              ) {
                Icon(
                  imageVector = Icons.Default.RocketLaunch,
                  contentDescription = null,
                  tint = NeonCyan,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = "Графический адаптер",
                    color = StarSilver,
                    fontSize = 11.sp
                  )
                  Text(
                    text = graphicsInfo.bestEngineName,
                    color = GlowGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                  )
                }
              }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = NeonCyan.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
              ) {
                Text(
                  text = graphicsInfo.engineType,
                  color = NeonCyan,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
            }
            Text(
              text = if (activeFlags.disableGpuRasterization) {
                "Флаги WebView: --disable-gpu-rasterization (CPU Software)"
              } else {
                "Флаги WebView: --ignore-gpu-blocklist + Vulkan HW"
              },
              color = if (activeFlags.disableGpuRasterization) Color(0xFFFFD54F) else GlowGreen,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }

        // --- FeGameX Engine Mode Selector: ONLY CPU & GPU ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = null,
              tint = NeonCyan,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Движок FeGameX",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = StarWhite
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val isCpu = feGameXEngineMode == GamePreferences.ENGINE_CPU
            val isGpu = !isCpu

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isCpu) Color(0xFFFFB300).copy(alpha = 0.18f) else CosmicSurfaceVariant.copy(alpha = 0.6f),
              border = BorderStroke(
                width = if (isCpu) 1.5.dp else 1.dp,
                color = if (isCpu) Color(0xFFFFB300) else StarSilver.copy(alpha = 0.25f)
              ),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 64.dp)
                .clickable { onSelectEngineMode(GamePreferences.ENGINE_CPU) }
                .testTag("engine_mode_cpu")
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.Center
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (isCpu) Color(0xFFFFD54F) else StarSilver,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "CPU",
                    color = if (isCpu) Color(0xFFFFD54F) else StarWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                  )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                  text = "disable-gpu-rasterization",
                  color = StarSilver,
                  fontSize = 10.sp
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isGpu) GlowGreen.copy(alpha = 0.18f) else CosmicSurfaceVariant.copy(alpha = 0.6f),
              border = BorderStroke(
                width = if (isGpu) 1.5.dp else 1.dp,
                color = if (isGpu) GlowGreen else StarSilver.copy(alpha = 0.25f)
              ),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 64.dp)
                .clickable { onSelectEngineMode(GamePreferences.ENGINE_GPU) }
                .testTag("engine_mode_gpu")
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.Center
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.RocketLaunch,
                    contentDescription = null,
                    tint = if (isGpu) GlowGreen else StarSilver,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "GPU",
                    color = if (isGpu) GlowGreen else StarWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                  )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                  text = "ignore-gpu-blocklist + Vulkan",
                  color = StarSilver,
                  fontSize = 10.sp
                )
              }
            }
          }
        }

        HorizontalDivider(color = CosmicSurfaceVariant)

        // --- FPS Limit ---
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Лимит FPS",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = StarWhite
              )
            }
            Text(
              text = if (currentFps == 0) "MAX" else "$currentFps FPS",
              color = NeonCyan,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            GamePreferences.FPS_OPTIONS.forEach { fps ->
              val isSelected = fps == currentFps
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) NeonCyan else CosmicSurfaceVariant,
                border = BorderStroke(
                  1.dp,
                  if (isSelected) NeonCyan else StarSilver.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .clickable { onSelectFps(fps) }
                  .testTag("fps_chip_$fps")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = CosmicDark,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                  }
                  Text(
                    text = if (fps == 0) "MAX" else "$fps",
                    color = if (isSelected) CosmicDark else StarWhite,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(color = CosmicSurfaceVariant)

        // --- Low Graphics ---
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CosmicSurfaceVariant.copy(alpha = 0.5f))
            .clickable { onToggleLowGraphics() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = null,
              tint = GlowGreen,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Низкая графика",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = StarWhite
              )
              Text(
                text = "Отключает Blur и тени для максимального FPS",
                fontSize = 11.sp,
                color = StarSilver
              )
            }
          }
          Switch(
            checked = isLowGraphics,
            onCheckedChange = { onToggleLowGraphics() },
            colors = SwitchDefaults.colors(
              checkedThumbColor = StarWhite,
              checkedTrackColor = GlowGreen,
              uncheckedThumbColor = StarSilver,
              uncheckedTrackColor = CosmicDark
            )
          )
        }

        HorizontalDivider(color = CosmicSurfaceVariant)

        // --- Notifications ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = NeonPurple,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Column {
                Text(
                  text = "Боевые уведомления",
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 14.sp,
                  color = StarWhite
                )
                Text(
                  text = "Оповещения о событиях флота",
                  fontSize = 11.sp,
                  color = StarSilver
                )
              }
            }
            Switch(
              checked = isNotificationsEnabled,
              onCheckedChange = { onToggleNotifications() },
              colors = SwitchDefaults.colors(
                checkedThumbColor = NeonCyan,
                checkedTrackColor = CosmicSurfaceVariant,
                uncheckedThumbColor = StarSilver,
                uncheckedTrackColor = CosmicDark
              )
            )
          }

          if (isNotificationsEnabled) {
            OutlinedButton(
              onClick = onSendTestNotification,
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("send_test_notification_button"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
              border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Проверить уведомление", fontSize = 12.sp)
            }
          }
        }

        HorizontalDivider(color = CosmicSurfaceVariant)

        // --- Screen & Immersive Toggles ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (isImmersive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Полноэкранный режим",
                fontSize = 13.sp,
                color = StarWhite
              )
            }
            Switch(
              checked = isImmersive,
              onCheckedChange = { onToggleImmersive() },
              colors = SwitchDefaults.colors(
                checkedThumbColor = NeonCyan,
                checkedTrackColor = CosmicSurfaceVariant
              )
            )
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.LockClock,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Экран всегда включен",
                fontSize = 13.sp,
                color = StarWhite
              )
            }
            Switch(
              checked = isKeepScreenOn,
              onCheckedChange = { onToggleKeepScreenOn() },
              colors = SwitchDefaults.colors(
                checkedThumbColor = NeonCyan,
                checkedTrackColor = CosmicSurfaceVariant
              )
            )
          }
        }

        HorizontalDivider(color = CosmicSurfaceVariant)

        // --- Export / Share APK ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Поделиться игрой / Экспорт APK",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = StarWhite
          )
          Text(
            text = "Отправьте установочный APK друзьям или скачайте в память устройства",
            fontSize = 11.sp,
            color = StarSilver
          )

          Button(
            onClick = { ApkShareHelper.shareApk(context) },
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("share_apk_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = NeonPurple,
              contentColor = StarWhite
            )
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Поделиться игрой (APK)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = { ApkShareHelper.saveApkToDownloads(context) },
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("download_apk_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
          ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Скачать APK в Загрузки", fontSize = 13.sp, fontWeight = FontWeight.Medium)
          }
        }

        HorizontalDivider(color = CosmicSurfaceVariant)

        // --- Maintenance Actions ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = onReload,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = CosmicSurfaceVariant,
              contentColor = NeonCyan
            )
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Перезагрузить игру", fontSize = 13.sp)
          }

          OutlinedButton(
            onClick = onClearData,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
          ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Очистить сохранения и кэш", fontSize = 13.sp)
          }
        }
      }
    },
    containerColor = CosmicSurface,
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
      ) {
        Text("Закрыть", color = NeonCyan, fontWeight = FontWeight.Bold)
      }
    }
  )
}
