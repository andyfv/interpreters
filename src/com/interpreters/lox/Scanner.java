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
            scanTokens();
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

            default:
                Lox.error(line, "Unexpected character.");
                break;
        }
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
