package com.interpreters.lox;

class Interpreter implements Expr.Visitor<Object> {

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
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
            case MINUS: return -(double)right;
        }

        return null;
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
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
            case GREATER:       return (double)left > (double)right;
            case GREATER_EQUAL: return (double)left >= (double)right;
            case LESS:          return (double)left < (double)right;
            case LESS_EQUAL:    return (double)left <= (double)right;

            // Arithmetic
            case MINUS:         return (double)left - (double)right;
            case SLASH:         return (double)left - (double)right;
            case STAR:          return (double)left * (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }
                break;
        }

        return null;
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
}
