/* LoxClass plays the role of a class and a metaclass
 *
 * */

package com.interpreters.lox;

import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    private final Map<String,LoxFunction> methods;

    LoxClass(LoxClass metaclass ,String name, Map<String, LoxFunction> methods) {
        super(metaclass);
        this.name    = name;
        this.methods = methods;
    }

    LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        return null;
    }

    /* Here is where class instances are created
        1) Create class instance(object)
        2) Check to see if there is 'init' (initializor) method
            2.1) If there is a 'init' method bind it with the instance and
                    invoke it like a normal method
        3) Return the instance
    * */
    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);

        LoxFunction initializer = findMethod("init");
        if (initializer != null) initializer.bind(instance).call(interpreter, arguments);

        return instance;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public String toString() {
        return name;
    }
}
