package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanEndpoints
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Bbwchan : LynxchanSite() {
  private val siteIconLazy by lazy { SiteIcon.fromFavicon(imageLoaderV2, "${domainString}/favicon.ico".toHttpUrl()) }

  override val siteDomainSetting: StringSetting? by lazy {
    StringSetting(prefs, "site_domain", defaultDomain.toString())
  }

  private val mediaHostsLazy = lazy { arrayOf(domainUrl.value) }
  private val siteUrlHandler = lazy { BbwchanUrlHandler(domainUrl.value, mediaHostsLazy.value) }

  override val defaultDomain: HttpUrl
    get() = DEFAULT_DOMAIN
  override val siteName: String
    get() = SITE_NAME
  override val siteIcon: SiteIcon
    get() = siteIconLazy
  override val urlHandler: Lazy<BaseLynxchanUrlHandler>
    get() = siteUrlHandler
  override val endpoints: Lazy<LynxchanEndpoints>
    get() = lynxchanEndpoints
  override val postingViaFormData: Boolean
    get() = false

  class BbwchanUrlHandler(baseUrl: HttpUrl, mediaHosts: Array<HttpUrl>) : BaseLynxchanUrlHandler(
    url = baseUrl,
    mediaHosts = mediaHosts,
    names = arrayOf("Bbwchan"),
    siteClass = Bbwchan::class.java
  )

  companion object {
    private const val TAG = "Bbwchan"
    const val SITE_NAME = "Bbwchan"

    private val DEFAULT_DOMAIN = "https://bbw-chan.nl/".toHttpUrl()
  }
}