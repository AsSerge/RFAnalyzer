package com.mantz_it.rfanalyzer.database

import android.graphics.Color
import androidx.paging.PagingSource
import androidx.room.*
import com.mantz_it.rfanalyzer.database.AppStateRepository.Companion.VERTICAL_SCALE_LOWER_BOUNDARY
import com.mantz_it.rfanalyzer.ui.composable.DemodulationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.floor
/**
 * <h1>RF Analyzer - StationDao</h1>
 *
 * Module:      StationDao.kt
 * Description: Data Access Object for all entities related to Bookmarks:
 *              - Station
 *              - Band
 *              - BookmarkList
 *              - OnlineStationProviderSettings
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2026 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 */

@Serializable
data class Coordinates(val latitude: Double, val longitude: Double) {
    @Serializable
    val version: Int = 1
    override fun toString(): String {
        return "${toDMS()} [${toGridLocator()}]"
    }

    fun toGridLocator(): String {
        return coordinatesToGridLocator(latitude, longitude)
    }

    fun toDMS(): String {
        return "${toDMS(latitude, true)}, ${toDMS(longitude, false)}"
    }

    companion object {
        fun coordinatesToGridLocator(lat: Double, lon: Double): String {
            // Adjust coordinates
            val adjLon = lon + 180.0
            val adjLat = lat + 90.0

            // First pair of letters (fields)
            val lonField = ('A' + (adjLon / 20).toInt())
            val latField = ('A' + (adjLat / 10).toInt())

            // Digits (squares)
            val lonSquare = ((adjLon % 20) / 2).toInt()
            val latSquare = (adjLat % 10).toInt()

            // Subsquare letters
            val lonSub = ('a' + (((adjLon % 2) / 2) * 24).toInt())
            val latSub = ('a' + (((adjLat % 1) / 1) * 24).toInt())

            return "$lonField$latField$lonSquare$latSquare$lonSub$latSub"
        }

        fun toDMS(value: Double, isLatitude: Boolean): String {
            val hemisphere = if (isLatitude) {
                if (value >= 0) "N" else "S"
            } else {
                if (value >= 0) "E" else "W"
            }
            val absValue = abs(value)
            val degrees = floor(absValue).toInt()
            val minutesFull = (absValue - degrees) * 60
            val minutes = floor(minutesFull).toInt()
            val seconds = (minutesFull - minutes) * 60
            return String.format("%d°%d'%.1f\"%s", degrees, minutes, seconds, hemisphere)
        }

        fun fromGridLocator(locator: String): Coordinates? {
            val out = maidenheadToLatLon(locator)
            return if(out == null) null
            else Coordinates(out.first, out.second)
        }

        fun fromDMS(dms: String): Coordinates? {
            val parts = dms.split(',').map { it.trim() }
            if (parts.size != 2) return null

            val lat = parseDMSPart(parts[0], isLatitude = true) ?: return null
            val lon = parseDMSPart(parts[1], isLatitude = false) ?: return null

            return Coordinates(lat, lon)
        }

        private fun parseDMSPart(part: String, isLatitude: Boolean): Double? {
            val validHemispheres = if (isLatitude) "NS" else "EW"
            fun valuesToDouble(deg: String, min: String, sec: String, hem: String): Double? {
                val degrees = deg.toDoubleOrNull() ?: return null
                val minutes = min.toDoubleOrNull() ?: 0.0
                val seconds = sec.toDoubleOrNull() ?: 0.0
                val sign = if (hem.equals("S", ignoreCase = true) || hem.equals(
                        "W",
                        ignoreCase = true
                    )
                ) -1.0 else 1.0
                val out = sign * (degrees + minutes / 60.0 + seconds / 3600.0)
                //Log.d("StationLocation.Coordinates", "valuesToDouble: $out")
                return out
            }
            //Log.d("StationLocation.Coordinates", "parseDMSPart: $part")
            if (part.contains('°')) {
                // formats like: 40°50'10"N
                val regex =
                    """(\d{1,3})°\s?(\d{1,2})'\s?(\d{1,2}(?:\.\d+)?)"\s?([$validHemispheres])""".toRegex(
                        RegexOption.IGNORE_CASE
                    )
                val match = regex.find(part)
                match?.let {
                    val (degStr, minStr, secStr, hemStr) = it.destructured
                    //Log.d("StationLocation.Coordinates", "parseDMSPart: $degStr  | $hemStr  |  $minStr  |  $secStr")
                    return valuesToDouble(degStr, minStr, secStr, hemStr)
                }
            } else {
                // formats like: 138E40'59", 52N31
                val regex =
                    """(\d{1,3})([$validHemispheres])(\d\d)'?(\d\d)?"?""".toRegex(RegexOption.IGNORE_CASE)
                val match = regex.find(part)
                match?.let {
                    val (degStr, hemStr, minStr, secStr) = it.destructured
                    //Log.d("StationLocation.Coordinates", "parseDMSPart (simple): $degStr  | $hemStr  |  $minStr  |  $secStr")
                    return valuesToDouble(degStr, minStr, secStr, hemStr)
                }
            }
            return null
        }

        fun maidenheadToLatLon(locator: String): Pair<Double, Double>? {
            if(locator.length < 6) return null

            // Convert letters to indices
            val lonField = locator[0].uppercaseChar() - 'A'
            val latField = locator[1].uppercaseChar() - 'A'

            val lonSquare = locator[2].digitToInt()
            val latSquare = locator[3].digitToInt()

            val lonSub = locator[4].lowercaseChar() - 'a'
            val latSub = locator[5].lowercaseChar() - 'a'

            // Calculate longitude
            var lon = lonField * 20.0 - 180.0       // field
            lon += lonSquare * 2.0                  // square
            lon += lonSub * (2.0 / 24.0)            // subsquare
            lon += 2.0 / 48.0                       // center of subsquare

            // Calculate latitude
            var lat = latField * 10.0 - 90.0        // field
            lat += latSquare * 1.0                   // square
            lat += latSub * (1.0 / 24.0)             // subsquare
            lat += 1.0 / 48.0                        // center of subsquare

            return Pair(lat, lon)
        }
    }
}

