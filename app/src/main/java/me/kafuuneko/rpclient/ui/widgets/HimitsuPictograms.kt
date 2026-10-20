// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Original pictograms for Himitsu empty states, drawn on the 24 dp Material icon grid.
 *
 * They are single-color like Material icons, so `Icon` tints them with theme colors; parts with
 * lower alpha keep a two-tone look after tinting. Use them in the existing icon slots so empty
 * states change only in appearance.
 */
object HimitsuPictograms {
    /** A shelf with one figure, a few books and an empty space: no characters yet. */
    val Shelf: ImageVector by lazy {
        pictogram(
            "Shelf",
            PictogramPath("M3,10h18v1.6H3z"),
            PictogramPath("M3,19h18v1.6H3z"),
            PictogramPath("M3,3.5h1.5v17.1H3z M19.5,3.5H21v17.1h-1.5z", alpha = 0.45f),
            PictogramPath("M8,4.4a1.6,1.6 0 1,1 -0.01,0z"),
            PictogramPath("M5.9,10c0-2.3 0.9-3.3 2.1-3.3s2.1,1 2.1,3.3z"),
            PictogramPath("M13.2,5.2h1.6v4.8h-1.6z M15.4,6.4h1.6v3.6h-1.6z", alpha = 0.45f),
            PictogramPath("M6.2,14h2.6v5h-2.6z", alpha = 0.45f),
            PictogramPath("M12,16.2h1.4v1.2H12z M14.6,16.2H16v1.2h-1.4z M17.2,16.2h1.4v1.2h-1.4z", alpha = 0.45f)
        )
    }

    /** A monitor with a blinking cursor: nothing has been written yet. */
    val Monitor: ImageVector by lazy {
        pictogram(
            "Monitor",
            PictogramPath("M4,3.5h16a2,2 0 0,1 2,2v9.5a2,2 0 0,1 -2,2H4a2,2 0 0,1 -2,-2V5.5a2,2 0 0,1 2,-2z M4,5.5v9.5h16V5.5z", evenOdd = true),
            PictogramPath("M10.5,17h3v2.2h-3z M7,19.2h10v1.6H7z", alpha = 0.45f),
            PictogramPath("M6.2,7.6h2.2v5.4H6.2z"),
            PictogramPath("M10,11.6h7v1.4h-7z", alpha = 0.45f)
        )
    }

    /** A plug pulled out of its socket: no model connected. */
    val Unplugged: ImageVector by lazy {
        pictogram(
            "Unplugged",
            PictogramPath("M3.5,8.5h5a2.5,2.5 0 0,1 2.5,2.5v2a2.5,2.5 0 0,1 -2.5,2.5h-5z"),
            PictogramPath("M11,9.9h2.6v1.4H11z M11,12.7h2.6v1.4H11z"),
            PictogramPath("M0.8,11.3h2.7v1.4H0.8z"),
            PictogramPath("M17,7h3.5a1.5,1.5 0 0,1 1.5,1.5v7a1.5,1.5 0 0,1 -1.5,1.5H17z M18.4,9.9v1.4h1.6V9.9z M18.4,12.7v1.4h1.6v-1.4z", alpha = 0.45f, evenOdd = true),
            PictogramPath("M15,5.2l0.9-1.6 1.2,0.7 -0.9,1.6z M14.7,19.6l0.9,-1.6 1.2,0.7 -0.9,1.6z", alpha = 0.45f)
        )
    }

    /** A closed book under the desk lamp: no world books yet. */
    val LampBook: ImageVector by lazy {
        pictogram(
            "LampBook",
            PictogramPath("M10.6,10.5L15.8,10.5L20.6,19.8H8.6z", alpha = 0.22f),
            PictogramPath("M2.6,19.8h6.2v1.6H2.6z M5,7.4h1.4v12.4H5z M5,6.6h7.6v1.4H5z"),
            PictogramPath("M10.2,10.5a3,3 0 0,1 6,0z"),
            PictogramPath("M10.6,17.2h9v2.6h-9z", alpha = 0.45f),
            PictogramPath("M10.6,16.2h9v1.2h-9z M10.6,16.2h1.3v3.6h-1.3z")
        )
    }

    private class PictogramPath(val pathData: String, val alpha: Float = 1f, val evenOdd: Boolean = false)

    private fun pictogram(name: String, vararg paths: PictogramPath): ImageVector {
        val builder = ImageVector.Builder(
            name = "Himitsu.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { path ->
            builder.addPath(
                pathData = addPathNodes(path.pathData),
                pathFillType = if (path.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black),
                fillAlpha = path.alpha
            )
        }
        return builder.build()
    }
}
