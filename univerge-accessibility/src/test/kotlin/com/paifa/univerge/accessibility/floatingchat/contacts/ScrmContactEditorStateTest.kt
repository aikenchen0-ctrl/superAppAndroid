package com.paifa.univerge.accessibility.floatingchat.contacts

import com.paifa.univerge.accessibility.scrm.ScrmContact
import com.paifa.univerge.accessibility.scrm.ScrmCustomerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmContactEditorStateTest {
    @Test
    fun permissionMaskRoundTripUsesDocumentedBits() {
        val cases = listOf(
            0 to FriendPermissionsDraft(PermissionChoice.Disabled, PermissionChoice.Disabled, PermissionChoice.Disabled),
            1 to FriendPermissionsDraft(PermissionChoice.Disabled, PermissionChoice.Enabled, PermissionChoice.Disabled),
            2 to FriendPermissionsDraft(PermissionChoice.Disabled, PermissionChoice.Disabled, PermissionChoice.Enabled),
            3 to FriendPermissionsDraft(PermissionChoice.Disabled, PermissionChoice.Enabled, PermissionChoice.Enabled),
            8 to FriendPermissionsDraft(PermissionChoice.Enabled, PermissionChoice.Disabled, PermissionChoice.Disabled)
        )
        cases.forEach { (mask, expected) ->
            val contact = ScrmContact(id = 42, friendPermissionMask = mask, friendPermissionSynced = true)
            val draft = permissionDraftFromContact(contact)
            assertEquals(expected, draft)
            assertEquals(mask, permissionRequestValues(draft).first)
        }
    }

    @Test
    fun unsyncedPermissionSnapshotStaysUnknown() {
        val draft = permissionDraftFromContact(
            ScrmContact(id = 42, friendPermissionMask = 3, friendPermissionSynced = false)
        )
        assertEquals(PermissionChoice.Unknown, draft.onlyChat)
        assertEquals(PermissionChoice.Unknown, draft.notSeeFriendMoments)
        assertEquals(PermissionChoice.Unknown, draft.notLetFriendSeeMyMoments)
        assertEquals(null, permissionRequestValuesOrNull(draft))
    }

    @Test
    fun profileDraftNormalizesBlankAndStableLabels() {
        val draft = CustomerProfileDraft(
            customerLevel = "  VIP  ", sourceChannel = "   ", sourceDetail = "  Live  ",
            profileKey = " key ", purchaseHistory = "  bought  ", socialAccounts = "  wx-1  ",
            faceImageUrl = "  ", notes = "  note  ", mappedLabelIds = listOf(8, 2, 8, 0),
            mappedLabelNames = listOf(" beta ", "alpha", "beta", " ")
        )
        assertEquals(
            CustomerProfileDraft(
                customerLevel = "VIP", sourceChannel = null, sourceDetail = "Live", profileKey = "key",
                purchaseHistory = "bought", socialAccounts = "wx-1", faceImageUrl = null, notes = "note",
                mappedLabelIds = listOf(2, 8), mappedLabelNames = listOf("alpha", "beta")
            ),
            normalizeCustomerProfileDraft(draft)
        )
    }

    @Test
    fun emptyLabelSelectionIsDestructiveChange() {
        val before = ContactLabelsDraft(setOf(1), setOf("VIP"))
        assertTrue(labelsChangeIsDestructive(before, ContactLabelsDraft()))
        assertFalse(labelsChangeIsDestructive(before, before))
    }

    @Test
    fun changedFieldsIgnoreServerTimestamps() {
        val before = ScrmCustomerProfile(
            id = 1, contactId = 42, customerLevel = "VIP", sourceChannel = "Live",
            createdAt = "2026-08-10T00:00:00Z", updatedAt = "2026-08-10T00:00:00Z"
        )
        val unchanged = CustomerProfileDraft(customerLevel = " VIP ", sourceChannel = "Live")
        assertFalse(profileHasChanges(before, unchanged))
        assertTrue(profileHasChanges(before, unchanged.copy(notes = "follow up")))
    }

    @Test
    fun unchangedProfileCannotEnterReviewing() {
        val snapshot = ScrmCustomerProfile(contactId = 42, customerLevel = "VIP")

        assertFalse(profileHasChanges(snapshot, CustomerProfileDraft(customerLevel = " VIP ")))
    }

    @Test
    fun unknownPermissionCannotBeSaved() {
        val draft = FriendPermissionsDraft(
            onlyChat = PermissionChoice.Unknown,
            notSeeFriendMoments = PermissionChoice.Disabled,
            notLetFriendSeeMyMoments = PermissionChoice.Disabled
        )

        assertEquals(null, permissionRequestValuesOrNull(draft))
    }
}
