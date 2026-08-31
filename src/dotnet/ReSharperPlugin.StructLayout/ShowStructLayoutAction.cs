using System;
using JetBrains.Application.Progress;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.ContextActions;
using JetBrains.ReSharper.Feature.Services.Cpp.Intentions;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Model;
using JetBrains.TextControl;
using JetBrains.Util;

namespace ReSharperPlugin.StructLayout
{
    // Alt+Enter entry point. It pushes the layout to the frontend so it opens the SAME tool window as the
    // editor context menu: it used to show a MessageBox text dump, which meant the two entry points
    // disagreed about what the plugin does, and the plugin description sends people here first.
    [ContextAction(
        GroupType = typeof(CppContextActions),
        Name = "Show struct layout",
        Description = "Shows the memory layout of the class, struct or union at the caret",
        Priority = 1)]
    public class ShowStructLayoutAction : CppContextActionBase
    {
        private ICppClassResolveEntity myClass;

        public ShowStructLayoutAction(CppContextActionDataProvider provider)
            : base(provider)
        {
        }

        public override string Text => "Show struct layout";

        public override bool IsAvailable(IUserDataHolder cache)
        {
            myClass = FindClassAtCaret();
            return myClass != null;
        }

        protected override Action<ITextControl> ExecutePsiTransaction(ISolution solution, IProgressIndicator progress)
        {
            LayoutTable layout = StructLayoutExtractor.TryExtract(myClass);
            if (layout == null)
                return null;

            return textControl =>
                solution.GetProtocolSolution().GetStructLayoutModel().ShowLayout.Fire(StructLayoutHost.ToProtocol(layout));
        }

        private ICppClassResolveEntity FindClassAtCaret()
        {
            for (ITreeNode node = Provider.SelectedElement; node != null; node = node.Parent)
            {
                IGenericSymbolNode symbolNode = node as IGenericSymbolNode;
                if (symbolNode == null)
                    continue;

                ICppClassResolveEntity clazz = symbolNode.TryGetResolveEntity() as ICppClassResolveEntity;
                if (clazz != null)
                    return clazz;
            }

            return null;
        }
    }
}
