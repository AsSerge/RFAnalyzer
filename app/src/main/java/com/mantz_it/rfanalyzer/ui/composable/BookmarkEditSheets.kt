package com.mantz_it.rfanalyzer.ui.composable

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mantz_it.rfanalyzer.database.AppStateRepository.Companion.VERTICAL_SCALE_LOWER_BOUNDARY
import com.mantz_it.rfanalyzer.database.AppStateRepository.Companion.VERTICAL_SCALE_UPPER_BOUNDARY
import com.mantz_it.rfanalyzer.database.Band
import com.mantz_it.rfanalyzer.database.BandWithBookmarkList
import com.mantz_it.rfanalyzer.database.BookmarkList
import com.mantz_it.rfanalyzer.database.BookmarkListType
import com.mantz_it.rfanalyzer.database.Coordinates
import com.mantz_it.rfanalyzer.database.DemodulationParameters
import com.mantz_it.rfanalyzer.database.Schedule
import com.mantz_it.rfanalyzer.database.SourceProvider
import com.mantz_it.rfanalyzer.database.Station
import com.mantz_it.rfanalyzer.database.StationWithBookmarkList
import com.mantz_it.rfanalyzer.database.SubBand
import kotlin.math.min
import kotlin.math.max
import kotlin.text.ifEmpty

