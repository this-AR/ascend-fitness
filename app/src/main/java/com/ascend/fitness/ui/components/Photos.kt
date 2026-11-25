package com.ascend.fitness.ui.components

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ascend.fitness.ui.theme.LocalAscend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private object PhotoCache {
    private val cache = LruCache<String, ImageBitmap>(48)

    suspend fun load(assets: AssetManager, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        // Only bundled photos are ever opened.
        if (!path.matches(Regex("img/[0-9a-f]{12}\\.webp"))) return null
        return withContext(Dispatchers.IO) {
            runCatching { assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }?.also { cache.put(path, it) }
    }
}

@Composable
private fun rememberPhoto(path: String): ImageBitmap? {
    val assets = LocalContext.current.assets
    val bmp by produceState<ImageBitmap?>(null, path) { value = if (path.isBlank()) null else PhotoCache.load(assets, path) }
    return bmp
}

/** Start and end position photos, alternating like a short animation. */
@Composable
fun ExercisePhotos(paths: List<String>, name: String, similarTo: String = "") {
    var i by remember(paths) { mutableIntStateOf(0) }
    LaunchedEffect(paths) { if (paths.size > 1) while (true) { delay(1200); i = (i + 1) % paths.size } }
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp)).background(Color.White)) {
        Crossfade(targetState = i, animationSpec = tween(350), label = "photo") { idx ->
            val bmp = rememberPhoto(paths[idx])
            if (bmp != null) Image(bmp, contentDescription = "$name, position ${idx + 1} of ${paths.size}",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
    if (similarTo.isNotBlank()) Muted("Photo shows a similar exercise: $similarTo")
}

@Composable
fun ExerciseThumb(path: String, modifier: Modifier = Modifier, primary: String = "", secondary: String = "") {
    val c = LocalAscend.current
    val bmp = rememberPhoto(path)
    Box(modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(if (bmp != null) Color.White else c.surface2),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (path.isBlank() && primary.isNotBlank()) MuscleThumbFigure(primary, secondary, Modifier.fillMaxSize().padding(vertical = 3.dp))
    }
}
