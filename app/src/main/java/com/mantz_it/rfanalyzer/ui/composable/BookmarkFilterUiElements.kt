package com.mantz_it.rfanalyzer.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mantz_it.rfanalyzer.database.BandFilter
import com.mantz_it.rfanalyzer.database.BookmarkList
import com.mantz_it.rfanalyzer.database.SourceProvider
import com.mantz_it.rfanalyzer.database.StationFilter
import kotlin.collections.plus

/**
 * <h1>RF Analyzer - BookmarkFilterUiElements</h1>
 *
 * Module:      BookmarkFilterUiElements.kt
 * Description: Composables for providing the filter bar which is shown in the bookmark manager (BookmarkManagerScreen)
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2026 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 */

sealed interface FilterSpec {
    val label: String

    data class FuzzySearchSpec(
        override val label: String,
        val helpText: String,
        val search: String,
        val onChange: (String) -> Unit
    ) : FilterSpec

    data class ListSpec<T>(
        override val label: String,
        val items: List<T>,
        val selected: Set<T>,
        val single: Boolean = false,
        val itemLabel: (T) -> String = { it.toString() },
        val icon: ImageVector,
        val onChange: (Set<T>) -> Unit
    ) : FilterSpec

    data class FrequencySpec(
        override val label: String,
        val min: Long,
        val max: Long,
        val icon: ImageVector,
        val onChange: (Long, Long) -> Unit
    ) : FilterSpec

    data class BooleanSpec(
        override val label: String,
        val active: Boolean,
        val icon: ImageVector,
        val onToggle: () -> Unit
    ) : FilterSpec
}

