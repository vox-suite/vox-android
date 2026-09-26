@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package `in`.voxagent.mobile.ui.theme

import `in`.voxagent.mobile.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Same Inter weights vox-web loads via next/font/google (400/500/600). */
val VoxInterFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** vox-web's heading font (used at weight 600 in its hero). */
val VoxFunnelDisplayFontFamily = FontFamily(
    Font(R.font.funnel_display_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.funnel_display_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
