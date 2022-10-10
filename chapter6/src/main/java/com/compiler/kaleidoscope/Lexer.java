package com.compiler.kaleidoscope;

import java.io.IOException;
import java.io.InputStream;

import static com.compiler.kaleidoscope.Token.*;


public class Lexer {
    public static String identifierStr = ""; // Filled in if tok_identifier
    public static double numVal;        // Filled in if tok_number

    private static final InputStream inputStream = System.in;

    private static final int EOF = -1;

    private static int lastChar = ' ';

    /// getTok - Return the next token from standard input.
    public static int getTok() throws IOException {
        // Skip any whitespace.
        while (lastChar == ' ' || lastChar == '\n') {
            lastChar = inputStream.read();
        }

        // identifier: [a-zA-Z][a-zA-Z0-9]*
        if (Character.isLetter(lastChar)) {
            identifierStr = String.valueOf((char) lastChar);
            while (Character.isLetterOrDigit(lastChar = inputStream.read())) {
                identifierStr += (char) lastChar;
            }

            if (identifierStr.equals("def")) {
                return TOK_DEF.getValue();
            }
            if (identifierStr.equals("extern")) {
                return TOK_EXTERN.getValue();
            }
            if (identifierStr.equals("if")) {
                return TOK_IF.getValue();
            }
            if (identifierStr.equals("then")) {
                return TOK_THEN.getValue();
            }
            if (identifierStr.equals("else")) {
                return TOK_ELSE.getValue();
            }
            if (identifierStr.equals("for")) {
                return TOK_FOR.getValue();
            }
            if (identifierStr.equals("in")) {
                return TOK_IN.getValue();
            }
            return TOK_IDENTIFIER.getValue();
        }

        // Number: [0-9.]+
        if (Character.isDigit(lastChar) || lastChar == '.') {
            String numStr = "";
            do {
                numStr += (char) lastChar;
                lastChar = inputStream.read();
            } while (Character.isDigit(lastChar) || lastChar == '.');

            numVal = Double.parseDouble(numStr);
            return TOK_NUMBER.getValue();
        }

        if (lastChar == '#') {
            // Comment until end of line.
            do {
                lastChar = inputStream.read();
            } while (lastChar != EOF && lastChar != '\n' && lastChar != '\r');

            if (lastChar != EOF) {
                return getTok();
            }
        }

        // Check for end of file.  Don't eat the EOF.
        if (lastChar == EOF) {
            return TOK_EOF.getValue();
        }

        // Otherwise, just return the character as its ascii value.
        int thisChar = lastChar;
        lastChar = inputStream.read();
        return thisChar;
    }
}
