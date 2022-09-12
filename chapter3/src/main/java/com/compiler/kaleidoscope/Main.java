package com.compiler.kaleidoscope;

import com.compiler.kaleidoscope.AST.FunctionAST;
import com.compiler.kaleidoscope.AST.PrototypeAST;

import static com.compiler.kaleidoscope.Parser.*;
import static com.compiler.kaleidoscope.Token.*;
import static org.bytedeco.llvm.global.LLVM.LLVMDumpModule;

public class Main {
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
            protoAST.codegen();
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
            topAST.codegen();
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
        // Prime the first token.
        getNextToken();

        // Run the main "interpreter loop" now.
        mainLoop();

        LLVMDumpModule(CodeGenerator.theModule);
    }
}
