package com.example.game

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ApkShareHelper {

  private const val APK_NAME = "CosmicBattle.apk"

  /**
   * Shares the installed APK file via Android share sheet (Android 8.1 - 17 compatible).
   */
  fun shareApk(context: Context) {
    try {
      val sourceApk = File(context.applicationInfo.sourceDir)
      if (!sourceApk.exists()) {
        Toast.makeText(context, "Не удалось найти APK файл приложения", Toast.LENGTH_SHORT).show()
        return
      }

      val cacheDir = File(context.cacheDir, "apk_share")
      if (!cacheDir.exists()) cacheDir.mkdirs()

      val targetFile = File(cacheDir, APK_NAME)
      FileInputStream(sourceApk).use { input ->
        FileOutputStream(targetFile).use { output ->
          input.copyTo(output)
        }
      }

      val authority = "${context.packageName}.fileprovider"
      val uri = FileProvider.getUriForFile(context, authority, targetFile)

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Cosmic Battle APK")
        putExtra(Intent.EXTRA_TEXT, "Установочный APK космического шутера Cosmic Battle!")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      context.startActivity(
        Intent.createChooser(shareIntent, "Поделиться игрой Cosmic Battle (APK)").apply {
          if (context !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      )
    } catch (e: Exception) {
      Toast.makeText(context, "Ошибка отправки APK: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
  }

  /**
   * Saves the APK file directly to Downloads on Android 10+ (MediaStore) or legacy Downloads/app storage on Android 8.1-9.
   */
  fun saveApkToDownloads(context: Context) {
    try {
      val sourceApk = File(context.applicationInfo.sourceDir)
      if (!sourceApk.exists()) {
        Toast.makeText(context, "Не удалось найти исходный APK", Toast.LENGTH_SHORT).show()
        return
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, APK_NAME)
          put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
          put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
          resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(sourceApk).use { input ->
              input.copyTo(output)
            }
          }
          Toast.makeText(context, "APK сохранён в папку 'Загрузки' (Download/$APK_NAME)", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(context, "Не удалось сохранить в Загрузки", Toast.LENGTH_SHORT).show()
        }
      } else {
        // Android 8.1 / 9: try public Downloads first, gracefully fall back to app external Downloads if needed
        val destFile = try {
          @Suppress("DEPRECATION")
          val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
          if (!publicDownloads.exists()) publicDownloads.mkdirs()
          val candidate = File(publicDownloads, APK_NAME)
          FileInputStream(sourceApk).use { input ->
            FileOutputStream(candidate).use { output ->
              input.copyTo(output)
            }
          }
          candidate
        } catch (_: SecurityException) {
          val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
          if (!appDownloads.exists()) appDownloads.mkdirs()
          val fallbackFile = File(appDownloads, APK_NAME)
          FileInputStream(sourceApk).use { input ->
            FileOutputStream(fallbackFile).use { output ->
              input.copyTo(output)
            }
          }
          fallbackFile
        }
        Toast.makeText(context, "APK сохранён: ${destFile.name}", Toast.LENGTH_LONG).show()
      }
    } catch (e: Exception) {
      Toast.makeText(context, "Ошибка сохранения APK: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
  }
}
