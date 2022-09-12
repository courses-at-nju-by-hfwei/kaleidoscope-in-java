package com.compiler.kaleidoscope;

import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.llvm.global.LLVM.*;

public class CodeGenerator {
    public static LLVMContextRef theContext;
    public static LLVMBuilderRef builder;
    public static LLVMModuleRef theModule;
    public static Map<String, LLVMValueRef> namedValues;

    static {
        theContext = LLVMContextCreate();
        builder = LLVMCreateBuilderInContext(theContext);
        theModule = LLVMModuleCreateWithNameInContext("module", theContext);
        namedValues = new HashMap<>();
    }
}
