/* Resolver
*   Tracks down a variable declaration that our interpreter refers to.
*   Resolving a variable declaration adds a new entry to the current innermost scope.
*
*       How it works:
* It is basically a sort of mini-interpreter. It walks the AST,
* visits every node, but it doesn't execute anything.
* This means that there is:
*   - NO SIDE EFFECTS. If a print statement is visited,
*       it doesn't actually print anything. If native function or
*       other function that usually reaches to the outside world
*       is visited, they are not executed but stubbed and thus have
*       no side effects
*
*   - NO CONTROL FLOW. Loops are visited only once and all
*       branches in a 'if statement' are visited. There are no short-circuits
*       for logic operators.
*
*   Usually visiting all the nodes(only once for loops) means that the
*   performance is O(n) or close to linear for more complex analyses.
*
*  Most important are the visits for:
*   - BLOCK statement   : introduces new scope for the statements it contains
*   - FUN declaration   : introduces new scope for the function body and its arguments
*   - VAR declaration   : adds new variable to the current scope
*   - VAR expression    : needs to be resolved before execution
*   - VAR assignment    : needs to be resolved before execution
*
*
* */

package com.interpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType
        { NONE
        , FUNCTION
        }

    // VISIT METHODS

    // BLOCK resolvement
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();

        return null;
    }


    /* FUN declaration resolvement

    Functions bind names and introduce a scope. The name of the function itself is
    bound to the surrounding scope where the function is declared. The function body
    is a new scope with the functions parameters bound to that new scope.
    * */
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        visitFunctionExpr(stmt.function);
        return null;
    }

    @Override
    public Void visitFunctionExpr(Expr.Function expr) {
        resolveFunction(expr, FunctionType.FUNCTION);
        return null;
    }

    /* VAR binding resolvement
        Variable binding is split in two steps:
            1) Declaring the variable
            (1.5) Resolve the variable's initializer
            2) Defining the variable
        * */
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);                 // 1)

        if (stmt.initializer != null) {     // (1.5)
            resolve(stmt.initializer);
        }

        define(stmt.name);                  // 2)
        return null;
    }


    /* VAR assignment resolvement

    First resolve the expression for the assigned value in case it also
    contains references to other variables.

    Then resolve the variable that's being assigned to using the helper
    function resolveLocal()
    * */
    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }


    /* VAR expression resolvement

    First, we check if the variable is being accessed inside its own initializer.
        Ex:
            {
                var a = a;
            }

    If the variable exist in the current scope but its value is 'false',
    that means that variable is declared but not defined. We report error.

    Then we actually resolve the variable expression with the helper function resolveLocal()
    * */
    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if  (   scopes.isEmpty()
            &&  scopes.peek().get(expr.name.lexeme) == Boolean.FALSE
            ) {
            Lox.error(expr.name, "Can't read local variable in its own initializer");
        }

        resolveLocal(expr, expr.name);
        return null;
    }


    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }


    /* Here we can see how resolvement is different from interpretation.
    When we resolve an 'if' statement there is no 'control flow'. We touch
    all branches of the statement: condition, thenBranch, elseBranch.
    In the interpreter we execute only the branch that is run. But here in the
    static analysis we analyze every branch that 'could' be run. Because either
    of the branches could be reached at runtime, we resolve both.
    * */
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if(stmt.elseBranch != null) resolve(stmt.elseBranch);

        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }
        if (stmt.value != null) resolve(stmt.value);
        return null;
    }

    /* Similar to the 'if' statement we resolve the condition and
    * the body of the 'while' statement only once.
    * */
    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    // Expressions
    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    // HELPER METHODS

    // Walk the list of statements and resolve each one
    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }


    /* Helper function to resolve functions
    * */
    private void resolveFunction(Expr.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();                                   // Create new scope for the body

        for (Token param : function.parameters) {       // Bind the parameters to the new scope
            declare(param);
            define(param);
        }

        resolve(function.body);                         // Resolve the function in that scope
        endScope();                                     // Exit the scope

        currentFunction = enclosingFunction;
    }

    /* Lexical scopes nest in both the interpreter and the resolver.
    * They behave like a stack.
    * */
    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    // Exiting a scope
    private void endScope() {
        scopes.pop();
    }

    /* 1) Declaration

    Adds the variable to the innermost scope so that it shadows anu outer one.
    Making the boolean 'false' in the entry <varName, boolean>(<String, Boolean>)
    means that the variable is in 'not ready' state. We are not finished resolving
    the variable's initializer.
    * */
    private void declare(Token name) {
        if (scopes.isEmpty()) return;

        Map<String, Boolean> scope = scopes.peek();

        // Make declaring multiple variable with same ame in a local scope an error.
        // Note: We allow this in the global scope. Mainly when we use the REPL
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already a variable with this name in this scope");
        }

        scope.put(name.lexeme, false);
    }

    /* 2) Defining the variable

    After the variable initializer has been resolved we can change the variable to
    'available' state by making the boolean 'true'.
    * */
    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().put(name.lexeme, true);
    }


    /* Resolve local expression

    Starting from the innermost scope working to the outwards, look in each scope for a
    matching name.

    If we find the variable, we resolve it.

    If the variable is never found, leave it unresolved and assume it's global.
    * */
    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0 ; i--) {
            if(scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }
}
