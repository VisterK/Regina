package node

import Optional
import References
import Tuple4
import lexer.NotFoundException
import lexer.PositionalException
import node.invocation.Call
import node.invocation.Constructor
import node.invocation.Invocation
import node.invocation.ResolvingMode
import node.operator.Index
import node.operator.NodeTernary
import node.statement.Assignment
import properties.*
import properties.primitive.PDictionary
import properties.primitive.PNumber
import properties.primitive.Primitive
import table.FileTable
import table.SymbolTable
import utils.Utils.NULL
import utils.Utils.toProperty
import utils.Utils.toVariable

/**
 * Format: `a.b.c.d` - `a`, `b`, `c` and `d` are children of link
 *
 * Represents tokens separated by dots. These tokens are link children. In Regina, links have the following purposes:
 * 1. A property of class, object or a primitive: `Point.x` or `Segment.parent.iter`
 * 2. A function of class, object or a primitive: `Double.round()`
 * 3. Reference to a class, object or a function from another file: `importedFile.className`
 * 4.
 * That's why links are complex, they should be carefully evaluated and assigned.
 *
 * Link invariants:
 * * First token in link might be anything
 * * n-th token is [Linkable]: [Identifier], [Invocation] or [Index]
 */
open class Link(
    symbol: String,
    value: String,
    position: Pair<Int, Int>,
    children: List<Node> = listOf(),
    val nullable: List<Int>
) : Node(symbol, value, position), Assignable {

    init {
        if (children.isNotEmpty()) {
            this.children.clear()
            this.children.addAll(children)
        }
    }

    override fun evaluate(symbolTable: SymbolTable): Any {
        var table = symbolTable.copy()
        var (index, currentVariable) = checkFirstVariable(0, symbolTable.copy(), symbolTable)
        if (currentVariable == null) {
            throw NotFoundException(left, symbolTable.getFileTable().filePath)
        }
        table = table.changeVariable(currentVariable)
        index++
        while (index < children.size) {
            val isResolved =
                checkNextVariable(index = index, table = table, initialTable = symbolTable, currentVariable!!)
            if (isResolved.value is NullValue) {
                return NULL
            }
            if (isResolved.value !is Variable) {
                throw PositionalException("Link not resolved", symbolTable.getFileTable().filePath, children[index])
            }
            currentVariable = isResolved.value
            table = table.changeVariable(currentVariable)
            index++
        }
        return if (currentVariable!! is Primitive && currentVariable !is PNumber) {
            (currentVariable as Primitive).getPValue()
        } else currentVariable
    }

    private fun checkNextFunction() {

    }

    private fun checkNextVariable(
        index: Int,
        table: SymbolTable,
        initialTable: SymbolTable,
        variable: Variable,
        findingUnresolved: Boolean = false
    ): Optional {
        when (children[index]) {
            is Call -> return checkNextCall(findingUnresolved, variable, index, table, initialTable)
            is Identifier -> {
                if (variable is Type && variable !is Object) {
                    val assignment = variable.getLinkedAssignment(this, index)
                    if (assignment != null)
                        return Optional(assignment)
                }
                val property = variable.getPropertyOrNull(children[index].value)
                if (property == null && nullable.contains(index))
                    return Optional(NullValue())
                return Optional(property)
            }
            is Index -> {
                val indexToken = (children[index] as Index).getDeepestLeft()
                if (indexToken is Call) return checkNextCall(findingUnresolved, variable, index, table, initialTable)
                val property = variable.getPropertyOrNull(indexToken.value)
                if (property == null && nullable.contains(index))
                    return Optional(NullValue())
                property
                    ?: if (variable is Type) (return Optional(variable.getAssignment(indexToken)))
                    else if (nullable.contains(index)) return Optional(NullValue()) else throw PositionalException(
                        "Property not found",
                        initialTable.getFileTable().filePath,
                        indexToken
                    )
                return Optional(
                    (children[index] as Index)
                        .evaluateIndexWithDeepestLeftProperty(property, table).toVariable(children[index])
                )
            }
            else -> throw PositionalException("Unexpected token", initialTable.getFileTable().filePath, children[index])
        }
    }

    private fun checkNextCall(
        findingUnresolved: Boolean,
        variable: Variable,
        index: Int,
        table: SymbolTable,
        initialTable: SymbolTable
    ): Optional {
        if (findingUnresolved)
            return Optional(NullValue())
        val function = variable.getFunctionOrNull((children[index] as Call))
        if (function == null && nullable.contains(index))
            return Optional(NullValue())
        if (function == null) {
            throw PositionalException(
                "Variable does not contain function",
                initialTable.getFileTable().filePath,
                children[index]
            )
        }
        return Optional(
            resolveFunctionCall(
                index = index,
                table = table,
                initialTable = initialTable,
                function = function
            )
        )
    }

    /**
     * Get first variable and child index of it
     */
    private fun checkFirstVariable(
        index: Int,
        table: SymbolTable,
        initialTable: SymbolTable,
        canBeFile: Boolean = true,
        findingUnresolved: Boolean = false
    ): Pair<Int, Variable?> {
        when (children[index]) {
            is Identifier -> {
                val identifier = table.getIdentifierOrNull(children[index])
                return if (identifier == null) {
                    if (canBeFile) {
                        val nextTable = addFile(table) ?: return Pair(0, null)
                        return checkFirstVariable(
                            index + 1,
                            table = nextTable,
                            initialTable = initialTable,
                            canBeFile = false,
                            findingUnresolved = findingUnresolved
                        )
                    } else Pair(index, null)
                } else Pair(index, identifier)
            }
            is Call -> if (findingUnresolved) {
                return Pair(index, NullValue())
            } else return Pair(
                index,
                resolveFunctionCall(
                    index,
                    table,
                    initialTable,
                    table.getFunction(children[index])
                )
            )
            is Constructor -> {
                if (findingUnresolved) {
                    return Pair(index, NullValue())
                }
                val type = table.getType(children[index].left).copyRoot()
                return Pair(
                    index,
                    (children[index] as Constructor).evaluateType(type, initialTable).toVariable(children[index])
                )
            }
            else -> {
                if (!canBeFile)
                    throw PositionalException("Unexpected token", initialTable.getFileTable().filePath, children[index])
                return Pair(index, children[index].evaluate(table).toVariable(children[index]))
            }
        }
    }

    /**
     * Update fileTable
     */
    private fun addFile(table: SymbolTable): SymbolTable? {
        val fileTable = table.getImportOrNull(left.value) ?: return null
        return table.changeFile(fileTable)
    }

    /**
     * Return function result and parent of function
     * here symbol table is ignored. Only value with same fileName
     */
    private fun resolveFunctionCall(
        index: Int,
        table: SymbolTable,
        initialTable: SymbolTable,
        function: RFunction
    ): Variable {
        var type = table.getCurrentType()
        if (type !is Type) {
            type = null
        }
        val tableForEvaluation = SymbolTable(
            fileTable = if (type is Type) type.fileTable
            else table.getFileTable(),
            variableTable = table.getCurrentType(),
            resolvingType = ResolvingMode.FUNCTION
        )
        (children[index] as Call).argumentsToParameters(function, initialTable, tableForEvaluation)
        val functionResult = (children[index] as Call).evaluateFunction(tableForEvaluation, function)
        return functionResult.toVariable(children[index])
    }

    override fun assign(assignment: Assignment, parent: Type?, symbolTable: SymbolTable, value: Any) {
        val (_, currentParent, _, index) = safeEvaluate(
            parent ?: Type(
                "@Fictive",
                mutableSetOf(),
                symbolTable.getImport(Node(value = "Global")),
                index = -1
            ),
            symbolTable
        )
        if (currentParent !is Type || index != children.lastIndex)
            throw PositionalException("Link not resolved", symbolTable.getFileTable().filePath, children.last())
        if (children.last() is Index) {
            val tokenIndex = (children.last() as Index).getDeepestLeft()
            if (tokenIndex is Call) throw PositionalException(
                "Call is prohibited on the left of the assignment",
                symbolTable.getFileTable().filePath,
                tokenIndex
            )
            (children.last() as Index).assignWithIndexable(
                currentParent.getProperty(tokenIndex, symbolTable.getFileTable()),
                symbolTable.changeVariable(currentParent),
                symbolTable,
                assignment,
                value.toProperty(assignment.right)
            )
        } else currentParent.setProperty(children.last().value, value.toProperty(assignment.right))
    }

    /**
     * @return currentVariable, its parent, assignment in parent if currentVariable is null, index of currentVariable
     */
    private fun safeEvaluate(parent: Type, symbolTable: SymbolTable): Tuple4<Variable?, Variable?, Assignment?, Int> {
        var currentParent: Variable? = null
        var table = symbolTable.copy()
        val initialTable = symbolTable.changeVariable(parent)
        var (index, currentVariable) = checkFirstVariable(0, table, initialTable, findingUnresolved = true)
        if (currentVariable is NullValue) {
            return Tuple4(NullValue(), currentVariable, null, index)
        }
        if (currentVariable == null) {
            return Tuple4(
                null,
                parent,
                parent.getAssignment(left)
                    ?: throw PositionalException("Assignment not found", symbolTable.getFileTable().filePath, left),
                index
            )
        }
        table = table.changeVariable(currentVariable)
        index++
        while (index < children.size) {
            val res = checkNextVariable(
                index,
                table = table,
                initialTable = initialTable,
                currentVariable!!,
                findingUnresolved = true
            )
            if (res.value is NullValue) {
                return Tuple4(NullValue(), currentVariable, null, index)
            }
            if (res.isGood && res.value is Assignment) {
                return Tuple4(null, currentVariable, res.value, index)
            }
            if (res.value !is Variable) {
                return Tuple4(null, currentVariable, null, index)
            }
            currentParent = currentVariable
            table = table.changeVariable(res.value)
            currentVariable = res.value
            index++
        }
        return Tuple4(currentVariable, currentParent, null, --index)
    }

    override fun getFirstUnassigned(parent: Type, symbolTable: SymbolTable): Pair<Type, Assignment?> {
        val (type, assignment) = getFirstUnassignedOrNull(parent, symbolTable)
        if (type == null) {
            return Pair(parent, assignment)
        }
        return Pair(type, assignment)
    }

    /**
     * @return assignment of unresolved or its parent. Both can be null simultaneously if variable is assigned
     */
    fun getFirstUnassignedOrNull(
        parent: Type,
        symbolTable: SymbolTable,
        forLValue: Boolean = false
    ): Pair<Type?, Assignment?> {
        val (currentVariable, currentParent, assignment, index) = safeEvaluate(parent, symbolTable)
        if (currentVariable is NullValue) {
            return Pair(null, null)
        }
        if (currentParent != null && currentParent !is Type) {
            return Pair(null, null)
        }
        if (forLValue && index == children.lastIndex) {
            return Pair(parent, null)
        }
        if (currentVariable == null && assignment == null && index < children.size) {
            return Pair(
                parent,
                parent.getLinkedAssignment(this, 0)
                    ?: throw PositionalException("Assignment not found", symbolTable.getFileTable().filePath)
            )
        }
        return Pair(currentParent as Type?, assignment)
    }

    override fun findUnassigned(symbolTable: SymbolTable, parent: Type): Pair<Type, Assignment>? {
        if (left is NodeTernary) {
            val found = left.findUnassigned(symbolTable, parent)
            if (found != null) {
                return found
            }
        }
        val (type, assignment) = getFirstUnassignedOrNull(parent, symbolTable)
        if (type == null || assignment == null) {
            return null
        }
        return Pair(type, assignment)
    }

    override fun getPropertyName(): Node = (children.last() as Assignable).getPropertyName()

    class NullValue : Variable() {
        override fun getPropertyOrNull(name: String): Property? {
            TODO("Not yet implemented")
        }

        override fun getProperty(node: Node, fileTable: FileTable): Property {
            TODO("Not yet implemented")
        }

        override fun getFunctionOrNull(node: Node): RFunction? {
            TODO("Not yet implemented")
        }

        override fun getFunction(node: Node, fileTable: FileTable): RFunction {
            TODO("Not yet implemented")
        }

        override fun getProperties(): PDictionary {
            TODO("Not yet implemented")
        }

        override fun toDebugClass(references: References, copying: Boolean): Pair<String, Any> {
            TODO("Not yet implemented")
        }
    }
}
