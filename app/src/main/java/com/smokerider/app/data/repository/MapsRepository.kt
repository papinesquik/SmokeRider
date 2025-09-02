package com.smokerider.app.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.math.ceil
import kotlin.system.measureTimeMillis

class MapsRepository(private val apiKey: String) {

    // Ritorna minuti o null (mai -1)
    suspend fun calculateDeliveryTime(
        clientLat: Double,
        clientLng: Double,
        riderLat: Double,
        riderLng: Double
    ): Double? = withContext(Dispatchers.IO) {
        val tag = "MapsRepository"
        try {
            // 1) Validazione input
            Log.d(tag, "calcETD inputs -> client=($clientLat,$clientLng), rider=($riderLat,$riderLng)")
            if (!clientLat.isFinite() || !clientLng.isFinite() || !riderLat.isFinite() || !riderLng.isFinite()) {
                Log.e(tag, "Invalid coordinates (NaN/Infinity).")
                return@withContext null
            }

            // 2) Costruzione URL
            val origin = "${riderLat},${riderLng}"
            val destination = "${clientLat},${clientLng}"
            val urlStr = buildString {
                append("https://maps.googleapis.com/maps/api/directions/json?")
                append("origin=").append(URLEncoder.encode(origin, "UTF-8"))
                append("&destination=").append(URLEncoder.encode(destination, "UTF-8"))
                append("&mode=driving")
                append("&departure_time=now")
                append("&traffic_model=best_guess")
                append("&key=").append(apiKey)
            }

            // Offusca la key nei log
            val maskedUrl = urlStr.replace(apiKey, apiKey.take(4) + "…")

            Log.d(tag, "Directions request URL: $maskedUrl")

            // 3) Chiamata HTTP
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            var minutes: Double? = null
            val elapsedMs = measureTimeMillis {
                val code = conn.responseCode
                Log.d(tag, "HTTP response code: $code")

                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                if (stream == null) {
                    Log.e(tag, "Null response stream (code=$code)")
                    return@withContext null
                }

                val body = stream.bufferedReader().use(BufferedReader::readText)
                val preview = if (body.length > 800) body.take(800) + "…[truncated]" else body
                Log.d(tag, "Raw JSON (preview): $preview")

                if (code !in 200..299) {
                    Log.e(tag, "HTTP error: $code")
                    return@withContext null
                }

                // 4) Parsing JSON
                val json = JSONObject(body)
                val status = json.optString("status")
                val errorMsg = json.optString("error_message")
                Log.d(tag, "Directions status=$status, error_message=$errorMsg")

                if (status != "OK") {
                    Log.e(tag, "Directions not OK -> status=$status err=$errorMsg")
                    return@withContext null
                }

                val leg0 = json.optJSONArray("routes")
                    ?.optJSONObject(0)
                    ?.optJSONArray("legs")
                    ?.optJSONObject(0)

                if (leg0 == null) {
                    Log.e(tag, "No route/leg found in response.")
                    return@withContext null
                }

                val durTrafficSec = leg0.optJSONObject("duration_in_traffic")?.optLong("value", -1L) ?: -1L
                val durSec = if (durTrafficSec > 0) durTrafficSec
                else leg0.optJSONObject("duration")?.optLong("value", -1L) ?: -1L

                if (durSec <= 0) {
                    Log.e(tag, "Invalid duration (seconds)=$durSec")
                    return@withContext null
                }

                minutes = ceil(durSec / 60.0) // minuti arrotondati
                Log.d(tag, "Parsed duration: $durSec sec -> ${minutes?.toInt()} min")
            }

            Log.d(tag, "Directions fetch+parse took ${elapsedMs}ms")
            minutes
        } catch (e: Exception) {
            Log.e(tag, "calculateDeliveryTime error", e)
            null
        }
    }
}
