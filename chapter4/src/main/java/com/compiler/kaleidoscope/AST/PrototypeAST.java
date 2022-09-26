package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.List;

import static org.bytedeco.llvm.global.LLVM.*;

/// PrototypeAST - This class represents the "prototype" for a function,
/// which captures its name, and its argument names (thus implicitly the number
/// of arguments the function takes).
public class PrototypeAST {
    String name;
    List<String> args;

    public PrototypeAST(String name, List<String> args) {
        this.name = name;
        this.args = args;
    }

    public String getName() {
        return this.name;
    }

    public LLVMValueRef codegen() {
        // Make the function type:  double(double,double) etc.
        int argCount = args.size();
        PointerPointer<LLVMTypeRef> paramTypes = new PointerPointer<>(argCount);
        for (int i = 0; i < argCount; ++i) {
            paramTypes.put(i, LLVMDoubleTypeInContext(CodeGenerator.theContext));
        }

        LLVMTypeRef retType = LLVMFunctionType(LLVMDoubleTypeInContext(CodeGenerator.theContext), paramTypes, argCount, 0);

        LLVMValueRef function = LLVMAddFunction(CodeGenerator.theModule, name, retType);

        // Set names for all arguments.
        for (int i = 0; i < argCount; ++i) {
            LLVMValueRef arg = LLVMGetParam(function, i);
            String argName = args.get(i);
            LLVMSetValueName2(arg, argName, argName.length());
        }

        return function;
    }
}
