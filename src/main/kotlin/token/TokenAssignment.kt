package token

import SymbolTable
import lexer.Parser

class TokenAssignment(
    symbol: String,
    value: String,
    position: Pair<Int, Int>,
    bindingPower: Int,
    nud: ((token: Token, parser: Parser) -> Token)?,
    led: ((
        token: Token, parser: Parser, token2: Token
    ) -> Token)?,
    std: ((token: Token, parser: Parser) -> Token)?
) : Token(symbol, value, position, bindingPower, nud, led, std) {
    override fun evaluate(symbolTable: SymbolTable): Any {
        val value = right.evaluate(symbolTable)
        symbolTable.assignLValue(this, value, symbolTable.parent)
        return value
    }
}