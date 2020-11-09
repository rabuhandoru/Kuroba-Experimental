package com.github.k1rakishou.model.util

import android.text.Spannable
import android.text.TextUtils
import android.text.style.CharacterStyle
import com.github.k1rakishou.core_spannable.ColorizableBackgroundColorSpan
import com.github.k1rakishou.core_spannable.ColorizableForegroundColorSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object ChanPostUtils {
  private val dateFormat = SimpleDateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.MEDIUM,
    Locale.ENGLISH
  )
  private val tmpDate = Date()

  @JvmStatic
  fun getTitle(post: ChanPost?, chanDescriptor: ChanDescriptor?): String {
    if (post != null) {
      if (!TextUtils.isEmpty(post.subject)) {
        return "/" + post.boardDescriptor.boardCode + "/ - " + post.subject.toString()
      }

      val comment = post.postComment.comment

      if (!TextUtils.isEmpty(comment)) {
        val length = Math.min(comment.length, 200)
        return "/" + post.boardDescriptor.boardCode + "/ - " + comment.subSequence(0, length)
      }

      return "/" + post.boardDescriptor.boardCode + "/" + post.postNo()
    }

    if (chanDescriptor != null) {
      if (chanDescriptor is CatalogDescriptor) {
        return "/" + chanDescriptor.boardCode() + "/"
      } else {
        val threadDescriptor = chanDescriptor as ThreadDescriptor
        return "/" + chanDescriptor.boardCode() + "/" + threadDescriptor.threadNo
      }
    }

    return ""
  }

  fun getLocalDate(post: ChanPost): String {
    tmpDate.time = post.timestamp * 1000L
    return dateFormat.format(tmpDate)
  }

  @JvmStatic
  fun findPostWithReplies(postNo: Long, posts: List<ChanPost>): HashSet<ChanPost> {
    val postsSet = HashSet<ChanPost>()
    findPostWithRepliesRecursive(postNo, posts, postsSet)
    return postsSet
  }

  @JvmStatic
  fun postCommentSpansSetThemeEngine(comment: CharSequence, themeEngine: ThemeEngine) {
    val spannableComment = comment as? Spannable
      ?: return

    val spans = spannableComment.getSpans(0, spannableComment.length, CharacterStyle::class.java)
    if (spans.isEmpty()) {
      return
    }

    spans.forEach { span ->
      if (span is ColorizableBackgroundColorSpan) {
        span.themeEngine = themeEngine
        return@forEach
      }

      if (span is ColorizableForegroundColorSpan) {
        span.themeEngine = themeEngine
        return@forEach
      }

      if (span is PostLinkable) {
        span.themeEngine = themeEngine
        return@forEach
      }
    }
  }

  /**
   * Finds a post by it's id and then finds all posts that has replied to this post recursively
   */
  private fun findPostWithRepliesRecursive(
    postNo: Long,
    posts: List<ChanPost>,
    postsSet: MutableSet<ChanPost>
  ) {
    for (post in posts) {
      if (post.postNo() == postNo && !postsSet.contains(post)) {
        postsSet.add(post)

        post.iterateRepliesFrom { replyId ->
          findPostWithRepliesRecursive(replyId, posts, postsSet)
        }
      }
    }
  }

}