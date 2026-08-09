package com.zai.mamsad.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zai.mamsad.MamsadApp
import com.zai.mamsad.data.AdminOverride
import com.zai.mamsad.data.MamsadRepository
import com.zai.mamsad.data.OrgEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the local admin panel.
 *
 * Exposes:
 *  - the cached catalog (so admin can browse all orgs even if hidden)
 *  - the local override map (so the list can show "edited" badge)
 *  - CRUD operations on local overrides
 */
class AdminViewModel(
    private val repo: MamsadRepository
) : ViewModel() {

    /** All cached orgs (including hidden — admin needs to see them). */
    val orgs: StateFlow<List<OrgEntity>> =
        repo.observeAll().stateIn(
            viewModelScope, SharingStarted.Eagerly, emptyList()
        )

    /** Local overrides — keyed by orgId for quick badge lookup. */
    val overrides: StateFlow<Map<Int, AdminOverride>> =
        repo.observeAdminOverrides()
            .map { list -> list.associateBy { it.orgId } }
            .stateIn(
                viewModelScope, SharingStarted.Eagerly, emptyMap()
            )

    /** Combined list of (org, override?) — drives AdminAdapter. */
    val orgsWithOverrides: StateFlow<List<Pair<OrgEntity, AdminOverride?>>> =
        combine(orgs, overrides) { list, ovMap ->
            list.map { it to ovMap[it.id] }
                .sortedWith(
                    compareByDescending<Pair<OrgEntity, *>> { it.first.featured }
                        .thenBy { it.first.title.lowercase() }
                )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveOverride(override: AdminOverride, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveAdminOverride(override)
            onDone()
        }
    }

    fun deleteOverride(orgId: Int) {
        viewModelScope.launch { repo.deleteAdminOverride(orgId) }
    }

    fun toggleFeatured(org: OrgEntity) {
        viewModelScope.launch {
            val current = repo.getAdminOverride(org.id)
            val newFeatured = !(current?.featured ?: org.featured)
            val updated = (current ?: AdminOverride(orgId = org.id)).copy(
                featured = newFeatured
            )
            repo.saveAdminOverride(updated)
        }
    }

    fun toggleHidden(org: OrgEntity) {
        viewModelScope.launch {
            val current = repo.getAdminOverride(org.id)
            val newHidden = !(current?.hidden ?: org.hidden)
            val updated = (current ?: AdminOverride(orgId = org.id)).copy(
                hidden = newHidden
            )
            repo.saveAdminOverride(updated)
        }
    }

    fun clearAllOverrides() {
        viewModelScope.launch { repo.clearAllAdminOverrides() }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdminViewModel(MamsadApp.instance.repository) as T
            }
        }
    }
}
