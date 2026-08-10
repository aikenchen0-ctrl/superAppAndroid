package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterial
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialDetail

internal data class ScrmMomentMaterialsPanelState(
    val loading: Boolean = false,
    val materials: List<ScrmMomentMaterial> = emptyList(),
    val selectedMaterial: ScrmMomentMaterial? = null,
    val selectedDetail: ScrmMomentMaterialDetail? = null,
    val detailOpen: Boolean = false,
    val status: String? = null,
    val error: String? = null
)
