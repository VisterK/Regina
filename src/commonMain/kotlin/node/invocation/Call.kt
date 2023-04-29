package node.invocation

import lexer.PositionalException
import node.Node
import node.statement.Assignment
import properties.EmbeddedFunction
import properties.RFunction
import properties.Variable
import table.FileTable
import table.SymbolTable
import utils.Utils.NULL
import utils.Utils.toVariable

class Call(
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

    val allArgs = children.subList(1, children.size)
    val unnamedArgs: List<Node>
        get() {
            val res = mutableListOf<Node>()
            var i = 1
            while (i < children.size && children[i] !is Assignment) {
                res.add(children[i])
                i++
            }
            return res
        }
    val namedArgs: List<Assignment>
        get() = children.subList(unnamedArgs.size + 1, children.size) as List<Assignment>

    /**
     * For function evaluation, new scope table is needed, to which all arguments will be
     * passed, named like parameters
     */
    override fun evaluate(symbolTable: SymbolTable): Any {
        val function = symbolTable.getFunction(this)
        val newTable = symbolTable.changeFile(
            symbolTable.getFileOfFunction(this, function)
        ).changeScope()
        newTable.resolvingType = ResolvingMode.FUNCTION
        argumentsToParameters(function, symbolTable, newTable)
        return evaluateFunction(newTable, function)
    }

    /**
     * Write arguments to parameters
     */
    fun argumentsToParameters(function: RFunction, argTable: SymbolTable, paramTable: SymbolTable) {
        val argResolvingMode = argTable.resolvingType
        val paramResolvingMode = paramTable.resolvingType
        argTable.resolvingType = ResolvingMode.FUNCTION
        paramTable.resolvingType = ResolvingMode.FUNCTION
        val assigned = mutableMapOf<String, Variable>()
        for ((index, arg) in unnamedArgs.withIndex()) {
            val paramName = getParamName(index, function, argTable.getFileTable())
            if (assigned[paramName] != null) {
                throw PositionalException("Argument already assigned", argTable.getFileTable().filePath, arg)
            }
            assigned[paramName] = arg.evaluate(argTable).toVariable(arg)
        }
        for (nArg in namedArgs) {
            if (!function.hasParam(nArg.name)) {
                throw PositionalException(
                    "Parameter with name `${nArg.name}` absent",
                    argTable.getFileTable().filePath,
                    nArg
                )
            }
            if (assigned[nArg.name] != null) {
                throw PositionalException("Argument already assigned", argTable.getFileTable().filePath, nArg)
            }
            assigned[nArg.name] = nArg.right.evaluate(argTable).toVariable(nArg)
        }
        for (defaultParam in function.defaultParams)
            if (assigned[defaultParam.name] == null) {
                assigned[defaultParam.name] = defaultParam.right.evaluate(paramTable).toVariable(defaultParam)
            }
        for (param in function.nonDefaultParams)
            if (assigned[param.value] == null) {
                throw PositionalException("Parameter not assigned", paramTable.getFileTable().filePath, param)
            }
        for (i in assigned)
            paramTable.addVariable(i.key, i.value)
        argTable.resolvingType = argResolvingMode
        paramTable.resolvingType = paramResolvingMode
    }

    private fun getParamName(index: Int, function: RFunction, fileTable: FileTable): String {
        return if (function.nonDefaultParams.lastIndex < index) {
            if (function.nonDefaultParams.size + function.defaultParams.lastIndex < index) {
                throw PositionalException("More arguments than parameters", fileTable.filePath, children[index])
            } else function.defaultParams[index - function.nonDefaultParams.size].name
        } else function.nonDefaultParams[index].value
    }

    fun evaluateFunction(symbolTable: SymbolTable, function: RFunction): Any {
        val res = if (function is EmbeddedFunction) {
            function.executeFunction(left, symbolTable)
        } else function.body.evaluate(symbolTable)
        return if (res is Unit) NULL else res
    }
}
