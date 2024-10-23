package com.interpreters.lox;

/* Wrapper around the function return value. We want this exception to unwind
    all the way to where the function call began, the LoxFunction.call() method.
* */
class Return extends RuntimeException {
    final Object value;

    Return(Object value) {
        super(null, null, false, false) ;
        this.value = value;
    }
}
