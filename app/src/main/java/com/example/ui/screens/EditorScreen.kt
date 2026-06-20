package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EditedPhoto
import com.example.data.model.UserPreset
import com.example.ui.theme.DarkGraphite
import com.example.ui.theme.LightSlate
import com.example.ui.theme.MediumSlate
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.LightroomBlue
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.HistogramData
import kotlinx.coroutines.launch
import java.io.File

enum class EditTab {
    PRESET, LIGHT, COLOR, EFFECTS, CROP_TRANSFORM
}

// Built-in presets definitions matching Lightroom styles
data class BuiltInPreset(
    val name: String,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temp: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    val vignette: Float = 0f,
    val dehaze: Float = 0f,
    val texture: Float = 0f,
    // HSL selectively boosted
    val blueSat: Float = 0f,
    val greenSat: Float = 0f,
    val magentaSat: Float = 0f,
    val redSat: Float = 0f
)

val BuiltInPresets = listOf(
    BuiltInPreset("Original"),
    BuiltInPreset("Cozy Warm", temp = 0.22f, tint = 0.05f, shadows = 0.15f, saturation = 0.10f),
    BuiltInPreset("Moody Teal", temp = -0.18f, tint = 0.05f, contrast = 0.20f, blueSat = 0.40f, greenSat = -0.35f, vignette = -0.30f),
    BuiltInPreset("Classic B&W", saturation = -1.0f, contrast = 0.25f, highlights = -0.1f, shadows = 0.15f),
    BuiltInPreset("Vibrant Landscape", vibrance = 0.35f, saturation = 0.12f, highlights = -0.15f, shadows = 0.15f),
    BuiltInPreset("Vintage Dream", contrast = -0.15f, shadows = 0.25f, saturation = -0.15f, temp = 0.08f),
    BuiltInPreset("Punchy Cyberpunk", contrast = 0.15f, vibrance = 0.40f, dehaze = 0.15f, blueSat = 0.30f, magentaSat = 0.40f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val photo by viewModel.activePhoto.collectAsState()
    val processedBitmap by viewModel.processedBitmap.collectAsState()
    val originalPreviewBitmap by viewModel.processedBitmap.collectAsState() // used for comparative snapshots
    val histogram by viewModel.liveHistogram.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val customPresets by viewModel.presetsState.collectAsState()

    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(EditTab.LIGHT) }
    var isComparing by remember { mutableStateOf(false) }
    var showColorMixer by remember { mutableStateOf(false) }
    var activeColorChannel by remember { mutableStateOf(0) } // 0..7 representing 8 colors

    // Save Preset dialogue trigger
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    // Export success dialog trigger
    var exportedFilepath by remember { mutableStateOf<String?>(null) }
    var showExportSuccessDialog by remember { mutableStateOf(false) }
    var isCurrentlyExporting by remember { mutableStateOf(false) }

    val photoData = photo ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = photoData.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateToGallery() },
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Gallery",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Undo Action
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.25f)
                        )
                    }

                    // Redo Action
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.25f)
                        )
                    }

                    // COMPARE Button: press-and-hold gesture down below on viewport will also run but this toggles instantly
                    IconButton(
                        onClick = { isComparing = !isComparing },
                        modifier = Modifier.testTag("compare_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Toggle Before/After comparison",
                            tint = if (isComparing) LightroomBlue else Color.White
                        )
                    }

                    // Save / Export trigger
                    IconButton(
                        onClick = {
                            isCurrentlyExporting = true
                            scope.launch {
                                val savedFile = viewModel.exportFullResolutionPhoto()
                                isCurrentlyExporting = false
                                if (savedFile != null) {
                                    exportedFilepath = savedFile.absolutePath
                                    showExportSuccessDialog = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("export_button")
                    ) {
                        if (isCurrentlyExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LightroomBlue)
                        } else {
                            Icon(imageVector = Icons.Default.SaveAlt, contentDescription = "Export in High Res", tint = LightroomBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                // Adjustment sliders block depending on active tab
                AdjustmentSlidersSection(
                    photo = photoData,
                    activeTab = activeTab,
                    showColorMixer = showColorMixer,
                    activeColorChannel = activeColorChannel,
                    customPresets = customPresets,
                    onPresetSelected = { bp ->
                        val updated = photoData.copy(
                            exposure = bp.exposure,
                            contrast = bp.contrast,
                            highlights = bp.highlights,
                            shadows = bp.shadows,
                            temp = bp.temp,
                            tint = bp.tint,
                            vibrance = bp.vibrance,
                            saturation = bp.saturation,
                            vignette = bp.vignette,
                            dehaze = bp.dehaze,
                            texture = bp.texture,
                            blueSat = bp.blueSat,
                            greenSat = bp.greenSat,
                            magentaSat = bp.magentaSat,
                            redSat = bp.redSat
                        )
                        viewModel.updateActivePhotoConfig(updated, isSliderDragging = false)
                    },
                    onCustomPresetSelected = { preset ->
                        viewModel.applyPreset(preset)
                    },
                    onConfigChanged = { updated, dragging ->
                        viewModel.updateActivePhotoConfig(updated, dragging)
                    },
                    onToggleColorMixer = { showColorMixer = !showColorMixer },
                    onSelectColorChannel = { activeColorChannel = it },
                    openSavePresetDialog = { showSavePresetDialog = true }
                )

                Divider(color = MediumSlate, thickness = 1.dp)

                // Tab Selector Items Area
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.height(72.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabSelectorItem(
                            tab = EditTab.PRESET,
                            activeTab = activeTab,
                            icon = Icons.Default.AutoAwesome,
                            label = "Presets"
                        ) { activeTab = EditTab.PRESET; showColorMixer = false }

                        TabSelectorItem(
                            tab = EditTab.LIGHT,
                            activeTab = activeTab,
                            icon = Icons.Default.LightMode,
                            label = "Light"
                        ) { activeTab = EditTab.LIGHT; showColorMixer = false }

                        TabSelectorItem(
                            tab = EditTab.COLOR,
                            activeTab = activeTab,
                            icon = Icons.Default.Palette,
                            label = "Color"
                        ) { activeTab = EditTab.COLOR }

                        TabSelectorItem(
                            tab = EditTab.EFFECTS,
                            activeTab = activeTab,
                            icon = Icons.Default.Camera,
                            label = "Effects"
                        ) { activeTab = EditTab.EFFECTS; showColorMixer = false }

                        TabSelectorItem(
                            tab = EditTab.CROP_TRANSFORM,
                            activeTab = activeTab,
                            icon = Icons.Default.Crop,
                            label = "Geometry"
                        ) { activeTab = EditTab.CROP_TRANSFORM; showColorMixer = false }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050505))
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            // Viewport of Image being adjusted
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(processedBitmap) {
                        detectTapGestures(
                            onPress = {
                                // Press-and-hold compare triggers original preview
                                isComparing = true
                                tryAwaitRelease()
                                isComparing = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (processedBitmap != null) {
                    if (isComparing) {
                        // Show raw preview (approximated by rendering with zero parameters)
                        Text(
                            text = "BEFORE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        Image(
                            bitmap = originalPreviewBitmap!!.asImageBitmap(),
                            contentDescription = "Original photo compare",
                            modifier = Modifier.fillMaxWidth(0.95f)
                        )
                    } else {
                        // Show active adjusted photo
                        Image(
                            bitmap = processedBitmap!!.asImageBitmap(),
                            contentDescription = "Edited photo viewport",
                            modifier = Modifier.fillMaxWidth(0.95f)
                        )
                    }
                } else {
                    CircularProgressIndicator(color = LightroomBlue)
                }
            }

            // Live Overlaid Histogram stream
            histogram?.let { h ->
                LiveHistogramCanvas(
                    histogram = h,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 120.dp, height = 50.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(0.5.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                )
            }
        }
    }

    // Custom Named Preset creating Dialogue
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Settings as Preset", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Copy your active slider parameters into a quick offline preset context.", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("Preset Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = LightroomBlue,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            viewModel.saveAsCustomPreset(newPresetName)
                            newPresetName = ""
                            showSavePresetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LightroomBlue)
                ) {
                    Text("Save Preset", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = DarkGraphite
        )
    }

    // Export complete dialog popup
    if (showExportSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showExportSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green)
                    Text("Export Success", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Your final maximum resolution development project was written successfully, fully offline.", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Stored Path:", color = Color.Gray, fontSize = 10.sp)
                    Text(exportedFilepath ?: "", color = Color.LightGray, fontSize = 10.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showExportSuccessDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = LightroomBlue)) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkGraphite
        )
    }
}