@Serializable
data class Schedule(
    val version: Int = 1,
    val startTimeUtc: String = "0000",      // e.g. "0930"  for 9:30 Utc
    val endTimeUtc: String = "2400",
    val days: Set<Weekday> = emptySet(),
    val remarks: String? = null
) {
    @Serializable
    enum class Weekday(val shortName: String, val fullName: String) {
        MONDAY("Mo", "Monday"),
        TUESDAY("Tu", "Tuesday"),
        WEDNESDAY("We", "Wednesday"),
        THURSDAY("Th", "Thursday"),
        FRIDAY("Fr", "Friday"),
        SATURDAY("Sa", "Saturday"),
        SUNDAY("Su", "Sunday");

        companion object {
            fun fromShortName(short: String): Weekday? =
                entries.firstOrNull { it.shortName.equals(short, ignoreCase = true) }

            val ordered = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)
        }
    }
    fun isEmpty(): Boolean {
        return startTimeUtc == "0000" && endTimeUtc == "2400" && days.isEmpty() && remarks?.ifEmpty { null } == null
    }
    fun isNotEmpty(): Boolean = !isEmpty()
    inline fun ifEmpty(alt: () -> Schedule?): Schedule? = if(isEmpty()) alt() else this
}



@Serializable
data class DemodulationParameters(
    val version: Int = 1,
    val squelch: Squelch?,
    //val agc: AGC? = null,
) {
    @Serializable
    data class Squelch(
        val enabled: Boolean = false,
        val thresholdDb: Float = VERTICAL_SCALE_LOWER_BOUNDARY, // in dBFS
    )
    //@Serializable
    //data class AGC(
    //    val mode: String = "Fast", // "Fast", "Slow", "Manual"
    //    val decayMs: Int = 100
    //)
}


enum class SourceProvider(val displayName: String, val color: Int) {
    BOOKMARK("Bookmark", Color.argb(255, 0, 0, 150) /*dark blue*/),
    EIBI("EiBi DB", Color.argb(255, 150, 0, 0) /*dark red*/),
    POTA("POTA Spots", Color.argb(255, 0, 150, 0) /*dark green*/),
    SOTA("SOTA Spots", Color.argb(255, 230, 230, 230) /*light grey*/),
}

@Serializable
data class SubBand(
    val version: Int = 1,
    val name: String,
    val startFrequency: Long,
    val endFrequency: Long,
    val notes: String? = null
)

enum class BookmarkListType(val displayName: String) {
    STATION("Stations"),
    BAND("Bands"),
}

// --- Type Converters ---
class StationTypeConverters {
    @TypeConverter
    fun fromCoordinates(coords: Coordinates?): String? =
        coords?.let { Json.encodeToString(Coordinates.serializer(), it) }

