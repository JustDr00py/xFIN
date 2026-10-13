package com.xfin.data.sources

import com.xfin.core.database.crypto.SecretCipher
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.model.SourceType
import com.xfin.data.iptv.xtream.XtreamClient
import com.xfin.data.iptv.xtream.XtreamCredentials
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SourceCredentials @Inject constructor(private val cipher: SecretCipher) {

    fun xtream(source: SourceEntity): XtreamCredentials =
        XtreamCredentials(source.url, source.username.orEmpty(), source.passwordEnc?.let(cipher::decrypt).orEmpty())

    /** Explicit guide URL, or the provider's built-in XMLTV endpoint for Xtream. */
    fun epgUrl(source: SourceEntity): String? =
        source.epgUrl ?: if (source.type == SourceType.XTREAM.name) XtreamClient.xmltvUrl(xtream(source)) else null
}
