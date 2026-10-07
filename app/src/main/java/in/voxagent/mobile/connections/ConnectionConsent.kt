package `in`.voxagent.mobile.connections

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.theme.SuccessGreen
import `in`.voxagent.mobile.ui.theme.VoidBlack

@Composable
internal fun ConnectionConsent(consent: Boolean, onConsentChange: (Boolean) -> Unit) {
    val consentBorderColor by
        animateColorAsState(
            targetValue = if (consent) SuccessGreen.copy(alpha = 0.4f) else BorderSubtle,
            label = "consentBorder",
        )
    val consentBgColor by
        animateColorAsState(
            targetValue = if (consent) SuccessGreen.copy(alpha = 0.08f) else VoidBlack,
            label = "consentBg",
        )

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(consentBgColor)
                .border(BorderStroke(1.dp, consentBorderColor), RoundedCornerShape(14.dp))
                .clickable { onConsentChange(!consent) }
                .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield_check),
            contentDescription = null,
            tint = if (consent) SuccessGreen else SmokeDark,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text =
                "I allow Vox to sync activity to my timeline and read connected account data when helping me. I can turn either use off independently.",
            color = Mist,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = consent,
            onCheckedChange = { onConsentChange(it) },
            colors =
                CheckboxDefaults.colors(
                    checkedColor = SuccessGreen,
                    checkmarkColor = VoidBlack,
                    uncheckedColor = SmokeDark,
                ),
            modifier = Modifier.size(20.dp),
        )
    }
}