    @TypeConverter
    fun toCoordinates(json: String?): Coordinates? =
        json?.let { Json.decodeFromString(Coordinates.serializer(), it) }

    @TypeConverter
    fun fromSchedule(schedule: Schedule?): String? =
        schedule?.let { Json.encodeToString(Schedule.serializer(), it) }

    @TypeConverter
    fun toSchedule(json: String?): Schedule? =
        json?.let { Json.decodeFromString(Schedule.serializer(), it) }

    @TypeConverter
    fun fromSubBands(subBands: List<SubBand>?): String? =
        subBands?.let { Json.encodeToString(ListSerializer(SubBand.serializer()), it) }

    @TypeConverter
    fun toSubBands(json: String?): List<SubBand>? =
        json?.let { Json.decodeFromString(ListSerializer(SubBand.serializer()), it) }

    @TypeConverter
    fun fromDemodulationParameters(value: DemodulationParameters?): String? =
        value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun toDemodulationParameters(json: String?): DemodulationParameters? =
        json?.let { Json.decodeFromString<DemodulationParameters>(it) }
}

// --- Entity Wrappers ---

data class StationWithBookmarkList(
    @Embedded
    val station: Station,

    @Relation(
        parentColumn = "bookmarkListId",
        entityColumn = "id"
    )
    val bookmarkList: BookmarkList?
)

data class BandWithBookmarkList(
    @Embedded
    val band: Band,

    @Relation(
        parentColumn = "bookmarkListId",
        entityColumn = "id"
    )
    val bookmarkList: BookmarkList?
)


// --- Entities ---

@Serializable
@Entity(tableName = "bookmarkLists")
data class BookmarkList(
    @PrimaryKey(autoGenerate = true) @Transient val id: Long = 0,
    val name: String = "",
    val type: BookmarkListType = BookmarkListType.STATION,
    val notes: String? = null,
    val color: Int? = null
)

@Serializable
@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["source", "remoteHash"], unique = true),
        Index(value = ["frequency"]),
        Index(value = ["favorite"]),
        Index(value = ["bookmarkListId"]),
        Index(value = ["source"])
    ]
)
data class Station(
    @PrimaryKey(autoGenerate = true) @Transient val id: Long = 0,
    @Transient val remoteHash: String? = null,    // null when source=BOOKMARK, otherwise unique hash
    val bookmarkListId: Long? = null,
    val name: String = "",
    val frequency: Long = 0L,
    val bandwidth: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val mode: DemodulationMode = DemodulationMode.OFF,
    val demodulationParameters: DemodulationParameters? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    val language: String? = null,
    val callsign: String? = null,
    val countryCode: String? = null,
    @Transient val countryName: String? = null,  // always derived from countryCode
    val address: String? = null,
    val coordinates: Coordinates? = null,
    val schedule: Schedule? = null,
    val spottime: Long? = null,
    @Transient val source: SourceProvider = SourceProvider.BOOKMARK
)

@Serializable
@Entity(
    tableName = "bands",
    indices = [
        Index(value = ["startFrequency"]),
        Index(value = ["endFrequency"]),
        Index(value = ["bookmarkListId"]),
        Index(value = ["favorite"])
    ]
)
data class Band(
    @PrimaryKey(autoGenerate = true) @Transient val id: Long = 0,
    val name: String = "",
    val bookmarkListId: Long? = null,
    val startFrequency: Long = 0L,
    val endFrequency: Long = 0L,
    val subBands: List<SubBand> = emptyList(),
    val notes: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val displayColor: Int? = null
)

@Serializable
@Entity(tableName = "onlineStationProviderSettings")
data class OnlineStationProviderSettings(
    @PrimaryKey val id: String,
    val autoUpdateEnabled: Boolean,
    val autoUpdateIntervalSeconds: Int,
    val lastUpdatedTimestamp: Long
)

// --- DAO Interfaces ---

@Dao
interface BookmarkListDao {
    @Query("SELECT COUNT(*) FROM bookmarkLists WHERE type = :type")
    suspend fun count(type: BookmarkListType): Int

    @Query("SELECT * FROM bookmarkLists ORDER BY name ASC")
    fun getAll(): Flow<List<BookmarkList>>

    @Query("SELECT * FROM bookmarkLists WHERE id = :id LIMIT 1")
    suspend fun getBookmarkListById(id: Long): BookmarkList?

