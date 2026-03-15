# Bookmarks

RF Analyzer offers a comprehensive bookmarking feature which lets the user
bookmark various information about stations and bands:

- Station: The word station is used for a generic RF transmitter or signal.
  Examples are broadcast radio stations, ham radio operators, ...
- Band: A band is a frequency range with a description (e.g. a ham radio band)

Station and band bookmarks can be sorted into lists and viewed as list or
as overlay elements directly inside the FFT plot. In both cases filtering can
be done based on frequency range, mode or search terms. Stations and bands can
also be marked as favorites which are quickly accessible from the [Demodulation
Tab](./demodulation.md#bookmarks).

Additionally, it is possible to access online station lists. Currently supported are:

- **EiBi Database** (Shortwave Broadcast Radio Stations)
- **POTA** (Parks-on-the-Air) Spots
- **SOTA** (Summits-on-the-Air) Spots

## Bookmark Manager

The Bookmark Manager is the main screen to access all bookmark-related features
of RF Analyzer. It can be entered via the Bookmark button which is floating
above the control drawer. It is also available from the Bookmark Favorites
dialog which can be entered via the button from the demodulation tab.

The screen is divided into three primary pages and a settings page, accessible
via the bottom navigation bar:

* **Lists**: Logical folders used to group both Stations and Bands. Also
  contains special **Online Lists** (EiBi, POTA, SOTA) with automated update
  settings.
* **Stations**: Lists all station bookmarks (including stations from online
  lists). Can be filtered for lists, online lists, frequencies and more.
* **Bands**: Lists all bands.
* **Settings**: Configure display of stations and bands inside the FFT. Manage
  backups, imports and exports of bookmarks.

## Bookmark Favorites Dialog

The Bookmark Favorites Dialog provides quick access to your most important
bookmarked stations and bands without leaving the main screen of the app. It
displays a list of stations and bands that you have marked with the Heart icon
(your Favorites). Tapping a station in this dialog immediately tunes the radio
to the stored frequency and applies the saved demodulation settings. Tapping a
band moves your viewport to exactly the start and end of the band if your SDR
bandwidth (sample rate) supports it.

## Lists

Lists are used to keep your database organized (e.g., "Airband", "Local
Repeaters", "Marine", "Amateur Radio Bands", etc.). To display the stations or
bands of a specific bookmark list (or online list), use the arrow-button on the
right hand side of the list card. This will automatically navigate to the
station or band page and select the selected list as filter. Note that
other filters (such as frequency range, etc.) are untouched.

### Create and Edit Lists

You can create a new list using the plus icon on the Lists page.
Lists are specific to a type: they contain either Stations or Bands, but
not both. Lists have a color setting which is applied when displaying
stations or bands inside the FFT plot.

### Actions on Lists

By tapping the three-dot menu on a list card, you can perform the following
actions:

- **Delete**: Remove the list and all its contents.
- **Edit**: Rename the list or change its color.
- **Export**: Save the contents of this specific list to a JSON file.
- **Copy/Move**: Batch process the stations or bands within the list to organize them into a different list.

### Manage Online Lists

Online lists are displayed as cards in the Lists page. Each
card provides the following actions:

- **Manual Download**: Trigger an immediate update from the provider's server.
- **Periodic Background Updates**: Enable the "Autorenew" toggle and use the slider to set a
  periodic update interval (e.g., every 24 hours).
- **Clear Cache**: Remove downloaded items.

The card also displays the time of the last successful download.

!!! note "Stations from Online Lists"
    Stations which were downloaded from online lists are handled like locally
    created bookmarks. However, they cannot be edited or deleted individually
    (because this would be overwritten with the next periodic update or manual
    download). If you want to edit a downloaded station, copy it into another
    list first.

## Stations

### Create and Edit Stations

To create a new station bookmark, either press the floating Plus-Button on the
bottom right cornor of the station page of the bookmark manager or the action
"Add Station Bookmark" from the menu that pops up when you press the bookmark
button on the main screen of the app. To edit a station, first expand a station
entry by tapping on it once, then press the three-dot menu button on the bottom
right corner of the station card. From there choose the option "Edit".

In either case you will get to the "Edit Station" Dialog which has the following
sections:

- Name
- Favorite Toggle
- List
- Station Frequency
- Demodulation Mode
- Station Bandwidth
- Notes
- Address (incl. Country)
- Coordinates
- Callsign
- Language
- Squelch
- Schedule

Note that not all sections are enabled by default for simplicity. You can
always add sections from the bottom of the "Edit Station" dialog if they are
missing. Sections which are empty are not saved. Some section require
additional explanation:

#### Station Frequency, Bandwidth and Mode

When tuning in on a station, these values will be applied to the demodulation settings.
For further information about these parameters refer to:

- Station Frequency = [Channel Frequency](./demodulation.md#channel-frequency)
- [Bandwidth](./demodulation.md#zoom-and-bandwidth)
- Mode = [Demodulation Mode](./demodulation.md#demodulation-mode)

#### Address

The address section contains a field for the country and the address line of a
station. The address line can hold arbitrary text but is meant to be an address
which you would find on a map. In case of POTA or SOTA stations it holds the
name of the park or summit.

If the station bookmark has an address set, you can view the location on a map
by pressing the "Open in Maps" action from the three-dot menu of the station
card. Note that this action will only take the address fro this section if no
Coordinates section is available, otherwise it will use the exact coordinates.

#### Coordinates

In the coordinates section you can enter classic coordinate (latitude and longitude).
It is also possible to specify a Maidenhead Locator. Entering either the locator
or the coordinates will fill in the other property automatically.

If the station bookmark has coordinates set, you can view the location on a map
by pressing the "Open in Maps" action from the three-dot menu of the station
card.

#### Call Sign

This field is intented to hold an (amateur radio) callsign.
In broadcasting and radio communications, a call sign is a unique identifier
for a transmitter station. ITU prefix is usually used to identify a country,
and the rest of the call sign is an individual station in that country.

#### Squelch

With this section you can specify whether the [Squelch
Setting](./demodulation.md#squelch-control) will be enabled or disabled when tuning in
on this station. The following options are available:

- **Squelch Enabled**: The squelch setting will automatically *turn on* and be
  set to the specified value when the station is selected and tuned.
- **Squelch Disabled**: The squelch setting will automatically be *turned off*
  when this station is selected (if it was previously enabled).
- **Squelch Section not selected**: If the squelch section is not selected, the
  squelch setting of the demodulator is untouched when selecting and tuning to
  this station.


Note that the squelch threshold is specified in
[dbFS](./advanced.md#understanding-db-dbm-and-dbfs). Therefore the threshold
might be different depending on the SDR, gain and antenna. RF Analyzer does
not (yet) automatically compensate for that.

#### Schedule

The schedule provides information about when this station is usually active and
transmitting. It is mainly used for broadcast radio stations which are imported
from the [EiBi Database](./bookmarks.md#eibi-database).

Select a start and end time (in UTC) and (if applicable) the weekdays on which
the station is active. The bookmark manager contains a filter which lets you
only display stations which are actually active at the current day and time. If
the schedule is not set (all values empty), it is assumed to be active all the
time.

### Filter Stations

The filter row allows you to narrow down the listed bookmarks:

- Search: A text search (matches text inside the fields: name, notes, country, language, callsign).
- Lists: View only specific lists.
- Frequency Range: View only stations between a specified start and end
  frequency. You can leave either start or end frequency empty (0 Hz) to create
  a "higher than" or "lower than" frequency filter.
- Modulation Mode: Filter for specific modes like "USB" or "NFM".
- Favorites: Toggle to see only your favorited (hearted) stations.
- Currently On-Air: Filters based on the stored
  [Schedule](./bookmarks.md#schedule) and the current time.

## Bands

### Create and Edit Bands

Bands represent chunks of the spectrum. The editor allows you to define: 

- Name
- List.
- Start Frequency
- End Frequency
- Notes
- Sub-Bands

#### Start and End Frequency

These values (in Hz) describe where the band starts and ends. When choosing to
view a band bookmark, the screen will be moved so that the start frequency is
exactly on the left edge and the end frequency on the right edge of the visible
screen in the FFT.

#### Notes

In the notes field you can put any text that you like to be associated with the band.
Note that this field is also searched via the search field in the [Band Filter](#filter-bands).

#### Sub-Bands

You can divide a band into smaller segments (e.g., the "CW" or "Data" portion
of a Ham band):

- Adding: Enter the name and range in the sub-band editor and press the Checkmark.
- Editing: Tap an existing sub-band to load its values into the editor.
- Removing: Use the Trashcan icon to remove a segment.

### Filter Bands

Bands can be filtered by their parent List, their Frequency Range, or their
Favorite status. There is also a search field which lets you search for text
inside the band name or notes field.

## Settings

### Display Stations and Bands in FFT

You can control how bookmarks appear as visual overlays on the waterfall:

- **Show/Hide in FFT**: Toggle the visibility of station or band labels in the
  FFT plot.
- **Custom Filter for FFT**: If enabled, you can specify a filter setting for
  labels in the FFT. This is enabled by default and initialized with an empty
  filter which means that all bookmarks are drawn as labels in the FFT screen.
  If the option **Custom Filter for FFT** is disabled, the filters you set in
  the Bookmark Manager's station and band screens are automatically applied to
  the FFT labels.

### Backup and Restore

- **Export Database**: Creates a full backup of all your lists, stations, and
  bands into a single file.
- **Restore**: Overwrites your current database with a backup file. 

!!! warn "Warning: Data Loss"
    The restore action is irrecoverable; always create a fresh backup before restoring.

### Import and Export

With the option **Import stations/bands** it is possible to import bookmarks from a file.
Supported import formats/sources are:

- **RF Analyzer JSON**: Import from a JSON file that was created via an export
  from RF Analyzer.
- **SDR# Frequencies**: Import the `Frequencies.xml` file usually found in the
  SDR# root directory.
- **SDR++ JSON**: Import the `frequency_manager_config.json` file from the
  SDR++ root directory.

With the option **Import Default Band Plans**, a set of prepared band plans
can be imported:

- Amateur Radio Band Plans (IARU Band Plans for your specific region)
- ISM, Maritime and Air Bands

Finally, if you upgraded from RF Analyzer version 1.13, the option **Legacy
Import** will be available to migrate your old bookmark database into the new
system.

#### Import Mode

When importing stations and bands, they must be placed inside a list. The way
this is handled can be selected with the *Import Mode*. This can be set after the
imported file was chosen and parsed:

- **Import as is**: Import as is. If the imported file contains names of
  lists for bands and stations, they will be sorted into those
  respectively. Should a list not yet exist, it is created automatically.
  If there are stations or bands which do not have a list name set, a new
  list with a default name will be created for them.
- **Import into single list**: This mode lets you chose an existing
  list for stations and another existing list for all bands which are
  imported. All list information from the imported file are droped and not
  used.
- **Import with prefix**: Imported stations and bands will be placed into
  lists as specified in the imported file (just as with option *Import as
  is*). But all list names will be prefixed with a tag you can enter.

## Available Online Lists

### EiBi Database

A highly respected community database for international shortwave broadcasting.
It includes station names, languages, and complex seasonal schedules.

Visit [eibispace.de](http://eibispace.de) for more information.

For configuration of periodic updates and other general instructions about
online lists see [Online Lists](./bookmarks.md#manage-online-lists).

### POTA Spots

Real-time "spots" for the Parks-on-the-Air program. These bookmarks are dynamic
and will update as amateur radio operators move between different parks and
frequencies. Enable a reasonable periodic update to keep up with the latest
activity and be able to match transmissions from activators to their spot data.

For configuration of periodic updates and other general instructions about
online lists see [Online Lists](./bookmarks.md#manage-online-lists).

### SOTA Spots

Real-time activity for Summits-on-the-Air. Similar to
[POTA](./bookmarks.md#pota-spots), these entries help you find "activators"
transmitting from mountain tops around the world.

For configuration of periodic updates and other general instructions about
online lists see [Online Lists](./bookmarks.md#manage-online-lists).
