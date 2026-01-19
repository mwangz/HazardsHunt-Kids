package com.example.hazardshuntkids

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil.compose.rememberAsyncImagePainter
import com.example.hazardshuntkids.ui.ApiKeySetupScreen
import com.example.hazardshuntkids.ui.ApiKeySettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
class `backup-mainactivity` : ComponentActivity(), TextToSpeech.OnInitListener {

    lateinit var tts: TextToSpeech
    private val CAMERA_PERMISSION_REQUEST = 1001
    private var pendingCameraLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tts = TextToSpeech(this, this)

        setContent {
            HazardsHuntApp()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.US
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    @Composable
    fun HazardsHuntApp() {
        var showCamera by remember { mutableStateOf(false) }
        var capturedImages by remember { mutableStateOf(listOf<Pair<Uri, String>>()) }
        var analysisResults by remember { mutableStateOf(mapOf<Uri, String>()) }
        var hasApiKey by remember { mutableStateOf(ApiKeyManager.hasKey(this@`backup-mainactivity`)) }
        var showSettings by remember { mutableStateOf(false) }

        val context = LocalContext.current

        if (showCamera) {
            CameraScreen(
                onFinished = { newImages ->
                    capturedImages = capturedImages + newImages
                    showCamera = false
                }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    Color.Magenta
                                                )
                                            ),
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) {
                                        append("HazardsHunt-Kids")
                                    }
                                },
                                fontSize = 22.sp
                            )
                        },
                        actions = {
                            if (hasApiKey) {
                                IconButton(onClick = { showSettings = true }) {
                                    Text("⚙️")
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    if (!hasApiKey) {
                        ApiKeySetupScreen { key ->
                            ApiKeyManager.save(this@`backup-mainactivity`, key)
                            hasApiKey = true
                        }
                    } else {
                        if (showSettings) {
                            ApiKeySettingsScreen(context) { showSettings = false }
                        } else {
                            MainContent(capturedImages, analysisResults) { showCamera = true }
                        }
                    }
                }
            }

            // 按拍照顺序分析并朗读
            LaunchedEffect(capturedImages) {
                val toAnalyze = capturedImages.filter { it.first !in analysisResults.keys }
                for ((uri, name) in toAnalyze) {
                    try {
                        val result = suspendCancellableCoroutine<String> { cont ->
                            OpenAIClient.analyzeImage(
                                context = context,
                                imageUri = uri,
//                                prompt = "This is $name. Analyze and list hazards clearly.",
                                onResult = { cont.resume(it) {} },
                                onError = { cont.resume("Error: $it") {} }
                            )
                        }
                        analysisResults = analysisResults + (uri to "$name: $result")
                        tts.speak("$name: $result", TextToSpeech.QUEUE_ADD, null, "gptResult")
                    } catch (e: Exception) {
                        analysisResults = analysisResults + (uri to "$name: Error: ${e.message}")
                    }
                }
            }
        }
    }

    @Composable
    fun MainContent(
        capturedImages: List<Pair<Uri, String>>,
        analysisResults: Map<Uri, String>,
        onStartCamera: () -> Unit
    ) {
        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStartCamera) {
                Text("Start Hazard Hunt")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (capturedImages.isNotEmpty()) {
                Text("Captured Photos & Analysis:", fontSize = 18.sp)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(capturedImages) { (uri, name) ->
                        val bitmap = remember(uri) { loadCorrectlyOrientedBitmap(context, uri) }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                        }
                        Text(
                            text = analysisResults[uri] ?: "Analyzing...",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }

    /** 读取 Bitmap 并修正方向 */
    fun loadCorrectlyOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()

            val exifInput = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(exifInput)
            exifInput.close()

            val orientation =
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CameraScreen(onFinished: (List<Pair<Uri, String>>) -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = context as LifecycleOwner

        var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

        val sessionCapturedImages = remember { mutableStateListOf<Pair<Uri, String>>() }

        // 拍照声音
        val soundPool = remember {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            SoundPool.Builder().setAudioAttributes(attrs).setMaxStreams(1).build()
        }
        val soundId = remember { soundPool.load(context, R.raw.camera_click, 1) }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Scanning for Hazards…") }) }
        ) { padding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { ctx: Context ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview =
                            Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }

        // 自动拍照
        LaunchedEffect(Unit) {
            var counter = 1
            repeat(5) {
                delay(3000)
                val photoFile = File(context.cacheDir, "hazard_${System.currentTimeMillis()}.jpg")
                val photoName = "picture$counter"
                counter++
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                val savedUri: Uri? = suspendCancellableCoroutine { cont ->
                    imageCapture?.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
                                val uri = saveImageToGallery(context, photoFile)
                                cont.resume(uri)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("Camera", "Error: ${exception.message}")
                                cont.resume(null)
                            }
                        }
                    )
                }
                savedUri?.let { sessionCapturedImages.add(it to photoName) }
            }
            onFinished(sessionCapturedImages.toList())
        }
    }

    fun saveImageToGallery(context: Context, file: File): Uri? {
        val filename = "hazard_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/HazardsHunt")
            }
        }
        return try {
            val resolver = context.contentResolver
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { resolver.openOutputStream(it).use { out -> file.inputStream().copyTo(out!!) } }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
