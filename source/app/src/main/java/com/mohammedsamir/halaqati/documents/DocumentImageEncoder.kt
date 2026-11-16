package com.mohammedsamir.halaqati.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Keeps document assets small before Chromium embeds them into PDF. */
object DocumentImageEncoder {
    /** Length cap (characters of the data URL, about the bytes stored in the database) for a picked photo or logo. */
    const val MAX_STORED_IMAGE_CHARS = 100_000

    fun resourceJpeg(context: Context, resId: Int, maxDimension: Int = 520, quality: Int = 84): String? {
        val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return null
        return try { encode(bitmap, maxDimension, transparent = false, jpegQuality = quality) } finally { bitmap.recycle() }
    }

    /** RC40: a picked photo becomes a small square-ish JPEG data URL (<= maxDimension px) that is stored in students.photo_path. */
    fun uriJpeg(context: Context, uri: android.net.Uri, maxDimension: Int = 320, quality: Int = 82, maxChars: Int = MAX_STORED_IMAGE_CHARS, squareCrop: Boolean = false): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        // RC79: portraits are cut to a centred square so they fill a round / rounded frame without stretching.
        val bitmap = if (squareCrop && decoded.width != decoded.height) {
            val side = minOf(decoded.width, decoded.height)
            Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side).also { if (it !== decoded) decoded.recycle() }
        } else decoded
        try {
            // RC74: every stored photo / logo stays under ~100 KB so the database does not grow: lower the quality first, then the size.
            var dim = maxDimension
            var q = quality
            var out = encode(bitmap, dim, transparent = false, jpegQuality = q)
            var guard = 0
            while (out.length > maxChars && guard < 16) {
                if (q > 50) q -= 10 else dim = (dim * 0.8f).toInt().coerceAtLeast(96)
                out = encode(bitmap, dim, transparent = false, jpegQuality = q)
                guard++
            }
            out
        } finally { bitmap.recycle() }
    }.getOrNull()

    fun dataUrl(value: String?, maxDimension: Int, transparent: Boolean): String? {
        if (value.isNullOrBlank() || !value.startsWith("data:image/")) return null
        val raw = runCatching { Base64.decode(value.substringAfter(','), Base64.DEFAULT) }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        return try { encode(bitmap, maxDimension, transparent = transparent, jpegQuality = 84) } finally { bitmap.recycle() }
    }

    private fun encode(source: Bitmap, maxDimension: Int, transparent: Boolean, jpegQuality: Int): String {
        val max = maxOf(source.width, source.height).coerceAtLeast(1)
        val scale = minOf(1f, maxDimension.toFloat() / max.toFloat())
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (targetW == source.width && targetH == source.height) source
        else Bitmap.createScaledBitmap(source, targetW, targetH, true)
        val flattened = if (transparent) scaled else Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).also { out ->
            Canvas(out).apply {
                drawColor(Color.WHITE)
                drawBitmap(scaled, 0f, 0f, null)
            }
        }
        return try {
            val bytes = ByteArrayOutputStream().use { out ->
                if (transparent) flattened.compress(Bitmap.CompressFormat.PNG, 100, out)
                else flattened.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 95), out)
                out.toByteArray()
            }
            val mime = if (transparent) "image/png" else "image/jpeg"
            "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            if (flattened !== scaled) flattened.recycle()
            if (scaled !== source) scaled.recycle()
        }
    }
}
