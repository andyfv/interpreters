package com.interpreters.lox;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

class Interpreter implements    Expr.Visitor<Object>,
                                Stmt.Visitor<Void> {

    /*
    @environment - stored as a field so that the variables in the environment
                    stay in memory as long as the interpreter is running.
    */
    final   Environment globals             = new Environment();
    private Environment environment         = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis();
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return object.toString();
    }

    // Visit Methods


    /*
    * Variable declaration
    *
    * IF the variable has an initializer - evaluate it.
    * IF NOT, set the variable to nil (null in Java).
    * */
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;

        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt)  {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }

        return null;
    }

    //
    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if(distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }

        return value;
    }

    /*
    * Variable expression7
    * */
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);

        if  (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        String fnName = stmt.name.lexeme;
        environment.define(fnName, new LoxFunction(fnName, stmt.function, environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        throw new Return(value);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.left);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }
    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG: return !isTruthy(right);
            // dynamically (at runtime) cast the expression if it is a number
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        return null;
    }


    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left     = evaluate(expr.left);
        Object right    = evaluate(expr.right);

        switch (expr.operator.type) {
            // Equality
            case BANG_EQUAL:    return !isEqual(left, right);
            case EQUAL_EQUAL:   return isEqual(left, right);

            // Comparison
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;

            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;

            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;

            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;

            // Arithmetic
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;

            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;

            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;

            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }
                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or two strings");
        }

        return null;
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new LoxFunction(null, expr, environment);
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;

        // Checking the function arity (number of arguments a function expects)
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                                    function.arity() + " arguments but got " +
                                    arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    // Operand checker for Unary expressions
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number");
    }

    // Operand checker for Binary expressions
    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers");
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;

        /* Handle the situation if @a is null because we don't want to
        invoke equals() on a a null
        */
        if (a == null) return false;

        return a.equals(b);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }


    /* Interpreting Resolved Variables
    *
    * Each time the Resolver visits a variable it tells the interpreter
    * how many scopes (@depth) there are between the current scope and the scope
    * where the variable is defined. At runtime, this corresponds exactly
    * to the number of Environments between the current one and the enclosing
    * one where the interpreter can find the variable's value. The Resolver
    * pass that number to the Interpreters own resolve() function. We store
    * the resolution information(Map<expr, depthOfScope as Integer>) in the
    * side table @locals.
    *
    * There is no need for a special structure or naming approach to avoid
    * getting confused when there are multiple expressions that reference the
    * same variable. This is because each Expr Node is its own Java Object with
    * its unique identity.
    *
    * Note: Alternative place to store that resolution information is right
    * in the syntax tree node itself. In complex tools like IDEs that often
    * re-parse and re-resolve parts of the program frequently it may be hard to
    * find all bits of state that needs to recalculated from the syntax tree.
    * A benefit of storing the resolution data outside the nodes is that it is
    * easy to discard it by just clearing the map.
    * */
    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        environment.define(stmt.name.lexeme, null);     // Define the class name in current environment
        LoxClass klass = new LoxClass(stmt.name.lexeme);      // Transform the class node -> LoxClass (runtime representation of a class).
        environment.assign(stmt.name, klass);                 // Store the class object in the variable we previously declared.
        return null;
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;    // Hold on to the outer scope
        try {
            this.environment = environment;         // Enter the inner scope

            for (Stmt statement: statements) {      // Execute the statements
                execute(statement);                 // in the inner scope
            }
        } finally {
            this.environment = previous;            // Exit the inner scope and
                                                    // return to the previous scope
        }
    }
}
