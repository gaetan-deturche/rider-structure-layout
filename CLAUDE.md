# rider-structure-layout (agent notes)

Rider plugin **Struct Layout** (id `dev.structlayout`) — shows the memory layout of a C++ class/struct/union.

**Design decision (2026-08-28, user's call):** do NOT mirror
[Viladoman/StructLayout](https://github.com/Viladoman/StructLayout), which reparses the sources with
libclang. Rider's C++ engine already computes layout under the project's *real* compile configuration
(UBT's toolchain, packing, defines, target triple), so a separate parse can only disagree with the true
build. We drive the engine's own calculator instead.

## The API this is built on

All public, all in `JetBrains.ReSharper.Cpp.dll` (namespace `JetBrains.ReSharper.Psi.Cpp.*`).
Undocumented, but signatures are byte-identical between Rider 2025.3.3 and 2026.2.1.

```csharp
// the entry point - walks a class and reports every entry to our visitor
public static CppSizeAndAlignment BuildLayout(this ICppClassResolveEntity cent, in CppTypeContext tc,
                                              ICppLayoutBuilder layoutBuilder, bool withVirtualBases)

public interface ICppLayoutBuilder {            // Psi.Cpp.Types
    bool WasInterrupted { get; }
    ICppClassLayoutBuilder BeginClass();
    ICppUnionLayoutBuilder BeginUnion();
    void OnPadding(int padding);
}
public interface ICppClassLayoutBuilder {
    record struct Entry(ICppResolveEntity Entity, EntryKind Kind);
    enum EntryKind { Base, VirtualBase, Declarator, AnonClass, Vbptr, Vfptr };
    record struct Layout(int Offset, CppSizeAndAlignment SizeAlignment);
    record struct BitFieldDescription(int OffsetInBytesFromStart, int OffsetInBitsInsideBitField, int Width);
    void OnEntry(Entry entry, Layout layout);
    void OnBitField(ICppGroupedVariableDeclaratorResolveEntity bitfield, BitFieldDescription d);
}
```

- `CppSizeAndAlignment` lives in `Psi.Cpp.**Symbols**` (not `.Types`) — public fields `Size`, `Alignment`,
  plus `IsValid()` and `Adjust(dialect)`. **Always `Adjust`** with `cache.GetDialect()`.
- **The calculator does not descend on its own.** `Base`/`VirtualBase`/`AnonClass` entries hand you the
  nested `ICppClassResolveEntity` with its offset; recursion is ours (`StructLayoutCollector.Recurse`).
  This mirrors what the engine's own `LayoutBuilderForPaddingCalculation` does for anon classes.
- Caret → entity: `((IGenericSymbolNode)node).TryGetResolveEntity()`
  (`CppResolveScopeNodeStructure`, namespace `Psi.Cpp.Tree`).
- Reference implementation to read when stuck: decompile
  `JetBrains.ReSharper.Feature.Services.Cpp.QuickDoc.CppLayoutPresenterUtil` — that is the code behind
  the size/offset tooltip Rider already shows, and `GetMemberLayoutForPresentation` /
  `GetSizeAndAlignmentForPresentation` are usable public shortcuts for single members.

**Known gap:** `GetSizeAndAlignmentForPresentation` refuses types where `HasTemplateParents()` is true.
Whether that also excludes concrete instantiations like `TArray<int32>` is **not yet verified** — it
matters for UE work and is the first thing to test.

## Trap: zones silently disable everything

`ZoneMarker` must require **only zones the host actually runs**. A self-defined `[ZoneDefinition]`
(e.g. `IStructLayoutZone`) that nothing activates lands in the backend's `AllDiscovered` list but not in
`ZonesToRun`, and then every component in the namespace is filtered out — the context action simply never
appears in Alt+Enter, with **no error in any log**. Cost one debug cycle on 2026-08-28.

`IRequire<ILanguageCppZone>` is correct here (that zone is in `ZonesToRun` whenever C++ is enabled; the
backend is even launched with `--enablecpp`). To reintroduce a plugin-owned zone, it needs
`[ZoneActivator] class ZoneActivator : IActivate<IStructLayoutZone>`.

How to check, in the dev sandbox's `log/backend.*.log`:

```
AllDiscovered zones: (243pcs)[...]   <- definition was found
ZonesToRun zones: (139pcs)[...]      <- must contain your zone, or nothing runs
```

Also in that log, `Additional Ad-Hoc Application Package: structlayout version 0.1.0 ... built on <ts>`
confirms the backend picked up the DLL, and the timestamp tells you whether it is the build you expect.

## Build

`JetBrains.ReSharper.Cpp.dll` is **not** in the `JetBrains.Rider.SDK` NuGet package, so the csproj
references `$(RiderHostPath)\JetBrains.*.dll` straight out of the install (`Private=false`,
`NoWarn=MSB3277` for the expected identity clashes). Backend targets **net472** (the Rider host is still
.NET Framework: `Cpp.dll` references `mscorlib`, not `System.Private.CoreLib`).

```powershell
.\deploy.ps1        # builds + installs unpacked into %APPDATA%\JetBrains\Rider2026.2\plugins\struct-layout
```

Rider must be **restarted** after deploying — backend plugins load once at host startup.

`global.json` pins the SDK to **9.0.308** with `rollForward: disable`: see
[[dotnet-sdk-9-0-312-missing-runtime]] — SDK 9.0.312 cannot run on this machine.

## Status

Stage 1 (done, compiles clean): backend-only extractor + Alt+Enter context action, dumps an ASCII table
via `MessageBox.ShowInfo`. Not yet run inside Rider.

Stage 2 (not started): rdgen protocol + Kotlin tool window with the graphical bar view, plus recursion
into member struct types (needs a declarator → type-entity lookup; `CppQualType.GetSizeAndAlignment`
and `CppSizeAndAlignmentUtil.ReferencedTypeResolveEntity` are the leads).

## Tools that made this tractable

**`tools/apiindex`** - lists types/members of an assembly from metadata alone (never loads it, so it
works on the backend DLLs without their dependency graph). This is how the whole layout API was found.

```bash
tools/apiindex/bin/Debug/net9.0/apiindex.exe "<ReSharperHost>/JetBrains.ReSharper.Cpp.dll" BuildLayout
```

Filters match the type's full name, a member name, or `Namespace.Type::Member`; nested types print their
full declaring chain (`ICppClassLayoutBuilder.Entry::Base`). No filter lists everything.

Pair it with **`ilspycmd`** (already installed at `~/.dotnet/tools`) to read a type once apiindex has
found where it lives: `ilspycmd -t <FullTypeName> -r <ReSharperHost> <assembly.dll>`. Note apiindex is
the only way to find a type's *assembly and namespace* - ilspycmd needs the exact full name up front.
