package com.interpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    // Initializers
    Environment() {                         // Global scope - no enclosing scope
        enclosing = null;
    }

    Environment(Environment enclosing) {    // Local scope - with enclosing scope
        this.enclosing = enclosing;
    }

    // Variable assignment: binds a (name -> value)
    void define(String name, Object value) {
        values.put(name, value);
    }

    // Variable lookup
    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(
                name,
                "Undefined variable '" + name.lexeme + "'.");
    }


    // Variable reassignment. Not allowed to create new variable
    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }
}