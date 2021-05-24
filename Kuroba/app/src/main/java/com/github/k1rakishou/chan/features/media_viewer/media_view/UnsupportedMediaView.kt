package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils

@SuppressLint("ViewConstructor")
class UnsupportedMediaView(
  context: Context,
  override val viewableMedia: ViewableMedia.Unsupported,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<UnsupportedMediaView.UnsupportedMediaViewParams, ViewableMedia.Unsupported>(context, null) {


  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_unsupported, this)

  }

  override fun preload(parameters: UnsupportedMediaViewParams) {
    // do not preload unsupported media
  }

  override fun bind(parameters: UnsupportedMediaViewParams) {
    // nothing to bind
  }

  override fun hide() {

  }

  override fun unbind() {
    // nothing to unbind
  }

  class UnsupportedMediaViewParams()
}