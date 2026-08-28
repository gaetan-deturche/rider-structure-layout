using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Psi;

namespace ReSharperPlugin.StructLayout
{
    // Requires ONLY zones the host actually runs. Do not add a self-defined [ZoneDefinition] here
    // unless something activates it: a required-but-unactivated zone silently disables every
    // component in this namespace, and the context action just never appears in Alt+Enter.
    [ZoneMarker]
    public class ZoneMarker : IRequire<ILanguageCppZone>
    {
    }
}
