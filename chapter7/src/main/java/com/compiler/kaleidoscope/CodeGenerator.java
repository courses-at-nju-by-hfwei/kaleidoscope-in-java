package com.compiler.kaleidoscope;

import org.bytedeco.llvm.LLVM.*;

import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.llvm.global.LLVM.*;

public class CodeGenerator {
    public static LLVMContextRef theContext;
    public static LLVMBuilderRef builder;
    public static LLVMModuleRef theModule;
    public static Map<String, LLVMValueRef> namedValues;

    public static LLVMPassManagerRef theFPM;

    static {
        theContext = LLVMContextCreate();
        builder = LLVMCreateBuilderInContext(theContext);
        namedValues = new HashMap<>();

        // Open a new module.
        theModule = LLVMModuleCreateWithNameInContext("my cool jit", theContext);

        // Create a new pass manager attached to it.
        theFPM = LLVMCreateFunctionPassManagerForModule(theModule);

        // Promote allocas to registers.
        LLVMAddPromoteMemoryToRegisterPass(theFPM);
        // Do simple "peephole" optimizations and bit-twiddling optzns.
        LLVMAddInstructionCombiningPass(theFPM);
        // Reassociate expressions.
        LLVMAddReassociatePass(theFPM);
        // Eliminate Common SubExpressions.
        LLVMAddGVNPass(theFPM);
        // Simplify the control flow graph (deleting unreachable blocks, etc).
        LLVMAddCFGSimplificationPass(theFPM);

        LLVMInitializeFunctionPassManager(theFPM);
    }
}
