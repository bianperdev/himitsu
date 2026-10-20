// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.disguise.Appearance

/**
 * The launcher icon of [appearance], as the launcher will draw it.
 *
 * `painterResource` cannot load adaptive icons, so the drawable is rendered to a bitmap once per
 * appearance and size.
 */
@Composable
fun AppearanceIcon(appearance: Appearance, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(appearance, sizePx) {
        ResourcesCompat.getDrawable(context.resources, appearance.iconRes, context.theme)
            ?.toBitmap(sizePx, sizePx)
            ?.asImageBitmap()
    } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.disguise_icon_preview),
        modifier = modifier.size(size)
    )
}
