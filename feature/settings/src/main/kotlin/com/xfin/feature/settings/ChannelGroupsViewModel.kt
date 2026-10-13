package com.xfin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xfin.core.model.ChannelGroup
import com.xfin.data.sources.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GroupShowing { ALL, SHOWN, HIDDEN }

data class GroupsQuery(val text: String = "", val startsWith: Boolean = false, val showing: GroupShowing = GroupShowing.ALL) {
    val isFiltering: Boolean get() = text.isNotBlank() || showing != GroupShowing.ALL
}

data class ChannelGroupsUiState(
    val loaded: Boolean = false,
    val total: Int = 0,
    val hiddenTotal: Int = 0,
    val query: GroupsQuery = GroupsQuery(),
    /** Groups matching the query, in playlist order. */
    val groups: List<ChannelGroup> = emptyList(),
)

/** Something the user just did in bulk, with what was hidden before so it can be undone. */
data class BulkChange(val message: String, val previouslyHidden: Set<String>)

@HiltViewModel
class ChannelGroupsViewModel @Inject constructor(private val channels: ChannelRepository) : ViewModel() {

    private val query = MutableStateFlow(GroupsQuery())
    private val _lastChange = MutableStateFlow<BulkChange?>(null)
    val lastChange: StateFlow<BulkChange?> = _lastChange

    private var all: List<ChannelGroup> = emptyList()

    val state: StateFlow<ChannelGroupsUiState> = combine(channels.observeGroups(), query) { groups, q ->
        all = groups
        ChannelGroupsUiState(
            loaded = true,
            total = groups.size,
            hiddenTotal = groups.count { it.hidden },
            query = q,
            groups = groups.filter { g ->
                when (q.showing) {
                    GroupShowing.ALL -> true
                    GroupShowing.SHOWN -> !g.hidden
                    GroupShowing.HIDDEN -> g.hidden
                } && GroupFilter.matches(g.name, q.text, q.startsWith)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChannelGroupsUiState())

    fun setText(text: String) = query.update { it.copy(text = text) }
    fun setStartsWith(on: Boolean) = query.update { it.copy(startsWith = on) }
    fun setShowing(showing: GroupShowing) = query.update { it.copy(showing = showing) }

    fun toggle(group: ChannelGroup) {
        viewModelScope.launch { channels.setGroupHidden(group.name, !group.hidden) }
    }

    /** Shows or hides every group currently listed (all groups when nothing is filtered). */
    fun setListedHidden(hidden: Boolean) {
        val names = state.value.groups.map { it.name }
        if (names.isEmpty()) return
        val verb = if (hidden) "Hid" else "Showed"
        bulk("$verb ${plural(names.size)}") { channels.setGroupsHidden(names, hidden) }
    }

    /** Shows only the listed groups and hides everything else. */
    fun showOnlyListed() {
        val keep = state.value.groups.mapTo(HashSet()) { it.name }
        if (keep.isEmpty()) return
        bulk("Showing only ${plural(keep.size)}") { channels.replaceHiddenGroups(all.map { it.name }.filterNot(keep::contains)) }
    }

    fun undo() {
        val change = _lastChange.value ?: return
        _lastChange.value = null
        viewModelScope.launch { channels.replaceHiddenGroups(change.previouslyHidden) }
    }

    fun dismissChange() {
        _lastChange.value = null
    }

    private fun bulk(message: String, apply: suspend () -> Unit) {
        val before = all.filter { it.hidden }.mapTo(HashSet()) { it.name }
        viewModelScope.launch {
            apply()
            _lastChange.value = BulkChange(message, before)
        }
    }

    private fun plural(n: Int) = if (n == 1) "1 group" else "%,d groups".format(n)
}
