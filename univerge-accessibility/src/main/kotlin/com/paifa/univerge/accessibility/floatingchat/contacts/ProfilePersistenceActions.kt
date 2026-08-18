package com.paifa.univerge.accessibility.floatingchat.contacts

import android.content.Context
import com.paifa.univerge.accessibility.data.LocalContactProfile
import com.paifa.univerge.accessibility.data.LocalGroupProfile
import com.paifa.univerge.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.univerge.accessibility.floatingchat.account.saveAccountProfile

internal class ProfilePersistenceActions(
    private val context: Context,
    private val accountProfiles: MutableMap<String, FloatingChatAccountProfile>,
    private val contactProfiles: MutableMap<String, LocalContactProfile>,
    private val groupProfiles: MutableMap<String, LocalGroupProfile>,
    private val onPersistContactProfile: (LocalContactProfile) -> Unit,
    private val onPersistGroupProfile: (LocalGroupProfile) -> Unit
) {
    fun updateAccountProfile(accountId: String, profile: FloatingChatAccountProfile) {
        accountProfiles[accountId] = profile
        saveAccountProfile(context, profile)
    }

    fun updateContactProfile(profile: LocalContactProfile) {
        contactProfiles[contactProfileKey(profile.accountId, profile.contactId)] = profile
        onPersistContactProfile(profile)
    }

    fun updateGroupProfile(profile: LocalGroupProfile) {
        groupProfiles[groupProfileKey(profile.accountId, profile.groupId)] = profile
        onPersistGroupProfile(profile)
    }
}
