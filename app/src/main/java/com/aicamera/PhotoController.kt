package com.aicamera

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class PhotoController(
    private val context: Context
) {

    fun takePhoto(imageCapture: ImageCapture) {

        val name = "AICamera_" +
            SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault()
            ).format(Date()) + ".jpg"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        val options =
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()

        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    Toast.makeText(
                        context,
                        "Фото сохранено",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(
                    exc: ImageCaptureException
                ) {
                    Toast.makeText(
                        context,
                        exc.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}
