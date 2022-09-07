package com.compiler.kaleidoscope.AST;

/// VariableExprAST - Expression class for referencing a variable, like "a".
public class VariableExprAST extends ExprAST {
    String name;

    public VariableExprAST(String name) {
        this.name = name;
    }
}
