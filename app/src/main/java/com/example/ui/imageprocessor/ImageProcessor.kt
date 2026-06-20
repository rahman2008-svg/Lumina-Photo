package com.example.ui.imageprocessor

import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.data.model.EditedPhoto
import com.example.data.model.UserPreset
import kotlin.math.pow
import kotlin.math.sqrt

object ImageProcessor {

    /**
     * Entrypoint for processing a bitmap with active photo edits.
     * Fits the original bitmap dimensions dynamically and performs processing in a single pass.
     */
    fun processPhoto(input: Bitmap, config: EditedPhoto): Bitmap {
        var processed = applyPixelAdjustments(input, config)
        processed = applyTransformations(processed, config.rotation, config.isFlippedHorizontal, config.isFlippedVertical)
        if (config.sharpening > 0f) {
            processed = applySharpening(processed, config.sharpening)
        }
        return processed
    }

    /**
     * Entrypoint for applying custom or built-in presets to edited photo state.
     */
    fun applyPresetToConfig(config: EditedPhoto, preset: UserPreset): EditedPhoto {
        return config.copy(
            exposure = preset.exposure,
            contrast = preset.contrast,
            highlights = preset.highlights,
            shadows = preset.shadows,
            temp = preset.temp,
            tint = preset.tint,
            vibrance = preset.vibrance,
            saturation = preset.saturation,
            redHue = preset.redHue, redSat = preset.redSat, redLum = preset.redLum,
            orangeHue = preset.orangeHue, orangeSat = preset.orangeSat, orangeLum = preset.orangeLum,
            yellowHue = preset.yellowHue, yellowSat = preset.yellowSat, yellowLum = preset.yellowLum,
            greenHue = preset.greenHue, greenSat = preset.greenSat, greenLum = preset.greenLum,
            aquaHue = preset.aquaHue, aquaSat = preset.aquaSat, aquaLum = preset.aquaLum,
            blueHue = preset.blueHue, blueSat = preset.blueSat, blueLum = preset.blueLum,
            purpleHue = preset.purpleHue, purpleSat = preset.purpleSat, purpleLum = preset.purpleLum,
            magentaHue = preset.magentaHue, magentaSat = preset.magentaSat, magentaLum = preset.magentaLum,
            texture = preset.texture,
            dehaze = preset.dehaze,
            vignette = preset.vignette,
            sharpening = preset.sharpening,
            rotation = preset.rotation,
            isFlippedHorizontal = preset.isFlippedHorizontal,
            isFlippedVertical = preset.isFlippedVertical
        )
    }

