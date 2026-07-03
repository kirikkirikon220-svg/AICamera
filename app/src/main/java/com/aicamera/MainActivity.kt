package com.aicamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.MediaStoreOutputOptions

import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: Recording? = null
    private var isRecording = false

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private lateinit var btnCapture: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnSettings: ImageButton

    private lateinit var modePhoto: TextView
    private lateinit var modeVideo: TextView
    private lateinit var txtTimer: TextView
    private lateinit var recordDot: TextView

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var seconds = 0
    private val timerRunnable = object : Runnable {
        override fun run() {
            seconds++

            val min = seconds / 60
            val sec = seconds % 60

            txtTimer.text = String.format("%02d:%02d", min, sec)

            timerHandler.postDelayed(this, 1000)
        }
    }

    private var videoMode = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)

        btnCapture = findViewById(R.id.btnCapture)
        btnGallery = findViewById(R.id.btnGallery)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnFlash = findViewById(R.id.btnFlash)
        btnSettings = findViewById(R.id.btnSettings)

        modePhoto = findViewById(R.id.modePhoto)
        modeVideo = findViewById(R.id.modeVideo)
        txtTimer = findViewById(R.id.txtTimer)
        recordDot = findViewById(R.id.recordDot)
        recordDot.visibility = android.view.View.GONE
        txtTimer.visibility = android.view.View.GONE

        btnCapture.setOnClickListener {

            if (videoMode) {
                recordVideo()
            } else {
                takePhoto()
            }
        }

        btnGallery.setOnClickListener {

            val intent = Intent(
                Intent.ACTION_VIEW,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )

            startActivity(intent)
        }

        btnSwitchCamera.setOnClickListener {

            cameraSelector =
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA

            startCamera()
        }

        btnFlash.setOnClickListener {

            camera?.cameraInfo?.let { info ->

                val enabled = info.torchState.value == 1

                camera?.cameraControl?.enableTorch(!enabled)

                Toast.makeText(
                    this,
                    if (!enabled) "Вспышка включена" else "Вспышка выключена",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnSettings.setOnClickListener {
            Toast.makeText(this, "Настройки (скоро)", Toast.LENGTH_SHORT).show()
        }

        modePhoto.setOnClickListener {

            videoMode = false

            modePhoto.setTextColor(android.graphics.Color.WHITE)
            modeVideo.setTextColor(android.graphics.Color.LTGRAY)

        }

        modeVideo.setOnClickListener {

            videoMode = true

            modeVideo.setTextColor(android.graphics.Color.WHITE)
            modePhoto.setTextColor(android.graphics.Color.LTGRAY)

        }

        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {

                    camera?.cameraInfo?.zoomState?.value?.let { zoom ->

                        val value =
                            (zoom.zoomRatio * detector.scaleFactor)
                                .coerceIn(
                                    zoom.minZoomRatio,
                                    zoom.maxZoomRatio
                                )

                        camera?.cameraControl?.setZoomRatio(value)
                    }

                    return true
                }
            })

        previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            preview.surfaceProvider = previewView.surfaceProvider

            cameraProvider.unbindAll()

            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )

        }, ContextCompat.getMainExecutor(this))
    }
    private fun takePhoto() {

        val name = "AI_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/AICamera"
                )
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Фото сохранено",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }


    private fun recordVideo() {

        if (recording != null) {
            recording?.stop()
            recording = null
            isRecording = false
            return
        }

        val name = "VID_${System.currentTimeMillis()}"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/AICamera"
                )
            }
        }

        val outputOptions =
            MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()

        var pendingRecording =
            videoCapture.output.prepareRecording(
                this,
                outputOptions
            )

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pendingRecording =
                pendingRecording.withAudioEnabled()
        }

        recording = pendingRecording.start(
            ContextCompat.getMainExecutor(this)
        ) { event ->

            when (event) {

                is VideoRecordEvent.Start -> {
                    isRecording = true
                    recordDot.visibility = android.view.View.VISIBLE
                    seconds = 0
                    txtTimer.visibility = android.view.View.VISIBLE
                    timerHandler.post(timerRunnable)
                    btnCapture.setImageResource(android.R.drawable.presence_video_online)
                }

                is VideoRecordEvent.Finalize -> {

                    isRecording = false
                    recording = null
                    timerHandler.removeCallbacks(timerRunnable)
                    seconds = 0
                    txtTimer.text = "00:00"
                    txtTimer.visibility = android.view.View.GONE
                    recordDot.visibility = android.view.View.GONE
                    btnCapture.setImageResource(android.R.drawable.ic_menu_camera)

                    Toast.makeText(
                        this,
                        "Видео сохранено",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

}
