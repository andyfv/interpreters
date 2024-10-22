package com.interpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.interpreters.lox.TokenType.*;
public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start   = 0;        // offset field that points to
    private int current = 0;
    private int line    = 1;
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and",     AND);
        keywords.put("class",   CLASS);
        keywords.put("else",    ELSE);
        keywords.put("false",   FALSE);
        keywords.put("for",     FOR);
        keywords.put("fun",     FUN);
        keywords.put("if",      IF);
        keywords.put("nil",     NIL);
        keywords.put("or",      OR);
        keywords.put("print",   PRINT);
        keywords.put("return",  RETURN);
        keywords.put("super",   SUPER);
        keywords.put("this",    THIS);
        keywords.put("TRUE",    TRUE);
        keywords.put("var",     VAR);
        keywords.put("while",   WHILE);
    }

    /* Store the source as a String*/
    Scanner(String source) {
        this.source = source;
    }

    /* The Scanner works goes through the source code,
    adding tokens until it runs out of characters.
    It will append EOF token at the end.
    * */
    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    /* Scan for tokens */
    private void scanToken() {
        char c = advance();
        switch(c) {
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;
            case '!':
                addToken(match('=') ? BANG_EQUAL      : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL     : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL      : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL   : GREATER);
                break;
            case '/':
                /* We need additional handling because the SLASH operator
                * is used for comments and also for division*/
                if (match('/')) { // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;

            // Skip over new lines and white spaces
            case ' ':
            case '\r':
            case '\t': break;

            // Increment the line counter
            case '\n': line++; break;

            case '"': string(); break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                    break;
                }
        }
    }

    /* Scan for identifier. First check to see if it matches any keyword.
    If it doesn't then it is a user-defined indentifier.
    * */
    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text     = source.substring(start, current);
        TokenType type  = keywords.get(text);
        if (type == null) type = IDENTIFIER;
        addToken(type);
    }

    /* Consume the number */
    private void number() {
        while (isDigit(peek())) advance();

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // Consume the "."

            while (isDigit(peek())) advance();
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    // Handle string literals
    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++; // handle multi-line strings
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // Consume the closing quotes
        advance();

        // Trim the quotes at the start and the end
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    /* We use match() to recognize characters in two stages.
    If for example we recognize '!' then we know that the lexeme
    starts with '!'. Then we check the next character to determine
    if we have '!=' or just '!'. If the char we expect is there we consume
    it by moving the current index
    * */
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    /* Lookahead without consuming the next character.
    * */
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0'; // Guard for EOF
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c) {
        return  (c >= 'a' && c <= 'z')
            ||  (c >= 'A' && c <= 'Z')
            ||  (c == '_');
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /* Check if the end of the line is reached */
    private boolean isAtEnd() {
        return current >= source.length();
    }

    /* InputAction: Take the next char from the source and return it */
    private char advance() {
        return source.charAt(current++);
    }

    /* OutputAction: Shortcut to add token to the token list */
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    /* OutputAction: Add token with a literal value to the token list */
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

}