/**
 * <h1>RF Analyzer - BookmarkEditSheets</h1>
 *
 * Module:      BookmarkEditSheets.kt
 * Description: ModalBottomSheets for editing stations and other bookmark related actions
 *              (e.g. import/export, copy/move, ...)
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2026 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 */


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStationBookmarkSheet(
    station: Station,
    bookmarkLists: List<BookmarkList>,
    isUnsafed: Boolean,
    onSave: (Station) -> Unit,
    onSaveTmp: (Station) -> Unit, // called when editing is interrupted and state must be changed. expects to be called again with isUnsafed=true
    onDismiss: () -> Unit,
    onCreateNewBookmarkList: () -> Unit, // callback to request creation of a new bookmarkList.
) {
    var name by remember { mutableStateOf(station.name) }
    var favorite by remember { mutableStateOf(station.favorite) }
    var frequency by remember { mutableLongStateOf(station.frequency) }
    var bandwidth by remember { mutableIntStateOf(station.bandwidth) }
    var mode by remember { mutableStateOf(station.mode) }
    var notes by remember { mutableStateOf(station.notes) }
    var demodulationParameters: DemodulationParameters? by remember { mutableStateOf(station.demodulationParameters) }
    var language by remember { mutableStateOf(station.language) }
    var callsign by remember { mutableStateOf(station.callsign) }
    var address by remember { mutableStateOf(station.address) }
    var countryCode by remember { mutableStateOf(station.countryCode) }
    var coordinates by remember { mutableStateOf(station.coordinates) }
    var schedule: Schedule? by remember { mutableStateOf(station.schedule) }
    var selectedBookmarkList by remember { mutableStateOf(bookmarkLists.firstOrNull { it.id == station.bookmarkListId } ?: bookmarkLists.firstOrNull()) }

    // Sync internal state when the station from the ViewModel changes
    // This is triggered whenever a new 'station' object is passed in or bookmarkLists list changes
    // Currently this is only the case when the user added a new bookmarkList while the EditStationBookmarkSheet is visible
    LaunchedEffect(station, bookmarkLists) {
        station.bookmarkListId?.let { newCatId ->
            val updatedBookmarkList = bookmarkLists.find { it.id == newCatId }
            if (updatedBookmarkList != null) selectedBookmarkList = updatedBookmarkList
        }
    }

    fun mergeIntoStation(): Station {
        val effectiveDemodulationParameters =
            if (demodulationParameters == null) null
            else if (demodulationParameters?.squelch == null) null
            else demodulationParameters
        return station.copy(
            name = name,
            favorite = favorite,
            frequency = frequency,
            bandwidth = bandwidth,
            updatedAt = System.currentTimeMillis(),
            mode = mode,
            notes = notes?.ifEmpty { null },
            callsign = callsign?.ifEmpty { null },
            language = language?.ifEmpty { null },
            demodulationParameters = effectiveDemodulationParameters,
            schedule = schedule?.ifEmpty { null },
            bookmarkListId = selectedBookmarkList?.id,
            source = SourceProvider.BOOKMARK,
            address = address?.ifEmpty { null },
            countryCode = countryCode?.ifEmpty { null },
            coordinates = coordinates
        )
    }

    fun hasChanges(): Boolean {
        val demodulationParametersDiffer =
            (station.demodulationParameters == null && demodulationParameters?.squelch != null) ||
            station.demodulationParameters?.squelch != null && (station.demodulationParameters.squelch != demodulationParameters?.squelch)
        return isUnsafed || station.name != name ||
                station.favorite != favorite ||
                station.frequency != frequency ||
                station.bandwidth != bandwidth ||
                station.mode != mode ||
                station.notes != notes?.ifEmpty { null } ||
                demodulationParametersDiffer ||
                station.language != language?.ifEmpty { null } ||
                station.callsign != callsign?.ifEmpty { null } ||
                station.address != address?.ifEmpty { null } ||
                station.countryCode != countryCode?.ifEmpty { null } ||
                station.coordinates != coordinates ||
                station.schedule != schedule?.ifEmpty { null } ||
                station.bookmarkListId != selectedBookmarkList?.id
    }

    // hook the showHelp function to save temporary State before navigating to manual:
    val showHelpOrig = LocalShowHelp.current
    val showHelpHook = { subUrl: String ->
        if (hasChanges()) onSaveTmp(mergeIntoStation())
        showHelpOrig(subUrl)
    }
    CompositionLocalProvider(LocalShowHelp provides showHelpHook) {
        BaseEditSheet(
            title = if (station.id == 0L) "Add Station" else "Edit Station",
            hasUnsavedChanges = { hasChanges() },
            disableSaveButton = name.isEmpty(),
            onSave = { onSave(mergeIntoStation()) },
            onDismiss = onDismiss
        ) {
            ScrollableColumnWithFadingEdge(modifier = Modifier.padding(top = 6.dp)) {
                Row {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        isError = name.isEmpty(),
                        supportingText = { if(name.isEmpty()) Text("Name must not be empty!") },
                        label = { Text("Name", Modifier.combinedClickable(onLongClick = { showHelpHook("bookmarks.html#create-and-edit-stations") }, onClick = {})) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { favorite = !favorite }, modifier = Modifier
                        .padding(bottom = 6.dp)
                        .align(Alignment.CenterVertically) ) {
                        Icon(imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = Color.Red,
                        )
                    }
                }
                Row {
                    OutlinedListDropDown(
                        label = "List",
                        items = bookmarkLists,
                        getDisplayName = { it?.name ?: "<none>" },
                        selectedItem = selectedBookmarkList,
                        onSelectionChanged = { selectedBookmarkList = it },
                        background = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f).height(DEFAULT_MIN_BOX_HEIGHT),
                        helpSubPath = "bookmarks.html#lists"
                    )
                    IconButton(
                        onClick = {
                            if (hasChanges()) onSaveTmp(mergeIntoStation())
                            onCreateNewBookmarkList()
                        },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add New List")
                    }
                }
                FrequencyChooser(
                    label = "Station Frequency",
                    unit = "Hz",
                    currentFrequency = frequency,
                    onFrequencyChanged = { frequency = it },
                    liveUpdate = true,
                    background = MaterialTheme.colorScheme.surfaceContainerLow,
                    helpSubPath = "bookmarks.html#station-frequency-bandwidth-and-mode"
                )
                Row {
                    OutlinedEnumDropDown(
                        label = "Demodulation Mode",
                        enumClass = DemodulationMode::class,
                        getDisplayName = { it.displayName },
                        selectedEnum = mode,
                        onSelectionChanged = { mode = it },
                        modifier = Modifier.weight(0.9f),
                        background = MaterialTheme.colorScheme.surfaceContainerLow,
                        helpSubPath = "bookmarks.html#station-frequency-bandwidth-and-mode"
                    )
                    if (mode != DemodulationMode.OFF) {
                        FrequencyChooser(
                            label = "Station Bandwidth",
                            unit = "Hz",
                            digitCount = 6,
                            currentFrequency = bandwidth.toLong(),
                            onFrequencyChanged = { bandwidth = it.toInt() },
                            liveUpdate = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp),
                            background = MaterialTheme.colorScheme.surfaceContainerLow,
                            helpSubPath = "bookmarks.html#station-frequency-bandwidth-and-mode"
                        )
                    }
                }
                if (notes != null) {
                    OutlinedTextField(
                        value = notes ?: "",
                        onValueChange = { notes = it },
                        label = { Text("Notes", Modifier.combinedClickable(onLongClick = { showHelpHook("bookmarks.html#create-and-edit-stations") }, onClick = {})) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (address != null || countryCode != null) {
                    OutlinedBox(
                        label = "Address",
                        background = MaterialTheme.colorScheme.surfaceContainerLow,
                        helpSubPath = "bookmarks.html#address",
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            CountryPicker(
                                selectedCountryCode = countryCode,
                                onCountryCodeSelected = { countryCode = it },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = address ?: "",
                                onValueChange = { address = it },
                                label = { Text("Address", Modifier.combinedClickable(onLongClick = { showHelpHook("bookmarks.html#address") }, onClick = {})) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                coordinates?.let { coords ->
                    Row {
                        OutlinedBox(
                            label = "Coordinates",
                            background = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.weight(1f),
                            helpSubPath = "bookmarks.html#coordinates",
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                OutlinedConditionTextField(
                                    value = coords.toGridLocator(),
                                    onValueChange = { coordinates = Coordinates.fromGridLocator(it) },
                                    checkCondition = { value ->
                                        if (Coordinates.maidenheadToLatLon(value) == null) {
                                            Pair(false, "Invalid Locator")
                                        } else {
                                            Pair(true, null)
                                        }
                                    },
                                    label = "Maidenhead Locator",
                                    background = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row {
                                    OutlinedDoubleTextField(
                                        value = coords.latitude,
                                        onValueChange = { latitude -> coordinates = coords.copy(latitude = latitude) },
                                        label = "Latitude",
                                        checkCondition = { if (it in -90.0..90.0) Pair(true, null) else Pair(false, "-90 to 90 degrees") },
                                        decimalPlaces = 6,
                                        background = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedDoubleTextField(
                                        value = coords.longitude,
                                        onValueChange = { longitude -> coordinates = coords.copy(longitude = longitude) },
                                        label = "Longitude",
                                        checkCondition = { if (it in -180.0..180.0) Pair(true, null) else Pair(false, "-180 to 180 degrees") },
                                        decimalPlaces = 6,
                                        background = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { coordinates = null },
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .align(Alignment.CenterVertically)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Coordinates")
                        }
                    }
                }
                if (callsign != null) {
                    OutlinedTextField(
                        value = callsign ?: "",
                        onValueChange = { callsign = it },
                        label = { Text("Call Sign", Modifier.combinedClickable(onLongClick = { showHelpHook("bookmarks.html#call-sign") }, onClick = {})) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (language != null) {
                    OutlinedTextField(
                        value = language ?: "",
                        onValueChange = { language = it },
                        label = { Text("Language", Modifier.combinedClickable(onLongClick = { showHelpHook("bookmarks.html#create-and-edit-stations") }, onClick = {})) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                demodulationParameters?.let { demodParams ->
                    if (demodParams.squelch != null) {
                        Row {
                            OutlinedSwitch(
                                label = "Squelch",
                                helpText = "Set a Squelch value for this station.",
                                isChecked = demodParams.squelch.enabled,
                                onCheckedChange = {
                                    demodulationParameters =
                                        demodParams.copy(squelch = demodParams.squelch.copy(enabled = it))
                                },
                                background = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.weight(1f),
                                helpSubPath = "bookmarks.html#squelch",
                            ) {
                                OutlinedSlider(
                                    label = "SquelchThreshold",
                                    unit = "dB",
                                    unitInLabel = false,
                                    minValue = VERTICAL_SCALE_LOWER_BOUNDARY,
                                    maxValue = VERTICAL_SCALE_UPPER_BOUNDARY,
                                    value = demodParams.squelch.thresholdDb,
                                    onValueChanged = { value ->
                                        demodulationParameters = demodParams.copy(
                                            squelch = demodParams.squelch.copy(thresholdDb = value)
                                        )
                                    },
                                    showOutline = false
                                )
                            }
                            IconButton(
                                onClick = { demodulationParameters = null },
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Squelch")
                            }
                        }
                    }
                }
                schedule?.let {
                    OutlinedBox(
                        label = "Schedule",
                        background = MaterialTheme.colorScheme.surfaceContainerLow,
                        helpSubPath = "bookmarks.html#schedule"
                    ) {
                        ScheduleEditor(it) { changedSchedule -> schedule = changedSchedule }
                    }
                }

                // Suggestions:
                @Composable
                fun AddChip(label: String, onClick: () -> Unit) {
                    AssistChip(leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null
                        )
                    }, onClick = onClick, label = { Text(label) })
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (notes == null) AddChip("Notes") { notes = "" }
                    if (address == null && countryCode == null) AddChip("Address") {
                        address = ""
                        countryCode = ""
                    }
                    if (coordinates == null) AddChip("Coordinates") { coordinates = Coordinates(0.0,0.0) }
                    if (schedule == null) AddChip("Schedule") { schedule = Schedule() }
                    if (language == null) AddChip("Language") { language = "" }
                    if (callsign == null) AddChip("Callsign") { callsign = "" }
                    if (demodulationParameters?.squelch == null) AddChip("Squelch") {
                        demodulationParameters = if (demodulationParameters == null)
                            DemodulationParameters(squelch = DemodulationParameters.Squelch())
                        else
                            demodulationParameters?.copy(squelch = DemodulationParameters.Squelch())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBandBookmarkSheet(
    band: Band,
    bookmarkLists: List<BookmarkList>,
    isUnsafed: Boolean,
    onSave: (Band) -> Unit,
    onSaveTmp: (Band) -> Unit, // called when editing is interrupted and state must be changed. expects to be called again with isUnsafed=true
    onDismiss: () -> Unit,
    onCreateNewBookmarkList: () -> Unit, // callback to request creation of a new bookmarkList.
) {
    var name by remember { mutableStateOf(band.name) }
    var favorite by remember { mutableStateOf(band.favorite) }
    var startFreq by remember { mutableLongStateOf(band.startFrequency) }
    var endFreq by remember { mutableLongStateOf(band.endFrequency) }
    var notes by remember { mutableStateOf(band.notes) }
    var selectedBookmarkList by remember {
        mutableStateOf(bookmarkLists.firstOrNull { it.id == band.bookmarkListId } ?: bookmarkLists.firstOrNull())
    }
    var subBands: List<SubBand> by remember { mutableStateOf(band.subBands) }

    // Track which SubBand is currently being edited (index in list or -1 for new)
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingSubBand by remember { mutableStateOf<SubBand?>(null) }

    // Sync internal state when the station from the ViewModel changes (see also EditStationBookmarkSheet)
    LaunchedEffect(band, bookmarkLists) {
        band?.bookmarkListId?.let { newCatId ->
            val updatedBookmarkList = bookmarkLists.find { it.id == newCatId }
            if (updatedBookmarkList != null) selectedBookmarkList = updatedBookmarkList
        }
    }

    fun hasChanges(): Boolean {
        return isUnsafed || band.name != name ||
                band.favorite != favorite ||
                band.startFrequency != startFreq ||
                band.endFrequency != endFreq ||
                band.notes != notes?.ifEmpty { null } ||
                band.subBands.toSet() != subBands.toSet() ||
                band.bookmarkListId != selectedBookmarkList?.id
    }

    fun mergeIntoBand(): Band {
        val now = System.currentTimeMillis()
        return band.copy(
            name = name,
            bookmarkListId = selectedBookmarkList?.id,
            startFrequency = startFreq,
            endFrequency = endFreq,
            notes = notes?.ifEmpty { null },
            favorite = favorite,
            subBands = subBands,
            updatedAt = now
        )
    }

    // hook the showHelp function to save temporary State before navigating to manual:
    val showHelpOrig = LocalShowHelp.current
    val showHelpHook = { subUrl: String ->
        if (hasChanges()) onSaveTmp(mergeIntoBand())
        showHelpOrig(subUrl)
    }
    CompositionLocalProvider(LocalShowHelp provides showHelpHook) {
        BaseEditSheet(
            title = if (band.id == 0L) "Add Band" else "Edit Band",
            hasUnsavedChanges = { hasChanges() },
            disableSaveButton = name.isEmpty(),
            onSave = { onSave(mergeIntoBand()) },
            onDismiss = onDismiss
        ) {
            ScrollableColumnWithFadingEdge(modifier = Modifier.padding(top = 6.dp)) {
                Row {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        isError = name.isEmpty(),
                        supportingText = { if(name.isEmpty()) Text("Name must not be empty!") },
                        label = { Text("Name", Modifier.combinedClickable(onLongClick = { showHelpHook("bookmarks.html#create-and-edit-bands") }, onClick = {})) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { favorite = !favorite }, modifier = Modifier
                        .padding(bottom = 6.dp)
                        .align(Alignment.CenterVertically) ) {
                        Icon(imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = Color.Red,
                        )
                    }
                }
                FrequencyChooser(
                    label = "Start Frequency",
                    currentFrequency = startFreq,
                    onFrequencyChanged = { startFreq = it },
                    unit = "Hz",
                    liveUpdate = true,
                    background = MaterialTheme.colorScheme.surfaceContainerLow,
                    helpSubPath = "bookmarks.html#start-and-end-frequency"
                )
                FrequencyChooser(
                    label = "End Frequency",
                    currentFrequency = endFreq,
                    onFrequencyChanged = { endFreq = it },
                    unit = "Hz",
                    liveUpdate = true,
                    background = MaterialTheme.colorScheme.surfaceContainerLow,
                    helpSubPath = "bookmarks.html#start-and-end-frequency"
                )
                Row {
                    OutlinedListDropDown(
                        label = "List",
                        items = bookmarkLists,
                        getDisplayName = { it?.name ?: "<none>" },
                        selectedItem = selectedBookmarkList,
                        onSelectionChanged = { selectedBookmarkList = it },
                        background = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f).height(DEFAULT_MIN_BOX_HEIGHT),
                        helpSubPath = "bookmarks.html#lists"
                    )
                    IconButton(
                        onClick = {
                            if (hasChanges()) onSaveTmp(mergeIntoBand())
                            onCreateNewBookmarkList()
                        },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add New List")
                    }
                }

                notes?.let { notesObj ->
                    OutlinedTextField(
                        value = notesObj,
                        onValueChange = { notes = it },
                        label = { Text("Notes", Modifier.combinedClickable(onLongClick = { showHelpHook("bookmarks.html#create-and-edit-bands") }, onClick = {})) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (subBands.isNotEmpty()) {
                    Text(
                        "Sub-bands",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subBands.forEachIndexed { index, sb ->
                        val label = buildString {
                            append(sb.name.ifEmpty { "Unnamed" })
                            append("  ")
                            append("${sb.startFrequency / 1000.0}–${sb.endFrequency / 1000.0} kHz")
                        }
                        AssistChip(
                            onClick = {
                                editingIndex = index
                                editingSubBand = sb.copy()
                            },
                            label = { Text(label) },
                            leadingIcon = { Icon(Icons.Default.Radio, contentDescription = null) }
                        )
                    }

                    if (notes == null) {
                        AssistChip(
                            onClick = { notes = "" },
                            label = { Text("Add Notes") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                        )
                    }
                    if (editingSubBand == null) {
                        AssistChip(
                            onClick = {
                                editingIndex = null
                                editingSubBand = SubBand(
                                    name = "",
                                    startFrequency = 0L,
                                    endFrequency = 0L,
                                    notes = ""
                                )
                            },
                            label = { Text("Add Sub-band") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                        )
                    }
                }

                editingSubBand?.let { sb ->
                    SubBandEditorRow(
                        subBand = sb,
                        minFrequency = startFreq,
                        maxFrequency = endFreq,
                        onChange = { editingSubBand = it },
                        onConfirm = {
                            subBands = if (editingIndex == null) subBands.plus(sb)
                            else subBands.toMutableList().apply { set(editingIndex!!, sb) }.toList()
                            editingSubBand = null
                            editingIndex = null
                        },
                        onCancel = {
                            editingSubBand = null
                            editingIndex = null
                        },
                        onDelete = {
                            subBands = subBands.minus(sb)
                            editingSubBand = null
                            editingIndex = null
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun EditBookmarkListSheet(
    bookmarkList: BookmarkList,
    onSave: (BookmarkList) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(bookmarkList.name) }
    var type by remember { mutableStateOf(bookmarkList.type) }
    var notes by remember { mutableStateOf(bookmarkList.notes ?: "") }
    var color by remember { mutableStateOf(bookmarkList.color) }

    // hook the showHelp function to save temporary State before navigating to manual:
    val showHelp = LocalShowHelp.current

    fun hasChanges(): Boolean {
        return bookmarkList.name != name || bookmarkList.type != type || bookmarkList.notes?.ifEmpty { null } != notes.ifEmpty { null } || bookmarkList.color != color
    }

    BaseEditSheet(
        title = if (bookmarkList.id == 0L) "Add List" else "Edit List",
        hasUnsavedChanges = ::hasChanges,
        disableSaveButton = name.isEmpty(),
        onSave = {
            onSave(
                BookmarkList(
                    id = bookmarkList.id,
                    name = name,
                    type = type,
                    notes = notes.ifEmpty { null },
                    color = color
                )
            )
        },
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            isError = name.isEmpty(),
            supportingText = { if(name.isEmpty()) Text("Name must not be empty!") },
            label = { Text("Name", Modifier.combinedClickable(onLongClick = { showHelp("bookmarks.html#create-and-edit-lists") }, onClick = {})) },
            modifier = Modifier.fillMaxWidth()
        )
        if (bookmarkList.id == 0L) { // don't allow to change the type of existing list
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BookmarkListType.entries.forEachIndexed { index, catType ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index,BookmarkListType.entries.size),
                        onClick = { type = catType },
                        selected = type == catType,
                    ) { Text(catType.displayName) }
                }
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes", Modifier.combinedClickable(onLongClick = { showHelp("bookmarks.html#create-and-edit-lists") }, onClick = {})) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        ColorPickerRow(
            selectedColor = Color(color ?: Color.Gray.toArgb()),
            onColorSelected = { color = it.toArgb() },
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}


@Composable
fun ScheduleEditor(
    schedule: Schedule,
    onScheduleChange: (Schedule) -> Unit
) {
    var selectedDays by remember { mutableStateOf(schedule.days.toMutableSet()) }
    var remarks by remember { mutableStateOf(schedule.remarks ?: "") }

    Column(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // --- Time inputs ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedLongTextField(
                value = schedule.startTimeUtc.toLong(),
                onValueChange = {
                    onScheduleChange(schedule.copy(startTimeUtc = it.toString().padStart(4, '0')))
                },
                checkCondition = { if (it in 0..2400) Pair(true, null) else Pair(false, "0000 .. 2400") },
                label = "Start (UTC)",
                digits = 4,
                background = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            )
            OutlinedLongTextField(
                value = schedule.endTimeUtc.toLong(),
                onValueChange = {
                    onScheduleChange(schedule.copy(endTimeUtc = it.toString().padStart(4, '0')))
                },
                checkCondition = { if (it in 0..2400) Pair(true, null) else Pair(false, "0000 .. 2400") },
                label = "End (UTC)",
                digits = 4,
                background = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            )
        }

        // --- Day selection ---
        Text("Days", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Schedule.Weekday.ordered.forEach { day ->
                val selected = selectedDays.contains(day)
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (selected) selectedDays.remove(day)
                        else selectedDays.add(day)
                        onScheduleChange(schedule.copy(days = selectedDays.toSet()))
                    },
                    label = { Text(day.shortName) },
                )
            }
        }
        OutlinedTextField(
            value = remarks,
            onValueChange = {
                remarks = it
                onScheduleChange(schedule.copy(remarks = it))
            },
            label = { Text("Remarks") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )
    }
}

@Composable
fun SubBandEditorRow(
    subBand: SubBand,
    minFrequency: Long,
    maxFrequency: Long,
    onChange: (SubBand) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = subBand.name,
                onValueChange = { onChange(subBand.copy(name = it)) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            FrequencyChooser(
                label = "Start Frequency",
                currentFrequency = subBand.startFrequency,
                onFrequencyChanged = { onChange(subBand.copy(startFrequency = it)) },
                unit = "Hz",
                minFrequency = minFrequency,
                maxFrequency = if (subBand.endFrequency in minFrequency..maxFrequency) subBand.endFrequency else maxFrequency,
                liveUpdate = true,
                background = MaterialTheme.colorScheme.surfaceContainerLow,
                helpSubPath = "bookmarks.html#sub-bands"
            )
            Spacer(Modifier.height(4.dp))
            FrequencyChooser(
                label = "End Frequency",
                currentFrequency = subBand.endFrequency,
                onFrequencyChanged = { onChange(subBand.copy(endFrequency = it)) },
                unit = "Hz",
                minFrequency = if (subBand.startFrequency in minFrequency..maxFrequency) subBand.startFrequency else minFrequency,
                maxFrequency = maxFrequency,
                liveUpdate = true,
                background = MaterialTheme.colorScheme.surfaceContainerLow,
                helpSubPath = "bookmarks.html#sub-bands"
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = subBand.notes ?: "",
                onValueChange = { onChange(subBand.copy(notes = it)) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                onDelete?.let {
                    IconButton(onClick = it, modifier = Modifier.padding(end = 16.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                IconButton(onClick = onConfirm) {
                    Icon(Icons.Default.Check, contentDescription = "Confirm")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyOrMoveSheet(
    itemsWithBookmarkList: List<Any>,
    itemType: BookmarkListType,
    bookmarkLists: List<BookmarkList>,
    onCopy: (BookmarkList) -> Unit,
    onMove: (BookmarkList) -> Unit,
    onDismiss: () -> Unit
) {
    val itemTypeName = when (itemType) {
        BookmarkListType.STATION -> "Station"
        BookmarkListType.BAND -> "Band"
    }
    val itemTypeNamePlural = itemTypeName + "s"

    var selectedOption by remember { mutableStateOf("copy") }
    var selectedBookmarkList by remember { mutableStateOf(bookmarkLists.firstOrNull()) }

    BaseEditSheet(
        title = if (selectedOption == "copy") "Copy $itemTypeNamePlural" else "Move $itemTypeNamePlural",
        onSave = {
            val destBookmarkList = selectedBookmarkList ?: return@BaseEditSheet
            if (selectedOption == "copy") onCopy(destBookmarkList)
            else onMove(destBookmarkList)
        },
        onDismiss = onDismiss,
        hasUnsavedChanges = null,
        overwriteSaveTitle = if (selectedOption == "copy") "Copy" else "Move"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val totalItems = itemsWithBookmarkList.size
            val maxItemsToList = 5
            Text(
                "You are about to $selectedOption $totalItems ${if (totalItems > 1) itemTypeNamePlural.lowercase() else itemTypeName.lowercase()}:",
                style = MaterialTheme.typography.bodyLarge
            )

            Column(modifier = Modifier.padding(start = 16.dp)) {
                itemsWithBookmarkList.take(maxItemsToList).forEach { itemWithBookmarkList ->
                    val name: String
                    val bookmarkList: BookmarkList?

                    when (itemWithBookmarkList) {
                        is StationWithBookmarkList -> {
                            name = itemWithBookmarkList.station.name
                            bookmarkList = itemWithBookmarkList.bookmarkList
                        }
                        is BandWithBookmarkList -> {
                            name = itemWithBookmarkList.band.name
                            bookmarkList = itemWithBookmarkList.bookmarkList
                        }
                        else -> {
                            name = "Unknown Item"
                            bookmarkList = null
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            //modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        if (bookmarkList != null) {
                            val backgroundColor = Color(bookmarkList.color ?: 0)
                            Box(
                                modifier = Modifier
                                    .background(backgroundColor, shape = RoundedCornerShape(40.dp))
                            ) {
                                Text(
                                    bookmarkList.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = backgroundColor.contrastingTextColor(),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        } else {
                            Text("No list", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
                if (totalItems > maxItemsToList) {
                    Text("...and ${totalItems - maxItemsToList} more.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    onClick = { selectedOption = "copy" },
                    selected = selectedOption == "copy",
                ) { Text("Copy") }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    onClick = { selectedOption = "move" },
                    selected = selectedOption == "move",
                ) { Text("Move") }
            }

            OutlinedListDropDown(
                label = "Target List",
                items = bookmarkLists,
                getDisplayName = { it?.name ?: "<none>" },
                selectedItem = selectedBookmarkList,
                onSelectionChanged = { selectedBookmarkList = it },
                background = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.height(DEFAULT_MIN_BOX_HEIGHT),
                helpSubPath = ""
            )

            Text(
                text = if (selectedOption == "copy") "The ${itemTypeNamePlural.lowercase()} will be duplicated into the target list." else "The ${itemTypeNamePlural.lowercase()} will be moved to the target list.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    itemsWithBookmarkList: List<Any>,
    itemType: BookmarkListType,
    onExport: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val itemTypeName = when (itemType) {
        BookmarkListType.STATION -> "Station"
        BookmarkListType.BAND -> "Band"
    }
    val itemTypeNamePlural = itemTypeName + "s"
    var selectedUri: Uri? by remember { mutableStateOf(null) }
    val destinationFileChooser = rememberCreateFilePicker(
        suggestedFileName = "RFAnalyzer_Exported_$itemTypeNamePlural.json",
        mimeType = "application/json",
        onAbort = { },
        onFileCreated = { destUri -> selectedUri = destUri })

    BaseEditSheet(
        title = "Export $itemTypeNamePlural",
        onSave = {
            val destUri = selectedUri ?: return@BaseEditSheet
            onExport(destUri)
        },
        onDismiss = onDismiss,
        hasUnsavedChanges = null,
        disableSaveButton = selectedUri == null,
        overwriteSaveTitle = "Export"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val totalItems = itemsWithBookmarkList.size
            val maxItemsToList = 5
            Text(
                "You are about to export $totalItems ${if (totalItems > 1) itemTypeNamePlural.lowercase() else itemTypeName.lowercase()}:",
                style = MaterialTheme.typography.bodyLarge
            )

            Column(modifier = Modifier.padding(start = 16.dp)) {
                itemsWithBookmarkList.take(maxItemsToList).forEach { itemWithBookmarkList ->
                    val name: String
                    val bookmarkList: BookmarkList?

                    when (itemWithBookmarkList) {
                        is StationWithBookmarkList -> {
                            name = itemWithBookmarkList.station.name
                            bookmarkList = itemWithBookmarkList.bookmarkList
                        }
                        is BandWithBookmarkList -> {
                            name = itemWithBookmarkList.band.name
                            bookmarkList = itemWithBookmarkList.bookmarkList
                        }
                        else -> {
                            name = "Unknown Item"
                            bookmarkList = null
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (bookmarkList != null) {
                            val backgroundColor = Color(bookmarkList.color ?: 0)
                            Box(
                                modifier = Modifier
                                    .background(backgroundColor, shape = RoundedCornerShape(40.dp))
                            ) {
                                Text(
                                    bookmarkList.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = backgroundColor.contrastingTextColor(),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        } else {
                            Text("No list", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
                if (totalItems > maxItemsToList) {
                    Text("...and ${totalItems - maxItemsToList} more.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            if (selectedUri != null) {
                Text("Destination: ${selectedUri?.path}")
            } else {
                Text("Please select a destination:")
            }
            Button(onClick = destinationFileChooser) {
                Text("Select Destination")
            }

            // TODO: "or Share" option here

            Text(
                text = "The ${itemTypeNamePlural.lowercase()} will be exported into a JSON file.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun BookmarkFavoriteDialog(
    stationFavorites: List<Station>,
    bandFavorites: List<Band>,
    showStationFavoritesInitially: Boolean,
    onTabChanged: (Boolean) -> Unit,
    onOpenBookmarksClicked: () -> Unit,
    onAddStationBookmarkClicked: () -> Unit,
    onAddBandBookmarkClicked: () -> Unit,
    onTuneToStation: (Station) -> Unit,
    onViewBand: (Band) -> Unit,
    onDismiss: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    @Composable
    fun buttonRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onOpenBookmarksClicked,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row {
                    Text("Manager", modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 16.dp))
                    Icon(Icons.Default.MenuBook, contentDescription = "Bookmark Manager")
                }
            }
            Button(
                onClick = onAddStationBookmarkClicked,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row {
                    Icon(Icons.Default.Add, contentDescription = "Add Station")
                    Icon(Icons.Default.Radio, contentDescription = "Add Station")
                }
            }
            Button(
                onClick = onAddBandBookmarkClicked,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row {
                    Icon(Icons.Default.Add, contentDescription = "Add Band")
                    Icon(IconFrequencyBand, contentDescription = "Add Band")
                }
            }
        }
    }

    @Composable
    fun ColumnScope.title() {
        Text(
            "Bookmark Favorites",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(all = 10.dp)
                .align(Alignment.CenterHorizontally)
        )
    }

    @Composable
    fun tabButtons() {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                onClick = { onTabChanged(true) },
                selected = showStationFavoritesInitially,
            ) {
                Row {
                    Text("Stations", modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically))
                    Icon(Icons.Default.Radio, contentDescription = "Stations")
                }
            }
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                onClick = { onTabChanged(false) },
                selected = !showStationFavoritesInitially,
            ) {
                Row {
                    Text("Bands", modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically))
                    Icon(IconFrequencyBand, contentDescription = "Bands")
                }
            }
        }
    }

    @Composable
    fun favColumn(modifier: Modifier) {
        if (showStationFavoritesInitially && stationFavorites.isEmpty() || !showStationFavoritesInitially && bandFavorites.isEmpty()) {
            Box(modifier = modifier) {
                Text(
                    "<no favorites>",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center)
                )
            }
        } else {
            LazyColumn(modifier = modifier) {
                if (showStationFavoritesInitially) {
                    items(stationFavorites) { station ->
                        ListItem(
                            headlineContent = { Text(station.name) },
                            modifier = Modifier.clickable { onTuneToStation(station) }
                        )
                    }
                } else {
                    items(bandFavorites) { band ->
                        ListItem(
                            headlineContent = { Text(band.name) },
                            modifier = Modifier.clickable { onViewBand(band) }
                        )
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .fillMaxHeight(if (isLandscape) 0.9f else 0.7f)
        ) {

            if (isLandscape) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                    ) {
                        title()
                        tabButtons()
                        Spacer(Modifier.weight(1f))
                        buttonRow()
                    }
                    favColumn(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight())
                }
            } else {
                Column(Modifier
                    .fillMaxSize()
                    .padding(16.dp)) {
                    title()
                    tabButtons()
                    favColumn(Modifier.weight(1f))
                    buttonRow()
                }
            }
        }
    }
}

@Preview
@Composable
fun EditStationPreview() {
    CompositionLocalProvider(LocalShowHelp provides {}) {
        EditStationBookmarkSheet(
            station = Station(name = "Test Station"),
            bookmarkLists = emptyList(),
            isUnsafed = false,
            onSave = {},
            onSaveTmp = {},
            onDismiss = {},
            onCreateNewBookmarkList = {}
        )
    }
}

@Preview
@Composable
fun FavoriteDialogPreview() {
    CompositionLocalProvider(LocalShowHelp provides {}) {
        BookmarkFavoriteDialog(
            stationFavorites = listOf(Station(name="test")),
            bandFavorites = emptyList(),
            showStationFavoritesInitially = true,
            onTabChanged = {},
            onOpenBookmarksClicked = {},
            onAddStationBookmarkClicked = {},
            onAddBandBookmarkClicked = {},
            onTuneToStation = {},
            onViewBand = {},
            onDismiss = {}
        )
    }
}
