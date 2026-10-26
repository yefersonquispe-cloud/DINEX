package com.example.dinex

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray

/**
 * Real street map (OpenStreetMap tiles, no API key) with a Leaflet heat
 * layer, rendered in a WebView. The tiles need internet; the list under the
 * map always works. The card stays on the phone because web has no heatmap yet.
 */
@Composable
actual fun SpendingHeatmap(places: List<SpendingPlace>, modifier: Modifier) {
    if (places.isEmpty()) {
        SpendingHeatmapFallback(places, modifier, "Aún no hay lugares con coordenadas para dibujar el mapa.")
        return
    }
    WebSpendingHeatmap(places, modifier)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebSpendingHeatmap(places: List<SpendingPlace>, modifier: Modifier) {
    val context = LocalContext.current
    val html = remember(places, context) {
        heatmapHtml(
            places = places,
            leafletCss = readMapAsset(context, "map/leaflet.css"),
            leafletJs = readMapAsset(context, "map/leaflet.js"),
            heatJs = readMapAsset(context, "map/leaflet-heat.js"),
        )
    }
    Box(modifier.background(Brush.linearGradient(listOf(Mint, Card)))) {
        // Visible mientras las calles no cargan (por ejemplo, sin internet).
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Red.copy(alpha = .55f), Orange.copy(alpha = .22f), Color.Transparent))),
            )
            Spacer(Modifier.height(8.dp))
            Text("Cargando mapa…", color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "Las calles se cargan con internet. Tus lugares están en la lista de abajo.",
                color = Gray, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp,
            )
        }
        key(html) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }
                    WebView(context).apply {
                        // WRAP_CONTENT makes the page 0 px tall, so 100% heights collapse.
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        webViewClient = WebViewClient()
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                Log.d("DinexMap", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                                return true
                            }
                        }
                        loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
                    }
                },
            )
        }
    }
}

private fun readMapAsset(context: Context, path: String): String =
    runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrDefault("")

private fun heatmapHtml(
    places: List<SpendingPlace>,
    leafletCss: String,
    leafletJs: String,
    heatJs: String,
): String {
    val points = JSONArray()
    val labels = JSONArray()
    places.forEach { place ->
        points.put(JSONArray().put(place.latitude).put(place.longitude).put(place.total))
        labels.put("${place.label} · ${money(place.total)}")
    }
    val gradient = HeatStops.zip(HeatColors).joinToString(",", "{", "}") { (stop, color) ->
        "\"$stop\":\"#${(color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')}\""
    }
    val template = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            __LEAFLET_CSS__
            html, body, #map { height: 100%; margin: 0; background: transparent; }
            .leaflet-container { background: #EEF1EC; }
            .leaflet-control-attribution { font-size: 8px; }
            /* Softer streets so the heat colors stand out. */
            .leaflet-tile-pane { filter: saturate(0.55) brightness(1.04); }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>__LEAFLET_JS__</script>
          <script>__HEAT_JS__</script>
          <script>
            const points = __POINTS__;
            const labels = __LABELS__;
            // The WebView can start at 0 px tall inside the list; Leaflet.heat
            // throws on an empty canvas, so wait until the map has a size.
            function draw() {
              const map = L.map('map', { zoomControl: true, attributionControl: true });
              L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; OpenStreetMap'
              }).addTo(map);
              const latLngs = points.map(function (p) { return [p[0], p[1]]; });
              if (latLngs.length === 1) {
                map.setView(latLngs[0], 15);
              } else {
                map.fitBounds(latLngs, { padding: [36, 36], maxZoom: 16 });
              }
              const maxValue = Math.max.apply(null, points.map(function (p) { return p[2]; }).concat([1]));
              if (typeof L.heatLayer === 'function') {
                L.heatLayer(points.map(function (p) {
                  return [p[0], p[1], 0.25 + 0.75 * (p[2] / maxValue)];
                }), {
                  radius: 38, blur: 28, max: 1, maxZoom: 14, minOpacity: 0.45,
                  gradient: __GRADIENT__
                }).addTo(map);
              }
              points.forEach(function (p, i) {
                L.circleMarker([p[0], p[1]], {
                  radius: 4, color: '#FFFFFF', weight: 2,
                  fillColor: '#8E0F2E', fillOpacity: 0.95
                }).addTo(map).bindTooltip(document.createTextNode(labels[i]), { direction: 'top' });
              });
              new ResizeObserver(function () { map.invalidateSize(); }).observe(document.getElementById('map'));
            }
            (function waitForSize() {
              const el = document.getElementById('map');
              if (el.clientWidth > 0 && el.clientHeight > 0) draw();
              else requestAnimationFrame(waitForSize);
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
    return template
        .replace("__LEAFLET_CSS__", leafletCss)
        .replace("__LEAFLET_JS__", leafletJs)
        .replace("__HEAT_JS__", heatJs)
        .replace("__POINTS__", points.toString())
        .replace("__LABELS__", labels.toString())
        .replace("__GRADIENT__", gradient)
}
