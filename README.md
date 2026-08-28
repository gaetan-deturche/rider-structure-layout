# Struct Layout for JetBrains Rider

Shows the memory layout — offsets, sizes, alignment, padding, vtable pointers, bitfields — of a C++
class, struct or union. Alt+Enter on the type name, *Show struct layout*.

Rider already knows this: its C++ engine computes type layout to power the size/offset tooltip, using
the compile configuration the project actually builds with. This plugin drives that same calculator and
shows the whole picture instead of one member at a time — so the numbers cannot drift from the real
build the way a separate libclang parse can.

## Status

Early. The extractor works and the Alt+Enter action dumps a plain-text table; the graphical tool window
is not built yet.

## Building

Requires a Rider installation to reference the backend assemblies from, and the .NET SDK.

```powershell
.\deploy.ps1
```

Then restart Rider. See `CLAUDE.md` for the API details and the current gaps.
