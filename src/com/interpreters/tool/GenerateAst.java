package com.interpreters.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];

        // Expression nodes
        defineAst(outputDir, "Expr", Arrays.asList(
                "Assign     : Token name, Expr value",
                "Binary     : Expr left, Token operator, Expr right",
                "Function   : List<Token> parameters, List<Stmt> body",
                "Call       : Expr callee, Token paren, List<Expr> arguments",
                "Get        : Expr object, Token name",
                "Set        : Expr object, Token name, Expr value",
                "Super      : Token keyword, Token method",
                "This       : Token keyword",
                "Grouping   : Expr expression",
                "Literal    : Object value",
                "Logical    : Expr left, Token operator, Expr right",
                "Unary      : Token operator, Expr right",
                "Variable   : Token name"
        ));

        // Statement nodes
        defineAst(outputDir, "Stmt", Arrays.asList(
                "Block      : List<Stmt> statements",
                "Class      : Token name, Expr.Variable superclass, List<Stmt.Function> methods," +
                                " List<Stmt.Function> classMethods",
                "Expression : Expr expression",
                "Function   : Token name, Expr.Function function",
                "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
                "Print      : Expr expression",
                "Return     : Token keyword, Expr value",
                "While      : Expr condition, Stmt body",
                "Var        : Token name, Expr initializer"
        ));
    }

    // Create Stmt.java or Expr.java file with the equivalent classes
    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        String      path    = outputDir + "/" + baseName + ".java";
        PrintWriter writer  = new PrintWriter(path, "UTF-8");

        writer.println("package com.interpreters.lox;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println("abstract class " + baseName + " {");
        defineVisitor(writer, baseName, types);

        for (String type : types) {
            String className    = type.split(":")[0].trim();
            String fields       = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        // accept() method
        writer.println();
        writer.println("  abstract <R> R accept(Visitor<R> visitor);");

        writer.println("}");
        writer.close();
    }

    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
        writer.println("    interface Visitor<R> {");

        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("    R visit" + typeName + baseName + "(" +
                    typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("    }");
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
        // Define class
        writer.println("  static class " + className + " extends " + baseName + " {");

        // Define fields
        String[] fields = fieldList.split(", ");
        writer.println();
        for (String field : fields) {
            writer.println("    final " + field + ";");
        }

        // Define constructor
        writer.println("    " + className + "(" + fieldList + ") {");

        // Store parameters in fields
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("    this." + name + " = " + name + ";");
        }

        // Close constructor block
        writer.println("    }");

        // Visitor pattern
        writer.println();
        writer.println("    @Override");
        writer.println("    <R> R accept(Visitor<R> visitor) {");
        writer.println("        return visitor.visit" + className + baseName + "(this);");
        writer.println("    }");

        // Close class definition block
        writer.println("  }");
    }
}
