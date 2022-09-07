package com.compiler.kaleidoscope.AST;

import java.util.List;

/// CallExprAST - Expression class for function calls.
public class CallExprAST extends ExprAST{
    String callee;
    List<ExprAST> args;

    public CallExprAST(String callee, List<ExprAST> args) {
        this.callee = callee;
        this.args = args;
    }
}
