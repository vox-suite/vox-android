package `in`.voxagent.mobile.ui.kit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private val bitmaps = LruCache<String, Bitmap>(48)

private suspend fun load(url: String): Bitmap? {
    bitmaps.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            VoxHttp.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.byteStream().use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()?.also { bitmaps.put(url, it) }
    }
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, alpha: Float = 1f) {
    var bitmap by remember(url) { mutableStateOf(url?.let { bitmaps.get(it) }) }
    LaunchedEffect(url) {
        if (url != null && bitmap == null) bitmap = load(url)
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale, alpha = alpha) }
}
