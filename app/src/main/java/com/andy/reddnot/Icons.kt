package com.andy.reddnot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** A few Material icons that aren't in material-icons-core. */
object RnIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()

    val Bookmark = icon(
        "Bookmark",
        "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z",
    )
    val BookmarkBorder = icon(
        "BookmarkBorder",
        "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z",
    )
    val Sort = icon(
        "Sort",
        "M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z",
    )
    val OpenInBrowser = icon(
        "OpenInBrowser",
        "M19,4H5c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h4v-2H5V8h14v10h-4v2h4c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.89,-2 -2,-2zM12,10l-4,4h3v6h2v-6h3l-4,-4z",
    )
}
