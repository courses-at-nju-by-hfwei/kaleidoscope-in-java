package com.compiler.kaleidoscope.AST;

import org.bytedeco.llvm.LLVM.LLVMValueRef;

/// ExprAST - Base class for all expression nodes.
public abstract class ExprAST {
    public abstract LLVMValueRef codegen();
}
