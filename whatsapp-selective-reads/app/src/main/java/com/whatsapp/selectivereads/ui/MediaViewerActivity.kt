package com.whatsapp.selectivereads.ui

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.whatsapp.selectivereads.databinding.ActivityMediaViewerBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewerBinding

    companion object {
        const val EXTRA_MEDIA_PATH = "media_path"
        const val EXTRA_MEDIA_TYPE = "media_type"
        const val EXTRA_MESSAGE_TEXT = "message_text"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_TIMESTAMP = "timestamp"
        private const val TAG = "MediaViewerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mediaPath = intent.getStringExtra(EXTRA_MEDIA_PATH) ?: run {
            finish()
            return
        }
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "application/octet-stream"
        val messageText = intent.getStringExtra(EXTRA_MESSAGE_TEXT) ?: ""
        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: ""
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())

        setupToolbar(senderName)
        setupInfoPanel(senderName, messageText, timestamp)

        Log.d(TAG, "Media path: $mediaPath, type: $mediaType")

        val resolvedPath = resolveMediaPath(mediaPath, mediaType)
        if (resolvedPath != null) {
            Log.d(TAG, "Resolved to: $resolvedPath")
            setupMediaDisplay(resolvedPath, mediaType)
            setupDownloadButton(resolvedPath, mediaType)
        } else {
            Log.e(TAG, "Could not resolve media: $mediaPath")
            binding.mediaImageView.visibility = View.GONE
            binding.mediaNotAvailable.visibility = View.VISIBLE
            binding.mediaNotAvailable.text = "Media not available"
            binding.downloadButton.visibility = View.GONE
        }
    }

    private fun resolveMediaPath(mediaPath: String, mediaType: String): String? {
        // First, check if it's already a valid file path
        val file = File(mediaPath)
        if (file.exists() && file.length() > 0) return mediaPath

        // Handle content:// URIs (WhatsApp notification media)
        if (mediaPath.startsWith("content://") || mediaPath.startsWith("file://")) {
            return try {
                val uri = Uri.parse(mediaPath)
                Log.d(TAG, "Trying to copy content URI: $uri")

                val ext = when {
                    mediaType.startsWith("image/") -> ".jpg"
                    mediaType.startsWith("video/") -> ".mp4"
                    mediaType.startsWith("audio/") -> ".ogg"
                    mediaType.startsWith("application/pdf") -> ".pdf"
                    else -> ".bin"
                }

                val tempFile = File(cacheDir, "media_${System.currentTimeMillis()}$ext")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    Log.d(TAG, "Copied to: ${tempFile.absolutePath}")
                    tempFile.absolutePath
                } else {
                    Log.e(TAG, "Copied file is empty")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy content URI: ${e.message}", e)
                null
            }
        }

        // Try as absolute path (maybe stored without content:// prefix)
        val tryFile = File(mediaPath)
        if (tryFile.isAbsolute && tryFile.exists()) return tryFile.absolutePath

        // Try internal storage pattern from our saveContentUriToInternal
        val internalFile = File(filesDir, "media/${conversationId()}/${File(mediaPath).name}")
        if (internalFile.exists()) return internalFile.absolutePath

        return null
    }

    private fun conversationId(): String {
        return intent.getStringExtra(EXTRA_MESSAGE_TEXT)?.hashCode()?.toString() ?: ""
    }

    private fun setupToolbar(senderName: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = senderName
    }

    private fun setupMediaDisplay(mediaPath: String, mediaType: String) {
        when {
            mediaType.startsWith("image/") -> {
                binding.mediaImageView.visibility = View.VISIBLE
                binding.mediaTypeIcon.visibility = View.GONE
                binding.mediaNotAvailable.visibility = View.GONE

                val bitmap = BitmapFactory.decodeFile(mediaPath)
                if (bitmap != null) {
                    binding.mediaImageView.setImageBitmap(bitmap)
                } else {
                    binding.mediaNotAvailable.visibility = View.VISIBLE
                    binding.mediaNotAvailable.text = "Unable to decode image"
                }
            }
            mediaType.startsWith("video/") -> {
                binding.mediaImageView.visibility = View.GONE
                binding.mediaTypeIcon.visibility = View.VISIBLE
                binding.mediaNotAvailable.visibility = View.GONE
                binding.mediaTypeIcon.setImageResource(android.R.drawable.ic_media_play)
                binding.mediaTypeLabel.text = "Video file available for download"
            }
            mediaType.startsWith("audio/") -> {
                binding.mediaImageView.visibility = View.GONE
                binding.mediaTypeIcon.visibility = View.VISIBLE
                binding.mediaNotAvailable.visibility = View.GONE
                binding.mediaTypeIcon.setImageResource(android.R.drawable.ic_btn_speak_now)
                binding.mediaTypeLabel.text = "Audio file available for download"
            }
            else -> {
                binding.mediaImageView.visibility = View.GONE
                binding.mediaTypeIcon.visibility = View.VISIBLE
                binding.mediaNotAvailable.visibility = View.GONE
                binding.mediaTypeIcon.setImageResource(android.R.drawable.ic_menu_save)
                binding.mediaTypeLabel.text = "Document available for download"
            }
        }
    }

    private fun setupInfoPanel(senderName: String, messageText: String, timestamp: Long) {
        binding.senderName.text = senderName
        binding.messageText.text = messageText
        binding.timestamp.text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            .format(Date(timestamp))
    }

    private fun setupDownloadButton(mediaPath: String, mediaType: String) {
        binding.downloadButton.setOnClickListener {
            downloadMedia(mediaPath, mediaType)
        }
    }

    private fun downloadMedia(sourcePath: String, mimeType: String) {
        try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                Snackbar.make(binding.root, "Source file not found", Snackbar.LENGTH_SHORT).show()
                return
            }

            val ext = when {
                mimeType.startsWith("image/") -> ".jpg"
                mimeType.startsWith("video/") -> ".mp4"
                mimeType.startsWith("audio/") -> ".ogg"
                mimeType.startsWith("application/pdf") -> ".pdf"
                else -> ".bin"
            }

            val fileName = "WhatsApp_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SelectiveReadReceipts")
                }

                val uri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Snackbar.make(binding.root, "Saved to Downloads/SelectiveReadReceipts", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Failed to save file", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "SelectiveReadReceipts")
                targetDir.mkdirs()
                val targetFile = File(targetDir, fileName)
                sourceFile.copyTo(targetFile, overwrite = true)
                Snackbar.make(binding.root, "Saved to ${targetFile.absolutePath}", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Download failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
