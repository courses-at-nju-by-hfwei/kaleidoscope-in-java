package com.compiler.kaleidoscope.AST;

import java.util.List;

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
}
