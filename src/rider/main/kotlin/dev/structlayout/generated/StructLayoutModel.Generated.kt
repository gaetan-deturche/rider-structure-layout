@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.jetbrains.rd.ide.model

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*
import com.jetbrains.rd.framework.impl.*

import com.jetbrains.rd.util.lifetime.*
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.string.*
import com.jetbrains.rd.util.*
import kotlin.time.Duration
import kotlin.reflect.KClass
import kotlin.jvm.JvmStatic



/**
 * #### Generated from [StructLayoutModel.kt:9]
 */
class StructLayoutModel private constructor(
    private val _getStructLayout: RdCall<LayoutRequest, LayoutResult?>,
    private val _showLayout: RdSignal<LayoutResult>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            val classLoader = javaClass.classLoader
            serializers.register(LazyCompanionMarshaller(RdId(-8592991782675632975), classLoader, "com.jetbrains.rd.ide.model.LayoutRowKind"))
            serializers.register(LazyCompanionMarshaller(RdId(569951940838269), classLoader, "com.jetbrains.rd.ide.model.LayoutRow"))
            serializers.register(LazyCompanionMarshaller(RdId(-8592991782966218062), classLoader, "com.jetbrains.rd.ide.model.LayoutRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-1467305804205918310), classLoader, "com.jetbrains.rd.ide.model.LayoutResult"))
        }
        
        
        
        
        private val __LayoutResultNullableSerializer = LayoutResult.nullable()
        
        const val serializationHash = -7389362328027469291L
        
    }
    override val serializersOwner: ISerializersOwner get() = StructLayoutModel
    override val serializationHash: Long get() = StructLayoutModel.serializationHash
    
    //fields
    
    /**
     * Computes the memory layout of the type at a caret position, using the C++ engine's own calculator.
     */
    val getStructLayout: IRdCall<LayoutRequest, LayoutResult?> get() = _getStructLayout
    
    /**
     * Backend -> frontend push, so the Alt+Enter context action (which already holds the resolved type) opens the same tool window as the editor menu instead of a text popup.
     */
    val showLayout: IAsyncSignal<LayoutResult> get() = _showLayout
    //methods
    //initializer
    init {
        _getStructLayout.async = true
        _showLayout.async = true
    }
    
    init {
        bindableChildren.add("getStructLayout" to _getStructLayout)
        bindableChildren.add("showLayout" to _showLayout)
    }
    
    //secondary constructor
    internal constructor(
    ) : this(
        RdCall<LayoutRequest, LayoutResult?>(LayoutRequest, __LayoutResultNullableSerializer),
        RdSignal<LayoutResult>(LayoutResult)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("StructLayoutModel (")
        printer.indent {
            print("getStructLayout = "); _getStructLayout.print(printer); println()
            print("showLayout = "); _showLayout.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): StructLayoutModel   {
        return StructLayoutModel(
            _getStructLayout.deepClonePolymorphic(),
            _showLayout.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val Solution.structLayoutModel get() = getOrCreateExtension("structLayoutModel", ::StructLayoutModel)



/**
 * #### Generated from [StructLayoutModel.kt:35]
 */
data class LayoutRequest (
    val filePath: String,
    val documentOffset: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(filePath)
        buffer.writeInt(documentOffset)
    }
    //companion
    
    companion object : IMarshaller<LayoutRequest> {
        override val _type: KClass<LayoutRequest> = LayoutRequest::class
        override val id: RdId get() = RdId(-8592991782966218062)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LayoutRequest  {
            val filePath = buffer.readString()
            val documentOffset = buffer.readInt()
            return LayoutRequest(filePath, documentOffset)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LayoutRequest)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as LayoutRequest
        
        if (filePath != other.filePath) return false
        if (documentOffset != other.documentOffset) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + documentOffset.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LayoutRequest (")
        printer.indent {
            print("filePath = "); filePath.print(printer); println()
            print("documentOffset = "); documentOffset.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [StructLayoutModel.kt:40]
 */
data class LayoutResult (
    val typeName: String,
    val totalSize: Int,
    val totalAlignment: Int,
    val totalPadding: Int,
    val interrupted: Boolean,
    val rows: List<LayoutRow>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(typeName)
        buffer.writeInt(totalSize)
        buffer.writeInt(totalAlignment)
        buffer.writeInt(totalPadding)
        buffer.writeBool(interrupted)
        buffer.writeList(rows) { v -> LayoutRow.write(ctx, buffer, v) }
    }
    //companion
    
    companion object : IMarshaller<LayoutResult> {
        override val _type: KClass<LayoutResult> = LayoutResult::class
        override val id: RdId get() = RdId(-1467305804205918310)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LayoutResult  {
            val typeName = buffer.readString()
            val totalSize = buffer.readInt()
            val totalAlignment = buffer.readInt()
            val totalPadding = buffer.readInt()
            val interrupted = buffer.readBool()
            val rows = buffer.readList { LayoutRow.read(ctx, buffer) }
            return LayoutResult(typeName, totalSize, totalAlignment, totalPadding, interrupted, rows)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LayoutResult)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as LayoutResult
        
        if (typeName != other.typeName) return false
        if (totalSize != other.totalSize) return false
        if (totalAlignment != other.totalAlignment) return false
        if (totalPadding != other.totalPadding) return false
        if (interrupted != other.interrupted) return false
        if (rows != other.rows) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + typeName.hashCode()
        __r = __r*31 + totalSize.hashCode()
        __r = __r*31 + totalAlignment.hashCode()
        __r = __r*31 + totalPadding.hashCode()
        __r = __r*31 + interrupted.hashCode()
        __r = __r*31 + rows.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LayoutResult (")
        printer.indent {
            print("typeName = "); typeName.print(printer); println()
            print("totalSize = "); totalSize.print(printer); println()
            print("totalAlignment = "); totalAlignment.print(printer); println()
            print("totalPadding = "); totalPadding.print(printer); println()
            print("interrupted = "); interrupted.print(printer); println()
            print("rows = "); rows.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [StructLayoutModel.kt:23]
 */
data class LayoutRow (
    val kind: LayoutRowKind,
    val name: String,
    val depth: Int,
    val offset: Int?,
    val size: Int,
    val alignment: Int,
    val bitWidth: Int?,
    val bitOffsetInByte: Int?
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeEnum(kind)
        buffer.writeString(name)
        buffer.writeInt(depth)
        buffer.writeNullable(offset) { buffer.writeInt(it) }
        buffer.writeInt(size)
        buffer.writeInt(alignment)
        buffer.writeNullable(bitWidth) { buffer.writeInt(it) }
        buffer.writeNullable(bitOffsetInByte) { buffer.writeInt(it) }
    }
    //companion
    
    companion object : IMarshaller<LayoutRow> {
        override val _type: KClass<LayoutRow> = LayoutRow::class
        override val id: RdId get() = RdId(569951940838269)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LayoutRow  {
            val kind = buffer.readEnum<LayoutRowKind>()
            val name = buffer.readString()
            val depth = buffer.readInt()
            val offset = buffer.readNullable { buffer.readInt() }
            val size = buffer.readInt()
            val alignment = buffer.readInt()
            val bitWidth = buffer.readNullable { buffer.readInt() }
            val bitOffsetInByte = buffer.readNullable { buffer.readInt() }
            return LayoutRow(kind, name, depth, offset, size, alignment, bitWidth, bitOffsetInByte)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LayoutRow)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as LayoutRow
        
        if (kind != other.kind) return false
        if (name != other.name) return false
        if (depth != other.depth) return false
        if (offset != other.offset) return false
        if (size != other.size) return false
        if (alignment != other.alignment) return false
        if (bitWidth != other.bitWidth) return false
        if (bitOffsetInByte != other.bitOffsetInByte) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + kind.hashCode()
        __r = __r*31 + name.hashCode()
        __r = __r*31 + depth.hashCode()
        __r = __r*31 + if (offset != null) offset.hashCode() else 0
        __r = __r*31 + size.hashCode()
        __r = __r*31 + alignment.hashCode()
        __r = __r*31 + if (bitWidth != null) bitWidth.hashCode() else 0
        __r = __r*31 + if (bitOffsetInByte != null) bitOffsetInByte.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LayoutRow (")
        printer.indent {
            print("kind = "); kind.print(printer); println()
            print("name = "); name.print(printer); println()
            print("depth = "); depth.print(printer); println()
            print("offset = "); offset.print(printer); println()
            print("size = "); size.print(printer); println()
            print("alignment = "); alignment.print(printer); println()
            print("bitWidth = "); bitWidth.print(printer); println()
            print("bitOffsetInByte = "); bitOffsetInByte.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [StructLayoutModel.kt:12]
 */
enum class LayoutRowKind {
    Base, 
    VirtualBase, 
    Field, 
    BitField, 
    AnonymousAggregate, 
    VfPtr, 
    VbPtr, 
    Padding;
    
    companion object : IMarshaller<LayoutRowKind> {
        val marshaller = FrameworkMarshallers.enum<LayoutRowKind>()
        
        
        override val _type: KClass<LayoutRowKind> = LayoutRowKind::class
        override val id: RdId get() = RdId(-8592991782675632975)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LayoutRowKind {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LayoutRowKind)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}
