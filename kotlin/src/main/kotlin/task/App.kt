package task

import com.sun.org.apache.xalan.internal.xsltc.compiler.sym.LPAREN
import com.sun.org.apache.xalan.internal.xsltc.compiler.sym.RPAREN
import jdk.incubator.vector.VectorOperators.POW
import java.io.File
import java.io.InputStream
import java.sql.Types.FLOAT
import java.sql.Types.VARCHAR
import java.util.LinkedList
import javax.management.Query.*

const val EOF_SYMBOL = -1
const val ERROR_STATE = 0
const val SKIP_VALUE = 0

const val NEWLINE = '\n'.code

interface Automaton {
    val states: Set<Int>
    val alphabet: IntRange
    fun next(state: Int, symbol: Int): Int
    fun value(state: Int): Int
    val startState: Int
    val finalStates: Set<Int>
}

object Example : Automaton {
    override val states = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
    override val alphabet = 0..255
    override val startState = 1
    override val finalStates = setOf(2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)

    private val numberOfStates = states.maxOrNull()!! + 1
    private val numberOfSymbols = alphabet.maxOrNull()!! + 1
    private val transitions = Array(numberOfStates) { IntArray(numberOfSymbols) }
    private val values: Array<Int> = Array(numberOfStates) { 0 }

    private fun setTransition(from: Int, symbol: Char, to: Int) {
        transitions[from][symbol.code] = to
    }

    private fun setValue(state: Int, terminal: Int) {
        values[state] = terminal
    }

    override fun next(state: Int, symbol: Int): Int =
        if (symbol == EOF_SYMBOL) ERROR_STATE
        else {
            assert(states.contains(state))
            assert(alphabet.contains(symbol))
            transitions[state][symbol]
        }

    override fun value(state: Int): Int {
        assert(states.contains(state))
        return values[state]
    }

    init {
        for (i in '0'..'9') {
            setTransition(1, i, 2)
            setTransition(2, i, 2)
        }
        setTransition(2, '.', 3)
        for (i in '0'..'9') {
            setTransition(3, i, 4)
            setTransition(4, i, 4)
        }
        for (i in 'a'..'z') {
            setTransition(1, i, 5)
            setTransition(5, i, 5)
        }
        for (i in 'A'..'Z') {
            setTransition(1, i, 5)
            setTransition(5, i, 5)
        }
        for (i in '0'..'9') {
            setTransition(5, i, 6)
            setTransition(6, i, 6)
        }
        setTransition(1, '+', 7)
        setTransition(1, '-', 8)
        setTransition(1, '*', 9)
        setTransition(1, '/', 10)
        setTransition(1, '^', 11)
        setTransition(1, '(', 12)
        setTransition(1, ')', 13)
        setTransition(1, ' ', 14)
        setTransition(1, '\n', 14)
        setTransition(1, '\r', 14)
        setTransition(1, '\t', 14)

        setValue(2, 1)
        setValue(4, 1)
        setValue(5, 2)
        setValue(6, 2)
        setValue(7, 3)
        setValue(8, 4)
        setValue(9, 5)
        setValue(10, 6)
        setValue(11, 7)
        setValue(12, 8)
        setValue(13, 9)

    }
}

data class Token(val value: Int, val lexeme: String, val startRow: Int, val startColumn: Int)

class Scanner(private val automaton: Automaton, private val stream: InputStream) {
    private var state = automaton.startState
    private var last: Int? = null
    private var buffer = LinkedList<Byte>()
    private var row = 1
    private var column = 1

    private fun updatePosition(symbol: Int) {
        if (symbol == NEWLINE) {
            row += 1
            column = 1
        } else {
            column += 1
        }
    }

    private fun getValue(): Int {
        var symbol = last ?: stream.read()
        state = automaton.startState

        while (true) {
            updatePosition(symbol)

            val nextState = automaton.next(state, symbol)
            if (nextState == ERROR_STATE) {
                if (automaton.finalStates.contains(state)) {
                    last = symbol
                    return automaton.value(state)
                } else throw Error("Invalid pattern at ${row}:${column}")
            }
            state = nextState
            buffer.add(symbol.toByte())
            symbol = stream.read()
        }
    }

    fun eof(): Boolean =
        last == EOF_SYMBOL

    fun getToken(): Token? {
        if (eof()) return null

        val startRow = row
        val startColumn = column
        buffer.clear()

        val value = getValue()
        return if (value == SKIP_VALUE)
            getToken()
        else
            Token(value, String(buffer.toByteArray()), startRow, startColumn)
    }
}

fun name(value: Int) =
    when (value) {
        1 -> "float"
        2 -> "variable"
        3 -> "plus"
        4 -> "minus"
        5 -> "times"
        6 -> "divide"
        7 -> "pow"
        8 -> "lparen"
        9 -> "rparen"
        else -> throw Error("Invalid value")
    }

