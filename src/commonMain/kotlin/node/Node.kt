/**
 * AST building algorithm was taken and rewritten from:
 * https://www.cristiandima.com/top-down-operator-precedence-parsing-in-go
 */
package node

import Optional
import lexer.PositionalException
import node.statement.Assignment
import properties.Type
import table.SymbolTable

/**
 * Tokens are building blocks of evaluated code.
 * * Each token represents some code element: variable identifier, operator, code block etc.
 * * Tokens are nodes of [abstract syntax tree](https://en.wikipedia.org/wiki/Abstract_syntax_tree)
 * * Tokens are used for [evaluation][evaluate]
 *
 * @property children children of a token in a syntax tree.
 * Usually these are the tokens that current one interacts with.
 * For instance, children of an addition token are its operands.
 * @property nud similarly to [led] and [std] taken from [TDOP parser](https://www.cristiandima.com/top-down-operator-precedence-parsing-in-go).
 * For more details, see [Lexer][lexer.Lexer]
 */
open class Node(
    var symbol: String = "",
    var value: String = "",
    val position: Pair<Int, Int> = Pair(0, 0),
    val children: MutableList<Node> = mutableListOf()
) {
    val left: Node
        get() = children[0]
    val right: Node
        get() = children[1]

    fun toTreeString(indentation: Int = 0): String {
        val res = StringBuilder()
        for (i in 0 until indentation)
            res.append(' ')
        res.append(this)
        res.append(":${this.position}")
        if (children.size > 0) {
            for (i in children)
                res.append('\n' + i.toTreeString(indentation + 2))
        }

        return res.toString()
    }

    private fun find(symbol: String): Node? {
        if (this.symbol == symbol) {
            return this
        }
        for (t in children) {
            val inChild = t.find(symbol)
            if (inChild != null) {
                return inChild
            }
        }
        return null
    }

    private fun findAndRemove(symbol: String) {
        val inChildren = children.find { it.value == symbol }
        if (inChildren != null) {
            children.remove(inChildren)
        } else {
            for (t in children)
                t.findAndRemove(symbol)
        }
    }

    override fun toString(): String = if (symbol == value) symbol else "$symbol:$value"

    /**
     * The most important method during interpretation.
     *
     * Recursively invoked when code is evaluated.
     *
     * Each parent token defines how its children should be evaluated/
     */
    open fun evaluate(symbolTable: SymbolTable): Any {
        throw PositionalException("Not implemented", symbolTable.getFileTable().filePath, this)
    }

    /**
     *
     */
    fun traverseUntilOptional(condition: (node: Node) -> Optional): Optional {
        val forThis = condition(this)
        if (forThis.value != null &&
            (if (forThis.value is Node) forThis.value.symbol != "(LEAVE)" else true)
        ) {
            return forThis
        }
        if (!forThis.isGood) {
            for (i in children) {
                val childRes = i.traverseUntilOptional(condition)
                if (childRes.value != null &&
                    (if (childRes.value is Node) childRes.value.symbol != "(LEAVE)" else true)
                ) {
                    return childRes
                }
            }
        }
        return condition(this)
    }

    open fun findUnassigned(symbolTable: SymbolTable, parent: Type): Pair<Type, Assignment>? {
        for(c in children) {
            val found = c.findUnassigned(symbolTable, parent)
            if(found != null)
                return found
        }
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Node) {
            return false
        }
        if (children.size != other.children.size) {
            return false
        }
        if (this.value != other.value) {
            return false
        }
        for (i in children.indices) {
            if (children[i] != other.children[i]) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var hash = value.hashCode()
        for ((i, c) in children.withIndex())
            hash += c.hashCode() * (i + 1)
        return hash
    }
}
