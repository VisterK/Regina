package token

import lexer.Parser
import node.Node

open class Token(
    var symbol: String = "",
    var value: String = "",
    val position: Pair<Int, Int> = 0 to 0,
    val bindingPower: Int = 0, // precedence priority
    var nud: ((Token, Parser) -> Token)? = null, // null denotation: values, prefix operators
    var led: ((Token, Parser, Token) -> Token)? = null, // left denotation: infix and suffix operators
    var std: ((Token, Parser) -> Token)? = null, // statement denotation
    val children: MutableList<Token> = mutableListOf()
) {
    val left: Token
        get() = children[0]
    val right: Token
        get() = children[1]

    open fun toNode(filePath: String): Node =
        Node(
            symbol = symbol,
            value = value,
            children = children.map { it.toNode(filePath) }.toMutableList()
        )

    override fun toString(): String =
        "$symbol:$value"
}
