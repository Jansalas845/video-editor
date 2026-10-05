@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.personal.videoeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun EditorScreen(vm: EditorVm) {
    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { vm.addVideos(it) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.setMusic(u) }
    val addVideos = { pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    val addMusic = { pickAudio.launch(arrayOf("audio/*")) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    val clip = vm.clips.getOrNull(vm.selected)

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Editor de video") },
                actions = {
                    Button(onClick = { vm.export() }, enabled = vm.clips.isNotEmpty() && vm.exportProgress == null) {
                        Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Exportar")
                    }
                    Spacer(Modifier.width(8.dp))
                })
        },
    ) { inner ->
        BoxWithConstraints(Modifier.padding(inner).fillMaxSize()) {
            if (maxWidth >= 720.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Preview(clip); Timeline(vm, addVideos)
                    }
                    Column(Modifier.width(400.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Controls(vm, clip, addMusic)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Preview(clip); Timeline(vm, addVideos); Controls(vm, clip, addMusic)
                }
            }
        }
    }

    vm.exportProgress?.let { p ->
        AlertDialog(
            onDismissRequest = {}, title = { Text("Exportando…") },
            text = { Column {
                LinearProgressIndicator({ p }, Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                Text("${(p * 100).toInt()} %  ·  No cierres la app hasta que termine.")
            } },
            confirmButton = { TextButton(onClick = { vm.cancelExport() }) { Text("Cancelar") } })
    }
}

@Composable
private fun Preview(clip: Clip?) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(clip) {
        if (clip == null) { player.clearMediaItems(); return@LaunchedEffect }
        delay(300)   // pequeña espera para no recargar en cada cambio
        player.setVideoEffects(clipEffects(clip))
        player.setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(clipCfg(clip)).build())
        player.prepare()
        player.playWhenReady = false
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).background(Color.Black)) {
        if (clip == null) Text("Vista previa", Modifier.align(Alignment.Center), color = Color.White)
        else AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Timeline(vm: EditorVm, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Línea de tiempo · ${fmt(vm.totalMs)}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            FilledTonalButton(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Videos") }
        }
        if (vm.clips.isEmpty()) {
            Text("Toca «Videos» para agregar clips de tu galería.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.clips, key = { _, c -> c.id }) { i, c ->
                    val sel = i == vm.selected
                    Box(
                        Modifier.width(110.dp).height(70.dp).clip(MaterialTheme.shapes.small).background(Color.DarkGray)
                            .clickable { vm.selected = i }
                    ) {
                        c.thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        Text(fmt(c.endMs - c.startMs), Modifier.align(Alignment.BottomEnd).background(Color(0xAA000000)).padding(horizontal = 4.dp),
                            color = Color.White, style = MaterialTheme.typography.labelSmall)
                        if (sel) Box(Modifier.fillMaxSize().background(Color.Transparent).then(
                            Modifier.padding(0.dp)).clip(MaterialTheme.shapes.small).background(Color(0x332196F3)))
                    }
                }
            }
            Row {
                IconButton(onClick = { vm.move(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Mover atrás") }
                IconButton(onClick = { vm.move(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Mover adelante") }
                IconButton(onClick = { vm.duplicate() }) { Icon(Icons.Default.ContentCopy, "Duplicar") }
                IconButton(onClick = { vm.remove() }) { Icon(Icons.Default.Delete, "Eliminar") }
                Text("Clip ${vm.selected + 1} de ${vm.clips.size}", Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CommitSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onCommit: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Text("$label: ${"%.2f".format(v)}", style = MaterialTheme.typography.labelMedium)
    Slider(v, { v = it }, valueRange = range, onValueChangeFinished = { onCommit(v) })
}

@Composable
private fun Controls(vm: EditorVm, clip: Clip?, onMusic: () -> Unit) {
    if (clip != null) {
        Text("Recorte del clip", style = MaterialTheme.typography.titleSmall)
        var range by remember(clip.id, clip.startMs, clip.endMs) {
            mutableStateOf(clip.startMs.toFloat()..clip.endMs.toFloat())
        }
        Text("${fmt(range.start.toLong())} – ${fmt(range.endInclusive.toLong())}", style = MaterialTheme.typography.labelMedium)
        RangeSlider(
            value = range, onValueChange = { range = it }, valueRange = 0f..clip.durationMs.toFloat(),
            onValueChangeFinished = {
                var s = range.start.toLong(); var e = range.endInclusive.toLong()
                if (e - s < 500) { e = minOf(clip.durationMs, s + 500); s = maxOf(0, e - 500) }
                vm.edit { it.copy(startMs = s, endMs = e) }
            })

        HorizontalDivider()
        Text("Texto", style = MaterialTheme.typography.titleSmall)
        var text by remember(clip.id) { mutableStateOf(clip.text) }
        OutlinedTextField(text, { text = it; vm.edit { c -> c.copy(text = it) } }, Modifier.fillMaxWidth(), label = { Text("Texto sobre el clip") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextPos.values().forEach { p ->
                FilterChip(clip.textPos == p, { vm.edit { it.copy(textPos = p) } }, label = { Text(p.label) })
            }
        }

        HorizontalDivider()
        Text("Filtros", style = MaterialTheme.typography.titleSmall)
        FilterChip(clip.gray, { vm.edit { it.copy(gray = !it.gray) } }, label = { Text("Blanco y negro") })
        CommitSlider("Brillo", clip.brightness, -0.5f..0.5f) { v -> vm.edit { it.copy(brightness = v) } }
        CommitSlider("Contraste", clip.contrast, -0.5f..0.5f) { v -> vm.edit { it.copy(contrast = v) } }
        HorizontalDivider()
    }

    Text("Proyecto", style = MaterialTheme.typography.titleSmall)
    Text("Formato de salida", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Aspect.values().forEach { a -> FilterChip(vm.aspect == a, { vm.aspect = a }, label = { Text(a.label) }) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Silenciar audio original", Modifier.weight(1f))
        Switch(vm.muteOriginal, { vm.muteOriginal = it })
    }
    val m = vm.music
    if (m == null) {
        OutlinedButton(onClick = onMusic) { Icon(Icons.Default.MusicNote, null); Spacer(Modifier.width(6.dp)); Text("Agregar música") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, null)
            Text("${m.name} (${fmt(m.durationMs)})", Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { vm.music = null }) { Icon(Icons.Default.Close, "Quitar música") }
        }
    }
    Text("La vista previa muestra recorte, texto y filtros del clip seleccionado. El resultado final se genera con «Exportar».",
        style = MaterialTheme.typography.labelSmall)
}
