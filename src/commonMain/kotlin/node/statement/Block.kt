package node.statement

import lexer.PositionalException
import node.Node
import node.invocation.Call
import properties.primitive.Indexable
import properties.primitive.PDictionary
import properties.primitive.PInt
import properties.primitive.Primitive
import table.SymbolTable
import utils.Utils.toBoolean
import utils.Utils.toVariable

class Block(node: Node) :
    Node(node.symbol, node.value, node.position, node.children) {

    override fun evaluate(symbolTable: SymbolTable): Any {
        return when (symbol) {
            "{" -> evaluateBlock(symbolTable)
            "if" -> evaluateConditional(symbolTable)
            "while" -> evaluateCycle(symbolTable)
            "foreach" -> evaluateForeach(symbolTable)
            else -> throw PositionalException("Not a block", symbolTable.getFileTable().filePath, this)
        }
    }

    /**
     * children[0] is always an identifier [RegistryFactory] is responsible
     * children[1] is iterable
     * children[2] is a block
     * Method with duplicated code, but it is understandable
     */
    private fun evaluateForeach(symbolTable: SymbolTable): Any {
        val (iterable, isRange) = getIterable(symbolTable)
        val sequence = if (isRange) {
            val range = (iterable as List<PInt>).map { it.getPValue() }
            generateSequence(range[0], { it -> (it + range[2]) % (range[1] + range[2]) % range[2] == 0 })
        } else {
            iterable.asSequence()
        }

        for (value in sequence) {
            symbolTable.addVariable(left.value, value.toVariable(left))
            when (val res = children[2].evaluate(symbolTable)) {
                CycleStatement.CONTINUE -> continue
                CycleStatement.BREAK -> break
                !is Unit -> return res
            }
        }

        return Unit
    }


    private fun getIterable(symbolTable: SymbolTable): Pair<Iterable<*>, Boolean> {
        val evaluated = right.evaluate(symbolTable).toVariable(right)
        val isRange = right is Call && (right as Call).name.value == "range"

        val iterable = when {
            isRange -> (evaluated as Primitive).getPValue() as Iterable<*>
            evaluated !is Indexable || evaluated is PDictionary -> {
                throw PositionalException(
                    "Expected list, string, or range",
                    symbolTable.getFileTable().filePath,
                    right
                )
            }
            evaluated is String -> evaluated.map { it.toString() }
            else -> (evaluated as Primitive).getPValue()
        }

        return Pair(iterable as Iterable<*>, isRange)
    }


    private fun evaluateCycle(symbolTable: SymbolTable): Any {
        val condition = left
        val block = right
        while (condition.evaluate(symbolTable).toBoolean(condition, symbolTable.getFileTable())) {
            when (val res = block.evaluate(symbolTable)) {
                CycleStatement.CONTINUE -> continue
                CycleStatement.BREAK -> break
                !is Unit -> {
                    return res
                }
            }
        }
        return Unit
    }

    private fun evaluateConditional(symbolTable: SymbolTable): Any {
        val condition = left
        val trueBlock = right
        if (condition.evaluate(symbolTable).toBoolean(condition, symbolTable.getFileTable())) {
            return trueBlock.evaluate(symbolTable)
        } else if (children.size == 3) {
            return children[2].evaluate(symbolTable)
        }
        return Unit
    }

    private fun evaluateBlock(symbolTable: SymbolTable): Any {
        for (token in children) {
            val result = when {
                token is Block && token.value == "{" -> throw PositionalException(
                    "Block within a block. Maybe `if`, `else`, or `while` was omitted?",
                    symbolTable.getFileTable().filePath,
                    token
                )
                token.symbol == "return" -> if (token.children.isNotEmpty()) token.left.evaluate(symbolTable) else Unit
                token.symbol == "break" -> CycleStatement.BREAK
                token.symbol == "continue" -> CycleStatement.CONTINUE
                token is Block -> token.evaluate(symbolTable)
                else -> token.evaluate(symbolTable)
            }

            if (result !is Unit) {
                return result
            }
        }
        return Unit
    }


    enum class CycleStatement {
        BREAK,
        CONTINUE
    }
}
