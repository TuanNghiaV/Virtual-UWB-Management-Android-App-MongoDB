# Virtual UWB UI Polish Skill

Use this skill when modifying Jetpack Compose UI for the Virtual UWB Android app.

## Goal

Polish the app UI into a premium, modern, mobile-native Android experience while preserving all existing functionality.

The app is a functional indoor UWB positioning product, not a marketing website.

## Visual References

Read:

- `docs/design/apple-reference.md` as taste reference only.
- `docs/design/virtual-uwb-design.md` as the actual product design direction.

The Apple reference should influence:
- restraint
- spacing
- typography
- minimal chrome
- premium polish
- clean surfaces

Do not copy:
- full-bleed web landing sections
- product marketing tiles
- ultra-low information density
- single-accent-only rule
- photography-first layout

## Product Context

Virtual UWB includes:
- Map screen with anchors, tags, geofences, trails, phone marker
- Devices screen
- Geofences screen
- Events screen with realtime Supabase events
- Find My precision guidance
- System/Debug screen
- Supabase Local/Remote mode
- Position logs
- Geofence ENTER/EXIT events

## Non-Negotiables

Do not modify:
- ViewModel logic
- repositories
- Supabase client
- SQL
- navigation behavior unless explicitly requested
- business logic
- simulation logic

Only modify presentation/UI files unless asked otherwise.

Preserve all:
- callbacks
- state parameters
- Lat/Lon values
- event types
- device roles
- geofence types
- realtime status
- data source mode
- controls

## Design Direction

Use:
- Material 3 mobile UI
- light theme
- off-white background
- premium tonal surfaces
- rounded cards
- subtle hairline borders
- restrained shadows
- expressive but readable typography
- modern bottom navigation
- clean status pills
- horizontal filter chips
- compact technical data rows

Semantic colors:
- indigo/purple = primary brand and actions
- blue = anchors
- red = restricted/danger
- green = safe/ENTER
- orange = EXIT/warning
- gray = secondary technical data

## Avoid

Avoid:
- old enterprise dashboard look
- generic admin panel UI
- heavy borders
- cramped forms
- removing technical information
- turning app screens into landing pages
- overusing gradients/glassmorphism
- changing app logic while polishing UI

## Workflow

Polish one screen at a time.

Recommended order:
1. EventsScreen
2. MapScreen
3. FindMyScreen
4. DevicesScreen
5. GeofencesScreen
6. Debug/SystemScreen

After editing one screen:
- ensure code compiles
- do not proceed to another screen unless asked