fun printTokens(scanner: Scanner) {
    val token = scanner.getToken()
    if (token != null) {
        print("${name(token.value)}(\"${token.lexeme}\") ")
        printTokens(scanner)
    }
}

class Parser(private val scanner: Scanner) {
    private var last: Token? = null

    // E ::= T EE;
    fun E(): Boolean {
        return T() && EE()
    }

    // EE ::= + T EE | - T EE | epsilon;
    fun EE(): Boolean {
        if (last?.value == PLUS) {
            last = scanner.getToken()
            return T() && EE()
        } else if (last?.value == MINUS) {
            last = scanner.getToken()
            return T() && EE()
        }
        return true
    }

    // T ::= X TT;
    fun T(): Boolean {
        return X() && TT()
    }

    // TT ::= * X TT | / X TT | epsilon;
    fun TT(): Boolean {
        if (last?.value == TIMES) {
            last = scanner.getToken()
            return X() && TT()
        } else if (last?.value == DIV) {
            last = scanner.getToken()
            return X() && TT()
        }
        return true
    }

    // X ::= Y XX;
    fun X(): Boolean {
        return Y() && XX()
    }

    // XX ::= ^ X | epsilon;
    fun XX(): Boolean {
        if (last?.value == POW) {
            last = scanner.getToken()
            return X()
        }
        return true
    }

    // Y ::= - F | + F | F
    fun Y(): Boolean {
        if (last?.value == MINUS) {
            last = scanner.getToken()
            return F()
        } else if (last?.value == PLUS) {
            last = scanner.getToken()
            return F()
        } else {
            return F()
        }
    }

    // F ::= ( E ) | float | variable;
    fun F(): Boolean {
        if (last?.value == LPAREN) {
            last = scanner.getToken()
            if (E() && last?.value == RPAREN) {
                last = scanner.getToken()
                return true
            }
        } else if (last?.value == FLOAT) {
            last = scanner.getToken()
            return true
        } else if (last?.value == VARCHAR) {
            last = scanner.getToken()
            return true
        }
        return false
    }

    fun parse(): Boolean {

        return E() && last == null
    }
}

//Parser
class Rezognizer(private val scanner: Scanner) {
    private var last: Token? = null

    fun recognize(): Boolean {
        last = scanner.getToken()
        val status = recognizeE()
        return if (last == null) status
        else false
    }

    fun recognizeE() = (last?.let { recognizeTerminal(it.value) } == true) && recognizeE_()

    fun recognizeE_(): Boolean {
        val lookahead = last?.value
        if (lookahead == null) {
            true
        }
        return when (lookahead) {
            PLUS -> recognizeTerminal(PLUS) && recognizeE()
            MINUS -> recognizeTerminal(MINUS) && recognizeE()
            RPAREN -> true
            else -> false
        }
    }

    // ...
    // E ::= T EE;
    fun E(): Boolean {
        return T() && EE()
    }

    // EE ::= + T EE | - T EE | epsilon;
    fun EE(): Boolean {
        if (last?.value == PLUS) {
            last = scanner.getToken()
            return T() && EE()
        } else if (last?.value == MINUS) {
            last = scanner.getToken()
            return T() && EE()
        }
        return true
    }

    // T ::= X TT;
    fun T(): Boolean {
        return X() && TT()
    }

    // TT ::= * X TT | / X TT | epsilon;
    fun TT(): Boolean {
        if (last?.value == TIMES) {
            last = scanner.getToken()
            return X() && TT()
        } else if (last?.value == DIV) {
            last = scanner.getToken()
            return X() && TT()
        }
        return true
    }

    // X ::= Y XX;
    fun X(): Boolean {
        return Y() && XX()
    }

    // XX ::= ^ X | epsilon;
    fun XX(): Boolean {
        if (last?.value == POW) {
            last = scanner.getToken()
            return X()
        }
        return true
    }

    // Y ::= - F | + F | F
    fun Y(): Boolean {
        if (last?.value == MINUS) {
            last = scanner.getToken()
            return F()
        } else if (last?.value == PLUS) {
            last = scanner.getToken()
            return F()
        } else {
            return F()
        }
    }

    // F ::= ( E ) | float | variable;
    fun F(): Boolean {
        if (last?.value == LPAREN) {
            last = scanner.getToken()
            if (E() && last?.value == RPAREN) {
                last = scanner.getToken()
                return true
            }
        } else if (last?.value == FLOAT) {
            last = scanner.getToken()
            return true
        } else if (last?.value == VARCHAR) {
            last = scanner.getToken()
            return true
        }
        return false
    }

    private fun recognizeTerminal(value: Int) =
        if (last?.value == value) {
            last = scanner.getToken()
            true
        } else false
}

fun main(args: Array<String>) {
    val scanner = Scanner(Example, File(args[0]).inputStream())
    //printTokens(scanner)
    if (Parser(scanner).parse()) {
        print("accept")
    } else {
        print("reject")
    }
}