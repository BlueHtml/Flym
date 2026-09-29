package net.frju.flym.ui.entrydetails

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import net.fred.feedex.R
import net.frju.flym.service.FetcherService
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.IOException
import java.util.Locale

object ImageActions {

    private const val REQUEST_WRITE_STORAGE = 4101
    private const val DOWNLOAD_FOLDER = "Flym"

    fun show(context: Context, entryId: String, imageUrl: String, originalImageUrl: String?) {
        val items = arrayOf(
                context.getString(R.string.image_action_share),
                context.getString(R.string.image_action_copy),
                context.getString(R.string.image_action_open)
        )

        android.app.AlertDialog.Builder(context)
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> share(context, entryId, imageUrl, originalImageUrl)
                        1 -> copyToDownloads(context, entryId, imageUrl, originalImageUrl)
                        2 -> open(context, entryId, imageUrl, originalImageUrl)
                    }
                }
                .show()
    }

    private fun share(context: Context, entryId: String, imageUrl: String, originalImageUrl: String?) {
        prepare(context, entryId, imageUrl, originalImageUrl) { file, mimeType ->
            val contentUri = FileProvider.getUriForFile(context, providerAuthority(context), file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newRawUri("image", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.image_action_share)))
            } catch (_: Exception) {
                Toast.makeText(context, R.string.cant_open_image, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun open(context: Context, entryId: String, imageUrl: String, originalImageUrl: String?) {
        prepare(context, entryId, imageUrl, originalImageUrl) { file, mimeType ->
            val contentUri = FileProvider.getUriForFile(context, providerAuthority(context), file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                clipData = ClipData.newRawUri("image", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.image_action_open)))
            } catch (_: Exception) {
                Toast.makeText(context, R.string.cant_open_image, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToDownloads(context: Context, entryId: String, imageUrl: String, originalImageUrl: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            findActivity(context)?.let {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_STORAGE)
                Toast.makeText(context, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(context, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        prepare(context, entryId, imageUrl, originalImageUrl) { file, mimeType ->
            doAsync {
                try {
                    val name = uniqueDownloadFileName(context, mimeType, originalImageUrl ?: imageUrl)
                    val savedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        copyToDownloadsQ(context, file, mimeType, name)
                    } else {
                        copyToDownloadsLegacy(context, file, name)
                    }
                    uiThread {
                        if (savedUri != null) {
                            Toast.makeText(context, context.getString(R.string.image_saved_to, DOWNLOAD_FOLDER), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.image_save_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    uiThread {
                        Toast.makeText(context, R.string.image_save_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun prepare(context: Context, entryId: String, imageUrl: String, originalImageUrl: String?, onReady: (File, String) -> Unit) {
        doAsync {
            try {
                val source = resolveSource(imageUrl, originalImageUrl)
                val file = FetcherService.ensureImageDownloaded(entryId, source)
                val mimeType = guessMimeType(source, file)
                uiThread { onReady(file, mimeType) }
            } catch (_: Exception) {
                uiThread {
                    Toast.makeText(context, R.string.image_download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resolveSource(imageUrl: String, originalImageUrl: String?): String {
        if (imageUrl.startsWith("file:", ignoreCase = true) && !originalImageUrl.isNullOrBlank()) {
            return originalImageUrl
        }
        return imageUrl
    }

    private fun providerAuthority(context: Context): String = context.packageName + ".fileprovider"

    private fun guessMimeType(sourceUrl: String, file: File): String {
        val uri = Uri.parse(sourceUrl)
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).toLowerCase(Locale.US)
        val type = if (extension.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) else null
        if (type != null && type.startsWith("image/")) {
            return type
        }

        val detectedType = detectImageMimeType(file)
        if (detectedType != null) {
            return detectedType
        }

        val fileExtension = file.extension.toLowerCase(Locale.US)
        val fileType = if (fileExtension.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension) else null
        return if (fileType?.startsWith("image/") == true) fileType else "image/*"
    }

    private fun uniqueDownloadFileName(context: Context, mimeType: String, sourceUrl: String): String {
        val sourceName = Uri.decode(Uri.parse(sourceUrl).lastPathSegment.orEmpty())
                .substringAfterLast('/')
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
        val baseName = if (sourceName.isNotEmpty() && !sourceName.matches(Regex("[0-9a-fA-F]{40}"))) sourceName else "image"
        val extension = extensionFor(baseName, mimeType)
        val nameWithExtension = if (extension.isEmpty()) baseName else if (baseName.endsWith(extension, true)) baseName else baseName + extension

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = downloadRelativePath()
            var candidate = nameWithExtension
            var index = 1
            while (existsInDownloads(context, relativePath, candidate)) {
                candidate = addSuffix(nameWithExtension, index++)
            }
            return candidate
        }

        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FOLDER)
        var candidate = nameWithExtension
        var index = 1
        while (File(directory, candidate).exists()) {
            candidate = addSuffix(nameWithExtension, index++)
        }
        return candidate
    }

    private fun extensionFor(name: String, mimeType: String): String {
        val dot = name.lastIndexOf('.')
        if (dot > 0 && dot < name.length - 1) {
            return ""
        }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return if (!extension.isNullOrBlank()) "." + extension else ".jpg"
    }

    private fun addSuffix(name: String, index: Int): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) {
            name.substring(0, dot) + " (" + index + ")" + name.substring(dot)
        } else {
            name + " (" + index + ")"
        }
    }

    private fun downloadRelativePath(): String =
            Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_FOLDER + "/"

    private fun detectImageMimeType(file: File): String? {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read >= 3 && header[0].toInt() and 0xFF == 0xFF && header[1].toInt() and 0xFF == 0xD8 && header[2].toInt() and 0xFF == 0xFF) {
                    "image/jpeg"
                } else if (read >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(
                                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                        ))) {
                    "image/png"
                } else if (read >= 6 && String(header, 0, 6, Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }) {
                    "image/gif"
                } else if (read >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP") {
                    "image/webp"
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun existsInDownloads(context: Context, relativePath: String, displayName: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = MediaStore.MediaColumns.RELATIVE_PATH + " = ? AND " + MediaStore.MediaColumns.DISPLAY_NAME + " = ?"
        context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(relativePath, displayName),
                null
        ).use { cursor ->
            return cursor?.moveToFirst() == true
        }
    }

    private fun copyToDownloadsQ(context: Context, source: File, mimeType: String, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, downloadRelativePath())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri).use { output ->
                if (output == null) throw IOException("Unable to open output stream")
                source.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun copyToDownloadsLegacy(context: Context, source: File, displayName: String): Uri? {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FOLDER)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create download directory")
        }

        val target = File(directory, displayName)
        val temp = File(directory, "." + displayName + ".flym.tmp")
        try {
            source.inputStream().use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!temp.renameTo(target)) {
                throw IOException("Unable to finalize downloaded image")
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        return Uri.fromFile(target)
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
        }
        return current as? Activity
    }
}
