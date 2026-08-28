// Lists types and members of a .NET assembly by reading metadata only - never loads the assembly, so
// it works on the Rider backend DLLs without their dependency graph.
//
//   apiindex <assembly.dll> [filter ...]
//
// A filter matches case-insensitively against the type's full name, the member name, or the qualified
// "Namespace.Type::Member" form, so "ICppLayoutBuilder", "BuildLayout" and "CppSizeAndAlignment::Size"
// all work. No filter lists everything.

using System.Reflection;
using System.Reflection.Metadata;
using System.Reflection.PortableExecutable;

if (args.Length == 0)
{
    Console.Error.WriteLine("usage: apiindex <assembly.dll> [filter ...]");
    return 1;
}

string path = args[0];
string[] filters = args.Skip(1).Select(f => f.ToLowerInvariant()).ToArray();

using FileStream stream = File.OpenRead(path);
using PEReader pe = new PEReader(stream);
MetadataReader md = pe.GetMetadataReader();

bool Match(string s) => filters.Length == 0 || filters.Any(f => s.ToLowerInvariant().Contains(f));

// Nested types carry an empty namespace, so the declaring chain has to be rebuilt by hand.
string FullName(TypeDefinition td)
{
    string name = md.GetString(td.Name);
    TypeDefinitionHandle declaring = td.GetDeclaringType();
    if (!declaring.IsNil)
        return FullName(md.GetTypeDefinition(declaring)) + "." + name;

    string ns = md.GetString(td.Namespace);
    return string.IsNullOrEmpty(ns) ? name : ns + "." + name;
}

foreach (TypeDefinitionHandle th in md.TypeDefinitions)
{
    TypeDefinition td = md.GetTypeDefinition(th);
    string full = FullName(td);
    bool typeHit = Match(full);
    bool printedAnything = false;

    foreach (MethodDefinitionHandle mh in td.GetMethods())
    {
        MethodDefinition method = md.GetMethodDefinition(mh);
        string name = md.GetString(method.Name);
        if (!typeHit && !Match(name) && !Match(full + "::" + name))
            continue;

        bool isPublic = (method.Attributes & MethodAttributes.MemberAccessMask) == MethodAttributes.Public;
        bool isStatic = (method.Attributes & MethodAttributes.Static) != 0;
        Console.WriteLine($"{(isPublic ? "pub" : "   ")} {(isStatic ? "static" : "      ")} {full}::{name}");
        printedAnything = true;
    }

    foreach (FieldDefinitionHandle fh in td.GetFields())
    {
        FieldDefinition field = md.GetFieldDefinition(fh);
        string name = md.GetString(field.Name);
        if (!typeHit && !Match(name) && !Match(full + "::" + name))
            continue;

        bool isPublic = (field.Attributes & FieldAttributes.FieldAccessMask) == FieldAttributes.Public;
        Console.WriteLine($"{(isPublic ? "pub" : "   ")} field  {full}::{name}");
        printedAnything = true;
    }

    if (typeHit && !printedAnything)
        Console.WriteLine($"    type   {full}");
}

return 0;
