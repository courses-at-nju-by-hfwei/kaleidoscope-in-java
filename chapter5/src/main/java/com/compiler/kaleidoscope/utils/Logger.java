package com.compiler.kaleidoscope.utils;

import com.compiler.kaleidoscope.AST.ExprAST;
import com.compiler.kaleidoscope.AST.PrototypeAST;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class Logger {
    /// logError* - These are little helper functions for error handling.
    public static ExprAST logError(String str) {
        System.err.printf("logError: %s\n", str);
        return null;
    }

    public static PrototypeAST logErrorP(String str) {
        logError(str);
        return null;
    }

    public static LLVMValueRef logErrorV(String str) {
        logError(str);
        return null;
    }
}
