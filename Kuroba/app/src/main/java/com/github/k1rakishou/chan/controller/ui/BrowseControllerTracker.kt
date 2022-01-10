package com.github.k1rakishou.chan.controller.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewParent
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import kotlin.math.abs
import kotlin.math.max

class BrowseControllerTracker(
  context: Context,
  private val browseController: BrowseController,
  private val navigationController: NavigationController
) : ControllerTracker(context) {
  private var actionDownSimulated = false
  private var trackingType = TrackingType.None
  private var browseControllerViewWidth: Int = 0

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (event.pointerCount != 1) {
      return false
    }

    val navController = navigationController
    if (navController.top == null) {
      return false
    }

    val shouldNotInterceptTouchEvent = navController.isBlockingInput || !browseController.shown
    if (shouldNotInterceptTouchEvent) {
      return false
    }

    val width = browseController.view.width
    if (width <= 0) {
      return false
    }

    val actionMasked = event.actionMasked
    if (actionMasked != MotionEvent.ACTION_DOWN && interceptedEvent == null) {
      // Action down wasn't called here, ignore
      return false
    }

    when (actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        this.browseControllerViewWidth = width

        interceptedEvent?.recycle()
        interceptedEvent = null

        interceptedEvent = MotionEvent.obtain(event)
      }
      MotionEvent.ACTION_MOVE -> {
        val startX = interceptedEvent!!.x
        val startY = interceptedEvent!!.y
        val currentX = event.x
        val currentY = event.y

        val deltaX = currentX - startX
        val deltaY = currentY - startY

        val isInDrawerActionZone = startX < DRAWER_ACTION_ZONE_X_PX

        if (abs(deltaY) >= slopPixels || isInDrawerActionZone) {
          blockTracking = true
        }

        if (!blockTracking && (abs(deltaX) > abs(deltaY))) {
          // If moving from left to right and moved at least [minimalMovedPixels] pixels
          if (currentX > startX && abs(deltaX) >= minimalMovedPixels) {
            startTracking(event, TrackingType.OpenDrawer)
            return true
          }

          // If moving from right to left and moved at least [minimalMovedPixels] pixels
          if (currentX < startX && abs(deltaX) >= minimalMovedPixels) {
            startTracking(event, TrackingType.OpenSlidingPane)
            return true
          }
        }
      }
      MotionEvent.ACTION_CANCEL,
      MotionEvent.ACTION_UP -> {
        interceptedEvent?.recycle()
        interceptedEvent = null
        trackingType = TrackingType.None
        blockTracking = false
        actionDownSimulated = false
        this.browseControllerViewWidth = 0
      }
    }

    return false
  }

  override fun onTouchEvent(parentView: ViewParent, event: MotionEvent): Boolean {
    if (trackingType == TrackingType.None) {
      // tracking already ended
      return false
    }

    if (event.pointerCount != 1) {
      return false
    }

    if (!actionDownSimulated) {
      when (trackingType) {
        TrackingType.None -> throw IllegalStateException("This shouldn't be handled here")
        TrackingType.OpenDrawer -> {
          if (!simulateDrawerActionDown(event)) {
            endTracking()
            return false
          }
        }
        TrackingType.OpenSlidingPane -> {
          if (!simulateSlidingPaneLayoutActionDown(event)) {
            endTracking()
            return false
          }
        }
      }

      actionDownSimulated = true
    }

    parentView.requestDisallowInterceptTouchEvent(true)

    return when (trackingType) {
      TrackingType.None -> throw IllegalStateException("This shouldn't be handled here")
      TrackingType.OpenDrawer -> handleOpenDrawerGesture(event)
      TrackingType.OpenSlidingPane -> handleOpenSlidingPaneLayoutGesture(event)
    }
  }

  private fun handleOpenSlidingPaneLayoutGesture(event: MotionEvent): Boolean {
    var deltaX = trackStartPosition - event.x
    if (deltaX < 0f) {
      deltaX = 0f
    }

    val motionEvent = MotionEvent.obtain(
      event.downTime,
      event.eventTime,
      event.action,
      browseControllerViewWidth - deltaX,
      event.y,
      0
    )

    try {
      return browseController.passMotionEventIntoSlidingPaneLayout(motionEvent)
    } finally {
      motionEvent.recycle()

      when (event.actionMasked) {
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          endTracking()
        }
      }
    }
  }

  private fun handleOpenDrawerGesture(event: MotionEvent): Boolean {
    val motionEvent = MotionEvent.obtain(
      event.downTime,
      event.eventTime,
      event.action,
      max(0f, event.x - trackStartPosition),
      event.y,
      0
    )

    try {
      return browseController.passMotionEventIntoDrawer(motionEvent)
    } finally {
      motionEvent.recycle()

      when (event.actionMasked) {
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          endTracking()
        }
      }
    }
  }

  private fun simulateDrawerActionDown(event: MotionEvent): Boolean {
    val motionEvent = MotionEvent.obtain(
      event.downTime,
      event.eventTime,
      MotionEvent.ACTION_DOWN,
      0f,
      max(0f, event.y - (slopPixels + 1)),
      0
    )

    try {
      return browseController.passMotionEventIntoDrawer(motionEvent)
    } finally {
      motionEvent.recycle()
    }
  }

  private fun simulateSlidingPaneLayoutActionDown(event: MotionEvent): Boolean {
    val motionEvent = MotionEvent.obtain(
      event.downTime,
      event.eventTime,
      MotionEvent.ACTION_DOWN,
      browseControllerViewWidth.toFloat(),
      max(0f, event.y - (slopPixels + 1)),
      0
    )

    try {
      return browseController.passMotionEventIntoSlidingPaneLayout(motionEvent)
    } finally {
      motionEvent.recycle()
    }
  }

  override fun requestDisallowInterceptTouchEvent() {
    if (interceptedEvent != null) {
      interceptedEvent!!.recycle()
      interceptedEvent = null
    }

    blockTracking = false

    if (trackingType != TrackingType.None) {
      endTracking()
    }
  }

  private fun startTracking(startEvent: MotionEvent, newTrackingType: TrackingType) {
    if (trackingType != TrackingType.None) {
      // this method was already called previously
      return
    }

    trackingType = newTrackingType
    trackStartPosition = startEvent.x.toInt()

    interceptedEvent?.recycle()
    interceptedEvent = null
  }

  private fun endTracking() {
    if (trackingType == TrackingType.None) {
      // this method was already called previously
      return
    }

    trackingType = TrackingType.None
    actionDownSimulated = false
    trackStartPosition = 0
  }

  enum class TrackingType {
    None,
    OpenDrawer,
    OpenSlidingPane
  }

  companion object {
    // Drawer has a zone where if you long tap the drawer will slightly open.
    private val DRAWER_ACTION_ZONE_X_PX = dp(20f)
  }
}