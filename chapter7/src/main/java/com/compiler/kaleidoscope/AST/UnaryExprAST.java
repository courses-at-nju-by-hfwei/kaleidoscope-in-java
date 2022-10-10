package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.Logger;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.*;

/// UnaryExprAST - Expression class for a unary operator.
public class UnaryExprAST extends ExprAST{
    char op;
    ExprAST operand;

    public UnaryExprAST(char op, ExprAST operand) {
        this.op = op;
        this.operand = operand;
    }

    @Override
    public LLVMValueRef codegen() {
        LLVMValueRef operandV = operand.codegen();
        if (operandV == null) {
            return null;
        }

        LLVMValueRef F = LLVMGetNamedFunction(CodeGenerator.theModule, "unary" + op);
        if (F == null) {
            return Logger.logErrorV("Unknown unary operator");
        }
        PointerPointer<Pointer> operandArg = new PointerPointer<>(1)
                .put(0, operandV);
        return LLVMBuildCall(CodeGenerator.builder, F, operandArg, 1, "unop");
    }
}