    private fun applyPixelAdjustments(src: Bitmap, c: EditedPhoto): Bitmap {
        val width = src.width
        val height = src.height
        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val cx = width / 2f
        val cy = height / 2f
        val maxDist = sqrt(cx * cx + cy * cy)

        // Pre-compute exposure modifier
        val expFactor = 2f.pow(c.exposure)

        // Pre-compute contrast modifier
        val contrastFactor = if (c.contrast != 0f) {
            (1.0f + c.contrast) * (1.0f + c.contrast)
        } else {
            1.0f
        }

        // Color temperature shifting coefficients
        val tempR = if (c.temp > 0f) c.temp * 24f else c.temp * 15f
        val tempB = if (c.temp > 0f) -c.temp * 24f else -c.temp * 15f
        val tempG = c.temp * 6f

        // Tint shifting coefficients
        val tintR = c.tint * 10f
        val tintG = -c.tint * 15f
        val tintB = c.tint * 10f

        val hsl = FloatArray(3)

        for (i in pixels.indices) {
            val color = pixels[i]
            var r = ((color shr 16) and 0xFF).toFloat()
            var g = ((color shr 8) and 0xFF).toFloat()
            var b = (color and 0xFF).toFloat()

            // 1. Exposure
            if (c.exposure != 0f) {
                r *= expFactor
                g *= expFactor
                b *= expFactor
            }

            // Calculate luminance before other operations
            var lum = 0.299f * r + 0.587f * g + 0.114f * b

            // 2. Highlights / Shadows adjustments
            if (c.highlights != 0f) {
                val weightH = (lum / 255f).coerceIn(0f, 1f).pow(1.5f)
                val highlightShift = c.highlights * weightH * 45f
                r += highlightShift
                g += highlightShift
                b += highlightShift
            }
            if (c.shadows != 0f) {
                val weightS = (1f - lum / 255f).coerceIn(0f, 1f).pow(1.5f)
                val shadowShift = c.shadows * weightS * 45f
                r += shadowShift
                g += shadowShift
                b += shadowShift
            }

            // 3. Contrast adjustment
            if (c.contrast != 0f) {
                r = (r - 128f) * contrastFactor + 128f
                g = (g - 128f) * contrastFactor + 128f
                b = (b - 128f) * contrastFactor + 128f
            }

            // 4. White Balance (Temp & Tint)
            if (c.temp != 0f) {
                r += tempR
                g += tempG
                b += tempB
            }
            if (c.tint != 0f) {
                r += tintR
                g += tintG
                b += tintB
            }

            // Recalculate luminance for saturation/vibrance
            lum = 0.299f * r + 0.587f * g + 0.114f * b

            // 5. Vibrance (Saturates desaturated components first)
            if (c.vibrance != 0f) {
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                val currentSat = if (mx > 0f) (mx - mn) / mx else 0f
                val vibAmount = c.vibrance * (1f - currentSat) * 0.8f
                r += (r - lum) * vibAmount
                g += (g - lum) * vibAmount
                b += (b - lum) * vibAmount
            }

            // 6. Saturation
            if (c.saturation != 0f) {
                val satFactor = c.saturation + 1f
                r = lum + (r - lum) * satFactor
                g = lum + (g - lum) * satFactor
                b = lum + (b - lum) * satFactor
            }

            // 7. Dehaze / Clarity (Texture simulator)
            if (c.dehaze != 0f || c.texture != 0f) {
                val combined = (c.dehaze * 0.6f) + (c.texture * 0.4f)
                // Boost local contrast with center-weighted bias
                val diff = lum - 128f
                val factor = 1f + combined * 0.4f
                r = r + (diff * (factor - 1f))
                g = g + (diff * (factor - 1f))
                b = b + (diff * (factor - 1f))
            }

            // 8. HSL Color Mix (Selective color adjusting - RED to MAGENTA)
            rgbToHsl(r.coerceIn(0f, 255f).toInt(), g.coerceIn(0f, 255f).toInt(), b.coerceIn(0f, 255f).toInt(), hsl)
            var h = hsl[0]
            var s = hsl[1]
            var l = hsl[2]

            // Binning Hue to 8 Lightroom channels
            // Red (345-15), Orange (15-45), Yellow (45-75), Green (75-155), Aqua (155-195), Blue (195-260), Purple (260-305), Magenta (305-345)
            var hShift = 0f
            var sShift = 0f
            var lShift = 0f

            if (h >= 345f || h < 15f) {
                hShift = c.redHue
                sShift = c.redSat
                lShift = c.redLum
            } else if (h in 15f..45f) {
                hShift = c.orangeHue
                sShift = c.orangeSat
                lShift = c.orangeLum
            } else if (h in 45f..75f) {
                hShift = c.yellowHue
                sShift = c.yellowSat
                lShift = c.yellowLum
            } else if (h in 75f..155f) {
                hShift = c.greenHue
                sShift = c.greenSat
                lShift = c.greenLum
            } else if (h in 155f..195f) {
                hShift = c.aquaHue
                sShift = c.aquaSat
                lShift = c.aquaLum
            } else if (h in 195f..260f) {
                hShift = c.blueHue
                sShift = c.blueSat
                lShift = c.blueLum
            } else if (h in 260f..305f) {
                hShift = c.purpleHue
                sShift = c.purpleSat
                lShift = c.purpleLum
            } else if (h in 305f..345f) {
                hShift = c.magentaHue
                sShift = c.magentaSat
                lShift = c.magentaLum
            }

            if (hShift != 0f || sShift != 0f || lShift != 0f) {
                // Hue shift: shifts maximum 25 degrees
                h = (h + hShift * 25f)
                if (h < 0f) h += 360f
                if (h >= 360f) h -= 360f

                // Saturation shift: scale smoothly
                s = if (sShift > 0f) {
                    s + (1f - s) * sShift
                } else {
                    s + s * sShift
                }.coerceIn(0f, 1f)

                // Luminance shift: boost or damp
                l = if (lShift > 0f) {
                    l + (1f - l) * lShift * 0.4f
                } else {
                    l + l * lShift * 0.4f
                }.coerceIn(0f, 1f)

                // Re-calculate back to raw RGB
                val rgbOut = hslToRgbInt(h, s, l)
                r = ((rgbOut shr 16) and 0xFF).toFloat()
                g = ((rgbOut shr 8) and 0xFF).toFloat()
                b = (rgbOut and 0xFF).toFloat()
            }

            // 9. Vignetting (Darkens or lightens corners)
            if (c.vignette != 0f) {
                val x = i % width
                val y = i / width
                val dx = x - cx
                val dy = y - cy
                val dNorm = sqrt(dx * dx + dy * dy) / maxDist
                
                // Vignette applies more strongly towards edges
                val intensity = dNorm.coerceIn(0f, 1f).pow(2f)
                val vFactor = if (c.vignette < 0f) {
                    1f + c.vignette * intensity * 0.75f // shadow vignette
                } else {
                    1f + c.vignette * intensity * 0.35f // light glow vignette
                }
                r *= vFactor
                g *= vFactor
                b *= vFactor
            }

            val rI = r.coerceIn(0f, 255f).toInt()
            val gI = g.coerceIn(0f, 255f).toInt()
            val bI = b.coerceIn(0f, 255f).toInt()

            pixels[i] = (0xFF shl 24) or (rI shl 16) or (gI shl 8) or bI
        }

        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    private fun applyTransformations(src: Bitmap, rotation: Int, horizontalFlip: Boolean, verticalFlip: Boolean): Bitmap {
        if (rotation == 0 && !horizontalFlip && !verticalFlip) return src

        val matrix = Matrix()
        if (horizontalFlip) matrix.postScale(-1f, 1f)
        if (verticalFlip) matrix.postScale(1f, -1f)
        if (rotation != 0) matrix.postRotate(rotation.toFloat())

        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Fast 3x3 sharp convolution kernel filter
     */
    private fun applySharpening(src: Bitmap, sharpness: Float): Bitmap {
        val width = src.width
        val height = src.height
        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val itemPixels = IntArray(width * height)
        src.getPixels(itemPixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)

        val weightCenter = 1f + 4f * sharpness
        val weightEdge = -sharpness

        // Execute kernel across internal body pixels bounds
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                // 3x3 simple cross sharpening
                val c0 = itemPixels[idx]
                val cLeft = itemPixels[idx - 1]
                val cRight = itemPixels[idx + 1]
                val cUp = itemPixels[idx - width]
                val cDown = itemPixels[idx + width]

                var r = (((c0 shr 16) and 0xFF) * weightCenter) +
                        ((((cLeft shr 16) and 0xFF) + ((cRight shr 16) and 0xFF) + ((cUp shr 16) and 0xFF) + ((cDown shr 16) and 0xFF)) * weightEdge)

                var g = (((c0 shr 8) and 0xFF) * weightCenter) +
                        ((((cLeft shr 8) and 0xFF) + ((cRight shr 8) and 0xFF) + ((cUp shr 8) and 0xFF) + ((cDown shr 8) and 0xFF)) * weightEdge)

                var b = ((c0 and 0xFF) * weightCenter) +
                        ((((cLeft and 0xFF) + (cRight and 0xFF) + (cUp and 0xFF) + (cDown and 0xFF)) * weightEdge) * weightEdge)

                outPixels[idx] = (0xFF shl 24) or
                        (r.coerceIn(0f, 255f).toInt() shl 16) or
                        (g.coerceIn(0f, 255f).toInt() shl 8) or
                        b.coerceIn(0f, 255f).toInt()
            }
        }

        // Fill borders to prevent dark outlines
        for (x in 0 until width) {
            outPixels[x] = itemPixels[x]
            outPixels[(height - 1) * width + x] = itemPixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outPixels[y * width] = itemPixels[y * width]
            outPixels[y * width + (width - 1)] = itemPixels[y * width + (width - 1)]
        }

        outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    // --- MATHS HSV/HSL TRANSLATORS ---

    fun rgbToHsl(r: Int, g: Int, b: Int, hsl: FloatArray) {
        val rF = r / 255f
        val gF = g / 255f
        val bF = b / 255f

        val max = maxOf(rF, gF, bF)
        val min = minOf(rF, gF, bF)
        val delta = max - min

        var h = 0f
        var s = 0f
        val l = (max + min) / 2f

        if (delta != 0f) {
            s = if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)

            h = when (max) {
                rF -> (gF - bF) / delta + (if (gF < bF) 6f else 0f)
                gF -> (bF - rF) / delta + 2f
                else -> (rF - gF) / delta + 4f
            }
            h *= 60f
        }

        hsl[0] = h
        hsl[1] = s
        hsl[2] = l
    }

    fun hslToRgbInt(h: Float, s: Float, l: Float): Int {
        var r = l
        var g = l
        var b = l

        if (s != 0f) {
            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            val hN = h / 360f

            r = hueToRgb(p, q, hN + 1f/3f)
            g = hueToRgb(p, q, hN)
            b = hueToRgb(p, q, hN - 1f/3f)
        }

        val rI = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
        val gI = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bI = (b * 255f + 0.5f).toInt().coerceIn(0, 255)

        return (0xFF shl 24) or (rI shl 16) or (gI shl 8) or bI
    }

    private fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var tVar = t
        if (tVar < 0f) tVar += 1f
        if (tVar > 1f) tVar -= 1f
        if (tVar < 1f/6f) return p + (q - p) * 6f * tVar
        if (tVar < 1f/2f) return q
        if (tVar < 2f/3f) return p + (q - p) * (2f/3f - tVar) * 6f
        return p
    }
}
