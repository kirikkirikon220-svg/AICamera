package com.aicamera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class CameraEngine(
    private val context: Context,
    private val previewView: PreviewView
) {

    var camera: Camera? = null

    var selector = CameraSelector.DEFAULT_BACK_CAMERA

    lateinit var imageCapture: ImageCapture

    lateinit var videoCapture: VideoCapture<Recorder>

    fun start(onReady: (() -> Unit)? = null) {

        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({

            val provider = future.get()

            val preview =
                androidx.camera.core.Preview.Builder().build()

            imageCapture =
                ImageCapture.Builder().build()

            val recorder =
                Recorder.Builder().build()

            videoCapture =
                VideoCapture.withOutput(recorder)

            preview.surfaceProvider =
                previewView.surfaceProvider

            provider.unbindAll()

            camera =
                provider.bindToLifecycle(
                    context as androidx.lifecycle.LifecycleOwner,
                    selector,
                    preview,
                    imageCapture,
                    videoCapture
                )

            onReady?.invoke()

        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera() {

        selector =
            if (selector ==
                CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

        start()

    }


    fun takePhotoStub() {
        // Здесь позже будет вся логика фотографирования
    }

}