    @Query("SELECT * FROM bookmarkLists WHERE name = :name AND (:type IS NULL OR type = :type) LIMIT 1")
    fun findByNameAndType(name: String, type: BookmarkListType?): Flow<BookmarkList?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmarkList: BookmarkList): Long

    @Update
    suspend fun update(bookmarkList: BookmarkList)

    @Delete
    suspend fun delete(bookmarkList: BookmarkList)

    @Query("DELETE FROM bookmarkLists")
    suspend fun deleteAllBookmarkLists()
}

@Dao
interface StationDao {
    @Query("SELECT * FROM stations WHERE favorite = 1 ORDER BY frequency ASC")
    fun getFavoriteStations(): Flow<List<Station>>

    @Query("SELECT * FROM stations WHERE id IN (:ids)")
    fun getStationsByIds(ids: Set<Long>): Flow<List<Station>>

    @Query("SELECT * FROM stations WHERE source = :source")
    fun getStationsBySource(source: SourceProvider): Flow<List<Station>>

    @Query("SELECT * FROM stations WHERE source = :source AND remoteHash IN (:hashes)")
    suspend fun getBySourceAndHashes(source: SourceProvider, hashes: List<String>): List<Station>

    @Query("SELECT * FROM stations WHERE bookmarkListId IN (:bookmarkListIds)")
    fun getStationsByBookmarkListIds(bookmarkListIds: Set<Long>): Flow<List<Station>>

    @Transaction
    @Query("""
        SELECT s.*, c.*
        FROM stations s
        LEFT JOIN bookmarkLists c ON c.id = s.bookmarkListId
        WHERE
            (:onlyFavorites = 0 OR s.favorite = 1)
        AND (:minFrequency = 0 OR s.frequency >= :minFrequency)
        AND (:maxFrequency = 0 OR s.frequency <= :maxFrequency)        
        AND (:sourcesSize = 0 OR s.source IN (:sources))
        AND (:bookmarkListsSize = 0 OR s.bookmarkListId IN (:bookmarkLists) OR s.source != 'BOOKMARK')
        AND (:search = '' OR (
            s.name LIKE '%' || :search || '%' OR
            s.notes LIKE '%' || :search || '%' OR
            s.language LIKE '%' || :search || '%' OR
            s.callsign LIKE '%' || :search || '%' OR
            s.countryCode LIKE '%' || :search || '%' OR
            s.countryName LIKE '%' || :search || '%'
        ))
        AND (:modeSize = 0 OR s.mode IN (:modes))
        AND (:onlyOnAirNow = 0
            OR s.schedule IS NULL
            OR (
                (
                    s.schedule NOT LIKE '%"days":[%'  -- no days field OR empty days
                    OR s.schedule LIKE '%"days":[]%'
                    OR s.schedule LIKE '%' || '"' || :currentWeekday || '"' || '%'
                )
                AND (
                    s.schedule NOT LIKE '%"startTimeUtc":"%'
                    OR CAST(substr(s.schedule, instr(s.schedule, '"startTimeUtc":"') + 16, 4) AS INTEGER) <= :currentTimeUtc
                )
                AND (
                    s.schedule NOT LIKE '%"endTimeUtc":"%'
                    OR CAST(substr(s.schedule, instr(s.schedule, '"endTimeUtc":"') + 14, 4) AS INTEGER) >= :currentTimeUtc
                )
            )
        )
        ORDER BY s.frequency ASC, s.name ASC, s.source ASC
    """)
    fun getStationsFiltered(
        onlyFavorites: Boolean,
        minFrequency: Long,
        maxFrequency: Long,
        sources: Set<SourceProvider>,
        sourcesSize: Int,
        bookmarkLists: Set<Long>,
        bookmarkListsSize: Int,
        modes: Set<DemodulationMode>,
        modeSize: Int,
        search: String,
        onlyOnAirNow: Boolean,
        currentTimeUtc: Int,  // e.g. 1530
        currentWeekday: String,  // e.g. "MONDAY"
    ): Flow<List<StationWithBookmarkList>>

