using System.Text;

namespace ReSharperPlugin.StructLayout
{
    public static class StructLayoutFormatter
    {
        public static string Format(LayoutTable layout)
        {
            StringBuilder text = new StringBuilder();

            text.AppendLine(string.Format("{0}  -  size {1}, align {2}, padding {3} bytes",
                layout.TypeName, layout.TotalSize, layout.TotalAlignment, layout.TotalPadding));

            if (layout.Interrupted)
                text.AppendLine("WARNING: the layout walk was interrupted, rows below are incomplete.");

            text.AppendLine();
            text.AppendLine("offset  size  align  kind        member");

            foreach (LayoutEntry row in layout.Rows)
            {
                string offset = row.Offset.HasValue ? row.Offset.Value.ToString() : "-";
                if (row.BitWidth.HasValue)
                    offset = offset + ":" + row.BitOffsetInByte;

                string size = row.BitWidth.HasValue ? row.BitWidth.Value + "b" : row.Size.ToString();
                string align = row.Alignment > 0 ? row.Alignment.ToString() : "-";

                text.AppendLine(string.Format("{0,6}  {1,4}  {2,5}  {3,-10}  {4}{5}",
                    offset, size, align, KindLabel(row.Kind), new string(' ', row.Depth * 2), row.Name));
            }

            return text.ToString();
        }

        private static string KindLabel(LayoutEntryKind kind)
        {
            switch (kind)
            {
                case LayoutEntryKind.Base: return "base";
                case LayoutEntryKind.VirtualBase: return "vbase";
                case LayoutEntryKind.Field: return "field";
                case LayoutEntryKind.BitField: return "bitfield";
                case LayoutEntryKind.AnonymousAggregate: return "anon";
                case LayoutEntryKind.VfPtr: return "vfptr";
                case LayoutEntryKind.VbPtr: return "vbptr";
                case LayoutEntryKind.Padding: return "PADDING";
                default: return "?";
            }
        }
    }
}
