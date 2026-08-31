using System.Collections.Generic;
using JetBrains.ReSharper.Psi.Cpp.Resolve;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Types;
using JetBrains.ReSharper.Psi.Cpp.Util;

namespace ReSharperPlugin.StructLayout
{
    // Walks one class through the engine's own layout calculator. Nested aggregates are handled by
    // re-entering BuildLayout with a child collector, the way the engine's padding builder recurses
    // into anonymous classes: the calculator does not descend into bases or members by itself.
    public sealed class StructLayoutCollector : ICppLayoutBuilder
    {
        private readonly CppTypeContext myTypeContext;
        private readonly List<LayoutEntry> myRows;
        private readonly int myBaseOffset;
        private readonly int myDepth;
        private bool myInterrupted;
        private int myPadding;

        // OnPadding only reports a byte count, so the gap's position is tracked as the walk proceeds.
        private int myLastEnd;
        private bool myEmptyNested;

        // Unreal types nest deeply through value members; without caps one walk can produce thousands
        // of rows and the picture becomes useless anyway.
        private const int MaxMemberDepth = 4;
        private const int MaxRows = 2000;

        public StructLayoutCollector(in CppTypeContext typeContext, List<LayoutEntry> rows, int baseOffset, int depth)
        {
            myTypeContext = typeContext;
            myRows = rows;
            myBaseOffset = baseOffset;
            myDepth = depth;
            myLastEnd = baseOffset;
        }

        public bool WasInterrupted => myInterrupted;

        public bool HadEmptyNested => myEmptyNested;

        public int TotalPadding => myPadding;

        public ICppClassLayoutBuilder BeginClass()
        {
            return new ClassBuilder(this);
        }

        public ICppUnionLayoutBuilder BeginUnion()
        {
            return new UnionBuilder(this);
        }

        public void OnPadding(int padding)
        {
            if (padding <= 0)
                return;

            myPadding += padding;
            myRows.Add(new LayoutEntry
            {
                Kind = LayoutEntryKind.Padding,
                Name = "<padding>",
                Depth = myDepth,
                Offset = myLastEnd,
                Size = padding,
                Alignment = 1
            });
            myLastEnd += padding;
        }

        public CppSizeAndAlignment Walk(ICppClassResolveEntity clazz)
        {
            return clazz.BuildLayout(in myTypeContext, this, true);
        }

        // A nested class - especially a base declared in another header, which is the norm in Unreal - is
        // not necessarily complete in the OUTER class's view pos. Resolving it in its own view pos first
        // is what the top-level extractor does; reusing the parent's context makes such a base walk
        // report nothing, so every inherited member silently disappears.
        private void Recurse(ICppClassResolveEntity nested, int absoluteOffset)
        {
            CppTypeContext context = myTypeContext;

            CppFileResolveEntitiesCache nestedCache = nested.GetRootFileResolveCache();
            if (nestedCache != null)
            {
                CppViewPos nestedViewPos = CppViewPos.Full(nestedCache.GlobalNamespace);
                if (nested.IsClassComplete(in nestedViewPos))
                    context = new CppTypeContext(nestedViewPos, nestedCache.TypeFactory);
            }

            int before = myRows.Count;
            StructLayoutCollector child = new StructLayoutCollector(in context, myRows, absoluteOffset, myDepth + 1);
            child.Walk(nested);

            // a nested walk that reports nothing means its members are missing from the picture
            if (myRows.Count == before)
                myEmptyNested = true;

            myPadding += child.myPadding;
            if (child.myInterrupted)
                myInterrupted = true;
        }

        // A member whose TYPE is a class/struct gets its own members walked, so the view can open an
        // opaque block (FName, FVector, ...) instead of stopping at it. Bases and anonymous aggregates
        // hand over an entity directly; a declarator only gives a type, hence the extra hop.
        private void TryRecurseIntoMemberType(ICppDeclaratorResolveEntity declarator, int absoluteOffset)
        {
            if (declarator == null || myDepth >= MaxMemberDepth || myRows.Count >= MaxRows)
                return;

            CppQualType memberType = declarator.GetCppType();

            // an array of structs lays out as its element type repeated; show the element's members
            CppQualType valueType = CppTypesUtil.IsArray(memberType)
                ? CppTypesUtil.GetArrayInnerTypeRecursively(memberType)
                : memberType;

            if (!CppTypesUtil.IsResolvedClassType(valueType))
                return;

            ICppClassResolveEntity clazz = CppTypesUtil.GetResolvedClassTypeEntity(valueType);
            if (clazz == null)
                return;

            Recurse(clazz, absoluteOffset);
        }

        private static string NameOf(ICppResolveEntity entity)
        {
            if (entity == null)
                return "<unnamed>";

            string name = entity.Name.ToString();
            return string.IsNullOrEmpty(name) ? "<unnamed>" : name;
        }

