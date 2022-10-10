package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.Common;
import com.compiler.kaleidoscope.utils.Logger;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
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
        // Special case '=' because we don't want to emit the LHS as an expression.
        if (op == '=') {
            // Assignment requires the LHS to be an identifier.
            if (!(LHS instanceof VariableExprAST LHSE)) {
                return Logger.logErrorV("destination of '=' must be a variable");
            }

            // Codegen the RHS.
            LLVMValueRef val = RHS.codegen();
            if (val == null) {
                return null;
            }

            // Look up the name.
            LLVMValueRef variable = CodeGenerator.namedValues.get(LHSE.getName());
            if (variable == null) {
                return Logger.logErrorV("Unknown variable name");
            }

            LLVMBuildStore(CodeGenerator.builder, val, variable);
            return val;
        }

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
                return LLVMBuildUIToFP(CodeGenerator.builder, L, Common.DOUBLE_TYPE,"booltmp");
            default:
                break;
        }

        // If it wasn't a builtin binary operator, it must be a user defined one. Emit
        // a call to it.
        LLVMValueRef F = LLVMGetNamedFunction(CodeGenerator.theModule, "binary" + op);
        assert(F != null);

        PointerPointer<Pointer> ops = new PointerPointer<>(2)
                .put(0, L)
                .put(1, R);
        return LLVMBuildCall(CodeGenerator.builder, F, ops, 2,"binop");
    }
}
