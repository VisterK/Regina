import node.Node
import node.variable.NodeDictionary
import token.Token

class TokenDictionary(node: Token) : Token(
    node.symbol,
    node.value,
    node.position,
    node.bindingPower,
    node.nud,
    node.led,
    node.std,
    node.children
) {

    override fun toNode(filePath: String): Node {
        return NodeDictionary(Node(symbol, value, position, children.map { it.toNode(filePath) }.toMutableList()))
    }
}