@Composable
fun FilterChipRow(
    specs: List<FilterSpec>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    alwaysExpanded: Boolean = false
) {
    Column(modifier) {
        specs.filter { it is FilterSpec.FuzzySearchSpec }.forEach { spec ->
            val fuzzySpec = spec as FilterSpec.FuzzySearchSpec
            OutlinedTextField(
                value = fuzzySpec.search,
                onValueChange = { fuzzySpec.onChange(it) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (fuzzySpec.search.isNotEmpty()) {
                        IconButton(onClick = { fuzzySpec.onChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text(fuzzySpec.helpText) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .then(if (!expanded) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            maxLines = if (!expanded) 1 else Int.MAX_VALUE
        ) {
            val isAnyFilterActive = specs.any { isFilterActive(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                // Expand button
                if (!alwaysExpanded) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse Filters" else "Expand Filters",
                            modifier = Modifier.graphicsLayer(rotationZ = if (expanded) 180f else 0f)
                        )
                    }
                }

                // Clear All Chip
                if (isAnyFilterActive) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Clear, null) }
                }
            }

            if (!alwaysExpanded || isAnyFilterActive) {
                VerticalDivider(Modifier.height(32.dp).align(Alignment.CenterVertically).padding(end = 16.dp))
            }

            // ACTIVE CHIPS FIRST
            specs.sortedBy { isFilterActive(it).not() }.forEach { spec ->
                key(spec.label) {  // important for compose to keep track after sorting
                    when (spec) {
                        is FilterSpec.FuzzySearchSpec -> Unit  // Fuzzy Search is handled above
                        is FilterSpec.ListSpec<*> -> {
                            val s = spec as FilterSpec.ListSpec<Any?>
                            ListFilterChip(
                                label = s.label,
                                items = s.items,
                                selectedItems = s.selected,
                                itemLabel = s.itemLabel,
                                onSelectionChanged = s.onChange,
                                singleSelection = s.single,
                                customIcon = s.icon
                            )
                        }
                        is FilterSpec.FrequencySpec ->
                            FrequencyRangeFilterChip(
                                label = spec.label,
                                startFrequency = spec.min,
                                endFrequency = spec.max,
                                onRangeChanged = spec.onChange,
                                customIcon = spec.icon
                            )
                        is FilterSpec.BooleanSpec ->
                            FilterChip(
                                selected = spec.active,
                                onClick = spec.onToggle,
                                label = { Text(spec.label) },
                                leadingIcon = { Icon(spec.icon, null) }
                            )
                    }
                }
            }
        }
    }
}

private fun isFilterActive(spec: FilterSpec): Boolean = when (spec) {
    is FilterSpec.FuzzySearchSpec -> spec.search.isNotBlank()
    is FilterSpec.ListSpec<*> -> {
        if(spec.single) spec.selected.first() != spec.items.first()
        else spec.selected.isNotEmpty()
    }
    is FilterSpec.FrequencySpec -> spec.min != 0L || spec.max != 0L
    is FilterSpec.BooleanSpec -> spec.active
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ListFilterChip(
    label: String,
    items: List<T>,
    selectedItems: Set<T>,
    onSelectionChanged: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: (T) -> String = { it.toString() },
    singleSelection: Boolean = false,
    customIcon: ImageVector = Icons.Default.Check
) {
    var showSheet by remember { mutableStateOf(false) }
    val isActive = if (singleSelection) selectedItems.firstOrNull() != items.firstOrNull() else selectedItems.isNotEmpty()
    var tempSelection by remember(selectedItems) { mutableStateOf(selectedItems) }

    val displayLabel = when {
        selectedItems.isEmpty() -> label
        selectedItems.size < 4 -> selectedItems.joinToString(",") { itemLabel(it) }
        else -> "${selectedItems.size} selected"
    }

    FilterChip(
        selected = isActive,
        onClick = { showSheet = true },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayLabel)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
        leadingIcon = { Icon(customIcon, contentDescription = null) },
        modifier = modifier
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                onSelectionChanged(tempSelection)
                showSheet = false
            }
        ) {
            ScrollableColumnWithFadingEdge(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                items.forEach { item ->
                    val checked = tempSelection.contains(item)
                    fun onToggle() {
                        tempSelection = if (singleSelection) {
                            setOf(item)
                        } else {
                            if (checked) tempSelection.minus(item)
                            else tempSelection.plus(item)
                        }
                        if (singleSelection) {
                            onSelectionChanged(tempSelection)
                            showSheet = false
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle() }
                            .padding(vertical = 8.dp),
                    ) {
                        if(singleSelection)
                            RadioButton(selected = checked, onClick = { onToggle() })
                        else
                            Checkbox(checked = checked, onCheckedChange = { onToggle() })
                        Text(itemLabel(item), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequencyRangeFilterChip(
    label: String,
    startFrequency: Long,
    endFrequency: Long,
    onRangeChanged: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    customIcon: ImageVector = Icons.Default.Check
) {
    var showSheet by remember { mutableStateOf(false) }
    val isActive = startFrequency != 0L || endFrequency != 0L
    var start by remember(startFrequency) { mutableLongStateOf(startFrequency) }
    var end by remember(endFrequency) { mutableLongStateOf(endFrequency) }


    val displayLabel =
        if (startFrequency != 0L && endFrequency != 0L) "${startFrequency.asStringWithUnit("Hz")}-${endFrequency.asStringWithUnit("Hz")}"
        else if (startFrequency != 0L) "≥${startFrequency.asStringWithUnit("Hz")}"
        else if (endFrequency != 0L) "≤${endFrequency.asStringWithUnit("Hz")}"
        else label

    FilterChip(
        selected = isActive,
        onClick = { showSheet = true },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayLabel)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
        leadingIcon = { Icon(customIcon, contentDescription = null) },
        modifier = modifier
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                onRangeChanged(start, end)
                showSheet = false
            }
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Select Frequency Range", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                FrequencyChooser(
                    label = "Start Frequency",
                    currentFrequency = start,
                    onFrequencyChanged = { start = it },
                    maxFrequency = if(end == 0L) 999_999_999_999L else end,
                    liveUpdate = true,
                    unit = "Hz",
                    background = MaterialTheme.colorScheme.surfaceContainerLow
                )
                FrequencyChooser(
                    label = "End Frequency",
                    currentFrequency = end,
                    onFrequencyChanged = { end = it },
                    minFrequency = start,
                    liveUpdate = true,
                    unit = "Hz",
                    background = MaterialTheme.colorScheme.surfaceContainerLow
                )
            }
        }
    }
}

// Helper to combine station bookmarkLists and SourceProviders into a single filter
sealed interface StationGroup {
    data class BookmarkListGroup(val bookmarkList: BookmarkList) : StationGroup
    data class SourceGroup(val provider: SourceProvider) : StationGroup
}

@Composable
fun StationsFilterRow(
    filter: StationFilter,
    allBookmarkLists: List<BookmarkList>,
    expanded: Boolean,
    onExpandChange: () -> Unit,
    onFilterChanged: (StationFilter) -> Unit,
    modifier: Modifier = Modifier,
    alwaysExpanded: Boolean = false
) {
    val specs = listOf(
        FilterSpec.FuzzySearchSpec(
            label = "Search",
            helpText = "Search in Name, Notes, Call, …",
            search = filter.search,
            onChange = { onFilterChanged(filter.copy(search = it)) }
        ),
        FilterSpec.ListSpec(
            label = "Lists",
            items = allBookmarkLists.map { cat -> StationGroup.BookmarkListGroup(cat) } +
                    SourceProvider.entries.filter { it != SourceProvider.BOOKMARK } .map { sp -> StationGroup.SourceGroup(sp) },
            selected = buildSet {
                filter.bookmarkLists.mapNotNull { catId -> allBookmarkLists.find { it.id == catId } }.forEach { add(StationGroup.BookmarkListGroup(it)) }
                filter.sources
                    .filter { it != SourceProvider.BOOKMARK }
                    .forEach { add(StationGroup.SourceGroup(it)) }
            },
            itemLabel = {
                when (it) {
                    is StationGroup.BookmarkListGroup -> it.bookmarkList.name
                    is StationGroup.SourceGroup -> it.provider.displayName
                }
            },
            icon = Icons.Default.List,
            onChange = { new ->
                val newBookmarkLists = new.filterIsInstance<StationGroup.BookmarkListGroup>().map { it.bookmarkList }.toSet()
                val newSourcesTmp = new.filterIsInstance<StationGroup.SourceGroup>().map { it.provider }.toSet()
                // Add or remove BOOKMARK Source depending if any bookmarkList was selected:
                val newSources = if (newBookmarkLists.isEmpty()) newSourcesTmp - SourceProvider.BOOKMARK else newSourcesTmp + SourceProvider.BOOKMARK
                onFilterChanged(filter.copy(bookmarkLists = newBookmarkLists.map { it.id }.toSet(), sources = newSources))
            }
        ),
        FilterSpec.FrequencySpec(
            label = "Frequency",
            min = filter.minFrequency,
            max = filter.maxFrequency,
            icon = IconFrequencyFilter,
            onChange = { min, max -> onFilterChanged(filter.copy(minFrequency = min, maxFrequency = max)) }
        ),
        FilterSpec.ListSpec(
            label = "Mode",
            items = DemodulationMode.entries.filter { it != DemodulationMode.OFF },
            selected = filter.mode,
            itemLabel = { it.displayName },
            icon = Icons.Default.Tune,
            onChange = { onFilterChanged(filter.copy(mode = it)) }
        ),
        FilterSpec.BooleanSpec(
            label = "Favorites",
            active = filter.onlyFavorites,
            icon = Icons.Default.Favorite,
            onToggle = { onFilterChanged(filter.copy(onlyFavorites = !filter.onlyFavorites)) }
        ),
        FilterSpec.ListSpec(
            label = "Schedule",
            items = listOf("All", "Currently On-Air"),
            selected = setOf(if (filter.onlyOnAirNow) "Currently On-Air" else "All"),
            single = true,
            icon = Icons.Default.AccessTime,
            onChange = {
                val selectedItem = it.firstOrNull()
                val onAir = selectedItem != null && selectedItem == "Currently On-Air"
                onFilterChanged(filter.copy(onlyOnAirNow = onAir))
            }
        )
    )

    FilterChipRow(
        specs = specs,
        expanded = expanded,
        onToggleExpand = onExpandChange,
        onClear = { onFilterChanged(StationFilter()) },
        modifier = modifier,
        alwaysExpanded = alwaysExpanded
    )
}

@Composable
fun BandFilterRow(
    filter: BandFilter,
    allBookmarkLists: List<BookmarkList>,
    expanded: Boolean,
    onExpandChange: () -> Unit,
    onFilterChanged: (BandFilter) -> Unit,
    modifier: Modifier = Modifier,
    alwaysExpanded: Boolean = false
) {
    val specs = listOf(
        FilterSpec.FuzzySearchSpec(
            label = "Search",
            helpText = "Search in Name, Notes, …",
            search = filter.search,
            onChange = { onFilterChanged(filter.copy(search = it)) }
        ),
        FilterSpec.ListSpec(
            label = "Lists",
            items = allBookmarkLists,
            selected = filter.bookmarkLists.mapNotNull { catId -> allBookmarkLists.find { it.id == catId } }.toSet(),
            itemLabel = { it.name },
            icon = Icons.Default.List,
            onChange = { onFilterChanged(filter.copy(bookmarkLists = it.map{ it.id }.toSet())) }
        ),
        FilterSpec.FrequencySpec(
            label = "Frequency",
            min = filter.minFrequency,
            max = filter.maxFrequency,
            icon = IconFrequencyFilter,
            onChange = { min, max -> onFilterChanged(filter.copy(minFrequency = min, maxFrequency = max)) }
        ),
        FilterSpec.BooleanSpec(
            label = "Favorites",
            active = filter.onlyFavorites,
            icon = Icons.Default.Favorite,
            onToggle = { onFilterChanged(filter.copy(onlyFavorites = !filter.onlyFavorites)) }
        )
    )
    FilterChipRow(
        specs = specs,
        expanded = expanded,
        onToggleExpand = onExpandChange,
        onClear = { onFilterChanged(BandFilter()) },
        modifier = modifier,
        alwaysExpanded = alwaysExpanded
    )
}
