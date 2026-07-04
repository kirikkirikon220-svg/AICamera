package com.aicamera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture

class CameraController {

    var camera: Camera? = null

    var imageCapture: ImageCapture? = null

    var videoCapture: VideoCapture<Recorder>? = null

    var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    var flashMode = ImageCapture.FLASH_MODE_AUTO

    fun nextFlashMode(): Int {

        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }

        imageCapture?.flashMode = flashMode

        return flashMode
    }

    fun switchCamera(): CameraSelector {

        cameraSelector =
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

        return cameraSelector
    }
}
