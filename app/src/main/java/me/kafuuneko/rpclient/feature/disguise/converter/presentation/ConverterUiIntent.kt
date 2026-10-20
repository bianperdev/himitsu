// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.converter.presentation

import me.kafuuneko.rpclient.libs.disguise.converter.ConverterCategory
import me.kafuuneko.rpclient.libs.disguise.converter.ConverterKey
import me.kafuuneko.rpclient.libs.disguise.converter.MeasureUnit

/** User actions on the unit converter decoy. */
sealed class ConverterUiIntent {
    data object Init : ConverterUiIntent()
    data class Press(val key: ConverterKey) : ConverterUiIntent()
    data class SelectCategory(val category: ConverterCategory) : ConverterUiIntent()
    data class SelectFrom(val unit: MeasureUnit) : ConverterUiIntent()
    data class SelectTo(val unit: MeasureUnit) : ConverterUiIntent()

    /** The swap button, which is also where the converter shortcut is checked. */
    data object Swap : ConverterUiIntent()
}
