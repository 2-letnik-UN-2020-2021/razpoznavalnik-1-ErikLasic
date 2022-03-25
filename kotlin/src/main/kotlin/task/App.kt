package task

import java.io.File
import java.io.InputStream
import java.util.LinkedList

data class Token(val value: Int, val lexeme: String, val startRow: Int, val startColumn: Int)

interface Scanner {
    fun eof(): Boolean
    fun getToken(): Token?
}

class MockupScanner(private var tokens: List<Token>) : Scanner {
    override fun eof() = tokens.isEmpty()

    override fun getToken() =
        if (tokens.isEmpty()) null
        else {
            val token = tokens.first()
            tokens = tokens.drop(1)
            token
        }
}

class Rezognizer(private val scanner: Scanner) {
    private var last: Token? = null

    fun recognize(): Boolean {
        last = scanner.getToken()
        val status = recognizeE()
        return if (last == null) status
        else false
    }

    fun recognizeE() = recognizeT() && recognizeE_()

    fun recognizeE_() {
        val lookahead = last?.value
        if (lookahead == null) {
            true
        }
        return when(lookahead) {
            PLUS -> recognizeTerminal(PLUS) && recognizeE()
            MINUS -> recognizeTerminal(MINUS) && recognizeE()
            RPAREN -> true
            else -> false
        }
    }

    // ...

    private fun recognizeTerminal(value: Int) =
        if (last?.value == value) {
            last = scanner.getToken()
            true
        }
        else false
}

fun main(args: Array<String>) {
    if (Rezognizer(MockupScanner(Example, File(args[0]).inputStream).recognize())) {
        print("accept")
    } else {
        print("reject")
    }
}