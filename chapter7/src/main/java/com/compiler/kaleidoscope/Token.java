package com.compiler.kaleidoscope;

// The lexer returns tokens [0-255] if it is an unknown character, otherwise one
// of these for known things.
public enum Token {
    TOK_EOF(-1),

    // commands
    TOK_DEF(-2),
    TOK_EXTERN(-3),

    // primary
    TOK_IDENTIFIER(-4),
    TOK_NUMBER(-5),

    // control
    TOK_IF(-6),
    TOK_THEN(-7),
    TOK_ELSE(-8),
    TOK_FOR(-9),
    TOK_IN(-10),
    TOK_BINARY(-11),
    TOK_UNARY(-12),
    TOK_VAR(-13);

    private final int value;

    Token(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }
}
