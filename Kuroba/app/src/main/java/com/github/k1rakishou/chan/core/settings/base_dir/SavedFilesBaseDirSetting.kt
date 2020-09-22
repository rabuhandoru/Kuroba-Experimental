package com.github.k1rakishou.chan.core.settings.base_dir

import android.net.Uri
import android.os.Environment
import com.github.k1rakishou.SettingProvider
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.core.settings.IntegerSetting
import com.github.k1rakishou.chan.core.settings.StringSetting
import com.github.k1rakishou.chan.utils.AndroidUtils.getApplicationLabel
import com.github.k1rakishou.chan.utils.AndroidUtils.postToEventBus
import java.io.File

class SavedFilesBaseDirSetting(
  override var activeBaseDir: IntegerSetting,
  override val fileApiBaseDir: StringSetting,
  override val safBaseDir: StringSetting
) : BaseDirectorySetting() {

  constructor(settingProvider: SettingProvider) : this(
    IntegerSetting(
      settingProvider,
      "saved_files_active_base_dir_ordinal",
      0
    ),
    StringSetting(
      settingProvider,
      "preference_image_save_location",
      getDefaultSaveLocationDir()
    ),
    StringSetting(
      settingProvider,
      "preference_image_save_location_uri",
      ""
    )
  )

  init {
    fileApiBaseDir.addCallback { _, _ ->
      postToEventBus(ChanSettings.SettingChanged(fileApiBaseDir))
    }

    safBaseDir.addCallback { _, _ ->
      postToEventBus(ChanSettings.SettingChanged(safBaseDir))
    }
  }

  override fun setFileBaseDir(dir: String) {
    fileApiBaseDir.setSyncNoCheck(dir)
    activeBaseDir.setSync(ActiveBaseDir.FileBaseDir.ordinal)
  }

  override fun setSafBaseDir(dir: Uri) {
    safBaseDir.setSyncNoCheck(dir.toString())
    activeBaseDir.setSync(ActiveBaseDir.SAFBaseDir.ordinal)
  }

  override fun resetFileDir() {
    fileApiBaseDir.setSyncNoCheck(getDefaultSaveLocationDir())
  }

  override fun resetSafDir() {
    safBaseDir.setSyncNoCheck("")
  }

  override fun resetActiveDir() {
    activeBaseDir.setSync(ActiveBaseDir.FileBaseDir.ordinal)
  }

  companion object {
    private const val FILES_DIR = "files"

    fun getDefaultSaveLocationDir(): String {
      return (Environment.getExternalStorageDirectory().toString()
        + File.separator
        + getApplicationLabel()
        + File.separator
        + FILES_DIR)
    }
  }
}