package task

import java.io.File
import java.io.InputStream
import java.util.LinkedList

import java.io.InputStream
import java.util.LinkedList

const val EOF_SYMBOL = -1
const val ERROR_STATE = 0
const val SKIP_VALUE = 0
const val EOF_VALUE = -1

const val A_VALUE = 1
const val B_VALUE = 2
const val C_VALUE = 3
const val D_VALUE = 4
const val E_VALUE = 5
const val F_VALUE = 6

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
    override val states = setOf(1, 2, 3, 4, 5, 6, 7, 8)
    override val alphabet = -1 .. 255
    override val startState = 1
    override val finalStates = setOf(2, 3, 4, 5, 6, 7, 8)

    private val numberOfStates = states.maxOrNull()!! + 1
    private val numberOfSymbols = alphabet.maxOrNull()!! + 1
    private val transitions = Array(numberOfStates) {IntArray(numberOfSymbols)}
    private val values: Array<Int> = Array(numberOfStates) {0}

    private fun setTransition(from: Int, symbol: Char, to: Int) {
        transitions[from][symbol.code + 1] = to
    }

    private fun setTransition(from: Int, symbol: Int, to: Int) {
        transitions[from][symbol + 1] = to
    }

    private fun setValue(state: Int, terminal: Int) {
        values[state] = terminal
    }

    override fun next(state: Int, symbol: Int): Int {
        assert(states.contains(state))
        assert(alphabet.contains(symbol))
        return transitions[state][symbol + 1]
    }

    override fun value(state: Int): Int {
        assert(states.contains(state))
        return values[state]
    }

    init {
        for ((c, i) in 'a' .. 'f' zip 2 .. 7) {
            setTransition(1, c, i)
        }
        setTransition(1, EOF_SYMBOL, 8)

        setValue(2, A_VALUE)
        setValue(3, B_VALUE)
        setValue(4, C_VALUE)
        setValue(5, D_VALUE)
        setValue(6, E_VALUE)
        setValue(7, F_VALUE)
        setValue(8, EOF_VALUE)
    }
}

data class Token(val value: Int, val lexeme: String, val startRow: Int, val startColumn: Int)

interface Scanner {
    fun getToken(): Token
}

class StreamScanner(private val automaton: Automaton, private val stream: InputStream) : Scanner {
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

    override fun getToken(): Token {
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

class Recognizer(private val scanner: Scanner) {
    private var last: Token? = null

    fun recognize(): Boolean {
        last = scanner.getToken()
        val status = recognizeS()
        return when (last?.value) {
            EOF_VALUE -> status
            else -> false
        }
    }

    private fun recognizeS(): Boolean {
        return when(last?.value) {
            A_VALUE, C_VALUE, E_VALUE -> recognizeA() && recognizeC()
            B_VALUE -> recognizeB() && recognizeTerminal(F_VALUE)
            else -> false
        }
    }

    private fun recognizeA(): Boolean {
        return when(last?.value) {
            E_VALUE, C_VALUE -> recognizeD()
            A_VALUE -> recognizeTerminal(A_VALUE) && recognizeB()
            else -> false
        }
    }

    private fun recognizeB(): Boolean =
        recognizeTerminal(B_VALUE)

    private fun recognizeC(): Boolean =
        recognizeTerminal(C_VALUE) && recognizeTerminal(D_VALUE)

    private fun recognizeD(): Boolean {
        return when(last?.value) {
            E_VALUE -> recognizeTerminal(E_VALUE)
            C_VALUE -> true
            else -> false
        }
    }

    private fun recognizeTerminal(value: Int) =
        if (last?.value == value) {
            last = scanner.getToken()
            true
        }
        else false
}

fun main(args: Array<String>) {
    if (Recognizer(StreamScanner(Example, File(args[0]).InputStream())).recognize()) {
        print("accept")
    } else {
        print("reject")
    }
}