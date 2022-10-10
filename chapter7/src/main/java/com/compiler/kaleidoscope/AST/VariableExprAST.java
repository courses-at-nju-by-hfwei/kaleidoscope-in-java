package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.Logger;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

/// VariableExprAST - Expression class for referencing a variable, like "a".
public class VariableExprAST extends ExprAST {
    String name;

    public VariableExprAST(String name) {
        this.name = name;
    }

    @Override
    public LLVMValueRef codegen() {
        LLVMValueRef V = CodeGenerator.namedValues.get(name);
        if (V == null) {
            Logger.logErrorV("Unknown variable name");
        }
        return V;
    }
}
