package com.paifa.ubikitouch.accessibility.floatingchat.tools

import android.content.Context
import android.content.SharedPreferences
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import kotlin.math.roundToInt

internal fun referenceToolActionsFor(actions: List<FloatingChatToolAction>): List<FloatingChatToolAction> {
    val availableActions = actions.toSet()
    val referenceOrder = listOf(
        FloatingChatToolAction.Assistant,
        FloatingChatToolAction.AiVoice,
        FloatingChatToolAction.Contacts,
        FloatingChatToolAction.Blink,
        FloatingChatToolAction.Gallery,
        FloatingChatToolAction.Camera,
        FloatingChatToolAction.Location,
        FloatingChatToolAction.Favorite,
        FloatingChatToolAction.RedPacket,
        FloatingChatToolAction.Transfer,
        FloatingChatToolAction.Files,
        FloatingChatToolAction.Card,
        FloatingChatToolAction.Moments,
        FloatingChatToolAction.MomentMaterials,
        FloatingChatToolAction.QuickPhrase
    ).filter { action -> action in availableActions }

    return referenceOrder.ifEmpty { actions }
}

internal fun toolActionOpensBottomPanel(action: FloatingChatToolAction): Boolean {
    return toolActionBottomPanelMode(action) != BottomPanelMode.None
}

internal fun toolActionBottomPanelModeName(action: FloatingChatToolAction): String {
    return toolActionBottomPanelMode(action).name
}

private fun toolActionBottomPanelMode(action: FloatingChatToolAction): BottomPanelMode {
    return when (action) {
        FloatingChatToolAction.Contacts -> BottomPanelMode.Contacts
        FloatingChatToolAction.Assistant -> BottomPanelMode.Assistant
        FloatingChatToolAction.AiVoice -> BottomPanelMode.AiVoice
        FloatingChatToolAction.QuickPhrase -> BottomPanelMode.QuickPhrase
        FloatingChatToolAction.Moments -> BottomPanelMode.Moments
        FloatingChatToolAction.MomentMaterials -> BottomPanelMode.MomentMaterials
        FloatingChatToolAction.RedPacket -> BottomPanelMode.RedPacket
        FloatingChatToolAction.Transfer -> BottomPanelMode.Transfer
        FloatingChatToolAction.Location -> BottomPanelMode.Location
        FloatingChatToolAction.Favorite -> BottomPanelMode.Favorite
        FloatingChatToolAction.Card -> BottomPanelMode.Card
        else -> BottomPanelMode.None
    }
}

internal fun moveToolAction(
    actions: List<FloatingChatToolAction>,
    fromIndex: Int,
    toIndex: Int
): List<FloatingChatToolAction> {
    if (actions.isEmpty()) return actions
    val safeFrom = fromIndex.coerceIn(0, actions.lastIndex)
    val safeTo = toIndex.coerceIn(0, actions.lastIndex)
    if (safeFrom == safeTo) return actions
    val next = actions.toMutableList()
    val moved = next.removeAt(safeFrom)
    next.add(safeTo, moved)
    return next
}

internal fun toolReorderTargetIndex(
    startIndex: Int,
    dragOffsetY: Float,
    itemSlotHeightPx: Float,
    itemCount: Int
): Int {
    if (itemCount <= 0) return 0
    if (itemSlotHeightPx <= 0f) return startIndex.coerceIn(0, itemCount - 1)
    val crossedSlots = (dragOffsetY / itemSlotHeightPx).roundToInt()
    return (startIndex + crossedSlots).coerceIn(0, itemCount - 1)
}

internal fun loadToolActionOrder(
    context: Context,
    availableActions: List<FloatingChatToolAction>
): List<FloatingChatToolAction> {
    val fallback = referenceToolActionsFor(availableActions)
    val stored = toolOrderPrefs(context).getString(KEY_TOOL_ACTION_ORDER, null)
        ?.split(",")
        ?.mapNotNull { name -> runCatching { FloatingChatToolAction.valueOf(name) }.getOrNull() }
        .orEmpty()
    return mergeToolActionOrder(
        storedActions = stored,
        fallbackActions = fallback
    )
}

internal fun mergeToolActionOrder(
    storedActions: List<FloatingChatToolAction>,
    fallbackActions: List<FloatingChatToolAction>
): List<FloatingChatToolAction> {
    if (storedActions.isEmpty()) return fallbackActions
    val available = fallbackActions.toSet()
    val ordered = storedActions
        .filter { action -> action in available }
        .distinct()
        .toMutableList()
    fallbackActions
        .filterNot { action -> action in ordered }
        .forEach { missingAction ->
            val fallbackIndex = fallbackActions.indexOf(missingAction)
            val previousKnownAction = fallbackActions
                .take(fallbackIndex)
                .lastOrNull { action -> action in ordered }
            val nextKnownAction = fallbackActions
                .drop(fallbackIndex + 1)
                .firstOrNull { action -> action in ordered }
            val insertIndex = when {
                previousKnownAction != null -> ordered.indexOf(previousKnownAction) + 1
                nextKnownAction != null -> ordered.indexOf(nextKnownAction)
                else -> ordered.size
            }
            ordered.add(insertIndex, missingAction)
        }
    return ordered.ifEmpty { fallbackActions }
}

internal fun saveToolActionOrder(
    context: Context,
    actions: List<FloatingChatToolAction>
) {
    toolOrderPrefs(context)
        .edit()
        .putString(KEY_TOOL_ACTION_ORDER, actions.joinToString(",") { action -> action.name })
        .apply()
}

private fun toolOrderPrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(TOOL_ORDER_PREFS, Context.MODE_PRIVATE)
}

private const val TOOL_ORDER_PREFS = "floating_chat_tool_order"
private const val KEY_TOOL_ACTION_ORDER = "tool_action_order"
