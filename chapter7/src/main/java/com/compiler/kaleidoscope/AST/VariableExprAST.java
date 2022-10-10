package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.Common;
import com.compiler.kaleidoscope.utils.Logger;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.*;

/// VariableExprAST - Expression class for referencing a variable, like "a".
public class VariableExprAST extends ExprAST {
    String name;

    public VariableExprAST(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public LLVMValueRef codegen() {
        // Look this variable up in the function.
        LLVMValueRef V = CodeGenerator.namedValues.get(name);
        if (V == null) {
            Logger.logErrorV("Unknown variable name");
        }

        // Load the value.
        return LLVMBuildLoad2(CodeGenerator.builder, Common.DOUBLE_TYPE, V, name);
    }
}
