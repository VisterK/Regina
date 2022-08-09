/**
 * AST building algorithm was taken and rewritten from:
 * https://www.cristiandima.com/top-down-operator-precedence-parsing-in-go
 */
package token

import Optional
import lexer.Parser
import lexer.PositionalException
import node.Node
import properties.Type
import table.SymbolTable
import token.operator.TokenTernary
import token.statement.Assignment

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
open class Token(
    var symbol: String = "",
    var value: String = "",
    val position: Pair<Int, Int> = Pair(0, 0),
    val bindingPower: Int = 0, // precedence priority
    var nud: ((token: Token, parser: Parser) -> Token)? = null, // null denotation: values, prefix operators
    var led: ((token: Token, parser: Parser, token2: Token) -> Token)? = null, // left denotation: infix and suffix operators
    var std: ((token: Token, parser: Parser) -> Token)? = null, // statement denotation
    val children: MutableList<Token> = mutableListOf()
) {
    val left: Token
        get() = children[0]
    val right: Token
        get() = children[1]

    fun toTreeString(indentation: Int = 0): String {
        val res = StringBuilder()
        for (i in 0 until indentation)
            res.append(' ')
        res.append(this)
        res.append(":${this.position}")
        if (children.size > 0)
            for (i in children)
                res.append('\n' + i.toTreeString(indentation + 2))

        return res.toString()
    }

    private fun find(symbol: String): Token? {
        if (this.symbol == symbol)
            return this
        for (t in children) {
            val inChild = t.find(symbol)
            if (inChild != null)
                return inChild
        }
        return null
    }

    private fun findAndRemove(symbol: String) {
        val inChildren = children.find { it.value == symbol }
        if (inChildren != null)
            children.remove(inChildren)
        else
            for (t in children)
                t.findAndRemove(symbol)
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
        throw PositionalException("Not implemented", this)
    }

    /**
     *
     */
    fun traverseUntilOptional(condition: (token: Token) -> Optional): Optional {
        val forThis = condition(this)
        if (forThis.value != null && (if (forThis.value is Token) forThis.value.symbol != "(LEAVE)" else true))
            return forThis
        if (!forThis.isGood)
            for (i in children) {
                val childRes = i.traverseUntilOptional(condition)
                if (childRes.value != null && (if (childRes.value is Token) childRes.value.symbol != "(LEAVE)" else true))
                    return childRes
            }
        return condition(this)
    }

    /**
     * Find unresolved property and return class instance with this property and corresponding assignment
     */
    fun traverseUnresolvedOptional(symbolTable: SymbolTable, parent: Type): Pair<Type, Assignment?> {
        val res = traverseUntilOptional {
            when (it) {
                // Second part of ternary might be unresolved. Say, `if(parent == 0) 0 else parent.someProperty`.
                // If parent == 0, then someProperty is unresolved, but it is fine
                is TokenTernary -> {
                    val condition = it.left.traverseUnresolvedOptional(symbolTable, parent)
                    if (condition.second == null) {
                        if (it.evaluateCondition(symbolTable.changeVariable(parent)) != 0) {
                            val result = it.right.traverseUnresolvedOptional(symbolTable, parent)
                            if (result.second == null)
                                Optional(Token("(LEAVE)"))
                            else Optional(result)
                        } else {
                            val result = it.children[2].traverseUnresolvedOptional(symbolTable, parent)
                            if (result.second == null)
                                Optional(Token("(LEAVE)"))
                            else Optional(result)
                        }
                    } else Optional(condition)
                }
                is Assignable -> {
                    val result = it.getFirstUnassigned(parent, symbolTable.changeVariable(parent))
                    if (result.second == null)
                        Optional(Token("(LEAVE)"))
                    else Optional(result)
                }
                else -> Optional()
            }
        }
        if (res.value is Token && res.value.symbol == "(LEAVE)")
            return Pair(parent, null)
        if (res.value == null)
            return Pair(parent, null)
        return res.value as Pair<Type, Assignment?>
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Token)
            return false
        if (children.size != other.children.size)
            return false
        if (this.value != other.value)
            return false
        var areEqual = true
        for (i in children.indices)
            areEqual = children[i] == other.children[i]
        return areEqual
    }

    override fun hashCode(): Int {
        var hash = value.hashCode()
        for ((i, c) in children.withIndex())
            hash += c.hashCode() * (i + 1)
        return hash
    }

    fun toNode(): Node {
        throw NotImplementedError()
    }
}