    @Transaction
    @Query("""
        SELECT s.*, c.*
        FROM stations s
        LEFT JOIN bookmarkLists c ON c.id = s.bookmarkListId
        WHERE
            (:onlyFavorites = 0 OR s.favorite = 1)
        AND (:minFrequency = 0 OR s.frequency >= :minFrequency)
        AND (:maxFrequency = 0 OR s.frequency <= :maxFrequency)        
        AND (:sourcesSize = 0 OR s.source IN (:sources))
        AND (:bookmarkListsSize = 0 OR s.bookmarkListId IN (:bookmarkLists) OR s.source != 'BOOKMARK')
        AND (:search = '' OR (
            s.name LIKE '%' || :search || '%' OR
            s.notes LIKE '%' || :search || '%' OR
            s.language LIKE '%' || :search || '%' OR
            s.callsign LIKE '%' || :search || '%' OR
            s.countryCode LIKE '%' || :search || '%' OR
            s.countryName LIKE '%' || :search || '%'
        ))
        AND (:modeSize = 0 OR s.mode IN (:modes))
        AND (:onlyOnAirNow = 0
            OR s.schedule IS NULL
            OR (
                (
                    s.schedule NOT LIKE '%"days":[%'  -- no days field OR empty days
                    OR s.schedule LIKE '%"days":[]%'
                    OR s.schedule LIKE '%' || '"' || :currentWeekday || '"' || '%'
                )
                AND (
                    s.schedule NOT LIKE '%"startTimeUtc":"%'
                    OR CAST(substr(s.schedule, instr(s.schedule, '"startTimeUtc":"') + 16, 4) AS INTEGER) <= :currentTimeUtc
                )
                AND (
                    s.schedule NOT LIKE '%"endTimeUtc":"%'
                    OR CAST(substr(s.schedule, instr(s.schedule, '"endTimeUtc":"') + 14, 4) AS INTEGER) >= :currentTimeUtc
                )
            )
        )
        ORDER BY s.frequency ASC, s.name ASC, s.source ASC
    """)
    fun getStationsFilteredPaging(
        onlyFavorites: Boolean,
        minFrequency: Long,
        maxFrequency: Long,
        sources: Set<SourceProvider>,
        sourcesSize: Int,
        bookmarkLists: Set<Long>,
        bookmarkListsSize: Int,
        modes: Set<DemodulationMode>,
        modeSize: Int,
        search: String,
        onlyOnAirNow: Boolean,
        currentTimeUtc: Int,  // e.g. 1530
        currentWeekday: String,  // e.g. "MONDAY"
    ): PagingSource<Int, StationWithBookmarkList>

    @Query("""
        SELECT s.id
        FROM stations s
        LEFT JOIN bookmarkLists c ON c.id = s.bookmarkListId
        WHERE
            (:onlyFavorites = 0 OR s.favorite = 1)
        AND (:minFrequency = 0 OR s.frequency >= :minFrequency)
        AND (:maxFrequency = 0 OR s.frequency <= :maxFrequency)        
        AND (:sourcesSize = 0 OR s.source IN (:sources))
        AND (:bookmarkListsSize = 0 OR s.bookmarkListId IN (:bookmarkLists) OR s.source != 'BOOKMARK')
        AND (:search = '' OR (
            s.name LIKE '%' || :search || '%' OR
            s.notes LIKE '%' || :search || '%' OR
            s.language LIKE '%' || :search || '%' OR
            s.callsign LIKE '%' || :search || '%' OR
            s.countryCode LIKE '%' || :search || '%' OR
            s.countryName LIKE '%' || :search || '%'
        ))
        AND (:modeSize = 0 OR s.mode IN (:modes))
        AND (:onlyOnAirNow = 0
            OR s.schedule IS NULL
            OR (
                (
                    s.schedule NOT LIKE '%"days":[%'  -- no days field OR empty days
                    OR s.schedule LIKE '%"days":[]%'
                    OR s.schedule LIKE '%' || '"' || :currentWeekday || '"' || '%'
                )
                AND (
                    s.schedule NOT LIKE '%"startTimeUtc":"%'
                    OR CAST(substr(s.schedule, instr(s.schedule, '"startTimeUtc":"') + 16, 4) AS INTEGER) <= :currentTimeUtc
                )
                AND (
                    s.schedule NOT LIKE '%"endTimeUtc":"%'
                    OR CAST(substr(s.schedule, instr(s.schedule, '"endTimeUtc":"') + 14, 4) AS INTEGER) >= :currentTimeUtc
                )
            )
        )
        ORDER BY s.frequency ASC, s.name ASC, s.source ASC
    """)
    fun getStationIdsFiltered(
        onlyFavorites: Boolean,
        minFrequency: Long,
        maxFrequency: Long,
        sources: Set<SourceProvider>,
        sourcesSize: Int,
        bookmarkLists: Set<Long>,
        bookmarkListsSize: Int,
        modes: Set<DemodulationMode>,
        modeSize: Int,
        search: String,
        onlyOnAirNow: Boolean,
        currentTimeUtc: Int,  // e.g. 1530
        currentWeekday: String,  // e.g. "MONDAY"
    ): Flow<List<Long>>


