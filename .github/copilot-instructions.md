# Copilot Instructions for VirtualUWB

## Project Context

VirtualUWB is an Android mobile app for indoor UWB positioning, geofencing, realtime safety events, position logging, and precision finding.

The project uses:

- Kotlin
- Jetpack Compose
- Material3
- Android single-activity architecture
- Supabase PostgREST
- Supabase Realtime
- PostgreSQL / PostGIS
- Clean-ish architecture with domain models, repositories, DTOs, mappers, ViewModel, and Compose screens

The app currently supports:

- Local / Supabase data source switching
- Device CRUD
- Geofence CRUD
- Position logs
- Geofence ENTER / EXIT events
- Realtime Events tab
- Map restricted zone warning
- Demo reset positions
- Find My precision direction UI
- Debug/System tools

## Design References

When working on UI or presentation code, read:

- `docs/design/DESIGN.md` as the main product UI direction
- `docs/design/apple-reference.md` as taste reference only

The Apple reference is only for:

- restraint
- spacing
- typography discipline
- minimal chrome
- premium polish
- clean surfaces

Do not copy Apple web landing page layouts literally.

VirtualUWB is a functional Android mobile app, not a marketing website.

## Non-Negotiable Rules

Do not modify these unless the user explicitly asks:

- ViewModel business logic
- repositories
- Supabase client
- SQL files
- DTOs
- mappers
- domain models
- simulation logic
- geofence math
- realtime subscription logic
- navigation behavior

When doing UI polish, only modify:

- Compose screen files
- small reusable UI helpers
- theme files if needed
- navigation icon/label presentation if explicitly requested

Preserve all existing:

- callbacks
- state parameters
- Lat/Lon data
- device roles
- geofence types
- event types
- realtime status
- data source status
- CRUD behavior
- simulation controls
- debug/test actions

## UI Direction

Use a premium, modern Android mobile UI style:

- Material3 inspired
- light theme
- off-white background
- tonal white cards
- rounded surfaces
- subtle hairline borders
- restrained shadows
- expressive headers
- clean status pills
- modern bottom navigation
- compact but readable technical data

Semantic colors:

- indigo / purple = primary brand and actions
- blue = anchors
- red = restricted zones and danger alerts
- green = safe zones and ENTER/safe states
- orange = EXIT events and warnings
- gray = secondary technical data

## Avoid

Avoid:

- old enterprise dashboard look
- generic admin panel UI
- heavy borders
- cramped forms
- web-style landing pages
- removing technical information
- excessive gradients
- excessive glassmorphism
- changing logic while polishing UI

## Workflow

Polish one screen at a time.

Recommended order:

1. EventsScreen
2. MapScreen
3. FindMyScreen
4. DevicesScreen
5. GeofencesScreen
6. Debug/SystemScreen

After editing a screen:

- keep code compile-safe
- preserve all callbacks
- do not continue to another screen unless requested