package com.mohammedsamir.halaqati.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.ui.theme.textAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object AvatarCache {
    val cache = object : LruCache<String, ImageBitmap>(48) {}
}

/**
 * RC40: a round student avatar. With a saved photo (a data URL in students.photo_path) and the "show photos" setting on, it
 * draws the photo inside a thin gold ring (like a story); otherwise it draws the first letter on an emerald tint.
 */
@Composable
fun StudentAvatar(photoPath: String?, name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val show by AppGraph.uiPreferences.state.collectAsState()
    val usable = show.showStudentPhotos && photoPath?.startsWith("data:image/") == true
    val image by produceState<ImageBitmap?>(null, photoPath, usable) {
        value = if (!usable) null else {
            val key = photoPath.hashCode().toString() + ":" + photoPath!!.length
            AvatarCache.cache.get(key) ?: withContext(Dispatchers.Default) {
                runCatching {
                    val raw = Base64.decode(photoPath.substringAfter(','), Base64.DEFAULT)
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)?.asImageBitmap()
                }.getOrNull()
            }?.also { AvatarCache.cache.put(key, it) }
        }
    }
    val ring = Brush.sweepGradient(listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
    val frame: androidx.compose.ui.graphics.Shape = if (show.avatarShape == com.mohammedsamir.halaqati.data.AvatarShape.ROUNDED) androidx.compose.foundation.shape.RoundedCornerShape(size * 0.22f) else CircleShape
    val showRing = show.avatarShape != com.mohammedsamir.halaqati.data.AvatarShape.CIRCLE
    Box(
        modifier.size(size).clip(frame).let { if (image != null && showRing) it.border(2.dp, ring, frame) else it }
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(if (showRing) size - 4.dp else size).clip(frame))
        } else {
            Text(avatarInitial(name), fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.42f).sp, color = textAccent("primary"))
        }
    }
}

private val AVATAR_TITLES = setOf("dr", "prof", "eng", "mr", "mrs", "ms", "د", "أ", "م", "أ.د", "الشيخ", "الأستاذ", "الدكتور", "المهندس")

/** RC90: the letter shown when there is no photo; titles (Dr., د., أ.) are skipped so «Dr. mohammed» gives M, not D. */
internal fun avatarInitial(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val word = words.firstOrNull { it.trimEnd('.').lowercase() !in AVATAR_TITLES } ?: words.firstOrNull() ?: return "ط"
    return word.take(1).uppercase()
}
