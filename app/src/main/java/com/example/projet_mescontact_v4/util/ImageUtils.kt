package com.example.projet_mescontact_v4.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Outils photo : import (galerie/caméra) vers le stockage interne et décodage réduit. */
object ImageUtils {

    fun photosDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(context: Context, prefix: String): File =
        File(photosDir(context), "${prefix}_${System.nanoTime()}.jpg")

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    /** Décode [path] réduit à environ [maxSide] px ; null si absent ou illisible. */
    fun decodeScaled(path: String?, maxSide: Int): Bitmap? {
        if (path == null || !File(path).exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copie [source] dans [dest] en JPEG, réduite et redressée selon l'EXIF.
     * @return false si l'image est illisible (le fichier [dest] est alors supprimé).
     */
    fun importPhoto(context: Context, source: Uri, dest: File, maxSide: Int = 1024): Boolean {
        val resolver = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            }
            val bitmap = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            val orientation = try {
                resolver.openInputStream(source)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                }
            }
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    fun deleteQuietly(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}
