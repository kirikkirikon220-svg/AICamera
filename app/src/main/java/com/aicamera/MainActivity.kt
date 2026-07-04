package com.aicamera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var focusLocked = false
    private var gridEnabled = false

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null


    private val horizonListener = object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {

            val x = event.values[0]
            val y = event.values[1]

            val angle = Math.toDegrees(
                kotlin.math.atan2(x.toDouble(), y.toDouble())
            ).toFloat()

            smoothAngle += (-angle - smoothAngle) * 0.15f
            horizonLine.rotation = smoothAngle

            if (kotlin.math.abs(angle) < 15f) {

                if (horizonLine.visibility != View.VISIBLE) {
                    horizonLine.alpha = 0f
                    horizonLine.visibility = View.VISIBLE
                horizonCenter.visibility = View.VISIBLE
                    horizonLine.animate().alpha(1f).setDuration(150).start()
                }

                if (kotlin.math.abs(angle) < 1f) {
                    horizonLine.setBackgroundColor(android.graphics.Color.parseColor("#FFD54F"))
                    horizonCenter.setBackgroundColor(android.graphics.Color.parseColor("#FFD54F"))
                } else {
                    horizonLine.setBackgroundColor(android.graphics.Color.WHITE)
                    horizonCenter.setBackgroundColor(android.graphics.Color.WHITE)
                }

            } else {
                if (horizonLine.visibility == View.VISIBLE) {
                    horizonLine.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction {
                            horizonLine.visibility = View.GONE
                            horizonCenter.visibility = View.GONE
                        }
                        .start()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private var downTime = 0L

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private lateinit var btnCapture: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var txtFlashMode: TextView
    private lateinit var btnTimer: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAspect: ImageButton
    private lateinit var txtAspect: TextView

    private lateinit var zoom05: Button
    private lateinit var zoom1: Button
    private lateinit var zoom2: Button
    private lateinit var zoom3: Button

    private lateinit var modePhoto: TextView
    private lateinit var modeVideo: TextView
    private lateinit var txtTimer: TextView
    private lateinit var recordDot: TextView
    private lateinit var focusRing: android.view.View
    private lateinit var horizonLine: android.view.View
    private lateinit var horizonCenter: android.view.View
    private lateinit var gridV1: android.view.View
    private lateinit var gridV2: android.view.View
    private lateinit var gridH1: android.view.View
    private lateinit var gridH2: android.view.View

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
    private var captureDelay = 0

    private val aspectModes = listOf("FULL","3:4","9:16","1:1")
    private var aspectIndex = 1

    private var smoothAngle = 0f
    private val timerValues = listOf(0, 3, 5, 10)

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)

        btnCapture = findViewById(R.id.btnCapture)
        btnGallery = findViewById(R.id.btnGallery)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnFlash = findViewById(R.id.btnFlash)
        txtFlashMode = findViewById(R.id.txtFlashMode)
        btnSettings = findViewById(R.id.btnSettings)
        btnAspect = findViewById(R.id.btnAspect)
        txtAspect = findViewById(R.id.txtAspect)

        zoom05 = findViewById(R.id.zoom05)
        zoom1 = findViewById(R.id.zoom1)
        zoom2 = findViewById(R.id.zoom2)
        zoom3 = findViewById(R.id.zoom3)

        modePhoto = findViewById(R.id.modePhoto)
        modeVideo = findViewById(R.id.modeVideo)
        txtTimer = findViewById(R.id.txtTimer)
        recordDot = findViewById(R.id.recordDot)
        focusRing = findViewById(R.id.focusRing)
        horizonLine = findViewById(R.id.horizonLine)
        horizonCenter = findViewById(R.id.horizonCenter)

        gridV1 = findViewById(R.id.gridV1)
        gridV2 = findViewById(R.id.gridV2)
        gridH1 = findViewById(R.id.gridH1)
        gridH2 = findViewById(R.id.gridH2)
        recordDot.visibility = android.view.View.GONE

        btnAspect.setOnClickListener {

            aspectIndex = (aspectIndex + 1) % aspectModes.size

            txtAspect.text = aspectModes[aspectIndex]

            val bottomPanel = findViewById<android.view.View>(R.id.bottomPanel)

            if (aspectModes[aspectIndex] == "FULL") {
                bottomPanel.setBackgroundColor(
                    android.graphics.Color.parseColor("#66000000")
                )
            } else {
                bottomPanel.setBackgroundColor(
                    android.graphics.Color.BLACK
                )
            }

            Toast.makeText(
                this,
                "Формат: ${aspectModes[aspectIndex]}",
                Toast.LENGTH_SHORT
            ).show()

        }
        txtTimer.visibility = android.view.View.GONE

        btnCapture.setOnClickListener {

            if (!videoMode && captureDelay > 0) {

                txtTimer.visibility = android.view.View.VISIBLE

                object : CountDownTimer((captureDelay * 1000).toLong(),1000){

                    override fun onTick(ms: Long){
                        txtTimer.text = "${ms / 1000 + 1}"
                    }

                    override fun onFinish(){
                        txtTimer.visibility = android.view.View.GONE
                        if (captureDelay == 0) {
                takePhoto()
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    takePhoto()
                }, (captureDelay * 1000).toLong())
            }
                    }

                }.start()

                return@setOnClickListener
            }

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

            flashMode = (flashMode + 1) % 3

            when (flashMode) {

                0 -> {
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_AUTO
                    txtFlashMode.text = "AUTO"
                    Toast.makeText(this, "Вспышка: Авто", Toast.LENGTH_SHORT).show()
                }

                1 -> {
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_ON
                    txtFlashMode.text = "ON"
                    Toast.makeText(this, "Вспышка: Вкл", Toast.LENGTH_SHORT).show()
                }

                2 -> {
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                    txtFlashMode.text = "OFF"
                    Toast.makeText(this, "Вспышка: Выкл", Toast.LENGTH_SHORT).show()
                }
            }
        }
        }

        

        previewView.setOnTouchListener { view, event ->

            scaleGestureDetector.onTouchEvent(event)

            if (event.pointerCount > 1) {
                focusLocked = false
                return@setOnTouchListener true
            }

            if (event.action == android.view.MotionEvent.ACTION_DOWN) {

                downTime = System.currentTimeMillis()

            focusRing.x = event.x - focusRing.width / 2f
            focusRing.y = event.y - focusRing.height / 2f
            focusRing.visibility = android.view.View.VISIBLE
            focusRing.scaleX = 1.5f
            focusRing.scaleY = 1.5f
            focusRing.alpha = 0f

            focusRing.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(180)
                .start()

            val factory = SurfaceOrientedMeteringPointFactory(
                view.width.toFloat(),
                view.height.toFloat()
            )

            val point = factory.createPoint(
                event.x,
                event.y
            )

            val builder = FocusMeteringAction.Builder(point)

            if (!focusLocked) {
                builder.setAutoCancelDuration(3, TimeUnit.SECONDS)
            }

            val action = builder.build()

            camera?.cameraControl?.startFocusAndMetering(action)

            focusRing.postDelayed({
                focusRing.visibility = android.view.View.GONE
            }, 800)
            }

            if (event.action == android.view.MotionEvent.ACTION_UP) {

                val holdTime =
                    System.currentTimeMillis() - downTime

                if (holdTime >= 1000) {
                    focusLocked = !focusLocked

                    Toast.makeText(
                        this,
                        if (focusLocked) "AE/AF Lock включен"
                        else "AE/AF Lock выключен",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            true
        }


        btnSettings.setOnClickListener {

            gridEnabled = !gridEnabled

            val state =
                if (gridEnabled)
                    android.view.View.VISIBLE
                else
                    android.view.View.GONE

            gridV1.visibility = state
            gridV2.visibility = state
            gridH1.visibility = state
            gridH2.visibility = state

            Toast.makeText(
                this,
                if (gridEnabled) "Сетка включена"
                else "Сетка выключена",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnTimer.setOnClickListener {

            val index = timerValues.indexOf(captureDelay)
            captureDelay = timerValues[(index + 1) % timerValues.size]

            Toast.makeText(
                this,
                if (captureDelay == 0)
                    "Таймер выключен"
                else
                    "Таймер ${captureDelay} сек",
                Toast.LENGTH_SHORT
            ).show()
        }


        zoom05.setOnClickListener {
            camera?.cameraControl?.setZoomRatio(0.5f)
        }

        zoom1.setOnClickListener {
            camera?.cameraControl?.setZoomRatio(1.0f)
        }

        zoom2.setOnClickListener {
            camera?.cameraControl?.setZoomRatio(2.0f)
        }


        



        modePhoto.setOnClickListener {

            videoMode = false

            modePhoto.setBackgroundResource(R.drawable.mode_selected)
            modePhoto.setTextColor(android.graphics.Color.BLACK)

            modeVideo.setBackgroundResource(android.R.color.transparent)
            modeVideo.setTextColor(android.graphics.Color.WHITE)

        }

        modeVideo.setOnClickListener {

            videoMode = true

            modeVideo.setBackgroundResource(R.drawable.mode_selected)
            modeVideo.setTextColor(android.graphics.Color.BLACK)

            modePhoto.setBackgroundResource(android.R.color.transparent)
            modePhoto.setTextColor(android.graphics.Color.WHITE)

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

        // Zoom уже обрабатывается в основном OnTouchListener

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

        val name =
            "AICamera_${
                SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.getDefault()
                ).format(Date())
            }.jpg"

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

        imageCapture.targetRotation =
            previewView.display.rotation

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

        val name =
            "AICamera_${
                SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.getDefault()
                ).format(Date())
            }"

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

        videoCapture.targetRotation =
            previewView.display.rotation

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

        btnAspect.setOnClickListener {

            aspectIndex = (aspectIndex + 1) % aspectModes.size

            txtAspect.text = aspectModes[aspectIndex]

            Toast.makeText(
                this,
                "Формат: ${aspectModes[aspectIndex]}",
                Toast.LENGTH_SHORT
            ).show()

        }
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



    override fun onResume() {
        super.onResume()

        accelerometer?.let {
            sensorManager.registerListener(
                horizonListener,
                it,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(horizonListener)
    }

}
