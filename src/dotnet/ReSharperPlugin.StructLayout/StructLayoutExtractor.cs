using System;
using System.IO;
using System.Text;
using JetBrains.Annotations;
using JetBrains.ReSharper.Psi.Cpp.Resolve;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Types;
using JetBrains.ReSharper.Psi.Cpp.Util;

namespace ReSharperPlugin.StructLayout
{
    public static class StructLayoutExtractor
    {
        // Mirrors CppLayoutPresenterUtil.GetSizeAndAlignmentForPresentation, but keeps every entry the
        // walk reports instead of only the padding total.
        [CanBeNull]
        public static LayoutTable TryExtract([NotNull] ICppClassResolveEntity clazz)
        {
            CppFileResolveEntitiesCache cache = clazz.GetRootFileResolveCache();
            CppViewPos viewPos = CppViewPos.Full(cache.GlobalNamespace);

            if (!clazz.IsClassComplete(in viewPos))
                return null;

            CppTypeContext typeContext = new CppTypeContext(viewPos, cache.TypeFactory);

            LayoutTable result = new LayoutTable { TypeName = clazz.Name.ToString() };
            StructLayoutCollector collector = new StructLayoutCollector(in typeContext, result.Rows, 0, 0);

            CppSizeAndAlignment sizeAndAlignment = collector.Walk(clazz);
            if (!sizeAndAlignment.IsValid())
                return null;

            sizeAndAlignment = sizeAndAlignment.Adjust(cache.GetDialect());

            result.TotalSize = sizeAndAlignment.Size;
            result.TotalAlignment = sizeAndAlignment.Alignment;
            result.TotalPadding = collector.TotalPadding;
            result.Interrupted = collector.WasInterrupted;

#if STRUCT_LAYOUT_DIAGNOSTICS
            Dump(result);
#endif
            return result;
        }

#if STRUCT_LAYOUT_DIAGNOSTICS
        // Diagnostic, dev builds only (STRUCT_LAYOUT_DIAGNOSTICS, set for Debug in
        // Directory.Build.props): every entry the engine reported, including ones whose size it
        // could not compute. Written to %TEMP%\struct-layout-last.txt.
        private static void Dump(LayoutTable layout)
        {
            try
            {
                StringBuilder text = new StringBuilder();
                text.AppendLine(string.Format("{0}  size={1} align={2} padding={3} interrupted={4}  rows={5}",
                    layout.TypeName, layout.TotalSize, layout.TotalAlignment, layout.TotalPadding,
                    layout.Interrupted, layout.Rows.Count));

                foreach (LayoutEntry entry in layout.Rows)
                {
                    text.AppendLine(string.Format("  depth={0} kind={1,-18} offset={2,-6} size={3,-6} align={4,-4} bits={5} name={6}",
                        entry.Depth, entry.Kind,
                        entry.Offset.HasValue ? entry.Offset.Value.ToString() : "null",
                        entry.Size, entry.Alignment,
                        entry.BitWidth.HasValue ? entry.BitWidth.Value.ToString() : "-",
                        entry.Name));
                }

                File.WriteAllText(Path.Combine(Path.GetTempPath(), "struct-layout-last.txt"), text.ToString());
            }
            catch (Exception)
            {
                // diagnostics must never break the feature
            }
        }
#endif
    }
}
