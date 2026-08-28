using System.Collections.Generic;
using System.Linq;
using JetBrains.Application;
using JetBrains.Application.Parts;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Files;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Model;
using JetBrains.Util;

namespace ReSharperPlugin.StructLayout
{
    // Answers the frontend's getStructLayout call. The layout comes from the same extractor the
    // Alt+Enter action uses, so the two entry points can never disagree.
    //
    // Naming: LayoutTable/LayoutEntry/LayoutEntryKind are ours; LayoutResult/LayoutRow/LayoutRowKind
    // are the rdgen-generated protocol types in JetBrains.Rider.Model.
    // Must be eager (Container*, not Demand*): the constructor is what registers the protocol
    // handler. With on-demand instantiation nothing ever creates this component, so the frontend's
    // call silently never completes.
    [SolutionComponent(Instantiation.ContainerAsyncAnyThreadSafe)]
    public class StructLayoutHost
    {
        private readonly ISolution mySolution;

        public StructLayoutHost(Lifetime lifetime, ISolution solution)
        {
            mySolution = solution;
            solution.GetProtocolSolution().GetStructLayoutModel().GetStructLayout.Set(Handle);
        }

        private RdTask<LayoutResult> Handle(Lifetime requestLifetime, LayoutRequest request)
        {
            using (ReadLockCookie.Create())
            {
                ICppClassResolveEntity clazz = FindClass(request);
                LayoutResult result = clazz == null ? null : ToProtocol(StructLayoutExtractor.TryExtract(clazz));
                return RdTask<LayoutResult>.Successful(result);
            }
        }

        private ICppClassResolveEntity FindClass(LayoutRequest request)
        {
            VirtualFileSystemPath path = VirtualFileSystemPath.Parse(request.FilePath, InteractionContext.SolutionContext);

            IProjectFile projectFile = mySolution.FindProjectItemsByLocation(path).OfType<IProjectFile>().FirstOrDefault();
            if (projectFile == null)
                return null;

            IPsiSourceFile sourceFile = projectFile.ToSourceFile();
            if (sourceFile == null)
                return null;

            CppFile cppFile = sourceFile.GetPrimaryPsiFile() as CppFile;
            if (cppFile == null)
                return null;

            ITreeNode node = cppFile.FindNodeAt(new TreeTextRange(new TreeOffset(request.DocumentOffset)));
            for (; node != null; node = node.Parent)
            {
                if (node is IGenericSymbolNode symbolNode &&
                    symbolNode.TryGetResolveEntity() is ICppClassResolveEntity clazz)
                {
                    return clazz;
                }
            }

            return null;
        }

        private static LayoutResult ToProtocol(LayoutTable layout)
        {
            if (layout == null)
                return null;

            List<LayoutRow> rows = new List<LayoutRow>(layout.Rows.Count);
            foreach (LayoutEntry entry in layout.Rows)
            {
                rows.Add(new LayoutRow(
                    ToProtocol(entry.Kind),
                    entry.Name,
                    entry.Depth,
                    entry.Offset,
                    entry.Size,
                    entry.Alignment,
                    entry.BitWidth,
                    entry.BitOffsetInByte));
            }

            return new LayoutResult(
                layout.TypeName,
                layout.TotalSize,
                layout.TotalAlignment,
                layout.TotalPadding,
                layout.Interrupted,
                rows);
        }

        private static LayoutRowKind ToProtocol(LayoutEntryKind kind)
        {
            switch (kind)
            {
                case LayoutEntryKind.Base: return LayoutRowKind.Base;
                case LayoutEntryKind.VirtualBase: return LayoutRowKind.VirtualBase;
                case LayoutEntryKind.Field: return LayoutRowKind.Field;
                case LayoutEntryKind.BitField: return LayoutRowKind.BitField;
                case LayoutEntryKind.AnonymousAggregate: return LayoutRowKind.AnonymousAggregate;
                case LayoutEntryKind.VfPtr: return LayoutRowKind.VfPtr;
                case LayoutEntryKind.VbPtr: return LayoutRowKind.VbPtr;
                default: return LayoutRowKind.Padding;
            }
        }
    }
}
