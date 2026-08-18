package com.paifa.univerge.core.sidefunction

import com.paifa.univerge.core.model.GestureAction

data class SideFunctionConfig private constructor(
    val customActionIds: List<String>
) {
    val allActionIds: List<String>
        get() = FIXED_ACTION_IDS + customActionIds

    companion object {
        const val CLOSE_ALL_ACTION_ID = "close_all"
        const val MAX_CUSTOM_ACTIONS = 5

        val FIXED_ACTION_IDS: List<String> = listOf(
            CLOSE_ALL_ACTION_ID,
            GestureAction.Back.id
        )

        fun fromCustomActionIds(actionIds: List<String>): SideFunctionConfig {
            return SideFunctionConfig(
                customActionIds = actionIds.asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .take(MAX_CUSTOM_ACTIONS)
                    .toList()
            )
        }
    }
}
