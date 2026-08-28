package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.bool
import com.jetbrains.rd.generator.nova.PredefinedType.int
import com.jetbrains.rd.generator.nova.PredefinedType.string
import com.jetbrains.rider.model.nova.ide.SolutionModel

@Suppress("unused")
object StructLayoutModel : Ext(SolutionModel.Solution) {

    private val LayoutRowKind = enum {
        +"Base"
        +"VirtualBase"
        +"Field"
        +"BitField"
        +"AnonymousAggregate"
        +"VfPtr"
        +"VbPtr"
        +"Padding"
    }

    private val LayoutRow = structdef {
        field("kind", LayoutRowKind)
        field("name", string)
        field("depth", int)
        // absent inside a union, where every member sits at offset 0
        field("offset", int.nullable)
        field("size", int)
        field("alignment", int)
        field("bitWidth", int.nullable)
        field("bitOffsetInByte", int.nullable)
    }

    private val LayoutRequest = structdef {
        field("filePath", string)
        field("documentOffset", int)
    }

    private val LayoutResult = structdef {
        field("typeName", string)
        field("totalSize", int)
        field("totalAlignment", int)
        field("totalPadding", int)
        // the engine can bail mid-walk, in which case rows are partial
        field("interrupted", bool)
        field("rows", immutableList(LayoutRow))
    }

    init {
        call("getStructLayout", LayoutRequest, LayoutResult.nullable)
            .async
            .documentation = "Computes the memory layout of the type at a caret position, using the C++ engine's own calculator."
    }
}
