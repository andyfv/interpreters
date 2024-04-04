package com.interpreters.lox;

/* Error class that tracks the @token that identifies
where in the code the runtime error occurred.
* */
class RuntimeError extends RuntimeException {
    final Token token;

    RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }
}
