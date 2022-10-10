package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.utils.Common;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.LLVMConstReal;

/// NumberExprAST - Expression class for numeric literals like "1.0".
public class NumberExprAST extends ExprAST {
    double val;

    public NumberExprAST(double val) {
        this.val = val;
    }

    @Override
    public LLVMValueRef codegen() {
        return LLVMConstReal(Common.DOUBLE_TYPE, val);
    }
}
