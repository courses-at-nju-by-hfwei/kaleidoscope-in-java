package com.compiler.kaleidoscope.utils;

import com.compiler.kaleidoscope.CodeGenerator;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.*;

public class AllocaHelper {
    /// CreateEntryBlockAlloca - Create an alloca instruction in the entry block of
    /// the function.  This is used for mutable variables etc.
    public static LLVMValueRef createEntryBlockAlloca(LLVMValueRef theFunction, String varName) {
        LLVMBuilderRef builder = LLVMCreateBuilderInContext(CodeGenerator.theContext);
        LLVMBasicBlockRef entry = LLVMGetEntryBasicBlock(theFunction);
        LLVMValueRef firstInstr = LLVMGetFirstInstruction(entry);
        LLVMPositionBuilder(builder, entry, firstInstr);
        return LLVMBuildAlloca(builder, Common.DOUBLE_TYPE, varName);
    }
}
