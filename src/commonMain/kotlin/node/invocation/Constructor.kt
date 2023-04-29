package node.invocation

import lexer.PositionalException
import node.Identifier
import node.Node
import node.statement.Assignment
import properties.Type
import properties.Type.Companion.resolveTree
import table.SymbolTable
import utils.Utils.parseOneNode
import utils.Utils.toProperty

class Constructor(
    node: Node
) : Invocation(
    node.symbol,
    node.value,
    node.position,
    node.children
) {
    init {
        this.children.clear()
        this.children.addAll(node.children)
    }

    override fun evaluate(symbolTable: SymbolTable): Any {
        val type = if (left is Identifier) symbolTable.getType(left)
        else left.evaluate(symbolTable)
        if (type !is Type) {
            throw PositionalException("Expected type", symbolTable.getFileTable().filePath, left)
        }
        if (type.index == 0) {
            return evaluateType(type.copyRoot(), symbolTable)
        }
        return evaluateType(type, symbolTable)
    }

    fun evaluateType(type: Type, symbolTable: SymbolTable): Any {
        resolveArguments(type, symbolTable)
        val beforeNode = Call(parseOneNode("before()") as Invocation)
        val beforeResolving = type.getFunctionOrNull(beforeNode)
        if (beforeResolving != null)
            beforeNode.evaluateFunction(symbolTable, beforeResolving)
        if (type.assignments.isEmpty())
            type.callAfter(symbolTable)
        return if (symbolTable.resolvingType == ResolvingMode.TYPE) type else resolveTree(
            type,
            symbolTable.changeVariable(type).changeScope()
        )
    }

    private fun resolveArguments(type: Type, symbolTable: SymbolTable) {
        val resolvingMode = symbolTable.resolvingType
        symbolTable.resolvingType = ResolvingMode.FUNCTION
        type.setProperty("this", type)
        for (arg in children.subList(1, children.size)) {
            require(arg is Assignment) {
                throw PositionalException("Expected assignment", symbolTable.getFileTable().filePath, arg)
            }
            require(arg.left is Identifier) {
                throw PositionalException("Expected property name", symbolTable.getFileTable().filePath, arg)
            }
            val leftValue = arg.left.value
            val property = arg.right.evaluate(symbolTable).toProperty(arg.left)
            type.setProperty(leftValue, property)
            val prop = type.getPropertyOrNull(leftValue)
            if (prop is Type) {
                prop.setProperty("parent", type)
            }
            type.removeAssignment(arg.left)
        }
        symbolTable.resolvingType = resolvingMode
    }

}
