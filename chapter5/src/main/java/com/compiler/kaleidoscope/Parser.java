package com.compiler.kaleidoscope;

import com.compiler.kaleidoscope.AST.*;
import com.compiler.kaleidoscope.utils.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.compiler.kaleidoscope.Token.*;

public class Parser {
    /// CurTok/getNextToken - Provide a simple token buffer.  CurTok is the current
    /// token the parser is looking at.  getNextToken reads another token from the
    /// lexer and updates CurTok with its results.
    public static int curTok;

    /// BinopPrecedence - This holds the precedence for each binary operator that is
    /// defined.
    static Map<Integer, Integer> binopPrecedence = new HashMap<>();

    static {
        // Install standard binary operators.
        // 1 is lowest precedence.
        binopPrecedence.put((int) '<', 10);
        binopPrecedence.put((int) '+', 20);
        binopPrecedence.put((int) '-', 20);
        binopPrecedence.put((int) '*', 40); // highest.
    }

    static int getNextToken() {
        try {
            curTok = Lexer.getTok();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        return curTok;
    }

    /// GetTokPrecedence - Get the precedence of the pending binary operator token.
    static int getTokPrecedence() {
        if (curTok < 0 || curTok > 127) {
            return -1;
        }

        return binopPrecedence.getOrDefault(curTok, -1);
    }

    /// numberexpr ::= number
    public static ExprAST parseNumberExpr() {
        ExprAST result = new NumberExprAST(Lexer.numVal);
        getNextToken(); // consume the number
        return result;
    }

    /// parenexpr ::= '(' expression ')'
    public static ExprAST parseParenExpr() {
        getNextToken(); // eat (.
        ExprAST V = parseExpression();
        if (V == null) {
            return null;
        }
        if (curTok != ')') {
            return Logger.logError("expected ')");
        }
        getNextToken(); // eat ).
        return V;
    }

    /// identifierexpr
    ///   ::= identifier
    ///   ::= identifier '(' expression* ')'
    public static ExprAST parseIdentifierExpr() {
        String idName = Lexer.identifierStr;

        getNextToken(); // eat identifier.

        if (curTok != '(') { // Simple variable ref, not a function call.
            return new VariableExprAST(idName);
        }

        // Call.
        getNextToken(); // eat (.
        List<ExprAST> args = new LinkedList<>();
        if (curTok != ')') {
            while (true) {
                ExprAST arg = parseExpression();
                if (arg != null) {
                    args.add(arg);
                } else {
                    return null;
                }

                if (curTok == ')') {
                    break;
                }

                if (curTok != ',') {
                    return Logger.logError("Expected ')' or ',' in argument list");
                }
                getNextToken();
            }
        }

        // Eat the ')'.
        getNextToken();

        return new CallExprAST(idName, args);
    }

    /// primary
    ///   ::= identifierexpr
    ///   ::= numberexpr
    ///   ::= parenexpr
    ///   ::= ifexpr
    ///   ::= forexpr
    public static ExprAST parsePrimary() {
        if (curTok == TOK_IDENTIFIER.getValue()) {
            return parseIdentifierExpr();
        } else if (curTok == TOK_NUMBER.getValue()) {
            return parseNumberExpr();
        } else if (curTok == '(') {
            return parseParenExpr();
        } else if (curTok == TOK_IF.getValue()){
            return parseIfExpr();
        } else if (curTok == TOK_FOR.getValue()) {
            return parseForExpr();
        } else {
            return Logger.logError("unknown token when expecting an expression");
        }
    }

    /// expression
    ///       ::= primary binoprhs
    public static ExprAST parseExpression() {
        ExprAST LHS = parsePrimary();
        if (LHS == null) {
            return null;
        }
        return parseBinOpRHS(0, LHS);
    }

    /// binoprhs
    ///   ::= ('+' primary)*
    public static ExprAST parseBinOpRHS(int exprPrec, ExprAST LHS) {
        // If this is a binop, find its precedence.
        while (true) {
            int tokPrec = getTokPrecedence();

            // If this is a binop that binds at least as tightly as the current binop,
            // consume it, otherwise we are done.
            if (tokPrec < exprPrec) {
                return LHS;
            }

            // Okay, we know this is a binop.
            int binOp = curTok;
            getNextToken(); // eat binop

            // Parse the primary expression after the binary operator.
            ExprAST RHS = parsePrimary();
            if (RHS == null) {
                return null;
            }

            // If BinOp binds less tightly with RHS than the operator after RHS, let
            // the pending operator take RHS as its LHS.
            int nextPrec = getTokPrecedence();
            if (tokPrec < nextPrec) {
                RHS = parseBinOpRHS(tokPrec + 1, RHS);
                if (RHS == null) {
                    return null;
                }
            }

            // Merge LHS/RHS.
            LHS = new BinaryExprAST((char) binOp, LHS, RHS);
        } // loop around to the top of the while loop.
    }

    /// prototype
    ///   ::= id '(' id* ')'
    public static PrototypeAST parsePrototype() {
        if (curTok != TOK_IDENTIFIER.getValue()) {
            return Logger.logErrorP("Expected function in prototype");
        }

        String fnName = Lexer.identifierStr;
        getNextToken();

        if (curTok != '(') {
            return Logger.logErrorP("Expected '(' in prototype");
        }

        // Read the list of argument names.
        List<String> argNames = new LinkedList<>();
        while (getNextToken() == TOK_IDENTIFIER.getValue()) {
            argNames.add(Lexer.identifierStr);
        }
        if (curTok != ')') {
            return Logger.logErrorP("Expected ')' in prototype");
        }

        // success.
        getNextToken(); // eat ')'.

        return new PrototypeAST(fnName, argNames);
    }

    /// definition ::= 'def' prototype expression
    public static FunctionAST parseDefinition() {
        getNextToken(); // eat def.
        PrototypeAST proto = parsePrototype();
        if (proto == null) {
            return null;
        }

        ExprAST E = parseExpression();
        if (E != null) {
            return new FunctionAST(proto, E);
        }
        return null;
    }

    /// external ::= 'extern' prototype
    public static PrototypeAST parseExtern() {
        getNextToken(); // eat extern.
        return parsePrototype();
    }

    /// ifexpr ::= 'if' expression 'then' expression 'else' expression
    public static ExprAST parseIfExpr() {
        getNextToken(); // eat the if.

        // condition.
        ExprAST cond = parseExpression();
        if (cond == null) {
            return null;
        }

        if (curTok != TOK_THEN.getValue()) {
            return Logger.logError("expected then");
        }
        getNextToken(); // eat the then.

        ExprAST then = parseExpression();
        if (then == null) {
            return null;
        }

        if (curTok != TOK_ELSE.getValue()) {
            return Logger.logError("expected else");
        }
        getNextToken(); // eat the else.

        ExprAST Else = parseExpression();
        if (Else == null) {
            return null;
        }

        return new IfExprAST(cond, then, Else);
    }

    /// forexpr ::= 'for' identifier '=' expr ',' expr (',' expr)? 'in' expression
    public static ExprAST parseForExpr() {
        getNextToken(); // eat the for.

        if (curTok != TOK_IDENTIFIER.getValue()) {
            return Logger.logError("expected identifier after for");
        }

        String idName = Lexer.identifierStr;
        getNextToken(); // eat identifier.

        if (curTok != '=') {
            return Logger.logError("expected '=' after for");
        }
        getNextToken(); // eat '='.

        ExprAST start = parseExpression();
        if (start == null) {
            return null;
        }

        if (curTok != ',') {
            return Logger.logError("expected ',' after for start value");
        }
        getNextToken();

        ExprAST end = parseExpression();
        if (end == null) {
            return null;
        }

        // The step value is optional.
        ExprAST step = null;
        if (curTok == ',') {
            getNextToken();
            step = parseExpression();
            if (step == null) {
                return null;
            }
        }

        if (curTok != TOK_IN.getValue()) {
            return Logger.logError("expected 'in' after for");
        }
        getNextToken();

        ExprAST body = parseExpression();
        if (body == null) {
            return null;
        }

        return new ForExprAST(idName, start, end, step, body);
    }

    private static int anonCount = 0;

    /// toplevelexpr ::= expression
    public static FunctionAST parseTopLevelExpr() {
        ExprAST E = parseExpression();
        if (E == null) {
            return null;
        }
        // Make an anonymous proto.
        PrototypeAST proto = new PrototypeAST("__anon_func" + anonCount++, new LinkedList<>());
        return new FunctionAST(proto, E);
    }
}
