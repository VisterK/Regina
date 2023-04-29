package node.variable

import node.Node
import table.SymbolTable
import utils.Utils.toVariable

class NodeDictionary(node: Node) : Node(
    node.symbol,
    node.value,
    node.position,
    node.children
) {

    override fun evaluate(symbolTable: SymbolTable): Any {
        return children.associate {
            Pair(
                it.left.evaluate(symbolTable).toVariable(it.left),
                it.right.evaluate(symbolTable).toVariable(it.right)
            )
        }.toMutableMap()
    }
}
