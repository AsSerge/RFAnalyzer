package com.mantz_it.rfanalyzer.network

import android.util.Log
import com.mantz_it.rfanalyzer.database.COUNTRY_CODE_TO_NAME
import com.mantz_it.rfanalyzer.database.Coordinates
import com.mantz_it.rfanalyzer.database.SourceProvider
import com.mantz_it.rfanalyzer.database.Station
import com.mantz_it.rfanalyzer.ui.composable.DemodulationMode
import com.mantz_it.rfanalyzer.ui.composable.sha256
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * <h1>RF Analyzer - PotaStationFetcher</h1>
 *
 * Module:      PotaStationFetcher.kt
 * Description: Fetch stations (bookmarks) from Pota spots
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2026 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 */

object PotaStationFetcher {
    private const val TAG = "PotaStationFetcher"

    suspend fun fetchStations(url: String, oldStations: List<Station>): List<Station> = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchStation: Fetching $url")
        val url = URL(url)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "GET"
        val data = connection.inputStream.bufferedReader().readText()
        val jsonArray = JSONArray(data)
        val now = System.currentTimeMillis()
        Log.d(TAG, "fetchStations: Received ${jsonArray.length()} objects.")

        (0 until jsonArray.length()).mapNotNull { i ->
            val obj = jsonArray.getJSONObject(i)
            val spotId = obj.optInt("spotId", 0)
            val uniqueHash = sha256("POTA_$spotId")
            val oldStation = oldStations.find { it.remoteHash == uniqueHash }
            val freq = obj.optDouble("frequency", 0.0) * 1000.0
            val name = obj.optString("activator", "Unknown")
            val parkId = obj.optString("reference", "")
            val countryCode = parkId.substringBefore("-")
            val parkName = obj.optString("name", "-")
            val mode = when (obj.optString("mode")) {
                "CW" -> DemodulationMode.CW
                "FM" -> DemodulationMode.NFM
                "SSB" -> if (freq < 10_000.0) DemodulationMode.LSB else DemodulationMode.USB
                //"FT8"
                else -> DemodulationMode.OFF
            }
            val coordinates = Coordinates(obj.optDouble("latitude"), obj.optDouble("longitude"))
            val gridlocator = obj.optString("grid6")
            val comments = obj.optString("comments")
            //Log.d(TAG, "fetchStations: $name $freq $mode $coordinates  grid6:$gridlocator  ${obj.optString("mode")}")
            if (freq > 0) Station(
                name = "$name (Park: $parkId)",
                callsign = name,
                bookmarkListId = null,
                frequency = freq.toLong(),
                bandwidth = mode.defaultChannelWidth,
                createdAt = now,
                updatedAt = now,
                favorite = oldStation?.favorite ?: false,
                mode = mode,
                notes = comments,
                coordinates = coordinates,
                countryCode = if(countryCode.length == 2) countryCode else null,
                countryName = COUNTRY_CODE_TO_NAME[countryCode],
                address = parkName,
                spottime = parsePotaTimestamp(obj.optString("spotTime")).toEpochMilli(),
                remoteHash = uniqueHash,
                source = SourceProvider.POTA
            ) else null
        }
    }

    private fun parsePotaTimestamp(timestamp: String): Instant {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val localDateTime = LocalDateTime.parse(timestamp, formatter)
        return localDateTime.atZone(ZoneOffset.UTC).toInstant()
    }
}