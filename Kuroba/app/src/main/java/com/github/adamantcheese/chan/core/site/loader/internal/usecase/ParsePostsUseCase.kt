package com.github.adamantcheese.chan.core.site.loader.internal.usecase

import com.github.adamantcheese.chan.core.manager.*
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.site.parser.ChanReader
import com.github.adamantcheese.chan.core.site.parser.PostParseWorker
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.hashSetWithCap
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.filter.ChanFilter
import com.github.adamantcheese.model.repository.ChanPostRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class ParsePostsUseCase(
  private val verboseLogsEnabled: Boolean,
  private val dispatcher: CoroutineDispatcher,
  private val archivesManager: ArchivesManager,
  private val chanPostRepository: ChanPostRepository,
  private val filterEngine: FilterEngine,
  private val postFilterManager: PostFilterManager,
  private val savedReplyManager: SavedReplyManager,
  private val themeHelper: ThemeHelper,
  private val boardManager: BoardManager
) {

  suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    chanReader: ChanReader,
    postBuildersToParse: List<Post.Builder>,
    maxCount: Int
  ): List<Post> {
    BackgroundUtils.ensureBackgroundThread()

    chanPostRepository.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()

    if (verboseLogsEnabled) {
      Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
        "postsToParseSize=${postBuildersToParse.size}, " +
        "maxCount=$maxCount)")
    }

    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    val internalIds = postBuildersToParse
      .map { postBuilder -> postBuilder.id }
      .toMutableSet()

    val boardDescriptors = hashSetWithCap<BoardDescriptor>(256)

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      boardDescriptors.addAll(
        boardManager.getAllBoardDescriptorsForSite(chanDescriptor.siteDescriptor())
      )
    }

    val filters = loadFilters(chanDescriptor)

    return supervisorScope {
      return@supervisorScope postBuildersToParse
        .chunked(POSTS_PER_BATCH)
        .flatMap { postToParseChunk ->
          val deferred = postToParseChunk.map { postToParse ->
            return@map async(dispatcher) {
              return@async PostParseWorker(
                filterEngine,
                postFilterManager,
                savedReplyManager,
                themeHelper.theme,
                filters,
                postToParse,
                chanReader,
                internalIds,
                boardDescriptors
              ).parse()
            }
          }

          return@flatMap deferred.awaitAll().filterNotNull()
        }
    }
  }

  private fun loadFilters(chanDescriptor: ChanDescriptor): List<ChanFilter> {
    BackgroundUtils.ensureBackgroundThread()

    val board = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return emptyList()

    return filterEngine.enabledFilters
      .filter { filter -> filterEngine.matchesBoard(filter, board) }
  }

  companion object {
    private const val TAG = "ParsePostsUseCase"

    private const val POSTS_PER_BATCH = 16
  }

}