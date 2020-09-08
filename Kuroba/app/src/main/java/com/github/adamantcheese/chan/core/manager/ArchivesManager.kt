package com.github.adamantcheese.chan.core.manager

import android.content.Context
import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.SuspendableInitializer
import com.github.adamantcheese.model.data.archive.ArchiveType
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ArchivesManager(
  private val appContext: Context,
  private val applicationScope: CoroutineScope,
  private val gson: Gson,
  private val appConstants: AppConstants,
  private val verboseLogsEnabled: Boolean
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("ArchivesManager")

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val allArchivesData = mutableListOf<ArchiveData>()
  @GuardedBy("lock")
  private val allArchiveDescriptors = mutableListOf<ArchiveDescriptor>()

  private val archiveDescriptorIdMap = hashMapOf<String, Long>().apply {
    put("archive.4plebs.org", 1)
    put("archive.nyafuu.org", 2)
    put("archive.rebeccablacktech.com", 3)
    put("warosu.org", 4)
    put("desuarchive.org", 5)
    put("boards.fireden.net", 6)
    put("arch.b4k.co", 7)
    put("archive.b-stats.org", 8)
    put("archived.moe", 9)
    put("thebarchive.com", 10)
    put("archiveofsins.com", 11)
  }

  fun initialize() {
    applicationScope.launch(Dispatchers.Default) {
      initArchivesManager()
    }
  }

  private fun initArchivesManager() {
    val result = Try {
      val allArchives = loadArchives()

      val archiveDescriptors = allArchives.map { archive ->
        require(archiveDescriptorIdMap.isNotEmpty()) { "archiveDescriptorIdMap is empty" }

        val archiveId = archiveDescriptorIdMap[archive.domain]
          ?: throw NullPointerException("Couldn't find archiveId for archive with domain: ${archive.domain}")

        return@map ArchiveDescriptor(
          archiveId,
          archive.name,
          archive.domain,
          ArchiveType.byDomain(archive.domain)
        )
      }

      archiveDescriptors.forEach { descriptor ->
        for (archive in allArchives) {
          if (descriptor.domain == archive.domain) {
            archive.setArchiveDescriptor(descriptor)
            archive.setSupportedSites(setOf(Chan4.SITE_NAME))
            break
          }
        }
      }

      lock.write {
        allArchivesData.clear()
        allArchivesData.addAll(allArchives)

        allArchiveDescriptors.clear()
        allArchiveDescriptors.addAll(archiveDescriptors)
      }

      return@Try Unit
    }

    suspendableInitializer.initWithModularResult(result)
  }

  suspend fun getAllArchiveData(): List<ArchiveData> {
    suspendableInitializer.awaitUntilInitialized()

    return lock.read { allArchivesData.toList() }
  }

  suspend fun getAllArchivesDescriptors(): List<ArchiveDescriptor> {
    suspendableInitializer.awaitUntilInitialized()

    return lock.read { allArchiveDescriptors.toList() }
  }

  fun getRequestLinkForThread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    archiveDescriptor: ArchiveDescriptor
  ): String? {
    val archiveData = getArchiveDataByArchiveDescriptor(archiveDescriptor)
      ?: return null

    return String.format(
      Locale.ENGLISH,
      FOOLFUUKA_THREAD_ENDPOINT_FORMAT,
      archiveData.domain,
      threadDescriptor.boardCode(),
      threadDescriptor.threadNo
    )
  }

  private fun getArchiveDataByArchiveDescriptor(archiveDescriptor: ArchiveDescriptor): ArchiveData? {
    return lock.read {
      return@read allArchivesData.firstOrNull { archiveData ->
        return@firstOrNull archiveData.name == archiveDescriptor.name
          && archiveData.domain == archiveDescriptor.domain
      }
    }
  }

  private fun loadArchives(): List<ArchiveData> {
    return appContext.assets.open(ARCHIVES_JSON_FILE_NAME).use { inputStream ->
      return@use gson.fromJson<Array<ArchiveData>>(
        JsonReader(InputStreamReader(inputStream)),
        Array<ArchiveData>::class.java
      ).toList()
    }
  }

  fun supports(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    return supports(threadDescriptor.boardDescriptor)
  }

  fun supports(boardDescriptor: BoardDescriptor): Boolean {
    return lock.read {
      return@read allArchivesData.any { archiveData ->
        return@any archiveData.supports(boardDescriptor)
      }
    }
  }

  fun getSupportedArchiveDescriptors(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ArchiveDescriptor> {
    return lock.read {
      return@read allArchivesData
        .filter { archiveData -> archiveData.supports(threadDescriptor.boardDescriptor) }
        .map { archiveData -> archiveData.getArchiveDescriptor() }
    }
  }

  fun byBoardDescriptor(boardDescriptor: BoardDescriptor): ArchiveDescriptor? {
    return lock.read {
      return@read allArchivesData
        .firstOrNull { archiveData -> archiveData.supports(boardDescriptor) }
        ?.getArchiveDescriptor()
    }
  }

  fun isSiteArchive(siteDescriptor: SiteDescriptor): Boolean {
    return lock.read {
      return@read allArchiveDescriptors.any { archiveDescriptor -> archiveDescriptor.domain == siteDescriptor.siteName }
    }
  }

  data class ArchiveData(
    @Expose(serialize = false, deserialize = false)
    private var archiveDescriptor: ArchiveDescriptor? = null,
    @Expose(serialize = false, deserialize = false)
    private var supportedSites: Set<String>,
    @SerializedName("name")
    val name: String,
    @SerializedName("domain")
    val domain: String,
    @SerializedName("boards")
    val supportedBoards: Set<String>,
    @SerializedName("files")
    val supportedFiles: Set<String>
  ) {
    fun isEnabled(): Boolean = domain !in disabledArchives

    fun setSupportedSites(sites: Set<String>) {
      supportedSites = sites
    }

    fun setArchiveDescriptor(archiveDescriptor: ArchiveDescriptor) {
      require(this.archiveDescriptor == null) { "Double initialization!" }

      this.archiveDescriptor = archiveDescriptor
    }

    fun getArchiveDescriptor(): ArchiveDescriptor {
      return requireNotNull(archiveDescriptor) {
        "Attempt to access archiveDescriptor before ArchiveData was fully initialized"
      }
    }

    fun supports(boardDescriptor: BoardDescriptor): Boolean {
      val suitableSite = (boardDescriptor.siteDescriptor.siteName == domain
        || boardDescriptor.siteDescriptor.siteName in supportedSites)

      return suitableSite && boardDescriptor.boardCode in supportedBoards
    }
  }

  companion object {
    // These archives are disabled for now
    private val disabledArchives = setOf(
      // Disabled because it's weird as hell. I can't even say whether it's working or not.
      "archive.b-stats.org",

      // Disabled because it requires Cloudflare authentication which is not supported for
      // now.
      "warosu.org",

      // Disable because it always returns 403 when sending requests via the OkHttpClient,
      // but works normally when opening in the browser. Apparently some kind of
      // authentication is required.
      "thebarchive.com"
    )

    private const val TAG = "ArchivesManager"
    private const val ARCHIVES_JSON_FILE_NAME = "archives.json"
    private const val FOOLFUUKA_THREAD_ENDPOINT_FORMAT = "https://%s/_/api/chan/thread/?board=%s&num=%d"
    private const val FOOLFUUKA_POST_ENDPOINT_FORMAT = "https://%s/_/api/chan/post/?board=%s&num=%d"
  }
}