package utils

import lexer.ExpectedTypeException
import lexer.Parser
import lexer.PositionalException
import node.Identifier
import node.Node
import node.invocation.Invocation
import node.operator.Index
import node.statement.Assignment
import properties.*
import properties.primitive.*
import table.FileTable
import table.SymbolTable
import kotlin.reflect.KClass

object Utils {
    val NULL = Null()
    val FALSE = PInt(0)
    val TRUE = PInt(1)

    init {
        PList.initializeEmbeddedListFunctions()
        PString.initializeEmbeddedStringFunctions()
        PNumber.initializeEmbeddedNumberFunctions()
        PDouble.initializeEmbeddedDoubleFunctions()
        PDictionary.initializeDictionaryFunctions()

        PInt.initializeIntProperties()
        PDouble.initializeDoubleProperties()
        PList.initializeListProperties()
        PString.initializeStringProperties()
        PDictionary.initializeDictionaryProperties()
    }

    fun Boolean.toPInt(): PInt = if (this) TRUE else FALSE

    // fun Boolean.toInt(): Int = if (this) 1 else 0
    fun Boolean.toNonZeroInt(): Int = if (this) 1 else -1

    fun Any.toBoolean(node: Node, fileTable: FileTable): Boolean {
        try {
            return this.toString().toDouble() != 0.0
        } catch (e: NumberFormatException) {
            throw PositionalException("expected numeric value", fileTable.filePath, node)
        }
    }

    fun Any.toVariable(node: Node = Node()): Variable =
        if (this is Variable) this else Primitive.createPrimitive(this, node)

    fun Any.toProperty(node: Node = Node()): Property =
        if (this is Property) this else Primitive.createPrimitive(this, node)

    fun parseAssignment(assignment: String) =
        Parser(assignment, "@NoFile").statements().first().toNode("@NoFile") as Assignment

    fun parseOneNode(node: String) = Parser(node, "@NoFile").statements().first().toNode("@NoFile")

    /**
     * Prints AST with indentation to  show children.
     * **For debug**.
     */
    fun List<Node>.treeView(): String {
        val res = StringBuilder()
        for (t in this) {
            res.append(t.toTreeString(0))
            res.append('\n')
        }
        return res.toString()
    }

    private fun createIdent(node: Node, name: String) = Node(symbol = name, value = name, position = node.position)

    fun getIdent(node: Node, name: String, args: SymbolTable) = args.getIdentifier(createIdent(node, name))
    inline fun <reified T : Primitive> getPrimitive(args: SymbolTable, node: Node, name: String? = null): T {
        val primitive = if (name == null) node.evaluate(args) else getIdent(node, name, args)
        if (primitive !is T) {
            throw ExpectedTypeException(listOf(T::class), args.getFileTable().filePath, node, primitive)
        }
        return primitive
    }

    fun getPDictionary(args: SymbolTable, node: Node, name: String) = getPrimitive<PDictionary>(args, node, name)

    fun getPList(args: SymbolTable, node: Node, name: String) = getPrimitive<PList>(args, node, name)

    fun getPString(args: SymbolTable, node: Node, name: String) = getPrimitive<PString>(args, node, name)

    fun getPNumber(args: SymbolTable, node: Node, name: String? = null) = getPrimitive<PNumber>(args, node, name)

    fun getPInt(args: SymbolTable, node: Node, name: String) = getPrimitive<PInt>(args, node, name)

    fun getPDouble(args: SymbolTable, node: Node, name: String) = getPrimitive<PDouble>(args, node, name)

    fun getInstance(args: SymbolTable, node: Node, name: String) = getPrimitive<Type>(args, node, name)


    fun <T> List<T>.subList(start: Int): List<T> = this.subList(start, this.size)

    fun castToPList(list: Any): PList {
        return list as PList
    }

    fun castToPString(str: Any): PString {
        return str as PString
    }

    fun castToPNumber(num: Any): PNumber {
        return num as PNumber
    }

    fun mapToString(mapped: KClass<*>): String {
        return when (mapped) {
            RFunction::class -> "Function"
            PInt::class -> "Int"
            PDouble::class -> "Double"
            PNumber::class -> "Number"
            PString::class -> "String"
            PList::class -> "List"
            PDictionary::class -> "Dictionary"
            Identifier::class -> "Identifier"
            Invocation::class -> "Invocation"
            Index::class -> "Index"
            else -> mapped.toString().split(".").last()
        }
    }
}
