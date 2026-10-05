@file:OptIn(UnstableApi::class)

package com.personal.videoeditor

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class EditorVm(app: Application) : AndroidViewModel(app) {
    var clips by mutableStateOf<List<Clip>>(emptyList())
    var selected by mutableIntStateOf(0)
    var music by mutableStateOf<Music?>(null)
    var aspect by mutableStateOf(Aspect.LANDSCAPE)
    var muteOriginal by mutableStateOf(false)
    var exportProgress by mutableStateOf<Float?>(null)
    var message by mutableStateOf<String?>(null)

    private var nextId = 1L
    private var transformer: Transformer? = null
    private var outFile: File? = null

    val totalMs get() = clips.sumOf { it.endMs - it.startMs }

    // ---------- Importar ----------
    fun addVideos(uris: List<Uri>) {
        viewModelScope.launch {
            for (u in uris) {
                try { clips = clips + withContext(Dispatchers.IO) { probe(u) } }
                catch (e: Exception) { message = "No se pudo leer uno de los videos" }
            }
            if (clips.isNotEmpty() && selected !in clips.indices) selected = 0
        }
    }

    private fun probe(uri: Uri): Clip {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(getApplication<Application>(), uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: throw IOException("sin duración")
            val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val thumb = frame?.let { Bitmap.createScaledBitmap(it, 240, (240f * it.height / it.width).toInt().coerceAtLeast(1), true) }
            return Clip(nextId++, uri, dur, 0, dur, thumb = thumb)
        } finally { r.release() }
    }

    fun setMusic(uri: Uri) {
        viewModelScope.launch {
            try {
                music = withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(ctx, uri)
                        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: throw IOException()
                        var name = "Música"
                        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) name = it.getString(0) ?: name
                        }
                        Music(uri, name, dur)
                    } finally { r.release() }
                }
            } catch (e: Exception) { message = "No se pudo leer ese audio" }
        }
    }

    // ---------- Edición ----------
    fun edit(f: (Clip) -> Clip) {
        if (selected !in clips.indices) return
        clips = clips.toMutableList().also { it[selected] = f(it[selected]) }
    }

    fun move(dir: Int) {
        val j = selected + dir
        if (selected !in clips.indices || j !in clips.indices) return
        clips = clips.toMutableList().also { val t = it[selected]; it[selected] = it[j]; it[j] = t }
        selected = j
    }

    fun duplicate() {
        val c = clips.getOrNull(selected) ?: return
        clips = clips.toMutableList().also { it.add(selected + 1, c.copy(id = nextId++)) }
        selected += 1
    }

    fun remove() {
        if (selected !in clips.indices) return
        clips = clips.toMutableList().also { it.removeAt(selected) }
        selected = selected.coerceAtMost(clips.size - 1).coerceAtLeast(0)
    }

    // ---------- Exportar ----------
    fun export() {
        if (clips.isEmpty()) { message = "Agrega al menos un video"; return }
        if (exportProgress != null) return
        val ctx = getApplication<Application>()
        val out = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        outFile = out
        val pres = Presentation.createForWidthAndHeight(aspect.w, aspect.h, Presentation.LAYOUT_SCALE_TO_FIT)

        val videoItems = clips.map { c ->
            EditedMediaItem.Builder(MediaItem.Builder().setUri(c.uri).setClippingConfiguration(clipCfg(c)).build())
                .setRemoveAudio(muteOriginal)
                .setEffects(Effects(listOf<AudioProcessor>(), clipEffects(c, pres)))
                .build()
        }
        val seqs = mutableListOf<EditedMediaItemSequence>()
        seqs.add(EditedMediaItemSequence.Builder().apply { videoItems.forEach { addItem(it) } }.build())
        music?.let { m ->
            val end = minOf(m.durationMs, totalMs)
            val cfg = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(end).build()
            val item = EditedMediaItem.Builder(MediaItem.Builder().setUri(m.uri).setClippingConfiguration(cfg).build())
                .setRemoveVideo(true).build()
            seqs.add(EditedMediaItemSequence.Builder().addItem(item).build())
        }

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                viewModelScope.launch {
                    try {
                        withContext(Dispatchers.IO) { saveToGallery(out) }
                        message = "Video guardado en Galería (Movies/VideoEditor)"
                    } catch (e: Exception) { message = "No se pudo guardar: ${e.message}" }
                    out.delete(); exportProgress = null
                }
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                out.delete(); exportProgress = null
                message = "Error al exportar: ${exportException.message}"
            }
        }
        val t = Transformer.Builder(ctx).setVideoMimeType(MimeTypes.VIDEO_H264).addListener(listener).build()
        transformer = t
        exportProgress = 0f
        try {
            t.start(Composition.Builder(seqs).build(), out.absolutePath)
        } catch (e: Exception) {
            exportProgress = null; message = "No se pudo iniciar la exportación: ${e.message}"; return
        }
        viewModelScope.launch {
            val holder = ProgressHolder()
            while (exportProgress != null) {
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) exportProgress = holder.progress / 100f
                delay(300)
            }
        }
    }

    fun cancelExport() {
        transformer?.cancel()
        outFile?.delete()
        exportProgress = null
    }

    private fun saveToGallery(file: File) {
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Video_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoEditor")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("MediaStore")
        resolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
        values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
