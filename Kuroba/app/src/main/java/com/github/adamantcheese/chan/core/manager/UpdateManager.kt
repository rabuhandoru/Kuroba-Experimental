/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.manager

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.core.cache.FileCacheListener
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.net.update.DevUpdateApiRequest
import com.github.adamantcheese.chan.core.net.update.ReleaseUpdateApiRequest
import com.github.adamantcheese.chan.core.net.update.ReleaseUpdateApiRequest.ReleaseUpdateApiResponse
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.state.PersistableChanState
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils.runOnMainThread
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.fsaf.file.RawFile
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Calls the update API and downloads and requests installs of APK files.
 *
 * The APK files are downloaded to the public Download directory, and the default APK install
 * screen is launched after downloading.
 */
class UpdateManager(
  private val context: Context
) : CoroutineScope {

  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var fileManager: FileManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var okHttpClient: NetModule.ProxiedOkHttpClient

  private var updateDownloadDialog: ProgressDialog? = null
  private var cancelableDownload: CancelableDownload? = null

  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default + job + CoroutineName("UpdateManager")

  init {
    inject(this)
  }

  /**
   * Runs every time onCreate is called on the StartActivity.
   */
  fun autoUpdateCheck() {
    BackgroundUtils.ensureMainThread()

    if (
      PersistableChanState.previousVersion.get() < BuildConfig.VERSION_CODE
      && PersistableChanState.previousVersion.get() != 0
    ) {
      onReleaseAlreadyUpdated()
      // Don't process the updater because a dialog is now already showing.
      return
    }

    if (
      BuildConfig.DEV_BUILD &&
      PersistableChanState.previousDevHash.get() != BuildConfig.COMMIT_HASH
    ) {
      onDevAlreadyUpdated()
      return
    }

    launch { runUpdateApi(false) }
  }

  fun manualUpdateCheck() {
    launch { runUpdateApi(true) }
  }

  @Suppress("ConstantConditionIf")
  private suspend fun runUpdateApi(manual: Boolean) {
    BackgroundUtils.ensureBackgroundThread()

    if (PersistableChanState.hasNewApkUpdate.get()) {
      // If we noticed that there was an apk update on the previous check - show the
      // notification
      notifyNewApkUpdate()
    }

    if (!manual) {
      val lastUpdateTime = PersistableChanState.updateCheckTime.get()
      val interval = TimeUnit.DAYS.toMillis(BuildConfig.UPDATE_DELAY.toLong())
      val now = System.currentTimeMillis()
      val delta = lastUpdateTime + interval - now

      if (delta > 0) {
        return
      }

      PersistableChanState.updateCheckTime.set(now)
    }

    if (!BuildConfig.DEV_BUILD) {
      Logger.d(TAG, "Calling update API for release")
      updateRelease(manual)
    } else {
      Logger.d(TAG, "Calling update API for dev")
      updateDev(manual)
    }
  }

  @Suppress("ConstantConditionIf")
  private suspend fun updateDev(manual: Boolean) {
    BackgroundUtils.ensureBackgroundThread()

    val request = Request.Builder()
      .url(BuildConfig.DEV_API_ENDPOINT + "/latest_apk_uuid")
      .get()
      .build()

    val response = DevUpdateApiRequest(request, okHttpClient.proxiedClient).execute()

    coroutineScope {
      withContext(Dispatchers.Main) {
        when (response) {
          is JsonReaderRequest.JsonReaderResponse.Success -> {
            onSuccessfullyGotLatestApkUuid(response.result, manual)
          }
          is JsonReaderRequest.JsonReaderResponse.ServerError -> {
            Logger.e(TAG, "Error while trying to get new dev apk, status code: ${response.statusCode}")
            failedUpdate(manual)
          }
          is JsonReaderRequest.JsonReaderResponse.UnknownServerError -> {
            Logger.e(TAG, "Unknown error while trying to get new dev apk", response.error)
            failedUpdate(manual)
          }
          is JsonReaderRequest.JsonReaderResponse.ParsingError -> {
            Logger.e(TAG, "Parsing error while trying to get new dev apk", response.error)
            failedUpdate(manual)
          }
        }
      }
    }
  }

  private fun onSuccessfullyGotLatestApkUuid(
    response: DevUpdateApiRequest.DevUpdateApiResponse,
    manual: Boolean
  ) {
    BackgroundUtils.ensureMainThread()

    try {
      val versionCode = response.versionCode
      val commitHash = response.commitHash

      if (commitHash == BuildConfig.COMMIT_HASH) {
        // Same version and commit, no update needed
        if (manual && BackgroundUtils.isInForeground()) {
          AlertDialog.Builder(context)
            .setTitle(getString(R.string.update_none, getApplicationLabel()))
            .setPositiveButton(R.string.ok, null)
            .show()
        }

        cancelApkUpdateNotification()
        return
      }

      //new version or commit, update
      val versionCodeStringMatcher = VERSION_CODE_PATTERN.matcher(versionCode.toString())
      if (versionCodeStringMatcher.matches()) {
        val fauxResponse = ReleaseUpdateApiResponse()
        fauxResponse.versionCode = versionCode
        fauxResponse.versionCodeString = calculateVersionCodeString(
          versionCodeStringMatcher,
          commitHash
        )

        fauxResponse.apkURL = (
          BuildConfig.DEV_API_ENDPOINT + "/apk/" + versionCode + "_" + commitHash + ".apk"
          ).toHttpUrl()
        fauxResponse.body = SpannableStringBuilder.valueOf("New dev build; see commits!")

        processUpdateApiResponse(fauxResponse, manual)
      } else {
        throw Exception() // to reuse the failed code below
      }
    } catch (e: Exception) { // any exceptions just fail out
      failedUpdate(manual)
    }
  }

  @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  private fun calculateVersionCodeString(versionCodeStringMatcher: Matcher, commitHash: String): String {
    return ("v" + Integer.valueOf(versionCodeStringMatcher.group(1))
      + "." + Integer.valueOf(versionCodeStringMatcher.group(2))
      + "." + Integer.valueOf(versionCodeStringMatcher.group(3))
      + "-" + commitHash.substring(0, 7))
  }

  private suspend fun updateRelease(manual: Boolean) {
    BackgroundUtils.ensureBackgroundThread()

    val request = Request.Builder()
      .url(BuildConfig.UPDATE_API_ENDPOINT)
      .get()
      .build()

    val response = ReleaseUpdateApiRequest(
      request,
      okHttpClient.proxiedClient
    ).execute()

    coroutineScope {
      withContext(Dispatchers.Main) {
        when (response) {
          is JsonReaderRequest.JsonReaderResponse.Success -> {
            if (
              !processUpdateApiResponse(response.result, manual)
              && manual
              && BackgroundUtils.isInForeground()
            ) {
              AlertDialog.Builder(context)
                .setTitle(getString(R.string.update_none, getApplicationLabel()))
                .setPositiveButton(R.string.ok, null)
                .show()
            }
          }
          is JsonReaderRequest.JsonReaderResponse.ServerError -> {
            Logger.e(TAG, "Error while trying to get new release apk, status code: ${response.statusCode}")
            failedUpdate(manual)
          }
          is JsonReaderRequest.JsonReaderResponse.UnknownServerError -> {
            Logger.e(TAG, "Unknown error while trying to get new release apk", response.error)
            failedUpdate(manual)
          }
          is JsonReaderRequest.JsonReaderResponse.ParsingError -> {
            Logger.e(TAG, "Parsing error while trying to get new release apk", response.error)
            failedUpdate(manual)
          }
        }
      }
    }
  }


  private fun processUpdateApiResponse(
    responseRelease: ReleaseUpdateApiResponse,
    manual: Boolean
  ): Boolean {
    BackgroundUtils.ensureMainThread()

    if (
      (responseRelease.versionCode > BuildConfig.VERSION_CODE || BuildConfig.DEV_BUILD)
      && BackgroundUtils.isInForeground()
    ) {

      // Do not spam dialogs if this is not the manual update check, use the notifications
      // instead
      if (manual) {
        val concat = responseRelease.updateTitle.isNotEmpty()

        val updateMessage = if (concat) {
          TextUtils.concat(responseRelease.updateTitle, "; ", responseRelease.body)
        } else {
          responseRelease.body!!
        }

        val dialogTitle = getApplicationLabel().toString() + " " +
          responseRelease.versionCodeString + " available"

        val dialog = AlertDialog.Builder(context)
          .setTitle(dialogTitle)
          .setMessage(updateMessage)
          .setNegativeButton(R.string.update_later, null)
          .setPositiveButton(R.string.update_install) { _, _ -> updateInstallRequested(responseRelease) }
          .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
      }

      // There is an update, show the notification.
      //
      // (In case of the dev build we check whether the apk hashes differ or not beforehand,
      // so if they are the same this method won't even get called. In case of the release
      // build this method will be called in both cases so we do the check in this method)
      notifyNewApkUpdate()
      return true
    }

    cancelApkUpdateNotification()
    return false
  }

  private fun onDevAlreadyUpdated() {
    BackgroundUtils.ensureMainThread()

    // Show toast because dev updates may happen every day (to avoid alert dialog spam)
    showToast(
      context,
      getApplicationLabel().toString() + " was updated to the latest commit."
    )

    PersistableChanState.previousDevHash.set(BuildConfig.COMMIT_HASH)
    cancelApkUpdateNotification()
  }

  private fun onReleaseAlreadyUpdated() {
    BackgroundUtils.ensureMainThread()

    // Show dialog because release updates are infrequent so it's fine
    val text = Html.fromHtml(
      "<h3>" + getApplicationLabel() + " was updated to " + BuildConfig.VERSION_NAME + "</h3>"
    )

    val dialog = AlertDialog.Builder(context)
      .setMessage(text)
      .setPositiveButton(R.string.ok, null)
      .create()

    dialog.setCanceledOnTouchOutside(false)
    dialog.show()

    val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
    button.isEnabled = false

    runOnMainThread({
      dialog.setCanceledOnTouchOutside(true)
      button.isEnabled = true
    }, 1500)

    // Also set the new app version to not show this message again
    PersistableChanState.previousVersion.set(BuildConfig.VERSION_CODE)
    cancelApkUpdateNotification()
  }

  private fun notifyNewApkUpdate() {
    launch(Dispatchers.Main) {
      PersistableChanState.hasNewApkUpdate.set(true)
      settingsNotificationManager.notify(SettingNotificationType.ApkUpdate)
    }
  }

  private fun cancelApkUpdateNotification() {
    launch(Dispatchers.Main) {
      PersistableChanState.hasNewApkUpdate.set(false)
      settingsNotificationManager.cancel(SettingNotificationType.ApkUpdate)
    }
  }

  @Suppress("ConstantConditionIf")
  private fun failedUpdate(manual: Boolean) {
    BackgroundUtils.ensureMainThread()

    val buildTag = if (BuildConfig.DEV_BUILD) {
      "dev"
    } else {
      "stable"
    }

    Logger.e(TAG, "Failed to process $buildTag API call for updating")

    if (manual && BackgroundUtils.isInForeground()) {
      AlertDialog.Builder(context)
        .setTitle(R.string.update_check_failed)
        .setPositiveButton(R.string.ok, null)
        .show()
    }
  }

  /**
   * Install the APK file specified in `update`. This methods needs the storage permission.
   *
   * @param responseRelease that contains the APK file URL
   */
  private fun doUpdate(responseRelease: ReleaseUpdateApiResponse) {
    BackgroundUtils.ensureMainThread()

    cancelableDownload?.cancel()
    cancelableDownload = null

    cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(
      responseRelease.apkURL.toString(),
      object : FileCacheListener() {
        override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
          BackgroundUtils.ensureMainThread()

          if (updateDownloadDialog != null) {
            updateDownloadDialog!!.progress =
              (updateDownloadDialog!!.max * (downloaded / total.toDouble())).toInt()
          }
        }

        override fun onSuccess(file: RawFile) {
          BackgroundUtils.ensureMainThread()

          if (updateDownloadDialog != null) {
            updateDownloadDialog!!.setOnDismissListener(null)
            updateDownloadDialog!!.dismiss()
            updateDownloadDialog = null
          }

          val fileName = getApplicationLabel().toString() +
            "_" + responseRelease.versionCodeString + ".apk"

          suggestCopyingApkToAnotherDirectory(file, fileName) {
            runOnMainThread({
              // Install from the filecache rather than downloads, as the
              // Environment.DIRECTORY_DOWNLOADS may not be "Download"
              installApk(file)
            }, TimeUnit.SECONDS.toMillis(1))
          }
        }

        override fun onNotFound() {
          onFail(IOException("Not found"))
        }

        override fun onFail(exception: Exception) {
          if (!BackgroundUtils.isInForeground()) {
            return
          }

          BackgroundUtils.ensureMainThread()
          val description = getString(R.string.update_install_download_failed_description, exception.message)

          if (updateDownloadDialog != null) {
            updateDownloadDialog!!.setOnDismissListener(null)
            updateDownloadDialog!!.dismiss()
            updateDownloadDialog = null
          }

          AlertDialog.Builder(context).setTitle(R.string.update_install_download_failed)
            .setMessage(description)
            .setPositiveButton(R.string.ok, null)
            .show()
        }

        override fun onCancel() {
          if (!BackgroundUtils.isInForeground()) {
            return
          }

          BackgroundUtils.ensureMainThread()
          if (updateDownloadDialog != null) {
            updateDownloadDialog!!.setOnDismissListener(null)
            updateDownloadDialog!!.dismiss()
            updateDownloadDialog = null
          }

          AlertDialog.Builder(context)
            .setTitle(R.string.update_install_download_failed)
            .setPositiveButton(R.string.ok, null)
            .show()
        }
      })
  }

  private fun suggestCopyingApkToAnotherDirectory(
    file: RawFile,
    fileName: String,
    onDone: () -> Unit
  ) {
    if (!BackgroundUtils.isInForeground() || !ChanSettings.showCopyApkUpdateDialog.get()) {
      onDone.invoke()
      return
    }

    val alertDialog = AlertDialog.Builder(context)
      .setTitle(R.string.update_manager_copy_apk_title)
      .setMessage(R.string.update_manager_copy_apk_message)
      .setNegativeButton(R.string.no) { _, _ -> onDone.invoke() }
      .setPositiveButton(R.string.yes) { _, _ ->
        fileChooser.openCreateFileDialog(fileName, object : FileCreateCallback() {
          override fun onResult(uri: Uri) {
            onApkFilePathSelected(file, uri)
            onDone.invoke()
          }

          override fun onCancel(reason: String) {
            showToast(context, reason)
            onDone.invoke()
          }
        })
      }.create()

    alertDialog.show()
  }

  private fun onApkFilePathSelected(downloadedFile: RawFile, uri: Uri) {
    val newApkFile = fileManager.fromUri(uri)
    if (newApkFile == null) {
      val message = getString(R.string.update_manager_could_not_convert_uri, uri.toString())
      showToast(context, message)
      return
    }

    if (!fileManager.exists(downloadedFile)) {
      val message = getString(
        R.string.update_manager_input_file_does_not_exist,
        downloadedFile.getFullPath()
      )

      showToast(context, message)
      return
    }

    if (!fileManager.exists(newApkFile)) {
      val message = getString(
        R.string.update_manager_output_file_does_not_exist,
        newApkFile.toString()
      )

      showToast(context, message)
      return
    }

    if (!fileManager.copyFileContents(downloadedFile, newApkFile)) {
      val message = getString(R.string.update_manager_could_not_copy_apk,
        downloadedFile.getFullPath(),
        newApkFile.getFullPath()
      )

      showToast(context, message)
      return
    }

    showToast(context, R.string.update_manager_apk_copied)
  }

  private fun installApk(apk: RawFile) {
    BackgroundUtils.ensureMainThread()

    if (!BackgroundUtils.isInForeground()) {
      return
    }

    // First open the dialog that asks to retry and calls this method again.
    AlertDialog.Builder(context)
      .setTitle(R.string.update_retry_title)
      .setMessage(getString(R.string.update_retry, getApplicationLabel()))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.update_retry_button) { _, _ -> installApk(apk) }
      .show()

    // Then launch the APK install intent.
    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    val apkFile = File(apk.getFullPath())
    val apkURI = FileProvider.getUriForFile(context, getAppFileProvider(), apkFile)
    intent.setDataAndType(apkURI, "application/vnd.android.package-archive")

    // The installer wants a content scheme from android N and up,
    // but I don't feel like implementing a content provider just for this feature.
    // Temporary change the strictmode policy while starting the intent.
    val vmPolicy = StrictMode.getVmPolicy()
    StrictMode.setVmPolicy(VmPolicy.LAX)
    openIntent(intent)
    StrictMode.setVmPolicy(vmPolicy)
  }

  private fun updateInstallRequested(responseRelease: ReleaseUpdateApiResponse) {
    val runtimePermissionsHelper = (context as StartActivity).runtimePermissionsHelper

    runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
      if (granted) {
        updateDownloadDialog = ProgressDialog(context).apply {
          setCanceledOnTouchOutside(true)

          setOnDismissListener {_ ->
            showToast(context, "Download will continue in background.")
            updateDownloadDialog = null
          }

          setTitle(R.string.update_install_downloading)
          max = 10000
          setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
          setProgressNumberFormat("")

          show()
        }

        doUpdate(responseRelease)
        return@requestPermission
      }

      runtimePermissionsHelper.showPermissionRequiredDialog(
        context,
        getString(R.string.update_storage_permission_required_title),
        getString(R.string.update_storage_permission_required)
      ) {
        updateInstallRequested(responseRelease)
      }
    }
  }

  fun onDestroy() {
    cancelableDownload?.cancel()
    cancelableDownload = null
  }

  companion object {
    private const val TAG = "UpdateManager"

    private val VERSION_CODE_PATTERN = Pattern.compile("(\\d+)(\\d{2})(\\d{2})")
  }

}