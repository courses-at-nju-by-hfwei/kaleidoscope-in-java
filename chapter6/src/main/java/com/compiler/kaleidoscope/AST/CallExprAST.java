package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.Logger;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.List;

import static org.bytedeco.llvm.global.LLVM.*;

/// CallExprAST - Expression class for function calls.
public class CallExprAST extends ExprAST{
    String callee;
    List<ExprAST> args;

    public CallExprAST(String callee, List<ExprAST> args) {
        this.callee = callee;
        this.args = args;
    }

    @Override
    public LLVMValueRef codegen() {
        // Look up the name in the global module table.
        LLVMValueRef calleeF = LLVMGetNamedFunction(CodeGenerator.theModule, callee);
        if (calleeF == null) {
            return Logger.logErrorV("Unknown function referenced");
        }

        // If argument mismatch error.
        if (LLVMCountParams(calleeF) != args.size()) {
            return Logger.logErrorV("Incorrect # arguments passed");
        }

        int argCount = args.size();
        PointerPointer<LLVMValueRef> argsv = new PointerPointer<>(argCount);
        for (int i = 0; i < argCount; ++i) {
            argsv.put(i, args.get(i).codegen());
            if (argsv.get(i) == null) {
                return null;
            }
        }

        return LLVMBuildCall(CodeGenerator.builder, calleeF, argsv, args.size(),"calltmp");
    }
}
