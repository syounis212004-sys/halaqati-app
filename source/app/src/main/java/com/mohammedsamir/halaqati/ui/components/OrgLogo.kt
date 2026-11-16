package com.mohammedsamir.halaqati.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object LogoCache { val cache = object : LruCache<String, ImageBitmap>(24) {} }

/** RC74: the saved logo (a small JPEG data URL) of an association, centre, mosque or halaqa; the gradient icon is the fallback. */
@Composable
fun OrgLogo(logo: String?, fallback: ImageVector, pair: GradientPair, tile: Dp = 56.dp, glyph: Dp = 28.dp, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, logo) {
        value = if (logo.isNullOrBlank() || !logo.startsWith("data:image/")) null else {
            val key = logo.hashCode().toString() + ":" + logo.length
            LogoCache.cache.get(key) ?: withContext(Dispatchers.Default) {
                runCatching {
                    val raw = Base64.decode(logo.substringAfter(','), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()
                }.getOrNull()
            }?.also { LogoCache.cache.put(key, it) }
        }
    }
    val img = image
    if (img == null) Box(modifier.size(tile).clip(RoundedCornerShape(tile / 3)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(fallback, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(if (glyph > 28.dp) 28.dp else glyph))
    }
    else Box(modifier.size(tile).clip(RoundedCornerShape(tile / 3)).background(Color.White), contentAlignment = Alignment.Center) {
        Image(img, contentDescription = "الشعار", contentScale = ContentScale.Crop, modifier = Modifier.size(tile))
    }
}
