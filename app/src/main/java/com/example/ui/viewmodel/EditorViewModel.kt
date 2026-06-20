package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.model.EditedPhoto
import com.example.data.model.UserPreset
import com.example.data.repository.PhotoRepository
import com.example.ui.imageprocessor.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Stack

sealed interface Screen {
    object Gallery : Screen
    data class Editor(val photo: EditedPhoto) : Screen
}

data class HistogramData(
    val red: FloatArray = FloatArray(256),
    val green: FloatArray = FloatArray(256),
    val blue: FloatArray = FloatArray(256),
    val luminance: FloatArray = FloatArray(256)
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PhotoRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = PhotoRepository(database.photoDao(), database.presetDao())
    }

    // List of saved photos
    val photosState: StateFlow<List<EditedPhoto>> = repository.allPhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of custom user-saved presets
    val presetsState: StateFlow<List<UserPreset>> = repository.allPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Screen State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Gallery)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Loading State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Active Editor States
    private val _activePhoto = MutableStateFlow<EditedPhoto?>(null)
    val activePhoto: StateFlow<EditedPhoto?> = _activePhoto.asStateFlow()

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null) // Downscaled original for editing
    
    private val _processedBitmap = MutableStateFlow<Bitmap?>(null) // Real-time processed bitmap
    val processedBitmap: StateFlow<Bitmap?> = _processedBitmap.asStateFlow()

    private val _liveHistogram = MutableStateFlow<HistogramData?>(null)
    val liveHistogram: StateFlow<HistogramData?> = _liveHistogram.asStateFlow()

    // Undo / Redo Stacks (stores state snapshots of EditPhoto)
    private val undoStack = Stack<EditedPhoto>()
    private val redoStack = Stack<EditedPhoto>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Thread scheduling for real-time slider drags
    private var processingJob: Job? = null


    fun navigateToGallery() {
        viewModelScope.launch {
            _isLoading.value = true
            // Save active progress in database when exiting
            _activePhoto.value?.let {
                repository.updatePhoto(it.copy(lastModifiedAt = System.currentTimeMillis()))
            }
            _activePhoto.value = null
            _originalBitmap.value = null
            _previewBitmap.value = null
            _processedBitmap.value = null
            _liveHistogram.value = null
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            _currentScreen.value = Screen.Gallery
            _isLoading.value = false
        }
    }

    fun openPhotoEditor(photo: EditedPhoto) {
        viewModelScope.launch {
            _isLoading.value = true
            _activePhoto.value = photo
            
            // Load Bitmap in the background
            val loadedBitmap = loadPhotoBitmap(photo)
            if (loadedBitmap != null) {
                _originalBitmap.value = loadedBitmap
                // Create suitable preview size for slider adjustments (max 600px)
                _previewBitmap.value = createScalePreview(loadedBitmap, 600)
                
                // Clear Undo / Redo history for new project session
                undoStack.clear()
                redoStack.clear()
                _canUndo.value = false
                _canRedo.value = false

                // Trigger initial rendering pass
                triggerRendering(photo)
                _currentScreen.value = Screen.Editor(photo)
            }
            _isLoading.value = false
        }
    }

    /**
     * Imports a image selected from the gallery or files, copies it to private files directory
     * so it's fully accessible offline, and then inserts a record.
     */
    fun importPhoto(uri: Uri, title: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val context = getApplication<Application>().applicationContext
                val file = File(context.filesDir, "photo_${System.currentTimeMillis()}.jpg")
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    withContext(Dispatchers.IO) {
                        val outputStream = FileOutputStream(file)
                        inputStream.copyTo(outputStream)
                        outputStream.close()
                        inputStream.close()
                    }

                    // Insert to database mapping pointing to offline absolute file path
                    val newPhoto = EditedPhoto(
                        originalImagePath = file.absolutePath,
                        isAsset = false,
                        title = title
                    )
                    val id = repository.insertPhoto(newPhoto)
                    // Auto open editor for imported photo
                    val inserted = newPhoto.copy(id = id.toInt())
                    openPhotoEditor(inserted)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Deletes a photograph project from local database and purges corresponding copied files
     */
    fun deletePhotoProject(photo: EditedPhoto) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            if (!photo.isAsset) {
                try {
                    val file = File(photo.originalImagePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Updates active editing parameter values (such as sliding Temp/Tint/Exposure) locally.
     * Schedules quick canvas renderings seamlessly.
     */
    fun updateActivePhotoConfig(updated: EditedPhoto, isSliderDragging: Boolean = false) {
        val current = _activePhoto.value ?: return
        
        // Push snapshot to undo stack once editing pauses or settles
        if (!isSliderDragging) {
            pushToUndoStack(current)
        }
        
        _activePhoto.value = updated
        triggerRendering(updated)
    }

    fun applyPreset(preset: UserPreset) {
        val current = _activePhoto.value ?: return
        pushToUndoStack(current)
        val updated = ImageProcessor.applyPresetToConfig(current, preset)
        _activePhoto.value = updated
        triggerRendering(updated)
    }

    fun saveAsCustomPreset(name: String) {
        val active = _activePhoto.value ?: return
        viewModelScope.launch {
            val preset = UserPreset(
                name = name,
                exposure = active.exposure,
                contrast = active.contrast,
                highlights = active.highlights,
                shadows = active.shadows,
                temp = active.temp,
                tint = active.tint,
                vibrance = active.vibrance,
                saturation = active.saturation,
                redHue = active.redHue, redSat = active.redSat, redLum = active.redLum,
                orangeHue = active.orangeHue, orangeSat = active.orangeSat, orangeLum = active.orangeLum,
                yellowHue = active.yellowHue, yellowSat = active.yellowSat, yellowLum = active.yellowLum,
                greenHue = active.greenHue, greenSat = active.greenSat, greenLum = active.greenLum,
                aquaHue = active.aquaHue, aquaSat = active.aquaSat, aquaLum = active.aquaLum,
                blueHue = active.blueHue, blueSat = active.blueSat, blueLum = active.blueLum,
                purpleHue = active.purpleHue, purpleSat = active.purpleSat, purpleLum = active.purpleLum,
                magentaHue = active.magentaHue, magentaSat = active.magentaSat, magentaLum = active.magentaLum,
                texture = active.texture,
                dehaze = active.dehaze,
                vignette = active.vignette,
                sharpening = active.sharpening,
                rotation = active.rotation,
                isFlippedHorizontal = active.isFlippedHorizontal,
                isFlippedVertical = active.isFlippedVertical
            )
            repository.insertPreset(preset)
        }
    }

    fun deleteUserPreset(preset: UserPreset) {
        viewModelScope.launch {
            repository.deletePreset(preset)
        }
    }

    // --- UNDO & REDO CONTROLS ---

    private fun pushToUndoStack(photo: EditedPhoto) {
        undoStack.push(photo)
        redoStack.clear() // Clear redo history whenever target settings fork
        _canUndo.value = true
        _canRedo.value = false
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = _activePhoto.value ?: return
            redoStack.push(current)
            _canRedo.value = true

            val undoState = undoStack.pop()
            _canUndo.value = undoStack.isNotEmpty()
            
            _activePhoto.value = undoState
            triggerRendering(undoState)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = _activePhoto.value ?: return
            undoStack.push(current)
            _canUndo.value = true

            val redoState = redoStack.pop()
            _canRedo.value = redoStack.isNotEmpty()

            _activePhoto.value = redoState
            triggerRendering(redoState)
        }
    }

    // --- RENDER PIPELINE ---

    private fun triggerRendering(config: EditedPhoto) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            // Slight delay (debounce) to make slider scrolls silky and responsive
            delay(15)
            val base = _previewBitmap.value ?: return@launch
            
            val processed = withContext(Dispatchers.Default) {
                ImageProcessor.processPhoto(base, config)
            }
            _processedBitmap.value = processed
            
            // Calculate active histogram on the background worker
            withContext(Dispatchers.Default) {
                calculateHistogram(processed)
            }
        }
    }

    /**
     * Render and export full resolution edited photo directly into pictures gallery.
     */
    suspend fun exportFullResolutionPhoto(): File? = withContext(Dispatchers.Default) {
        val original = _originalBitmap.value ?: return@withContext null
        val config = _activePhoto.value ?: return@withContext null
        
        // Process original max density size
        val finalPhoto = ImageProcessor.processPhoto(original, config)
        
        // Save in cache or external directory
        val context = getApplication<Application>().applicationContext
        val exportFile = File(context.cacheDir, "Export_${config.title}_${System.currentTimeMillis()}.jpg")
        
        try {
            val outputStream = FileOutputStream(exportFile)
            finalPhoto.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.close()
            exportFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- HELPERS ---

    private suspend fun loadPhotoBitmap(photo: EditedPhoto): Bitmap? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        try {
            if (photo.isAsset) {
                // Read from generated system drawables
                val resName = photo.originalImagePath
                val defId = when (resName) {
                    "img_city_neon" -> R.drawable.img_city_neon_1781962010282
                    "img_landscape" -> R.drawable.img_landscape_1781961970729
                    "img_portrait" -> R.drawable.img_portrait_1781961991876
                    else -> R.drawable.img_landscape_1781961970729
                }
                BitmapFactory.decodeResource(context.resources, defId)
            } else {
                // Read local absolute files
                BitmapFactory.decodeFile(photo.originalImagePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createScalePreview(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src

        val ratio = w.toFloat() / h.toFloat()
        val targetW = if (w > h) maxDim else (maxDim * ratio).toInt()
        val targetH = if (h > w) maxDim else (maxDim / ratio).toInt()

        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    /**
     * Scans colors from edited preview bitmap, generating accurate real-time channel histograms
     */
    private fun calculateHistogram(bitmap: Bitmap) {
        val redHist = FloatArray(256)
        val greenHist = FloatArray(256)
        val blueHist = FloatArray(256)
        val lumHist = FloatArray(256)

        val w = bitmap.width
        val h = bitmap.height
        val step = 4 // downsample for lightning fast calculations (around 1ms)
        
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var count = 0
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val color = pixels[y * w + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val lValue = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)

                redHist[r]++
                greenHist[g]++
                blueHist[b]++
                lumHist[lValue]++
                count++
            }
        }

        // Normalize values
        var maxVal = 1f
        for (i in 0..255) {
            if (redHist[i] > maxVal) maxVal = redHist[i]
            if (greenHist[i] > maxVal) maxVal = greenHist[i]
            if (blueHist[i] > maxVal) maxVal = blueHist[i]
            if (lumHist[i] > maxVal) maxVal = lumHist[i]
        }

        for (i in 0..255) {
            redHist[i] /= maxVal
            greenHist[i] /= maxVal
            blueHist[i] /= maxVal
            lumHist[i] /= maxVal
        }

        _liveHistogram.value = HistogramData(redHist, greenHist, blueHist, lumHist)
    }

    /**
     * Populates database with premium starting templates on first run so the photo list is immediately gorgeous!
     */
    fun populateSamplePhotosIfNeeded() {
        viewModelScope.launch {
            if (photosState.value.isEmpty()) {
                val p1 = EditedPhoto(
                    title = "Glacier Lake Sunrise",
                    originalImagePath = "img_landscape",
                    isAsset = true,
                    // Warm golden vibe defaults
                    temp = 0.15f,
                    vibrance = 0.2f,
                    saturation = 0.05f,
                    exposure = 0.05f
                )
                val p2 = EditedPhoto(
                    title = "Casual Golden Portrait",
                    originalImagePath = "img_portrait",
                    isAsset = true,
                    // Gentle portraits lighting defaults
                    shadows = 0.2f,
                    highlights = -0.1f,
                    temp = 0.05f,
                    texture = 0.1f
                )
                val p3 = EditedPhoto(
                    title = "Tokyo Rain Cyberpunk",
                    originalImagePath = "img_city_neon",
                    isAsset = true,
                    // Moody glowing magenta/teal defaults
                    contrast = 0.2f,
                    vignette = -0.3f,
                    blueSat = 0.3f,
                    magentaSat = 0.4f,
                    dehaze = 0.25f
                )

                repository.insertPhoto(p1)
                repository.insertPhoto(p2)
                repository.insertPhoto(p3)
            }
        }
    }
}
