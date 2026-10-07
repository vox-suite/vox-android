package `in`.voxagent.mobile.spaces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*

@Composable
internal fun SpaceLibraryCard(space: Space, busy: Boolean, onOpen: () -> Unit, onDrop: () -> Unit) {
    Surface(
        onClick = onOpen,
        enabled = !busy,
        color = Color(0xFF0A0A0A),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusLabel(if (space.run_state == "running") "running" else space.state)
                IconButton(onClick = onDrop, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        "Drop ${space.title}",
                        tint = SmokeDark,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(space.title, color = Mist, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(
                space.intent,
                color = SmokeDark,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            HorizontalDivider(
                color = BorderSubtle,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    spaceDate(space.created_at),
                    color = SmokeDark,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
                Text("Open →", color = Mist, fontSize = 11.sp)
            }
        }
    }
}
