package com.interpreters.lox;

import java.awt.event.WindowStateListener;
import java.util.List;

class LoxFunction implements LoxCallable {
    private final boolean       isInitializer;
    private final Expr.Function declaration;
    private final Environment   closure;
    private final String        name;

    LoxFunction(String name, Expr.Function declaration, Environment closure, boolean isInitializer) {
        this.isInitializer  = isInitializer;
        this.declaration    = declaration;
        this.closure        = closure;
        this.name           = name;
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(name, declaration, environment, isInitializer);
    }

    // Returns null in case the function doesn't return anything
    @Override
    public Object call (Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);

        for (int i = 0; i < declaration.parameters.size(); i++) {
            environment.define(declaration.parameters.get(i).lexeme,
                                arguments.get(i));
        }

        // Catch a 'return' statement from the function call
        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {                                            // If try to return from an 'init' method, we
            if (isInitializer) return closure.getAt(0, "this");     // return the instance instead of returning a value

            return returnValue.value;
        }

        return null;
    }

    @Override
    public int arity() {
        return declaration.parameters.size();
    }

    @Override
    public String toString() {
        if (name == null) return "<fn>";
        return "<fn " + name + ">";
    }
}
