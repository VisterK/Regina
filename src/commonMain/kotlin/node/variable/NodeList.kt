package node.variable

import node.Node
import table.SymbolTable
import utils.Utils.toVariable

class NodeList(node: Node) : Node(
    node.symbol,
    node.value,
    node.position,
    node.children
) {

    override fun evaluate(symbolTable: SymbolTable): Any {
        return children.map { it.evaluate(symbolTable).toVariable(it) }.toMutableList()
    }
}
