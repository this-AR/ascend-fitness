package com.ascend.fitness.ui.components

import android.content.res.AssetManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.ascend.fitness.domain.TIERS
import com.caverock.androidsvg.SVG

private object MedalCache {
    private val cache = HashMap<Int, SVG>()
    fun get(assets: AssetManager, tier: Int): SVG = synchronized(cache) {
        cache.getOrPut(tier) { SVG.getFromAsset(assets, "medals/tier$tier.svg") }
    }
}

/** Rank medal for tier index 0..9 (Rookie..Galactic demon). Drawn from the bundled vector artwork. */
@Composable
fun Medal(tierIndex: Int, size: Dp, modifier: Modifier = Modifier) {
    val t = tierIndex.coerceIn(0, 9)
    val svg = MedalCache.get(LocalContext.current.assets, t + 1)
    Canvas(modifier.size(size, size * 1.15f).semantics { contentDescription = "${TIERS[t].name} medal" }) {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.scale(this.size.width / 200f, this.size.height / 230f)
            svg.renderToCanvas(nc)
            nc.restore()
        }
    }
}
