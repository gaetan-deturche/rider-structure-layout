using System.Collections.Generic;

namespace ReSharperPlugin.StructLayout
{
    public enum LayoutEntryKind
    {
        Base,
        VirtualBase,
        Field,
        BitField,
        AnonymousAggregate,
        VfPtr,
        VbPtr,
        Padding
    }

    public sealed class LayoutEntry
    {
        public LayoutEntryKind Kind;
        public string Name;
        public int Depth;

        /// Byte offset from the start of the outermost object; null inside a union.
        public int? Offset;
        public int Size;
        public int Alignment;

        public int? BitWidth;
        public int? BitOffsetInByte;
    }

    public sealed class LayoutTable
    {
        public string TypeName;
        public int TotalSize;
        public int TotalAlignment;
        public int TotalPadding;
        public bool Interrupted;
        public readonly List<LayoutEntry> Rows = new List<LayoutEntry>();
    }
}
