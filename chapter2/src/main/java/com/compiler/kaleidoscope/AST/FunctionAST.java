package com.compiler.kaleidoscope.AST;

public class FunctionAST {
    PrototypeAST proto;
    ExprAST body;

    public FunctionAST(PrototypeAST proto, ExprAST body) {
        this.proto = proto;
        this.body = body;
    }
}