    @Query("DELETE FROM stations WHERE source = :source")
    suspend fun clearBySource(source: SourceProvider)

    @Transaction
    suspend fun replaceAllBySource(stations: List<Station>, source: SourceProvider) {
        val hashes = stations.mapNotNull { it.remoteHash }

        val existing = getBySourceAndHashes(source, hashes)
            .associateBy { it.remoteHash }

        // insert id for all stations that are already in the db
        val entities = stations.map { station ->
            val oldEntry = existing[station.remoteHash]
            station.copy(id = oldEntry?.id ?: 0)
        }

        insertAll(entities)
        deleteMissingForSource(source, hashes)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: Station): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<Station>)

    @Update
    suspend fun update(station: Station)

    @Query("UPDATE stations SET bookmarkListId = :bookmarkListId, updatedAt = :updatedAt, remoteHash = null, source = :source WHERE id IN (:ids)")
    suspend fun moveToBookmarkListByIds(ids: List<Long>, bookmarkListId: Long, updatedAt: Long, source: SourceProvider)

    @Delete
    suspend fun delete(station: Station)

    @Query("DELETE FROM stations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM stations WHERE bookmarkListId = :bookmarkListId")
    suspend fun deleteByBookmarkList(bookmarkListId: Long)

    @Query("""
        DELETE FROM stations
        WHERE source = :source
          AND remoteHash IS NOT NULL
          AND remoteHash NOT IN (:hashes)
    """)
    suspend fun deleteMissingForSource(
        source: SourceProvider,
        hashes: List<String>
    )

}

@Dao
interface BandDao {
    @Query("SELECT * FROM bands ORDER BY startFrequency ASC")
    fun getAllBands(): Flow<List<Band>>

    @Query("SELECT * FROM bands WHERE favorite = 1 ORDER BY startFrequency ASC")
    fun getFavoriteBands(): Flow<List<Band>>

    @Query("SELECT * FROM bands WHERE bookmarkListId IN (:bookmarkListIds)")
    fun getBandsByBookmarkListIds(bookmarkListIds: Set<Long>): Flow<List<Band>>

    @Query("SELECT * FROM bands WHERE id IN (:ids)")
    fun getBandsByIds(ids: Set<Long>): Flow<List<Band>>

    @Query("""
        SELECT * FROM bands
        WHERE
            (:onlyFavorites = 0 OR favorite = 1)
        AND (:minFrequency = 0 OR endFrequency >= :minFrequency)
        AND (:maxFrequency = 0 OR startFrequency <= :maxFrequency)
        AND (:bookmarkListsSize = 0 OR bookmarkListId IN (:bookmarkLists))
        ORDER BY startFrequency ASC
    """)
    fun getBandsFiltered(
        onlyFavorites: Boolean,
        minFrequency: Long,
        maxFrequency: Long,
        bookmarkLists: Set<Long>,
        bookmarkListsSize: Int
    ): Flow<List<Band>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(band: Band): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bands: List<Band>)

    @Update
    suspend fun update(band: Band)

    @Query("UPDATE bands SET bookmarkListId = :bookmarkListId, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun moveToBookmarkListByIds(ids: List<Long>, bookmarkListId: Long, updatedAt: Long)

    @Delete
    suspend fun delete(band: Band)

    @Query("DELETE FROM bands")
    suspend fun deleteAllBands()

    @Query("DELETE FROM bands WHERE bookmarkListId = :bookmarkListId")
    suspend fun deleteByBookmarkList(bookmarkListId: Long)

    @Query("DELETE FROM bands WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM bands WHERE id = :id LIMIT 1")
    suspend fun getBandById(id: Long): Band?
}

@Dao
interface OnlineStationProviderSettingsDao {
    @Query("SELECT * FROM onlineStationProviderSettings ORDER BY id ASC")
    fun getAllOnlineStationProviderSettings(): Flow<List<OnlineStationProviderSettings>>

