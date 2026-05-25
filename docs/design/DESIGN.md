# Virtual UWB Product Design System

## Product Identity

Virtual UWB is a premium Android mobile app for indoor UWB positioning, geofence monitoring, realtime safety events, position logging, and precision finding.

The app is used by engineers, construction-site operators, and stakeholders during technical demos.

The UI should feel:

- modern
- premium
- trustworthy
- technical
- mobile-native
- polished enough for a live product demo

This is not a generic CRUD admin app.  
This is not a web dashboard.  
This is not a marketing landing page.

It is a functional Android mobile product for realtime spatial intelligence.

---

## Design Direction

Use a modern premium mobile design language inspired by:

- Android Material3
- Material You / Material3 Expressive principles
- iOS-level spacing and visual polish
- Apple-like restraint and minimal chrome

Core qualities:

- light theme
- edge-to-edge mobile layout
- expressive but readable typography
- floating tonal surfaces
- rounded cards
- clean status pills
- subtle depth
- restrained shadows
- refined iconography
- high technical clarity
- good information density without clutter

The UI should look modern, but it must remain practical for Jetpack Compose implementation.

---

## Platform

Target:

- Android mobile app
- portrait orientation
- 6.3–6.8 inch phone screen
- Jetpack Compose
- Material3 components
- touch-first interaction
- bottom navigation

Avoid designs that feel like:

- desktop web dashboards
- spreadsheet tools
- old enterprise apps
- landing pages squeezed into mobile

---

## Color System

### Background

Use a soft, premium light background:

- app background: very light cool gray or off-white
- surfaces: white or tonal white
- elevated cards: subtle tonal fill
- map panel: light neutral with soft grid

### Primary Brand

Use indigo / purple as the primary product accent.

Use it for:

- selected bottom navigation
- primary buttons
- active chips
- phone marker
- precision finding arrow
- important action states

### Semantic Colors

Use clear semantic colors:

- blue = UWB anchors
- red = restricted zones, restricted alerts, danger
- green = safe zones, safe status, ENTER when safe
- orange = EXIT events, warnings, transitional status
- gray = secondary technical data

### Color Rules

Use color intentionally.

Do not over-color every card.

Restricted zone warnings must be visually obvious.

Safe states should feel calm.

Technical data should remain readable and not decorative.

---

## Typography

Use a clean mobile hierarchy.

### Screen Titles

- large
- confident
- expressive
- strong weight
- short and clear

Examples:

- Virtual UWB
- Events
- Devices
- Geofences
- Find My
- System

### Subtitles

- smaller
- muted
- explanatory
- one line when possible

### Data Labels

Use compact data rows:

- label: muted
- value: stronger
- coordinates: monospaced feel optional, but not required

### Numeric / Technical Data

Technical values must stay readable:

- latitude
- longitude
- bearing
- distance
- event count
- device role
- geofence type
- realtime status

Avoid:

- tiny unreadable data
- too many same-size text blocks
- overly dense paragraph blocks

---

## Layout Principles

Use:

- 16–20dp horizontal screen padding
- 8dp spacing rhythm
- large top header area
- clear visual hierarchy
- grouped sections
- rounded cards
- horizontal chips
- compact technical rows
- bottom navigation

Screens should feel breathable but not empty.

Do not remove technical information just to create whitespace.

---

## Common Components

### Bottom Navigation

Tabs:

1. Map
2. Devices
3. Geofences
4. Events
5. Find My
6. System / Debug

Rules:

- use icons
- use short labels
- selected tab uses primary indigo/purple
- unselected tabs use muted gray
- keep behavior unchanged

### Status Pills

Use status pills for:

- MongoDB API Live
- Connected
- Local
- MongoDB API
- Simulation Running
- Logging Enabled
- Restricted
- Safe
- Demo Ready

Pills should be:

- compact
- rounded
- color-coded
- readable
- not too decorative

### Cards

Use cards for:

- summary stats
- alerts
- map container
- event items
- device items
- geofence items
- forms
- system diagnostics

Card style:

- rounded corners
- tonal white surface
- subtle border or soft elevation
- good internal padding
- no heavy shadows
- no harsh outlines

### Buttons

Primary actions:

- filled or tonal
- rounded
- clear text

Secondary actions:

- outlined or tonal
- lower visual weight

Danger actions:

- red only when destructive or safety-critical

### Filter Chips

Use horizontal scrolling chips for:

- All
- Enter
- Exit
- Restricted
- Anchors
- Tags
- Safe
- Room

Chips should not wrap awkwardly.

---

# Screen Requirements

## 1. Map Screen

Purpose:

The hero screen for realtime indoor positioning.

Must include:

- title: “Virtual UWB”
- subtitle: “Realtime indoor positioning”
- status pill: “MongoDB API Live” or current data source
- summary cards:
  - Anchors
  - Tags
  - Data Source
  - Simulation
- restricted zone alert:
  - “Restricted Zone Alert”
  - “Tag T1 inside Restricted Zone”
