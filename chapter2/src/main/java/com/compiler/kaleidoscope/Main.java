package com.compiler.kaleidoscope;

import static com.compiler.kaleidoscope.Parser.*;
import static com.compiler.kaleidoscope.Token.*;

public class Main {
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
    }
}
