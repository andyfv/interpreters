package com.interpreters.lox;

import java.util.ArrayList;
import java.util.List;
import static com.interpreters.lox.TokenType.*;

/* Similar to the Scanner, the Parser consumes a flat input sequence.
- The Scanner consumes characters which it transforms to tokens.
- Then the Parser uses the list of tokens.

To make it work we make each grammar rule into a method. Each method
produces a syntax tree for that rule and returns it to the caller. If the
body of the rule contains a non-terminal reference to another rule, we call
that other rule's method.

Note: This is why left recursion is problematic for recursive descent.
The function for a left-recursive rule immediately calls itself, which calls
itself again, and again, and again until we get a stack overflow.

Grammar(ordered from the least to higher precedence, e.g. top-down parser):
===========================================================
program     -> declaration* EOF ;
declaration -> varDecl | statement ;
varDecl     -> "var" IDENTIFIER ( "=" expression )? ";" ;
statement   -> exprStmt | printStmt
exprStmt    -> expression ";" ;
printStmt   -> "print" expression ";" ;

expression  -> equality ;
equality    -> comparison ( ( "!=" | "==" ) comparison )* ;
comparison  -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term        -> factor ( ( "-" | "+" ) factor )* ;
factor      -> unary ( ( "/" | "*" ) unary )* ;
unary       -> ( "!" | "-" ) unary ;
primary     -> "true" | "false" | "nil"
             | NUMBER | STRING |
             | "(" expression ")"
             | IDENTIFIER;
===========================================================

@tokens     list of tokens
@current    points to the token waiting to be  parsed
* */
public class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(statement());
        }
        return statements;
    }

    private Stmt statement() {
        if(match(PRINT)) return printStatement();
        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Expr expression() {
        return equality();
    }

    // Binary operators

    private Expr equality() {
        Expr expr = comparison();

        /* For each iteration store the resulting expression into the
        @expr variable. Thus we create a left-associative nested tree
        of Binary operator nodes. The previous @expr is the left operand.
        * */
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            /* Get the previously matched operator token and
            call comparison() to parse the right-hand operand
            * */
            Token   operator    = previous();
            Expr    right       = comparison();

            /* Combine the operator and the left and right operand
            into a Binary syntax tree node.
            * */
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token   operator    = previous();
            Expr    right       = term();

            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token   operator    = previous();
            Expr    right       = factor();

            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while(match(SLASH, STAR)) {
            Token   operator    = previous();
            Expr    right       = unary();

            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Unary operators

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token   operator    = previous();
            Expr    right       = unary();

            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    // Parsing terminals
    private Expr primary() {
        if (match(FALSE))           return new Expr.Literal(false);
        if (match(TRUE))            return new Expr.Literal(true);
        if (match(NIL))             return new Expr.Literal(null);
        if (match(NUMBER, STRING))  return new Expr.Literal(previous().literal);
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression");
    }

    /* Check to see if the current token mathces any of the passed types
    * If it matches we return true, otherwise return false
    *
    * @types    the types we check against
    * */
    private boolean match(TokenType... types) {
        for(TokenType type : types) {
            if(check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if(check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() { // Check if we've ran out of tokens to parse
        return peek().type == EOF;
    }

    private Token peek() {  // Return the current token
        return tokens.get(current);
    }

    private Token previous() {  // Return the most recently consumed token
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if(previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }
}
