package com.github.k1rakishou.chan.features.proxies

import android.content.Context
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.manager.DialogFactory
import com.github.k1rakishou.chan.core.manager.ProxyStorage
import com.github.k1rakishou.chan.core.settings.state.PersistableChanState
import com.github.k1rakishou.chan.features.drawer.DrawerCallbacks
import com.github.k1rakishou.chan.features.proxies.data.ProxyEntryView
import com.github.k1rakishou.chan.features.proxies.data.ProxySetupState
import com.github.k1rakishou.chan.features.proxies.epoxy.epoxyProxyView
import com.github.k1rakishou.chan.ui.epoxy.epoxyDividerView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.AndroidUtils.getString
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class ProxySetupController(
  context: Context,
  private val drawerCallbacks: DrawerCallbacks?
) : Controller(context), ProxySetupView, ProxySelectionHelper.OnProxyItemClicked {

  @Inject
  lateinit var dialogFactory: DialogFactory

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var addProxyButton: ColorizableFloatingActionButton

  private val proxySelectionHelper = ProxySelectionHelper(this)
  private val presenter = ProxySetupPresenter(proxySelectionHelper)
  private val onApplyClickListener = { presenter.reloadProxies() }

  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    navigation.title = getString(R.string.controller_proxy_setup_title)

    view = AndroidUtils.inflate(context, R.layout.controller_proxy_setup)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)
    addProxyButton = view.findViewById(R.id.add_proxy_button)

    addProxyButton.setOnClickListener {
      requireNavController().pushController(ProxyEditorController(context, onApplyClickListener))
    }

    mainScope.launch {
      presenter.listenForStateUpdates()
        .collect { state -> onStateChanged(state) }
    }

    mainScope.launch {
      proxySelectionHelper.listenForSelectionChanges()
        .collect { selectionEvent -> onNewSelectionEvent(selectionEvent) }
    }

    presenter.onCreate(this)

    showProxyEditingNotification()
  }

  override fun onDestroy() {
    super.onDestroy()

    presenter.onDestroy()
    drawerCallbacks?.hideBottomPanel()
  }

  override fun onBack(): Boolean {
    val result = drawerCallbacks?.passOnBackToBottomPanel() ?: false
    if (result) {
      proxySelectionHelper.clearSelection()
    }

    return result
  }

  override fun showMessage(message: String) {
    showToast(message)
  }

  private fun onNewSelectionEvent(selectionEvent: BaseSelectionHelper.SelectionEvent) {
    when (selectionEvent) {
      BaseSelectionHelper.SelectionEvent.EnteredSelectionMode,
      BaseSelectionHelper.SelectionEvent.ItemSelectionToggled -> {
        if (selectionEvent is BaseSelectionHelper.SelectionEvent.EnteredSelectionMode) {
          drawerCallbacks?.showBottomPanel(proxySelectionHelper.getBottomPanelMenus())
          addProxyButton.hide()
        }

        enterSelectionModeOrUpdate()
      }
      BaseSelectionHelper.SelectionEvent.ExitedSelectionMode -> {
        drawerCallbacks?.hideBottomPanel()
        requireNavController().requireToolbar().exitSelectionMode()
        addProxyButton.show()
      }
    }
  }

  override fun onMenuItemClicked(
    proxyMenuItemType: ProxySelectionHelper.ProxyMenuItemType,
    selectedItems: List<ProxyStorage.ProxyKey>
  ) {
    when (proxyMenuItemType) {
      ProxySelectionHelper.ProxyMenuItemType.Delete -> {
        val proxiesCount = selectedItems.size

        dialogFactory.createSimpleConfirmationDialog(
          context = context,
          titleText = getString(R.string.controller_proxy_setup_delete_selected_proxies_title, proxiesCount),
          negativeButtonText = getString(R.string.do_not),
          positiveButtonText = getString(R.string.delete),
          onPositiveButtonClickListener = {
            presenter.deleteProxies(selectedItems)
            proxySelectionHelper.clearSelection()
            showToast(R.string.controller_proxy_editor_proxy_deleted)
          }
        )
      }
    }
  }

  private fun onStateChanged(state: ProxySetupState) {
    epoxyRecyclerView.withModels {
      when (state) {
        ProxySetupState.Uninitialized -> {
          // no-op
        }
        ProxySetupState.Empty -> {
          epoxyTextView {
            id("no_proxies_text_view")
            message(context.getString(R.string.controller_proxy_setup_no_proxies))
          }
        }
        is ProxySetupState.Data -> {
          state.proxyEntryViewList.forEachIndexed { index, proxyEntryView ->
            epoxyProxyView {
              id("epoxy_proxy_view_${proxyEntryView.proxyKeyString()}")
              proxySelectionHelper(proxySelectionHelper)
              proxyAddress(proxyEntryView.address)
              proxyPort(proxyEntryView.port.toString())
              proxyEnabled(proxyEntryView.enabled)
              proxySupportedSites(proxyEntryView.supportedSites)
              proxySupportedActions(proxyEntryView.supportedActions)
              proxyType(proxyEntryView.proxyType)
              proxySelection(proxyEntryView.selection)
              proxyHolderClickListener { onProxyItemViewClick(proxyEntryView) }
              proxyHolderLongClickListener { onProxyViewItemLongClick(proxyEntryView) }
              proxySettingsClickListener { onProxySettingsClick(proxyEntryView) }
            }

            if (index != state.proxyEntryViewList.lastIndex) {
              epoxyDividerView {
                id("epoxy_proxy_view_divider_${proxyEntryView.proxyKeyString()}")
              }
            }
          }
        }
      }
    }
  }

  private fun onProxyItemViewClick(proxyEntryView: ProxyEntryView) {
    if (proxySelectionHelper.isInSelectionMode()) {
      proxySelectionHelper.toggleSelection(proxyEntryView.proxyKey())
      return
    }

    presenter.toggleProxyEnableDisableState(proxyEntryView)
  }

  private fun onProxyViewItemLongClick(proxyEntryView: ProxyEntryView) {
    proxySelectionHelper.toggleSelection(proxyEntryView.proxyKey())
  }

  private fun onProxySettingsClick(proxyEntryView: ProxyEntryView) {
    if (proxySelectionHelper.isInSelectionMode()) {
      proxySelectionHelper.toggleSelection(proxyEntryView.proxyKey())
      return
    }

    val controller = ProxyEditorController(
      context,
      onApplyClickListener,
      proxyEntryView.proxyKey()
    )

    requireNavController().pushController(controller)
  }

  private fun showProxyEditingNotification() {
    if (PersistableChanState.proxyEditingNotificationShown.get()) {
      return
    }

    PersistableChanState.proxyEditingNotificationShown.set(true)

    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleTextId = R.string.controller_proxy_setup_title,
      descriptionTextId = R.string.controller_proxy_setup_proxy_editing_notification
    )
  }

  private fun enterSelectionModeOrUpdate() {
    val navController = requireNavController()
    val toolbar = navController.requireToolbar()

    if (!toolbar.isInSelectionMode) {
      toolbar.enterSelectionMode(formatSelectionText())
      return
    }

    navigation.selectionStateText = formatSelectionText()
    toolbar.updateSelectionTitle(navController.navigation)
  }

  private fun formatSelectionText(): String {
    require(proxySelectionHelper.isInSelectionMode()) { "Not in selection mode" }

    return getString(
      R.string.controller_proxy_setup_selected_n_proxies,
      proxySelectionHelper.selectedItemsCount()
    )
  }

}