using System;
using JetBrains.Application.Progress;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.ContextActions;
using JetBrains.ReSharper.Feature.Services.Cpp.Intentions;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.TextControl;
using JetBrains.Util;

namespace ReSharperPlugin.StructLayout
{
    [ContextAction(
        GroupType = typeof(CppContextActions),
        Name = "Show struct layout",
        Description = "Dumps the memory layout of the class, struct or union at the caret",
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
            string text = layout == null
                ? "No layout available for this type (incomplete, dependent or template-parented)."
                : StructLayoutFormatter.Format(layout);

            return textControl => MessageBox.ShowInfo(text, "Struct layout");
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
