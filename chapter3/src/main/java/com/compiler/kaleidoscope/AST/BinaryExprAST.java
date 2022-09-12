package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.Logger;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.*;

/// BinaryExprAST - Expression class for a binary operator.
public class BinaryExprAST extends ExprAST {
    char op;
    ExprAST LHS, RHS;

    public BinaryExprAST(char op, ExprAST LHS, ExprAST RHS) {
        this.op = op;
        this.LHS = LHS;
        this.RHS = RHS;
    }

    @Override
    public LLVMValueRef codegen() {
        LLVMValueRef L = LHS.codegen();
        LLVMValueRef R = RHS.codegen();
        if (L == null || R == null) {
            return null;
        }

        switch (op) {
            case '+':
                return LLVMBuildFAdd(CodeGenerator.builder, L, R, "addtmp");
            case '-':
                return LLVMBuildFSub(CodeGenerator.builder, L, R, "subtmp");
            case '*':
                return LLVMBuildFMul(CodeGenerator.builder, L, R, "multmp");
            case '<':
                L = LLVMBuildFCmp(CodeGenerator.builder, LLVMRealOLT, L, R,"cmptmp");
                // Convert bool 0/1 to double 0.0 or 1.0
                return LLVMBuildUIToFP(CodeGenerator.builder, L, LLVMDoubleTypeInContext(CodeGenerator.theContext),"booltmp");
            default:
                return Logger.logErrorV("invalid binary operator");
        }
    }
}
