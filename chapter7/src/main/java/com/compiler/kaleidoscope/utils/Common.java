package com.compiler.kaleidoscope.utils;

import com.compiler.kaleidoscope.CodeGenerator;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.LLVMConstReal;
import static org.bytedeco.llvm.global.LLVM.LLVMDoubleTypeInContext;

public class Common {
    public static final LLVMTypeRef DOUBLE_TYPE = LLVMDoubleTypeInContext(CodeGenerator.theContext);
    public static final LLVMValueRef ZERO = LLVMConstReal(DOUBLE_TYPE, 0);
    public static final LLVMValueRef ONE = LLVMConstReal(DOUBLE_TYPE, 1);
}
