package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.*;

/// NumberExprAST - Expression class for numeric literals like "1.0".
public class NumberExprAST extends ExprAST {
    double val;

    public NumberExprAST(double val) {
        this.val = val;
    }

    @Override
    public LLVMValueRef codegen() {
        LLVMTypeRef doubleType = LLVMDoubleTypeInContext(CodeGenerator.theContext);
        return LLVMConstReal(doubleType, val);
    }
}