@Composable
fun TabSelectorItem(
    tab: EditTab,
    activeTab: EditTab,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val isSelected = tab == activeTab
    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) LightroomBlue else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isSelected) LightroomBlue else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AdjustmentSlidersSection(
    photo: EditedPhoto,
    activeTab: EditTab,
    showColorMixer: Boolean,
    activeColorChannel: Int,
    customPresets: List<UserPreset>,
    onPresetSelected: (BuiltInPreset) -> Unit,
    onCustomPresetSelected: (UserPreset) -> Unit,
    onConfigChanged: (EditedPhoto, Boolean) -> Unit,
    onToggleColorMixer: () -> Unit,
    onSelectColorChannel: (Int) -> Unit,
    openSavePresetDialog: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkGraphite)
            .padding(16.dp)
            .animateContentSize()
    ) {
        when (activeTab) {
            EditTab.PRESET -> {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lightroom Presets", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Button(
                            onClick = { openSavePresetDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Active", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Built-in presets list row
                    Text("Built-In Looks", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(BuiltInPresets) { bp ->
                            Box(
                                modifier = Modifier
                                    .background(MediumSlate, RoundedCornerShape(8.dp))
                                    .border(1.dp, LightroomBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { onPresetSelected(bp) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(bp.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (customPresets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Custom Saved", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(customPresets) { cp ->
                                Box(
                                    modifier = Modifier
                                        .background(MediumSlate, RoundedCornerShape(8.dp))
                                        .border(1.dp, AccentOrange.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { onCustomPresetSelected(cp) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(cp.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            EditTab.LIGHT -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("LIGHT ADJUSTMENTS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        TextButton(onClick = {
                            onConfigChanged(photo.copy(exposure = 0f, contrast = 0f, highlights = 0f, shadows = 0f), false)
                        }) {
                            Text("Reset", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    EditorSliderControl(
                        label = "Exposure",
                        value = photo.exposure,
                        rangeLimits = -1f..1f,
                        valueTextFormatter = { String.format("%+.2f Ev", it) },
                        onValueChange = { onConfigChanged(photo.copy(exposure = it), true) },
                        onValueFinished = { onConfigChanged(photo.copy(exposure = it), false) }
                    )

                    EditorSliderControl(
                        label = "Contrast",
                        value = photo.contrast,
                        rangeLimits = -1f..1f,
                        valueTextFormatter = { String.format("%+.0f%%", it * 100) },
                        onValueChange = { onConfigChanged(photo.copy(contrast = it), true) },
                        onValueFinished = { onConfigChanged(photo.copy(contrast = it), false) }
                    )

                    EditorSliderControl(
                        label = "Highlights",
                        value = photo.highlights,
                        rangeLimits = -1f..1f,
                        valueTextFormatter = { String.format("%+.0f", it * 100) },
                        onValueChange = { onConfigChanged(photo.copy(highlights = it), true) },
                        onValueFinished = { onConfigChanged(photo.copy(highlights = it), false) }
                    )

                    EditorSliderControl(
                        label = "Shadows",
                        value = photo.shadows,
                        rangeLimits = -1f..1f,
                        valueTextFormatter = { String.format("%+.0f", it * 100) },
                        onValueChange = { onConfigChanged(photo.copy(shadows = it), true) },
                        onValueFinished = { onConfigChanged(photo.copy(shadows = it), false) }
                    )
                }
            }

            EditTab.COLOR -> {
                if (!showColorMixer) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("COLOR WORKSPACE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onToggleColorMixer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MediumSlate),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(12.dp), tint = LightroomBlue)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mixer", fontSize = 11.sp, color = LightroomBlue, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = {
                                    onConfigChanged(photo.copy(temp = 0f, tint = 0f, vibrance = 0f, saturation = 0f), false)
                                }) {
                                    Text("Reset", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        EditorSliderControl(
                            label = "Temp (Warmth)",
                            value = photo.temp,
                            rangeLimits = -1f..1f,
                            colorTrack = true, // uses custom blue-to-yellow gradient tracker
                            valueTextFormatter = { String.format("%+.0f", it * 100) },
                            onValueChange = { onConfigChanged(photo.copy(temp = it), true) },
                            onValueFinished = { onConfigChanged(photo.copy(temp = it), false) }
                        )

                        EditorSliderControl(
                            label = "Tint",
                            value = photo.tint,
                            rangeLimits = -1f..1f,
                            valueTextFormatter = { String.format("%+.0f", it * 100) },
                            onValueChange = { onConfigChanged(photo.copy(tint = it), true) },
                            onValueFinished = { onConfigChanged(photo.copy(tint = it), false) }
                        )

                        EditorSliderControl(
                            label = "Vibrance",
                            value = photo.vibrance,
                            rangeLimits = -1f..1f,
                            valueTextFormatter = { String.format("%+.0f", it * 100) },
                            onValueChange = { onConfigChanged(photo.copy(vibrance = it), true) },
                            onValueFinished = { onConfigChanged(photo.copy(vibrance = it), false) }
                        )

                        EditorSliderControl(
                            label = "Saturation",
                            value = photo.saturation,
                            rangeLimits = -1f..1f,
                            valueTextFormatter = { String.format("%+.0f%%", it * 100) },
                            onValueChange = { onConfigChanged(photo.copy(saturation = it), true) },
                            onValueFinished = { onConfigChanged(photo.copy(saturation = it), false) }
                        )
                    }
                } else {
                    // COLOR MIX HSL SELECTIVE ENGINE
                    SelectiveColorMixSection(
                        photo = photo,
                        activeChannel = activeColorChannel,
                        onChannelSelected = { onSelectColorChannel(it) },
                        onConfigChanged = { onConfigChanged(it, true) },
                        onConfigFinished = { onConfigChanged(it, false) },
                        onExitMixer = { onToggleColorMixer() }
                    )
                }
            }

            EditTab.EFFECTS -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("EFFECTS & DETAILS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        TextButton(onClick = {
                            onConfigChanged(photo.copy(texture = 0f, dehaze = 0f, vignette = 0f, sharpening = 0f), false)
                        }) {
                            Text("Reset", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    EditorSliderControl(
                        label = "Texture / Clarity",
                        value = photo.texture,
                        rangeLimits = -1f..1f,
                        valueTextFormatter = { String.format("%+.0f", it * 100) },
                        onValueChange = { onConfigChanged(photo.copy(texture = it), true) },
                        onValueFinished = { onConfigChanged(photo.copy(texture = it), false) }
                    )

                    EditorSliderControl(
                        label = "Dehaze",
                        value = photo.dehaze,
                        rangeLimits = -1f..1f,
                        valueTextFormatter = { String.format("%+.0f", it * 100) },
                        onValueChange = { onConfigChanged(photo.copy(dehaze = it), true) },
                        onValueFinished = { onConfigChanged(photo.copy(dehaze = it), false) }
                    )

                    EditorSliderControl(
                        label = "Vignette",
                        value = photo.vignette,
                        rangeLimits = -1f..1f,
                        valueTextFormatter = { String.format("%+.0f", it * 100) },
                        onValueChange = { onConfigChanged(photo.copy(vignette = it), true) },
                        onValueFinished = { onConfigChanged(photo.copy(vignette = it), false) }
                    )

                    EditorSliderControl(
                        label = "Sharpening Detail",
                        value = photo.sharpening,
                        rangeLimits = 0f..1f,
                        valueTextFormatter = { String.format("%.0f%%", it * 100) },
                        onValueChange = { onConfigChanged(photo.copy(sharpening = it), true) },
                        onValueFinished = { onConfigChanged(photo.copy(sharpening = it), false) }
                    )
                }
            }

            EditTab.CROP_TRANSFORM -> {
                Column {
                    Text("GEOMETRY & TRANSFORM", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TransformButton(icon = Icons.Default.RotateLeft, label = "Rotate L") {
                            var target = photo.rotation - 90
                            if (target < 0) target += 360
                            onConfigChanged(photo.copy(rotation = target), false)
                        }

                        TransformButton(icon = Icons.Default.RotateRight, label = "Rotate R") {
                            var target = photo.rotation + 90
                            if (target >= 360) target -= 360
                            onConfigChanged(photo.copy(rotation = target), false)
                        }

                        TransformButton(icon = Icons.Default.Flip, label = "Flip Horiz") {
                            onConfigChanged(photo.copy(isFlippedHorizontal = !photo.isFlippedHorizontal), false)
                        }

                        TransformButton(icon = Icons.Default.FlipCameraAndroid, label = "Flip Vert") {
                            onConfigChanged(photo.copy(isFlippedVertical = !photo.isFlippedVertical), false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransformButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(MediumSlate, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EditorSliderControl(
    label: String,
    value: Float,
    rangeLimits: ClosedFloatingPointRange<Float>,
    colorTrack: Boolean = false,
    valueTextFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onValueFinished: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(valueTextFormatter(value), color = LightroomBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Slider(
            value = value,
            valueRange = rangeLimits,
            onValueChange = { onValueChange(it) },
            onValueChangeFinished = { onValueFinished(value) },
            colors = SliderDefaults.colors(
                thumbColor = LightroomBlue,
                activeTrackColor = LightroomBlue,
                inactiveTrackColor = Color.DarkGray
            )
        )
    }
}

/**
 * 8-Channel Lightroom-style selective HSL Color Wheel Mixer
 */
@Composable
fun SelectiveColorMixSection(
    photo: EditedPhoto,
    activeChannel: Int,
    onChannelSelected: (Int) -> Unit,
    onConfigChanged: (EditedPhoto) -> Unit,
    onConfigFinished: (EditedPhoto) -> Unit,
    onExitMixer: () -> Unit
) {
    // Colors of the 8 channels: Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta
    val ChannelColors = listOf(
        Color(0xFFFF3B30), // Red
        Color(0xFFFF9500), // Orange
        Color(0xFFFFCC00), // Yellow
        Color(0xFF4CD964), // Green
        Color(0xFF5AC8FA), // Aqua
        Color(0xFF007AFF), // Blue
        Color(0xFF5856D6), // Purple
        Color(0xFFFF2D55)  // Magenta
    )

    val ChannelLabels = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("COLOR MIX (HSL)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ChannelColors[activeChannel], CircleShape)
                )
            }
            TextButton(onClick = { onExitMixer() }) {
                Text("Done", color = LightroomBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Left-to-right 8 colored selective circular channel buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelColors.forEachIndexed { idx, color ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (activeChannel == idx) 2.5.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable { onChannelSelected(idx) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Selective HSL sliders
        val activeHue = when (activeChannel) {
            0 -> photo.redHue; 1 -> photo.orangeHue; 2 -> photo.yellowHue; 3 -> photo.greenHue
            4 -> photo.aquaHue; 5 -> photo.blueHue; 6 -> photo.purpleHue; else -> photo.magentaHue
        }
        val activeSat = when (activeChannel) {
            0 -> photo.redSat; 1 -> photo.orangeSat; 2 -> photo.yellowSat; 3 -> photo.greenSat
            4 -> photo.aquaSat; 5 -> photo.blueSat; 6 -> photo.purpleSat; else -> photo.magentaSat
        }
        val activeLum = when (activeChannel) {
            0 -> photo.redLum; 1 -> photo.orangeLum; 2 -> photo.yellowLum; 3 -> photo.greenLum
            4 -> photo.aquaLum; 5 -> photo.blueLum; 6 -> photo.purpleLum; else -> photo.magentaLum
        }

        EditorSliderControl(
            label = "${ChannelLabels[activeChannel]} Hue",
            value = activeHue,
            rangeLimits = -1f..1f,
            valueTextFormatter = { String.format("%+.0f", it * 100) },
            onValueChange = {
                val updated = updateChannelValue(photo, activeChannel, HueType = true, value = it)
                onConfigChanged(updated)
            },
            onValueFinished = {
                val updated = updateChannelValue(photo, activeChannel, HueType = true, value = it)
                onConfigFinished(updated)
            }
        )

        EditorSliderControl(
            label = "${ChannelLabels[activeChannel]} Saturation",
            value = activeSat,
            rangeLimits = -1f..1f,
            valueTextFormatter = { String.format("%+.0f", it * 100) },
            onValueChange = {
                val updated = updateChannelValue(photo, activeChannel, SatType = true, value = it)
                onConfigChanged(updated)
            },
            onValueFinished = {
                val updated = updateChannelValue(photo, activeChannel, SatType = true, value = it)
                onConfigFinished(updated)
            }
        )

        EditorSliderControl(
            label = "${ChannelLabels[activeChannel]} Luminance",
            value = activeLum,
            rangeLimits = -1f..1f,
            valueTextFormatter = { String.format("%+.0f", it * 100) },
            onValueChange = {
                val updated = updateChannelValue(photo, activeChannel, LumType = true, value = it)
                onConfigChanged(updated)
            },
            onValueFinished = {
                val updated = updateChannelValue(photo, activeChannel, LumType = true, value = it)
                onConfigFinished(updated)
            }
        )
    }
}

private fun updateChannelValue(
    photo: EditedPhoto,
    channel: Int,
    HueType: Boolean = false,
    SatType: Boolean = false,
    LumType: Boolean = false,
    value: Float
): EditedPhoto {
    return when (channel) {
        0 -> { // RED
            if (HueType) photo.copy(redHue = value)
            else if (SatType) photo.copy(redSat = value)
            else photo.copy(redLum = value)
        }
        1 -> { // ORANGE
            if (HueType) photo.copy(orangeHue = value)
            else if (SatType) photo.copy(orangeSat = value)
            else photo.copy(orangeLum = value)
        }
        2 -> { // YELLOW
            if (HueType) photo.copy(yellowHue = value)
            else if (SatType) photo.copy(yellowSat = value)
            else photo.copy(yellowLum = value)
        }
        3 -> { // GREEN
            if (HueType) photo.copy(greenHue = value)
            else if (SatType) photo.copy(greenSat = value)
            else photo.copy(greenLum = value)
        }
        4 -> { // AQUA
            if (HueType) photo.copy(aquaHue = value)
            else if (SatType) photo.copy(aquaSat = value)
            else photo.copy(aquaLum = value)
        }
        5 -> { // BLUE
            if (HueType) photo.copy(blueHue = value)
            else if (SatType) photo.copy(blueSat = value)
            else photo.copy(blueLum = value)
        }
        6 -> { // PURPLE
            if (HueType) photo.copy(purpleHue = value)
            else if (SatType) photo.copy(purpleSat = value)
            else photo.copy(purpleLum = value)
        }
        else -> { // MAGENTA
            if (HueType) photo.copy(magentaHue = value)
            else if (SatType) photo.copy(magentaSat = value)
            else photo.copy(magentaLum = value)
        }
    }
}

/**
 * Beautiful Overlay Custom Live RGB Line Histogram Canvas
 */
@Composable
fun LiveHistogramCanvas(
    histogram: HistogramData,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val totalbins = 256
        val binWidth = width / totalbins

        // Translucent background
        drawRect(color = Color.Black.copy(alpha = 0.5f))

        val pathRed = Path()
        val pathGreen = Path()
        val pathBlue = Path()
        val pathLum = Path()

        for (i in 0 until totalbins) {
            val x = i * binWidth

            // Invert scale (since 0f height is top-left in Canvas coordinates)
            val yRed = height - (histogram.red[i] * height * 0.95f)
            if (i == 0) pathRed.moveTo(x, yRed) else pathRed.lineTo(x, yRed)

            val yGreen = height - (histogram.green[i] * height * 0.95f)
            if (i == 0) pathGreen.moveTo(x, yGreen) else pathGreen.lineTo(x, yGreen)

            val yBlue = height - (histogram.blue[i] * height * 0.95f)
            if (i == 0) pathBlue.moveTo(x, yBlue) else pathBlue.lineTo(x, yBlue)

            val yLum = height - (histogram.luminance[i] * height * 0.95f)
            if (i == 0) pathLum.moveTo(x, yLum) else pathLum.lineTo(x, yLum)
        }

        // Overlapping draw stroke matrices (translucent styles)
        drawPath(pathRed, color = Color(0x77FF3B30), style = Stroke(width = 1.dp.toPx()))
        drawPath(pathGreen, color = Color(0x774CD964), style = Stroke(width = 1.dp.toPx()))
        drawPath(pathBlue, color = Color(0x77007AFF), style = Stroke(width = 1.dp.toPx()))
        drawPath(pathLum, color = Color(0xAAFFFFFF), style = Stroke(width = 1.5.dp.toPx()))
    }
}
