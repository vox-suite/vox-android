package `in`.voxagent.mobile.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.ui.kit.VoxChip
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.theme.VoxSpaceGroteskFontFamily

@Composable
internal fun ConnectionsHeader(
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                Box(
                    modifier =
                        Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(Obsidian)
                            .border(BorderStroke(1.dp, BorderSubtle), CircleShape)
                            .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", color = Mist, fontSize = 24.sp, fontWeight = FontWeight.Light)
                }
            }
            Column {
                Text(
                    text = "Connected Apps",
                    color = Mist,
                    fontFamily = VoxSpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                )
                Text(
                    text = "Sync accounts with your Vox timeline",
                    color = GraphiteDark,
                    fontSize = 12.sp,
                )
            }
        }

        Box(
            modifier =
                Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(Obsidian)
                    .border(BorderStroke(1.dp, BorderSubtle), CircleShape)
                    .clickable(enabled = !isRefreshing, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    color = Mist,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    tint = Smoke,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian)
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = SmokeDark,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(text = "Search apps & connectors…", color = SmokeDark, fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle =
                        androidx.compose.ui.text.TextStyle(
                            color = Mist,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Default,
                        ),
                    cursorBrush = SolidColor(Mist),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier.size(24.dp).clip(CircleShape).clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = SmokeDark, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun FilterChips(
    filter: ConnectionFilter,
    counts: Triple<Int, Int, Int>,
    onFilterSelect: (ConnectionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (allCount, connCount, availCount) = counts
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoxChip(
            text = "All ($allCount)",
            selected = filter == ConnectionFilter.ALL,
            onClick = { onFilterSelect(ConnectionFilter.ALL) },
        )
        VoxChip(
            text = "Connected ($connCount)",
            selected = filter == ConnectionFilter.CONNECTED,
            onClick = { onFilterSelect(ConnectionFilter.CONNECTED) },
        )
        VoxChip(
            text = "Available ($availCount)",
            selected = filter == ConnectionFilter.AVAILABLE,
            onClick = { onFilterSelect(ConnectionFilter.AVAILABLE) },
        )
    }
}
