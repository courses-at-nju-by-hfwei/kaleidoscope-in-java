package com.compiler.kaleidoscope;

import com.compiler.kaleidoscope.AST.FunctionAST;
import com.compiler.kaleidoscope.AST.PrototypeAST;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import static com.compiler.kaleidoscope.Parser.*;
import static com.compiler.kaleidoscope.Token.*;
import static org.bytedeco.llvm.global.LLVM.*;

public class Main {
    public static final BytePointer error = new BytePointer();

    static LLVMExecutionEngineRef engine = new LLVMExecutionEngineRef();

    public static void handleDefinition() {
        FunctionAST fnAST = parseDefinition();
        if (fnAST != null) {
            System.err.println("Parsed a function definition.");
            fnAST.codegen();
        } else {
            // Skip token for error recovery.
            getNextToken();
        }
    }

    public static void handleExtern() {
        PrototypeAST protoAST = parseExtern();
        if (protoAST != null) {
            System.err.println("Parsed an extern.");
        } else {
            // Skip token for error recovery.
            getNextToken();
        }
    }

    public static void handleTopLevelExpression() {
        // Evaluate a top-level expression into an anonymous function.
        FunctionAST topAST = parseTopLevelExpr();
        if (topAST != null) {
            System.err.println("Parsed a top-level expr");

            LLVMValueRef code = topAST.codegen();
            if (code != null) {
                // fixme: shouldn't create an engine every time running a function
                if (LLVMCreateExecutionEngineForModule(engine, CodeGenerator.theModule, error) != 0) {
                    System.err.printf("failed to create execution engine, %s\n", error);
                    LLVMDisposeMessage(error);
                    return;
                }
                LLVMGenericValueRef result = LLVMRunFunction(engine, code, 0, new PointerPointer<>());
                System.err.printf("Evaluated to %f\n", LLVMGenericValueToFloat(LLVMDoubleType(), result));
            }
        } else {
            // Skip token for error recovery.
            getNextToken();
        }
    }

    /// top ::= definition | external | expression | ';'
    static void mainLoop() {
        while (true) {
            if (curTok == TOK_EOF.getValue()) {
                return;
            } else if (curTok == ';') {
                getNextToken();
            } else if (curTok == TOK_DEF.getValue()) {
                handleDefinition();
            } else if (curTok == TOK_EXTERN.getValue()) {
                handleExtern();
            } else {
                handleTopLevelExpression();
            }
        }
    }

    public static void main(String[] args) {
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMInitializeNativeTarget();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMLinkInMCJIT();

        // Prime the first token.
        getNextToken();

        // Run the main "interpreter loop" now.
        mainLoop();

        LLVMDumpModule(CodeGenerator.theModule);
    }
}
