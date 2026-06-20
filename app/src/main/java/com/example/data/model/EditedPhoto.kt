package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "edited_photos")
data class EditedPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalImagePath: String, // Resource path (e.g., "img_landscape") or local Uri/File path
    val isAsset: Boolean = false, // True if using a default asset background
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    
    // Light settings
    val exposure: Float = 0f, // -1f to 1f
    val contrast: Float = 0f, // -1f to 1f
    val highlights: Float = 0f, // -1f to 1f
    val shadows: Float = 0f, // -1f to 1f
    
    // Color settings
    val temp: Float = 0f, // -1f to 1f
    val tint: Float = 0f, // -1f to 1f
    val vibrance: Float = 0f, // -1f to 1f
    val saturation: Float = 0f, // -1f to 1f
    
    // HSL Mix Shifters
    val redHue: Float = 0f, val redSat: Float = 0f, val redLum: Float = 0f,
    val orangeHue: Float = 0f, val orangeSat: Float = 0f, val orangeLum: Float = 0f,
    val yellowHue: Float = 0f, val yellowSat: Float = 0f, val yellowLum: Float = 0f,
    val greenHue: Float = 0f, val greenSat: Float = 0f, val greenLum: Float = 0f,
    val aquaHue: Float = 0f, val aquaSat: Float = 0f, val aquaLum: Float = 0f,
    val blueHue: Float = 0f, val blueSat: Float = 0f, val blueLum: Float = 0f,
    val purpleHue: Float = 0f, val purpleSat: Float = 0f, val purpleLum: Float = 0f,
    val magentaHue: Float = 0f, val magentaSat: Float = 0f, val magentaLum: Float = 0f,
    
    // Effects
    val texture: Float = 0f, // -1f to 1f
    val dehaze: Float = 0f, // -1f to 1f
    val vignette: Float = 0f, // -1f to 1f
    
    // Details
    val sharpening: Float = 0f, // 0f to 1f
    
    // Transform
    val rotation: Int = 0, // 0, 90, 180, 270
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false
) : Serializable

@Entity(tableName = "user_presets")
data class UserPreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    
    // Light settings
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    
    // Color settings
    val temp: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    
    // HSL Mix
    val redHue: Float = 0f, val redSat: Float = 0f, val redLum: Float = 0f,
    val orangeHue: Float = 0f, val orangeSat: Float = 0f, val orangeLum: Float = 0f,
    val yellowHue: Float = 0f, val yellowSat: Float = 0f, val yellowLum: Float = 0f,
    val greenHue: Float = 0f, val greenSat: Float = 0f, val greenLum: Float = 0f,
    val aquaHue: Float = 0f, val aquaSat: Float = 0f, val aquaLum: Float = 0f,
    val blueHue: Float = 0f, val blueSat: Float = 0f, val blueLum: Float = 0f,
    val purpleHue: Float = 0f, val purpleSat: Float = 0f, val purpleLum: Float = 0f,
    val magentaHue: Float = 0f, val magentaSat: Float = 0f, val magentaLum: Float = 0f,
    
    // Effects
    val texture: Float = 0f,
    val dehaze: Float = 0f,
    val vignette: Float = 0f,
    
    // Details
    val sharpening: Float = 0f,
    
    // Transform
    val rotation: Int = 0,
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false
) : Serializable
