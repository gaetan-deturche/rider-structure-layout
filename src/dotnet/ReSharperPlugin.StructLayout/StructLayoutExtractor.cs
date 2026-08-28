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
            return result;
        }
    }
}
