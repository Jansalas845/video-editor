@file:OptIn(UnstableApi::class)

package com.personal.videoeditor

import android.graphics.Bitmap
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList

enum class TextPos(val label: String, val y: Float) { TOP("Arriba", 0.75f), CENTER("Centro", 0f), BOTTOM("Abajo", -0.75f) }
enum class Aspect(val label: String, val w: Int, val h: Int) {
    LANDSCAPE("16:9", 1920, 1080), PORTRAIT("9:16", 1080, 1920), SQUARE("1:1", 1080, 1080)
}

data class Clip(
    val id: Long,
    val uri: Uri,
    val durationMs: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String = "",
    val textPos: TextPos = TextPos.BOTTOM,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val gray: Boolean = false,
    val thumb: Bitmap? = null,
)

data class Music(val uri: Uri, val name: String, val durationMs: Long)

fun clipCfg(c: Clip): MediaItem.ClippingConfiguration =
    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()

private fun textEffect(c: Clip): Effect? {
    if (c.text.isBlank()) return null
    val sp = SpannableString(c.text)
    sp.setSpan(ForegroundColorSpan(android.graphics.Color.WHITE), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sp.setSpan(BackgroundColorSpan(0x99000000.toInt()), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sp.setSpan(AbsoluteSizeSpan(72), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, c.textPos.y).build()
    return OverlayEffect(ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(sp, settings)))
}

/** Efectos de video de un clip (se usan igual en la vista previa y en la exportación). */
fun clipEffects(c: Clip, last: Effect? = null): List<Effect> {
    val l = mutableListOf<Effect>()
    if (c.gray) l.add(RgbFilter.createGrayscaleFilter())
    if (c.brightness != 0f) l.add(Brightness(c.brightness))
    if (c.contrast != 0f) l.add(Contrast(c.contrast))
    textEffect(c)?.let { l.add(it) }
    if (last != null) l.add(last)
    return l
}

fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