- safe state:
  - “All tags safe”
  - “No tag is inside a restricted zone”
- indoor map panel:
  - light grid
  - room boundary
  - blue square anchors
  - circular tag markers
  - purple phone marker
  - red restricted zone polygon
  - green safe zone polygon
  - movement trails
  - device labels
- controls:
  - Start / Pause
  - Step
  - Reset Demo
  - Clear Trail
- selected tag details:
  - tag name
  - latitude
  - longitude
  - nearest anchor
  - distance
  - zone status

Map visual style:

- make the map panel feel custom and premium
- avoid a plain gray rectangle
- use a subtle grid
- make restricted zones clear but not ugly
- keep markers readable

---

## 2. Events Screen

Purpose:

Show realtime geofence ENTER and EXIT events.

Must include:

- title: “Events”
- subtitle: “Realtime geofence activity”
- realtime status
- count:
  - “Showing: 100 / 123”
- Refresh action
- horizontal filters:
  - All
  - Enter
  - Exit
  - Restricted
- event cards:
  - event type badge
  - device name
  - geofence name
  - geofence type
  - latitude / longitude
  - timestamp in DD/MM/YYYY HH:MM:SS device-local time

Event styling:

- ENTER = green accent unless restricted
- EXIT = orange accent
- restricted zone event = red priority accent
- event cards should be compact and readable
- avoid table-like layout

States:

- empty: “No events yet”
- offline: “Realtime offline, use Refresh”
- loading: simple and subtle

---

## 3. Devices Screen

Purpose:

Manage UWB anchors and tags.

Must include:

- title: “Devices”
- subtitle: “Manage anchors and mobile tags”
- data source status
- summary cards:
  - Anchors
  - Tags
  - Active
- filters:
  - All
  - Anchors
  - Tags
- device cards:
  - marker icon
  - device name
  - role badge
  - latitude
  - longitude
  - status
  - edit action
  - delete action
- Add Device button
- Add/Edit form:
  - name
  - role selector
  - latitude
  - longitude
  - save
  - cancel

Role styling:

- anchors = blue
- tags = purple/red

Avoid:

- table layout
- generic admin CRUD feel

---

## 4. Geofences Screen

Purpose:

Manage room zones, safe zones, and restricted zones.

Must include:

- title: “Geofences”
- subtitle: “Manage indoor safety zones”
- summary cards:
  - Total Zones
  - Safe Zones
  - Restricted
- geofence cards:
  - name
  - type badge
  - bounds:
    - min latitude
    - max latitude
    - min longitude
    - max longitude
  - edit action
  - delete action
- Add Geofence button
- Add/Edit form:
  - name
  - type selector
  - min latitude
  - max latitude
  - min longitude
  - max longitude
  - save
  - cancel

Type styling:

- Restricted Zone = red
- Safe Zone = green
- Room = neutral gray

---

## 5. Find My Screen

Purpose:

Precision finding UI that guides the phone toward a selected UWB tag.

Must include:

- title: “Find My”
- subtitle: “Precision guidance”
- selected tag status
- tag selector chips
- large hero direction component:
  - compass or radar style
  - directional arrow
  - distance
  - bearing
  - phone heading
- status details:
  - target
  - distance
  - bearing
  - phone azimuth
  - direction hint
- demo controls:
  - move north
  - move south
  - move east
  - move west
  - rotate left
  - rotate right
  - reset heading

Visual style:

- futuristic but clean
- hero arrow should be visually strong
- minimal clutter
- purple/indigo accent
- technical readouts in compact cards

---

## 6. System / Debug Screen

Purpose:

Show backend status, data source mode, realtime status, position logging, and diagnostics.

Must include:

- title: “System”
- subtitle: “Connectivity and diagnostics”
- status pill: “Demo Ready”
- Data Source card:
  - current mode
  - connection status
  - switch action
- Backend API card:
  - connection test
  - devices fetched
- Realtime card (SSE):
  - status
  - last update
- Geofence Events card:
  - last event status
  - fetch recent events action

Style:

- more technical than other screens
- still polished
- use status pills
- green for connected/live
- orange for warning
- red for failure

---

## Accessibility

Ensure:

- readable text
- sufficient contrast
- alerts are not color-only
- touch targets are large enough
- important technical data is scannable

---

## Implementation Constraints

The design must be realistic for:

- Kotlin
- Jetpack Compose
- Material3
- Canvas-based map rendering
- Android phone portrait layout

Avoid:

- unrealistic 3D
- heavy glassmorphism everywhere
- decorative effects that are hard to implement
- animations required for basic usability
- changing app logic for visual reasons

---

## UI Polish Rules for Agents

When modifying UI:

1. Preserve all state parameters and callbacks.
2. Do not modify ViewModels or repositories.
3. Do not remove existing functionality.
4. Keep code compile-safe.
5. Polish one screen at a time unless explicitly asked.
6. Prefer small reusable private composables inside the same file before creating new architecture.
7. Use Material3 components where possible.
8. Avoid experimental APIs unless already used.
9. Keep UI practical and maintainable.