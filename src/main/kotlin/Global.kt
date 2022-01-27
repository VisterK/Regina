import java.util.*
import kotlin.math.exp

object Global {
    fun evaluate(input: String): Number {
        if (input.contains('?')) {
            // remove all parentheses
            while (input.contains("(")) {
                var i = input.lastIndexOf(')') - 1
                val end = i + 1
                var sum = 1
                while (sum > 0) {
                    if (input[i] == ')')
                        sum++
                    else if (input[i] == '(')
                        sum--

                    i--
                }
                input.replaceRange(i..end, evaluate(input.substring(i..end)).toString())
            }

        } else return evaluateAlgebra(input)
    }

    fun evaluateAlgebra(input: String): Number {
        val expr = transform(input)
        val operators = Stack<Operator>()
        val values = Stack<Number>()
        var i = 0

        while (i <= expr.lastIndex) {
            when {
                expr[i].isDigit() -> i = evalNumber(i, expr, values)
                expr[i] == '(' -> operators.push(Operator.LEFT_PAR)
                expr[i] == ')' -> evalRightParentheses(operators, values)
                expr[i] == '?' -> {
                    if (operators.isNotEmpty())
                        values.push(calc(operators.pop(), values.pop(), values.pop()))

                    // false
                    if (values.pop() == 0) values.push(evaluate(expr.substring(expr.indexOf(':') + 1)))
                    // true
                    else values.push(evaluate(expr.substring(i + 1, expr.indexOf(':', i + 1))))

                }
                expr[i] == '-' -> i = evalMinus(i, expr, values, operators)
                else -> evalOperator(Operator.toOperator(expr[i]), operators, values)
            }
            i++
        }

        // calc all what is left
        while (operators.isNotEmpty())
            values.push(calc(operators.pop(), values.pop(), values.pop()))

        // after all calculations values contains one element which is the result
        return values.pop()
    }

    private fun evalNumber(index: Int, expr: String, values: Stack<Number>): Int {
        var i = index
        var isDouble = false
        var number = expr[i].toString()
        while (i < expr.lastIndex && (expr[i + 1].isDigit() || expr[i + 1] == '.')) {
            if (expr[i + 1] == '.')
                isDouble = true
            i++
            number += expr[i].toString()
        }
        values.push(if (isDouble) number.toDouble() else number.toInt())

        return i
    }

    private fun evalRightParentheses(operators: Stack<Operator>, values: Stack<Number>) {
        var current = operators.pop()
        while (current != Operator.LEFT_PAR) {
            values.push(calc(current, values.pop(), values.pop()))
            current = operators.pop()
        }
    }

    /**
     * Checking if minus is unary or not
     * and acting based on it
     */
    private fun evalMinus(i: Int, expr: String, values: Stack<Number>, operators: Stack<Operator>): Int {
        if (i == 0)
            return evalNumber(i, expr, values)
        else {
            val prev = Operator.toOperator(expr[i - 1])
            if (prev != Operator.LEFT_PAR
                && prev != Operator.RIGHT_PAR
                && prev != Operator.NOT_OPERATOR
            )
                return evalNumber(i, expr, values)
        }
        // minus is binary operator here
        evalOperator(Operator.SUB, operators, values)
        return i
    }

    private fun evalOperator(op: Operator, operators: Stack<Operator>, values: Stack<Number>) {
        // should not calc
        if (operators.isNotEmpty() && operators.peek().precedence > op.precedence)
            operators.push(op)
        else {
            // should calc previous operator
            if (operators.isNotEmpty())
                values.push(calc(operators.pop(), values.pop(), values.pop()))
            operators.push(op)
        }
    }

    /**
     * remove all whitespaces and
     * transform all operators to one-symbol chars
     */
    fun transform(expr: String): String {
        return expr
            .replace("\\s".toRegex(), "")
            .replace("//", "\\")
            .replace("==", "=")
            .replace("!=", "!")
            .replace(">=", "]")
            .replace("<=", "[")
            .replace("&&", "&")
            .replace("||", "|")
    }

    private fun calc(operator: Operator, first: Number, second: Number): Number {
        val a = if (second is Int) second.toDouble() else second as Double
        val b = if (first is Int) first.toDouble() else first as Double
        val res = when (operator) {
            Operator.MUL -> a * b
            Operator.DIV -> a / b
            Operator.INT_DIV -> (second.toInt()) / (first.toInt())

            Operator.ADD -> a + b
            Operator.SUB -> a - b

            Operator.MORE -> if (a > b) 1 else 0
            Operator.LESS -> if (a < b) 1 else 0
            Operator.MORE_EQUAL -> if (a >= b) 1 else 0
            Operator.LESS_EQUAL -> if (a <= b) 1 else 0
            Operator.EQUAL -> if (a == b) 1 else 0
            Operator.UNEQUAL -> if (a != b) 1 else 0

            Operator.LAND -> if (a != 0.0 && b != 0.0) 1 else 0
            Operator.LOR -> if (a != 0.0 || b != 0.0) 1 else 0
            else -> throw Exception("invalid operator \"$operator\"")
        }
        return if (first is Int && second is Int
            || operator == Operator.INT_DIV
            || operator.ordinal > 6
        ) res.toInt() else res
    }

    enum class Operator(val precedence: Int) {
        LEFT_PAR(10), // (
        RIGHT_PAR(10), // )

        MUL(1), // *
        DIV(1), // /
        INT_DIV(1), // //
        ADD(2), // +
        SUB(2), // -

        MORE(3), // >
        LESS(3), // <
        MORE_EQUAL(3), // >=
        LESS_EQUAL(3), // <=
        EQUAL(4), // ==
        UNEQUAL(4), // !=

        LAND(5), // &
        LOR(6), // |

        COND(7), // ?
        DIVIDER(7), // :

        NOT_OPERATOR(-1);

        companion object {
            fun toOperator(c: Char): Operator {
                return when (c) {
                    '(' -> LEFT_PAR
                    ')' -> RIGHT_PAR

                    '*' -> MUL
                    '/' -> DIV
                    '\\' -> INT_DIV
                    '+' -> ADD
                    '-' -> SUB

                    '>' -> MORE
                    '<' -> LESS
                    ']' -> MORE_EQUAL
                    '[' -> LESS_EQUAL
                    '=' -> EQUAL
                    '!' -> UNEQUAL

                    '&' -> LAND
                    '|' -> LOR

                    '?' -> COND
                    ':' -> DIVIDER
                    else -> NOT_OPERATOR
                }
            }
        }
    }
}