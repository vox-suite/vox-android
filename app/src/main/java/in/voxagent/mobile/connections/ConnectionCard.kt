package `in`.voxagent.mobile.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.ui.VoxStatusPill
import `in`.voxagent.mobile.ui.VoxStatusTone
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.theme.VoxSpaceGroteskFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun BrandMark(
    brand: BrandMeta,
    name: String,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier,
) {
    if (brand.bare) {
        Image(
            painter = painterResource(brand.iconRes),
            contentDescription = name,
            modifier = modifier.size(size).clip(RoundedCornerShape(11.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier =
                modifier
                    .size(size)
                    .clip(RoundedCornerShape(11.dp))
                    .background(brand.color.copy(alpha = 0.15f))
                    .border(
                        BorderStroke(1.dp, brand.color.copy(alpha = 0.35f)),
                        RoundedCornerShape(11.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(brand.iconRes),
                contentDescription = name,
                modifier = Modifier.size(size * 0.58f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
internal fun ConnectionCard(
    connector: ConnectorDescriptor,
    connection: ConnectionItem?,
    brand: BrandMeta,
    onClick: () -> Unit,
) {
    val isConnected = connection != null
    val needsAttention =
        connection != null &&
            (!connection.failure_code.isNullOrBlank() ||
                connection.authorization_state != "authorized")

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0E0F12))
                .border(
                    BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.7f)),
                    RoundedCornerShape(18.dp),
                )
                .drawBehind {
                    val glowRadius = 240.dp.toPx()
                    val glowCenter = Offset(size.width - 10.dp.toPx(), 10.dp.toPx())
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                0.0f to brand.color.copy(alpha = 0.40f),
                                0.35f to brand.color.copy(alpha = 0.18f),
                                0.70f to brand.color.copy(alpha = 0.03f),
                                1.0f to Color.Transparent,
                                center = glowCenter,
                                radius = glowRadius,
                            ),
                        radius = glowRadius,
                        center = glowCenter,
                    )
                }
                .clickable(onClick = onClick)
                .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = connector.name,
                        color = Mist,
                        fontFamily = VoxSpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = brand.tagline,
                        color = SmokeDark,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                BrandMark(brand = brand, name = connector.name, size = 46.dp)
            }

            Row {
                when {
                    needsAttention -> {
                        VoxStatusPill(text = "needs attention", tone = VoxStatusTone.Warning)
                    }
                    isConnected -> {
                        VoxStatusPill(text = "connected", tone = VoxStatusTone.Success)
                    }
                    else -> {
                        VoxStatusPill(text = "not connected", tone = VoxStatusTone.Neutral)
                    }
                }
            }

            Text(text = connector.description, color = Smoke, fontSize = 13.sp, lineHeight = 18.sp)

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (isConnected) BorderSubtle.copy(alpha = 0.5f)
                            else brand.color.copy(alpha = 0.18f),
                        contentColor = if (isConnected) Mist else brand.color,
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        if (isConnected) BorderSubtle else brand.color.copy(alpha = 0.45f),
                    ),
                modifier = Modifier.fillMaxWidth().height(40.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (connector.id == "youtube") R.drawable.ic_refresh
                                else if (isConnected) R.drawable.ic_settings else R.drawable.ic_plug
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (isConnected) Mist else brand.color,
                    )
                    Text(
                        text =
                            if (connector.id == "youtube") "Sync"
                            else if (isConnected) "Configure" else "Connect",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

internal fun formatSyncDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "Not yet synced"
    return try {
        val instant = Instant.parse(dateStr)
        val formatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        dateStr
    }
}
