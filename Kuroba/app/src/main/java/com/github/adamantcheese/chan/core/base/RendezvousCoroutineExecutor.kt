package com.github.adamantcheese.chan.core.base

import com.github.adamantcheese.chan.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Executes all callbacks sequentially using an rendezvous channel. This means that if a callback
 * is currently running all other callbacks are ignored
 * */
@OptIn(ExperimentalCoroutinesApi::class)
class RendezvousCoroutineExecutor(private val scope: CoroutineScope) {
  private val channel = Channel<SerializedAction>(Channel.RENDEZVOUS)

  init {
    scope.launch {
      channel.consumeEach { serializedAction ->
        try {
          serializedAction.action()
        } catch (error: Throwable) {
          Logger.e(TAG, "serializedAction unhandled exception", error)
        }
      }
    }
  }

  fun post(func: suspend () -> Unit) {
    val serializedAction = SerializedAction(func)
    channel.offer(serializedAction)
  }

  data class SerializedAction(
    val action: suspend () -> Unit
  )

  companion object {
    private const val TAG = "RendezvousCoroutineExecutor"
  }
}