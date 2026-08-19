package com.ar13x.jarvis.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One round trip for the whole Tasks tab first paint (plan §4.4). */
@Serializable
data class SectionsResponse(
    val priority: List<Task> = emptyList(),
    val recurring: List<Task> = emptyList(),
    val all: PagedTasks = PagedTasks(),
)

@Serializable
data class PagedTasks(
    val tasks: List<Task> = emptyList(),
    val page: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false,
)
