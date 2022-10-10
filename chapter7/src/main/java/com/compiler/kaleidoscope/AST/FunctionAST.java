package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.Parser;
import com.compiler.kaleidoscope.utils.AllocaHelper;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.*;

public class FunctionAST {
    PrototypeAST proto;
    ExprAST body;

    public FunctionAST(PrototypeAST proto, ExprAST body) {
        this.proto = proto;
        this.body = body;
    }

    public LLVMValueRef codegen() {
        // First, check for an existing function from a previous 'extern' declaration.
        LLVMValueRef theFunction = LLVMGetNamedFunction(CodeGenerator.theModule, proto.getName());

        if (theFunction == null) {
            theFunction = proto.codegen();
        }

        if (theFunction == null) {
            return null;
        }

        /*   todo
         *   if (!TheFunction->empty())
         *     return (Function*)LogErrorV("Function cannot be redefined.");
         */

        // If this is an operator, install it.
        if (proto.isBinaryOp()) {
            Parser.binopPrecedence.put((int) proto.getOperatorName(), proto.getBinaryPrecedence());
        }

        // Create a new basic block to start insertion into.
        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(theFunction, "entry");
        LLVMPositionBuilderAtEnd(CodeGenerator.builder, entry);

        // Record the function arguments in the NamedValues map.
        CodeGenerator.namedValues.clear();
        int argCount = LLVMCountParams(theFunction);
        for (int i = 0; i < argCount; ++i) {
            LLVMValueRef arg = LLVMGetParam(theFunction, i);
            String name = proto.args.get(i);

            // Create an alloca for this variable.
            LLVMValueRef alloca = AllocaHelper.createEntryBlockAlloca(theFunction, name);

            // Store the initial value into the alloca.
            LLVMBuildStore(CodeGenerator.builder, arg, alloca);
            CodeGenerator.namedValues.put(name, alloca);
        }

        LLVMValueRef retVal = body.codegen();

        // Error reading body, remove function.
        if (retVal == null) {
            LLVMDeleteFunction(theFunction);
            return null;
        }

        // Finish off the function.
        LLVMBuildRet(CodeGenerator.builder, retVal);

        // Validate the generated code, checking for consistency.
        LLVMVerifyFunction(theFunction, LLVMAbortProcessAction);

        // Optimize the function.
        LLVMRunFunctionPassManager(CodeGenerator.theFPM, theFunction);

        return theFunction;
    }
}