    @Query("SELECT * FROM onlineStationProviderSettings WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<OnlineStationProviderSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: OnlineStationProviderSettings): Long

    @Update
    suspend fun update(settings: OnlineStationProviderSettings)

    @Delete
    suspend fun delete(settings: OnlineStationProviderSettings)
}

val COUNTRY_CODE_TO_NAME: Map<String, String?> = mapOf(
    "AF" to "Afghanistan",
    "AL" to "Albania",
    "DZ" to "Algeria",
    "AS" to "American Samoa",
    "AD" to "Andorra",
    "AO" to "Angola",
    "AI" to "Anguilla",
    "AQ" to "Antarctica",
    "AG" to "Antigua & Barbuda",
    "AR" to "Argentina",
    "AM" to "Armenia",
    "AW" to "Aruba",
    "AU" to "Australia",
    "AT" to "Austria",
    "AZ" to "Azerbaijan",
    "BS" to "Bahamas",
    "BH" to "Bahrain",
    "BD" to "Bangladesh",
    "BB" to "Barbados",
    "BY" to "Belarus",
    "BE" to "Belgium",
    "BZ" to "Belize",
    "BJ" to "Benin",
    "BM" to "Bermuda",
    "BT" to "Bhutan",
    "BO" to "Bolivia",
    "BA" to "Bosnia & Herzegovina",
    "BW" to "Botswana",
    "BV" to "Bouvet Island",
    "BR" to "Brazil",
    "IO" to "British Indian Ocean Territory",
    "VG" to "British Virgin Islands",
    "BN" to "Brunei",
    "BG" to "Bulgaria",
    "BF" to "Burkina Faso",
    "BI" to "Burundi",
    "KH" to "Cambodia",
    "CM" to "Cameroon",
    "CA" to "Canada",
    "CV" to "Cape Verde",
    "BQ" to "Caribbean Netherlands",
    "KY" to "Cayman Islands",
    "CF" to "Central African Republic",
    "TD" to "Chad",
    "CL" to "Chile",
    "CN" to "China",
    "CX" to "Christmas Island",
    "CC" to "Cocos (Keeling) Islands",
    "CO" to "Colombia",
    "KM" to "Comoros",
    "CG" to "Congo - Brazzaville",
    "CD" to "Congo - Kinshasa",
    "CK" to "Cook Islands",
    "CR" to "Costa Rica",
    "HR" to "Croatia",
    "CU" to "Cuba",
    "CW" to "Curaçao",
    "CY" to "Cyprus",
    "CZ" to "Czechia",
    "CI" to "Côte d’Ivoire",
    "DK" to "Denmark",
    "DJ" to "Djibouti",
    "DM" to "Dominica",
    "DO" to "Dominican Republic",
    "EC" to "Ecuador",
    "EG" to "Egypt",
    "SV" to "El Salvador",
    "GQ" to "Equatorial Guinea",
    "ER" to "Eritrea",
    "EE" to "Estonia",
    "SZ" to "Eswatini",
    "ET" to "Ethiopia",
    "FK" to "Falkland Islands (Islas Malvinas)",
    "FO" to "Faroe Islands",
    "FJ" to "Fiji",
    "FI" to "Finland",
    "FR" to "France",
    "GF" to "French Guiana",
    "PF" to "French Polynesia",
    "TF" to "French Southern Territories",
    "GA" to "Gabon",
    "GM" to "Gambia",
    "GE" to "Georgia",
    "DE" to "Germany",
    "GH" to "Ghana",
    "GI" to "Gibraltar",
    "GR" to "Greece",
    "GL" to "Greenland",
    "GD" to "Grenada",
    "GP" to "Guadeloupe",
    "GU" to "Guam",
    "GT" to "Guatemala",
    "GG" to "Guernsey",
    "GN" to "Guinea",
    "GW" to "Guinea-Bissau",
    "GY" to "Guyana",
    "HT" to "Haiti",
    "HM" to "Heard & McDonald Islands",
    "HN" to "Honduras",
    "HK" to "Hong Kong",
    "HU" to "Hungary",
    "IS" to "Iceland",
    "IN" to "India",
    "ID" to "Indonesia",
    "IR" to "Iran",
    "IQ" to "Iraq",
    "IE" to "Ireland",
    "IM" to "Isle of Man",
    "IL" to "Israel",
    "IT" to "Italy",
    "JM" to "Jamaica",
    "JP" to "Japan",
    "JE" to "Jersey",
    "JO" to "Jordan",
    "KZ" to "Kazakhstan",
    "KE" to "Kenya",
    "KI" to "Kiribati",
    "KW" to "Kuwait",
    "KG" to "Kyrgyzstan",
    "LA" to "Laos",
    "LV" to "Latvia",
    "LB" to "Lebanon",
    "LS" to "Lesotho",
    "LR" to "Liberia",
    "LY" to "Libya",
    "LI" to "Liechtenstein",
    "LT" to "Lithuania",
    "LU" to "Luxembourg",
    "MO" to "Macao",
    "MG" to "Madagascar",
    "MW" to "Malawi",
    "MY" to "Malaysia",
    "MV" to "Maldives",
    "ML" to "Mali",
    "MT" to "Malta",
    "MH" to "Marshall Islands",
    "MQ" to "Martinique",
    "MR" to "Mauritania",
    "MU" to "Mauritius",
    "YT" to "Mayotte",
    "MX" to "Mexico",
    "FM" to "Micronesia",
    "MD" to "Moldova",
    "MC" to "Monaco",
    "MN" to "Mongolia",
    "ME" to "Montenegro",
    "MS" to "Montserrat",
    "MA" to "Morocco",
    "MZ" to "Mozambique",
    "MM" to "Myanmar (Burma)",
    "NA" to "Namibia",
    "NR" to "Nauru",
    "NP" to "Nepal",
    "NL" to "Netherlands",
    "NC" to "New Caledonia",
    "NZ" to "New Zealand",
    "NI" to "Nicaragua",
    "NE" to "Niger",
    "NG" to "Nigeria",
    "NU" to "Niue",
    "NF" to "Norfolk Island",
    "KP" to "North Korea",
    "MK" to "North Macedonia",
    "MP" to "Northern Mariana Islands",
    "NO" to "Norway",
    "OM" to "Oman",
    "PK" to "Pakistan",
    "PW" to "Palau",
    "PS" to "Palestine",
    "PA" to "Panama",
    "PG" to "Papua New Guinea",
    "PY" to "Paraguay",
    "PE" to "Peru",
    "PH" to "Philippines",
    "PN" to "Pitcairn Islands",
    "PL" to "Poland",
    "PT" to "Portugal",
    "PR" to "Puerto Rico",
    "QA" to "Qatar",
    "RO" to "Romania",
    "RU" to "Russia",
    "RW" to "Rwanda",
    "RE" to "Réunion",
    "WS" to "Samoa",
    "SM" to "San Marino",
    "SA" to "Saudi Arabia",
    "SN" to "Senegal",
    "RS" to "Serbia",
    "SC" to "Seychelles",
    "SL" to "Sierra Leone",
    "SG" to "Singapore",
    "SX" to "Sint Maarten",
    "SK" to "Slovakia",
    "SI" to "Slovenia",
    "SB" to "Solomon Islands",
    "SO" to "Somalia",
    "ZA" to "South Africa",
    "GS" to "South Georgia & South Sandwich Islands",
    "KR" to "South Korea",
    "SS" to "South Sudan",
    "ES" to "Spain",
    "LK" to "Sri Lanka",
    "BL" to "St. Barthélemy",
    "SH" to "St. Helena",
    "KN" to "St. Kitts & Nevis",
    "LC" to "St. Lucia",
    "MF" to "St. Martin",
    "PM" to "St. Pierre & Miquelon",
    "VC" to "St. Vincent & Grenadines",
    "SD" to "Sudan",
    "SR" to "Suriname",
    "SJ" to "Svalbard & Jan Mayen",
    "SE" to "Sweden",
    "CH" to "Switzerland",
    "SY" to "Syria",
    "ST" to "São Tomé & Príncipe",
    "TW" to "Taiwan",
    "TJ" to "Tajikistan",
    "TZ" to "Tanzania",
    "TH" to "Thailand",
    "TL" to "Timor-Leste",
    "TG" to "Togo",
    "TK" to "Tokelau",
    "TO" to "Tonga",
    "TT" to "Trinidad & Tobago",
    "TN" to "Tunisia",
    "TR" to "Turkey",
    "TM" to "Turkmenistan",
    "TC" to "Turks & Caicos Islands",
    "TV" to "Tuvalu",
    "UM" to "U.S. Outlying Islands",
    "VI" to "U.S. Virgin Islands",
    "UG" to "Uganda",
    "UA" to "Ukraine",
    "AE" to "United Arab Emirates",
    "GB" to "United Kingdom",
    "US" to "United States",
    "UY" to "Uruguay",
    "UZ" to "Uzbekistan",
    "VU" to "Vanuatu",
    "VA" to "Vatican City",
    "VE" to "Venezuela",
    "VN" to "Vietnam",
    "WF" to "Wallis & Futuna",
    "EH" to "Western Sahara",
    "YE" to "Yemen",
    "ZM" to "Zambia",
    "ZW" to "Zimbabwe",
    "AX" to "Åland Islands",
)
