package com.xfin.data.sources

import com.xfin.core.model.SourceType
import com.xfin.core.model.XtreamOutput

/** The user-entered settings of a source, as shown in the edit form. */
data class EditableSource(
    val id: Long,
    val type: SourceType,
    val name: String,
    val url: String,
    val epgUrl: String?,
    val userAgent: String?,
    val username: String?,
    val password: String?,
    val output: XtreamOutput,
)
