package com.paifa.univerge.accessibility

import com.paifa.univerge.accessibility.scrm.ScrmAdminBootstrapResult
import com.paifa.univerge.accessibility.scrm.ScrmAuthenticationException
import com.paifa.univerge.accessibility.scrm.ScrmCommonChatRoomQuery
import com.paifa.univerge.accessibility.scrm.ScrmContact
import com.paifa.univerge.accessibility.scrm.ScrmContactQuery
import com.paifa.univerge.accessibility.scrm.ScrmContactWxidQuery
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.scrm.scrmFloatingAccountRouteForContactId
import com.paifa.univerge.core.model.FloatingChatContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FloatingChatContactRelationSegment { All, Organization, Tags, Common, Customers }

data class FloatingChatContactRelation(
    val id: Int,
    val name: String,
    val wxid: String,
    val avatarUrl: String?,
    val organization: String? = null,
    val tags: List<String> = emptyList(),
    val commonGroups: List<String> = emptyList(),
    val customerLevel: String? = null,
    val source: String? = null,
    val updatedAt: String? = null
)

data class FloatingChatContactRelationsSnapshot(
    val accounts: List<FloatingChatContact> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedWeChatId: String? = null,
    val groups: List<FloatingChatContact> = emptyList(),
    val segment: FloatingChatContactRelationSegment = FloatingChatContactRelationSegment.All,
    val contacts: List<FloatingChatContactRelation> = emptyList(),
    val totalCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null
)

/** Public app-facing boundary for the SCRM-backed contact relations screen. */
object FloatingChatContactRelationsBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSnapshot = MutableStateFlow(FloatingChatContactRelationsSnapshot())
    val snapshot: StateFlow<FloatingChatContactRelationsSnapshot> = mutableSnapshot.asStateFlow()

    fun open() = UbikiAccessibilityService.instance?.requestFloatingChatContactRelations()

    internal fun updateShell(accounts: List<FloatingChatContact>, selectedAccountId: String?, groups: List<FloatingChatContact>) {
        val weChatId = selectedAccountId?.let(::scrmFloatingAccountRouteForContactId)?.weChatId
        mutableSnapshot.value = mutableSnapshot.value.copy(
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            selectedWeChatId = weChatId,
            groups = groups.filter { selectedAccountId.isNullOrBlank() || it.id.startsWith("${selectedAccountId}__") }
        )
    }

    fun refresh() = loadSegment(mutableSnapshot.value.segment)

    fun loadSegment(segment: FloatingChatContactRelationSegment, search: String = "") {
        val weChatId = mutableSnapshot.value.selectedWeChatId ?: return setError("当前没有可用的微信账号")
        mutableSnapshot.value = mutableSnapshot.value.copy(segment = segment, contacts = emptyList(), totalCount = 0, loading = true, error = null)
        scope.launch {
            runCatching {
                val manager = ScrmSettingsManager(UbikiAccessibilityService.instance!!.applicationContext)
                fun fetch() = loadRelations(manager, weChatId, segment, search)
                try { fetch() } catch (error: ScrmAuthenticationException) {
                    if (manager.bootstrapWithBundledAdminCredentials() !is ScrmAdminBootstrapResult.Success) throw error
                    fetch()
                }
            }.onSuccess { (contacts, total) ->
                mutableSnapshot.value = mutableSnapshot.value.copy(
                    contacts = contacts,
                    totalCount = total.takeIf { it > 0 } ?: contacts.size,
                    loading = false,
                    error = null
                )
            }.onFailure { error ->
                mutableSnapshot.value = mutableSnapshot.value.copy(loading = false, error = error.message ?: "联系人读取失败")
            }
        }
    }

    private fun loadRelations(
        manager: ScrmSettingsManager,
        weChatId: String,
        segment: FloatingChatContactRelationSegment,
        search: String
    ): Pair<List<FloatingChatContactRelation>, Int> {
        val session = manager.loadSelectedSessionOrBootstrap()
        val query = search.trim().takeIf { it.isNotEmpty() }
        val contacts = loadAllContacts(session.contactApi, weChatId, query)
        return when (segment) {
            FloatingChatContactRelationSegment.All -> contacts.map(::baseRelation) to contacts.size
            FloatingChatContactRelationSegment.Organization -> contacts.mapNotNull { contact ->
                val profile = session.contactApi.getCustomerProfile(contact.id, weChatId)
                val organization = profile.sourceChannel ?: profile.sourceDetail
                organization?.takeIf { it.isNotBlank() }?.let { baseRelation(contact).copy(organization = it, source = profile.sourceDetail) }
            } to 0
            FloatingChatContactRelationSegment.Tags -> contacts.mapNotNull { contact ->
                val detail = session.contactApi.getContactDetail(contact.id, commonChatRoomLimit = 0, relationLogLimit = 0)
                val tags = detail.labels.mapNotNull { it.tagName?.takeIf(String::isNotBlank) }
                tags.takeIf { it.isNotEmpty() }?.let { baseRelation(contact).copy(tags = it) }
            } to 0
            FloatingChatContactRelationSegment.Common -> contacts.mapNotNull { contact ->
                val friendId = contact.wxid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val page = session.contactApi.getCommonChatRooms(friendId, ScrmCommonChatRoomQuery(weChatId = weChatId, pageSize = scrmConversationPageSize()))
                val groups = page.items.mapNotNull { it.name?.takeIf(String::isNotBlank) }
                groups.takeIf { it.isNotEmpty() }?.let { baseRelation(contact).copy(commonGroups = it) }
            } to 0
            FloatingChatContactRelationSegment.Customers -> {
                val customerWxids = session.contactApi.getContactWxids(
                    ScrmContactWxidQuery(weChatId = weChatId, search = query, onlyFriends = true, profileOnly = true)
                ).wxids.toSet()
                contacts.filter { it.wxid in customerWxids }.map { contact ->
                    val profile = session.contactApi.getCustomerProfile(contact.id, weChatId)
                    baseRelation(contact).copy(customerLevel = profile.customerLevel, source = profile.sourceChannel)
                } to customerWxids.size
            }
        }
    }

    private fun loadAllContacts(api: com.paifa.univerge.accessibility.scrm.ScrmContactApi, weChatId: String, search: String?): List<ScrmContact> {
        val contacts = mutableListOf<ScrmContact>()
        var pageNumber = 1
        var totalCount: Int
        var returned: Int
        do {
            val page = api.getContacts(ScrmContactQuery(weChatId = weChatId, page = pageNumber, pageSize = scrmConversationPageSize(), search = search, onlyFriends = true, includeProfile = true))
            returned = page.items.size
            totalCount = page.totalCount
            contacts += page.items
            pageNumber += 1
        } while (shouldRequestNextScrmConversationPage(returned, contacts.size, totalCount, scrmConversationPageSize()))
        return contacts.distinctBy { it.id }
    }

    private fun baseRelation(contact: ScrmContact) = FloatingChatContactRelation(
        id = contact.id,
        name = contact.displayName,
        wxid = contact.wxid.orEmpty(),
        avatarUrl = contact.displayAvatarUrl,
        source = contact.source,
        updatedAt = contact.updatedAt
    )

    fun notifyClosed() = UbikiAccessibilityService.instance?.onFloatingChatContactRelationsClosed()

    private fun setError(message: String) { mutableSnapshot.value = mutableSnapshot.value.copy(loading = false, error = message) }
}