        private sealed class ClassBuilder : ICppClassLayoutBuilder
        {
            private readonly StructLayoutCollector myOwner;

            public ClassBuilder(StructLayoutCollector owner)
            {
                myOwner = owner;
            }

            public void OnEntry(ICppClassLayoutBuilder.Entry entry, ICppClassLayoutBuilder.Layout layout)
            {
                int absolute = myOwner.myBaseOffset + layout.Offset;

                switch (entry.Kind)
                {
                    case ICppClassLayoutBuilder.EntryKind.Vfptr:
                        Emit(LayoutEntryKind.VfPtr, "__vfptr", absolute, layout);
                        return;

                    case ICppClassLayoutBuilder.EntryKind.Vbptr:
                        Emit(LayoutEntryKind.VbPtr, "__vbptr", absolute, layout);
                        return;

                    case ICppClassLayoutBuilder.EntryKind.Base:
                    case ICppClassLayoutBuilder.EntryKind.VirtualBase:
                    {
                        LayoutEntryKind kind = entry.Kind == ICppClassLayoutBuilder.EntryKind.Base
                            ? LayoutEntryKind.Base
                            : LayoutEntryKind.VirtualBase;

                        Emit(kind, NameOf(entry.Entity), absolute, layout);
                        myOwner.Recurse((ICppClassResolveEntity)entry.Entity, absolute);
                        return;
                    }

                    case ICppClassLayoutBuilder.EntryKind.AnonClass:
                        Emit(LayoutEntryKind.AnonymousAggregate, NameOf(entry.Entity), absolute, layout);
                        myOwner.Recurse((ICppClassResolveEntity)entry.Entity, absolute);
                        return;

                    case ICppClassLayoutBuilder.EntryKind.Declarator:
                        Emit(LayoutEntryKind.Field, NameOf(entry.Entity), absolute, layout);
                        myOwner.TryRecurseIntoMemberType(entry.Entity as ICppDeclaratorResolveEntity, absolute);
                        return;
                }
            }

            public void OnBitField(ICppGroupedVariableDeclaratorResolveEntity bitfield,
                                   ICppClassLayoutBuilder.BitFieldDescription description)
            {
                int totalBits = description.OffsetInBytesFromStart * 8 + description.OffsetInBitsInsideBitField;

                myOwner.myRows.Add(new LayoutEntry
                {
                    Kind = LayoutEntryKind.BitField,
                    Name = NameOf(bitfield),
                    Depth = myOwner.myDepth,
                    Offset = myOwner.myBaseOffset + totalBits / 8,
                    BitOffsetInByte = totalBits % 8,
                    BitWidth = description.Width
                });
            }

            private void Emit(LayoutEntryKind kind, string name, int absolute, ICppClassLayoutBuilder.Layout layout)
            {
                myOwner.myRows.Add(new LayoutEntry
                {
                    Kind = kind,
                    Name = name,
                    Depth = myOwner.myDepth,
                    Offset = absolute,
                    Size = layout.SizeAlignment.Size,
                    Alignment = layout.SizeAlignment.Alignment
                });

                myOwner.myLastEnd = absolute + layout.SizeAlignment.Size;
            }
        }

        // Union members all sit at offset 0, so the engine hands out no per-entry offset here.
        private sealed class UnionBuilder : ICppUnionLayoutBuilder
        {
            private readonly StructLayoutCollector myOwner;

            public UnionBuilder(StructLayoutCollector owner)
            {
                myOwner = owner;
            }

            public void OnEntry(ICppUnionLayoutBuilder.Entry entry, ICppUnionLayoutBuilder.Layout layout)
            {
                LayoutEntryKind kind = entry.Kind == ICppUnionLayoutBuilder.EntryKind.AnonClass
                    ? LayoutEntryKind.AnonymousAggregate
                    : LayoutEntryKind.Field;

                myOwner.myRows.Add(new LayoutEntry
                {
                    Kind = kind,
                    Name = NameOf(entry.Entity),
                    Depth = myOwner.myDepth,
                    Offset = myOwner.myBaseOffset,
                    Size = layout.SizeAlignment.Size,
                    Alignment = layout.SizeAlignment.Alignment
                });

                if (entry.Kind == ICppUnionLayoutBuilder.EntryKind.AnonClass)
                    myOwner.Recurse((ICppClassResolveEntity)entry.Entity, myOwner.myBaseOffset);
            }

            public void OnBitField(ICppUnionLayoutBuilder.Entry entry, int width)
            {
                myOwner.myRows.Add(new LayoutEntry
                {
                    Kind = LayoutEntryKind.BitField,
                    Name = NameOf(entry.Entity),
                    Depth = myOwner.myDepth,
                    Offset = myOwner.myBaseOffset,
                    BitOffsetInByte = 0,
                    BitWidth = width
                });
            }
        }
    }
}
