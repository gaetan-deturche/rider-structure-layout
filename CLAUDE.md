# rider-structure-layout (agent notes)

Rider plugin **Struct Layout** (id `dev.structlayout`) — shows the memory layout of a C++ class/struct/union.

**Design decision (2026-08-28, user's call):** do NOT mirror
[Viladoman/StructLayout](https://github.com/Viladoman/StructLayout), which reparses the sources with
libclang. Rider's C++ engine already computes layout under the project's *real* compile configuration
(UBT's toolchain, packing, defines, target triple), so a separate parse can only disagree with the true
build. We drive the engine's own calculator instead.

## The API this is built on

All public, all in `JetBrains.ReSharper.Cpp.dll` (namespace `JetBrains.ReSharper.Psi.Cpp.*`).
Undocumented, but signatures are byte-identical across Rider 2025.3.3, 2026.2.1 and 2026.3 (build 263) —
the 2026.3 upgrade needed **no source change at all**, and rdgen 2026.3.2 regenerated the protocol
byte-identically. Re-verify with `tools/apiindex` before assuming that holds for the next version.

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

**Resolved:** `GetSizeAndAlignmentForPresentation` refuses types where `HasTemplateParents()` is true,
but that does NOT exclude concrete instantiations — `TArray<...>` and friends lay out fine in a real UE
project (confirmed on Rematch). We drive `BuildLayout` directly anyway, not that presentation helper.

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
references `$(RiderHostPath)\JetBrains.*.dll` (`Private=false`, `NoWarn=MSB3277` for the expected
identity clashes). `buildBackend` points `RiderHostPath` at `intellijPlatform.platformPath/lib/ReSharperHost`
— the Rider distribution Gradle downloads — so **no local Rider install is required**, which is what makes
CI work. Backend targets **net472** (the host is still .NET Framework: `Cpp.dll` references `mscorlib`).

Do NOT exclude `*.resources.dll` from that reference glob: MSBuild globs are case-insensitive on Windows,
so it also swallows `JetBrains.ReSharper.Resources.dll` (which holds `ReadLockCookie`). That same glob is
what absorbed `ILanguageCppZone` moving from `JetBrains.ReSharper.Psi.dll` to `JetBrains.ReSharper.Cpp.dll`
in 2026.3 without a code change.

**Upgrading to a new Rider:** bump `platformVersion` + `pluginSinceBuild`/`pluginUntilBuild` in
`gradle.properties` and the rdgen version in `protocol/build.gradle.kts` (it must match the platform's rd
protocol), then rebuild. `deploy.ps1` and `Claude\run-dev-rider.ps1` find Rider by reading
`product-info.json` and taking the highest build — **never trust the install folder's name**, an in-place
update keeps it (the folder `JetBrains Rider 261.22158.211` held 2026.2.1, then 2026.3).

```powershell
.\deploy.ps1        # builds all three parts, installs unpacked into the Claude\dev-ide sandbox
Claude\run-dev-rider.ps1   # isolated Rider on that sandbox, safe to run beside the real one
```

Rider must be **restarted** after deploying — backend plugins load once at host startup, and the
ReSharper backend process outlives the frontend, keeping the DLL locked for a few seconds after a kill.

**Auto-update repository:** `https://raw.githubusercontent.com/gaetan-deturche/rider-structure-layout/master/updatePlugins.xml`
(add it under Settings | Plugins | gear | Manage Plugin Repositories). `release.yml` regenerates it on
every `v*` tag — a plugin installed from disk never auto-updates, one installed from this repository does.

`global.json` pins the SDK to **9.0.308** with `rollForward: disable`: see
[[dotnet-sdk-9-0-312-missing-runtime]] — SDK 9.0.312 cannot run on this machine.

## Traps that cost a debug cycle each

- **`[SolutionComponent(Instantiation.Demand*)]` never runs.** A protocol handler registered in a
  component's constructor needs `Container*` (eager) instantiation, or nothing constructs it, the
  `RdCall` has no handler, and the frontend's call silently never completes — no exception anywhere.
- **rdgen ignores the Kotlin `namespace`.** The generated Kotlin lands in `com.jetbrains.rd.ide.model`
  (the model root's namespace) while the C# side honours `JetBrains.Rider.Model`. Import accordingly.
- **Rider APIs need `bundledModule(...)`.** `project.solution` lives in `intellij.rider.rdclient.dotnet`;
  with the 2.x Gradle plugin those product modules are not on the compile classpath by default.
- **`buildPlugin` zips `prepareSandbox`'s output**, so the backend DLL copy belongs in the sandbox task
  only — adding it to both produces a duplicate-entry failure.
- **`gradlew` must be mode 100755 in the index** (`git update-index --chmod=+x gradlew`) or CI cannot
  run it.
- **Two entry points must not render differently.** The Alt+Enter context action originally showed a
  `MessageBox` text dump while right-click opened the tool window; a colleague hit the documented
  Alt+Enter and concluded the release had no UI. It now fires the `showLayout` signal instead.

## Unreal notes

- `USTRUCT()` / `UPROPERTY()` add **no data members**. `GENERATED_BODY()` expands to typedefs, statics,
  friends and constructors only, so a plain USTRUCT's layout is exactly its declared members. Hidden
  state exists only on `UObject`-derived classes (vfptr + `ObjectFlags`/`InternalIndex`/`ClassPrivate`/
  `NamePrivate`/`OuterPrivate`) and arrives through **base recursion**.
- `FName` is **12 bytes in editor builds** (`WITH_CASE_PRESERVING_NAME` adds `DisplayIndex`), 8 in
  shipping. Seeing 12 is proof the layout follows the real compile config.
- A nested class must be resolved in **its own view pos**: a UE base lives in another header and may be
  incomplete in the derived class's view pos, in which case its walk reports nothing and every inherited
  member silently disappears (`StructLayoutCollector.Recurse`).
- Member-type recursion uses `ICppDeclaratorResolveEntity.GetCppType()` then
  `CppTypesUtil.{IsArray, GetArrayInnerTypeRecursively, IsResolvedClassType, GetResolvedClassTypeEntity}`
  — all public. Capped at 4 levels / 2000 rows because UE types nest deeply through value members.

## Status (0.3.0)

Targets **Rider 2026.3** (build 263). Working: backend extractor, rdgen protocol, right-click / Alt+L /
Alt+Enter all opening one tool window with a byte-matrix view (per-byte grid, hex or decimal offsets,
struct box, hatched padding, stacked lanes for unions, merged shapes for wrapped members and groups,
depth-adaptive row height, hover details, double-click to expand a struct-typed member). Verified against
`cl /d1reportSingleClassLayout` and on Unreal (`UObject` chain, Rematch).

Not done: per-member "go to declaration" from the view, and a legend.

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
