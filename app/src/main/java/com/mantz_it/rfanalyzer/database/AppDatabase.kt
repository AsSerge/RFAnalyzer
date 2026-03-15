package com.mantz_it.rfanalyzer.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * <h1>RF Analyzer - Database</h1>
 *
 * Module:      AppDatabase.kt
 * Description: The room-based database.
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2025 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

const val CURRENT_DB_SCHEMA_VERSION = 2

@Database(
    entities = [
        Recording::class,
        Station::class,
        Band::class,
        BookmarkList::class,
        OnlineStationProviderSettings::class
    ],
    version = CURRENT_DB_SCHEMA_VERSION,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ],
    exportSchema = true
)
@TypeConverters(StationTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun stationDao(): StationDao
    abstract fun bandDao(): BandDao
    abstract fun bookmarkListDao(): BookmarkListDao
    abstract fun onlineStationProviderSettingsDao(): OnlineStationProviderSettingsDao
}

