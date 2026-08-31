# Struct Layout for JetBrains Rider

Shows the memory layout of a C++ class, struct or union — offsets, sizes, alignment, padding, vtable
pointers and bitfields — as a byte matrix.

Rider already knows this: its C++ engine computes type layout to power the size/offset tooltip, using the
compile configuration the project actually builds with. This plugin drives that same calculator and shows
the whole picture instead of one member at a time, so the numbers cannot drift from the real build the way
a separate libclang parse can. On an Unreal project that means the layout reflects UBT's toolchain,
packing and defines — an editor build shows `FName` as 12 bytes, a shipping build as 8, and both are right.

## Using it

Put the caret on a type name, then any of:

- right-click → **Show Struct Layout**
- **Alt+L**
- **Alt+Enter** → *Show struct layout*

All three open the same view.

| In the view | |
|---|---|
Rows | one per `alignment` bytes, offset in the gutter (hex or decimal) |
Boxes | members over the bytes they occupy; a member wrapping to the next row is one merged shape |
Grey hatching | padding, with a tooltip explaining why it is there |
Outer frame | the struct's own extent, so trailing padding stays distinguishable from bytes past the end |
Purple frames | base classes and nested aggregates, one frame per element |
Stacked bands | union members, which share the same bytes |
Double-click | expand a struct-typed member to see its own members (or collapse it again) |
Hover | name, kind, offset in hex and decimal, size, alignment, byte range, nesting depth |

`Display alignment` overrides the bytes-per-row; `Expand All` / `Collapse All` apply to every element.

## Installing

Grab the zip from [Releases](https://github.com/gaetan-deturche/rider-structure-layout/releases) →
Settings → Plugins → gear → **Install Plugin from Disk**, then restart Rider (the backend half loads at
host startup). Requires Rider 2026.2.

## Building

```powershell
.\deploy.ps1                 # frontend + rdgen protocol + .NET backend, installed into a sandbox
Claude\run-dev-rider.ps1     # isolated Rider on that sandbox, safe beside your real one
```

No Rider installation is needed to build: the backend compiles against the Rider distribution Gradle
downloads. See `CLAUDE.md` for the API this is built on and the traps worth knowing.

## Diagnostics

Set `STRUCT_LAYOUT_DUMP=1` before starting Rider to have every layout written to
`%TEMP%\struct-layout-last.txt`, including entries whose size the engine could not compute.
