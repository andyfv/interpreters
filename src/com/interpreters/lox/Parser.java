package com.interpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
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

Grammar(ordered from the least to the highest precedence, e.g. top-down parser):
===========================================================
program     -> declaration* EOF ;
declaration -> classDecl
             | funDecl
             | varDecl
             | statement
             ;
classDecl   -> "class" IDENTIFIER ( "<" IDENTIFIER )?
               "{" function* "}" ;
funDecl     -> "fun" function ;
function    -> IDENTIFIER "(" parameters? ")" block ;           // IDENTIFIER is the name of the function
parameters  -> IDENTIFIER ( "," IDENTIFIER )* ;                 // IDENTIFIER is a name for optional one or more parameters
varDecl     -> "var" IDENTIFIER ( "=" expression )? ";" ;
statement   -> exprStmt
             | forStmt
             | ifStmt
             | printStmt
             | returnStmt
             | whileStmt
             | block
             ;
exprStmt    -> expression ";" ;
forStmt     -> "for" "(" ( varDecl | exprStmt | ";" )
                expression? ";"
                expression? ")" statement ;
ifStmt      -> "if" "(" expression ")" statement
             ( "else" statement )? ;
printStmt   -> "print" expression ";" ;
returnStmt  -> "return" expression? ";" ;
whileStmt   -> "while" "(" expression ")" statement ;
block       -> "{" declaration* "}" ;

expression  -> assignment ;
assignment  -> ( call "." )? IDENTIFIER "=" assignment
             | logic_or
             ;
logic_or    -> logic_and ( "or" logic_and )* ;
logic_and   -> equality ( "and" equality)* ;
equality    -> comparison ( ( "!=" | "==" ) comparison )* ;
comparison  -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term        -> factor ( ( "-" | "+" ) factor )* ;
factor      -> unary ( ( "/" | "*" ) unary )* ;
unary       -> ( "!" | "-" ) unary | call ;
call        -> primary ( "(" arguments? ")"  | "." IDENTIFIER )* ;
arguments   -> expression ( "," expression )* ;
primary     -> "true"
             | "false"
             | "nil"
             | NUMBER | STRING |
             | "(" expression ")"
             | IDENTIFIER
             | "this"
             ;
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
            statements.add(declaration());
        }
        return statements;
    }

    private Stmt statement() {
        if(match(FOR))          return forStatement();
        if(match(IF))           return ifStatement();
        if(match(PRINT))        return printStatement();
        if(match(RETURN))       return returnStatement();
        if(match(WHILE))        return whileStatement();
        if(match(LEFT_BRACE))   return new Stmt.Block(block());

        return expressionStatement();
    }

    /* For Loop (C-style for loop)
    Example: for (initializer; condition; increment) body

    How it works:
        Basically we desugar the for loop to a while loop. We don't add a new syntax tree node.
        Instead, we reuse the Stmt.While syntax tree node.
    * */
    private Stmt forStatement() {
        // Parse the opening parenthesis
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        // Parse the initializer clause
        Stmt initializer;
        if (match(SEMICOLON)) {                     // If the initializer has been omitted
            initializer = null;
        } else if (match(VAR)) {                    // If there is a variable declaration
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();    // Otherwise we parse an expression
        }

        // Parse the condition
        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        // Parse the increment
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        // Parse the body
        Stmt body = statement();

        // Add the increment at the end of the body
        if (increment != null) {
            body = new Stmt.Block( Arrays.asList(body
                                , new Stmt.Expression(increment)
                                ));
        }

        // Construct a while loop using the condition cause and the body
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // Add the initializer at the start of body
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after if.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token   keyword = previous();
        Expr    value   = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Stmt.Function function(String kind) {
        // Parse the function name
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        return new Stmt.Function(name, functionBody(kind));
    }

    private Expr.Function functionBody(String kind) {
        // Parse the parameters
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters");
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters");

        // Parse the function body
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();

        return new Expr.Function(parameters, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
        try {
            if (match(CLASS)) return classDeclaration();
            if (match(VAR)) return varDeclaration();
            if (check(FUN) && checkNext(IDENTIFIER)) {
                consume(FUN, null);
                return function("function");
            }

        return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");
        Expr.Variable superclass = null;

        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }

        List<Stmt.Function> methods      = new ArrayList<>();
        List<Stmt.Function> classMethods = new ArrayList<>();

        consume(LEFT_BRACE, "Expect '{' before class body");

        while (!check(RIGHT_BRACE) && !isAtEnd()){
            boolean isClassMethod = match(CLASS);
            (isClassMethod ? classMethods : methods).add(function("method"));
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.");
        return new Stmt.Class(name, superclass, methods, classMethods);
    }

    private boolean checkNext(TokenType tokenType) {
        if (isAtEnd()) return false;
        if (tokens.get(current + 1).type == EOF) return false;
        return tokens.get(current + 1).type == tokenType;
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        /* Check if there is initialization or just declaration
        Example:
            var temp;       // Variable declaration
            or
            var temp = 0;   // Variable declaration with initialization
        * */
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    // Binary operators

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token   equals  = previous();
            Expr    value   = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {                  // Parse the left-hand side as a normal expression.
                Expr.Get get = (Expr.Get)expr;                      // If we stumble onto '=' we take the expression and
                return new Expr.Set(get.object, get.name, value);   // transform it to the correct syntax node - Expr.Set
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator  = previous();
            Expr right      = and();
            expr            = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator  = previous();
            Expr right      = equality();
            expr            = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        /* For each iteration store the resulting expression into the
        @expr variable. Thus, we create a left-associative nested tree
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

        return call();
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if(!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments");
        return new Expr.Call(callee, paren, arguments);
    }

    // Parsing terminals
    private Expr primary() {
        if (match(FUN))             return functionBody("function");
        if (match(FALSE))           return new Expr.Literal(false);
        if (match(TRUE))            return new Expr.Literal(true);
        if (match(NIL))             return new Expr.Literal(null);
        if (match(NUMBER, STRING))  return new Expr.Literal(previous().literal);
        if (match(IDENTIFIER))      return new Expr.Variable(previous());
        if (match(THIS))            return new Expr.This(previous());
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression");
    }

    /* Check to see if the current token matches any of the passed types
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


    // Check the type TokenType of the 'current' against the TokenType passed as an argument
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
