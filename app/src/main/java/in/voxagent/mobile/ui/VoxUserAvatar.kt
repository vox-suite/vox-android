package `in`.voxagent.mobile.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.PureWhite
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

@Composable
fun VoxUserAvatar(
    avatarUrl: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    var avatarBitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val cacheKey = avatarUrl.hashCode().toString()
                val cacheFile = File(context.cacheDir, "vox_user_avatar_$cacheKey.png")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val cachedBm = BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (cachedBm != null) {
                        avatarBitmap = cachedBm.asImageBitmap()
                        return@withContext
                    }
                }

                val bytes: ByteArray? =
                    if (avatarUrl.startsWith("content://") || avatarUrl.startsWith("file://")) {
                        context.contentResolver.openInputStream(avatarUrl.toUri())?.use {
                            it.readBytes()
                        }
                    } else {
                        val request =
                            Request.Builder()
                                .url(avatarUrl)
                                .header(
                                    "User-Agent",
                                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36",
                                )
                                .build()
                        VoxHttp.client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body.bytes()
                            } else null
                        }
                    }

                if (bytes != null && bytes.isNotEmpty()) {
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (decoded != null) {
                        runCatching { cacheFile.writeBytes(bytes) }
                        avatarBitmap = decoded.asImageBitmap()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Obsidian)
                .border(BorderStroke(1.dp, BorderSubtle), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = avatarBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = displayName ?: "Google User Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!displayName.isNullOrBlank()) {
            Text(
                text = displayName.trim().first().uppercase(),
                color = PureWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = "Google Account Avatar",
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}
