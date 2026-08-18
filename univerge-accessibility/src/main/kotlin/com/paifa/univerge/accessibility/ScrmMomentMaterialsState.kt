package com.paifa.univerge.accessibility

import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterial
import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterialDetail

internal data class ScrmMomentMaterialsPanelState(
    val loading: Boolean = false,
    val materials: List<ScrmMomentMaterial> = emptyList(),
    val selectedMaterial: ScrmMomentMaterial? = null,
    val selectedDetail: ScrmMomentMaterialDetail? = null,
    val detailOpen: Boolean = false,
    val status: String? = null,
    val error: String? = null
)
