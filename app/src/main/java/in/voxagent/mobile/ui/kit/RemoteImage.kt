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
private val blurredBitmaps = LruCache<String, Bitmap>(24)

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

private fun fastBlur(src: Bitmap): Bitmap {
    val scaleDown = 6
    val w = (src.width / scaleDown).coerceAtLeast(16)
    val h = (src.height / scaleDown).coerceAtLeast(16)
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    boxBlur(pixels, w, h, radius = 6)
    boxBlur(pixels, w, h, radius = 6)
    small.setPixels(pixels, 0, w, 0, 0, w, h)
    return small
}

private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
    val r = radius.coerceAtLeast(1)
    val temp = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
        val count = 2 * r + 1
        for (i in -r..r) {
            val px = pixels[row + i.coerceIn(0, w - 1)]
            aSum += (px ushr 24) and 0xFF
            rSum += (px ushr 16) and 0xFF
            gSum += (px ushr 8) and 0xFF
            bSum += px and 0xFF
        }
        for (x in 0 until w) {
            temp[row + x] = ((aSum / count) shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            val pOut = pixels[row + (x - r).coerceIn(0, w - 1)]
            val pIn = pixels[row + (x + r + 1).coerceIn(0, w - 1)]
            aSum += ((pIn ushr 24) and 0xFF) - ((pOut ushr 24) and 0xFF)
            rSum += ((pIn ushr 16) and 0xFF) - ((pOut ushr 16) and 0xFF)
            gSum += ((pIn ushr 8) and 0xFF) - ((pOut ushr 8) and 0xFF)
            bSum += (pIn and 0xFF) - (pOut and 0xFF)
        }
    }
    for (x in 0 until w) {
        var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
        val count = 2 * r + 1
        for (i in -r..r) {
            val px = temp[i.coerceIn(0, h - 1) * w + x]
            aSum += (px ushr 24) and 0xFF
            rSum += (px ushr 16) and 0xFF
            gSum += (px ushr 8) and 0xFF
            bSum += px and 0xFF
        }
        for (y in 0 until h) {
            val col = y * w + x
            pixels[col] = ((aSum / count) shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            val pOut = temp[(y - r).coerceIn(0, h - 1) * w + x]
            val pIn = temp[(y + r + 1).coerceIn(0, h - 1) * w + x]
            aSum += ((pIn ushr 24) and 0xFF) - ((pOut ushr 24) and 0xFF)
            rSum += ((pIn ushr 16) and 0xFF) - ((pOut ushr 16) and 0xFF)
            gSum += ((pIn ushr 8) and 0xFF) - ((pOut ushr 8) and 0xFF)
            bSum += (pIn and 0xFF) - (pOut and 0xFF)
        }
    }
}

@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
    blur: Boolean = false,
) {
    var bitmap by remember(url, blur) {
        mutableStateOf(url?.let { if (blur) blurredBitmaps.get(it) else bitmaps.get(it) })
    }
    LaunchedEffect(url, blur) {
        if (url != null && bitmap == null) {
            val loaded = load(url)
            if (loaded != null) {
                bitmap = if (blur) {
                    blurredBitmaps.get(url) ?: withContext(Dispatchers.Default) {
                        fastBlur(loaded).also { blurredBitmaps.put(url, it) }
                    }
                } else loaded
            }
        }
    }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            alpha = alpha,
        )
    }
}
