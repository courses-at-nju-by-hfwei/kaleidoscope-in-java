package com.compiler.kaleidoscope.AST;

/// NumberExprAST - Expression class for numeric literals like "1.0".
public class NumberExprAST extends ExprAST {
    double val;

    public NumberExprAST(double val) {
        this.val = val;
    }
}
