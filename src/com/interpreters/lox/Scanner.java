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

    /* Check if the end of the line is reached */
    private boolean isAtEnd() {
        return current >= source.length();
    }
}
