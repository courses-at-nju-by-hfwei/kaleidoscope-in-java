package com.compiler.kaleidoscope;

import com.compiler.kaleidoscope.AST.*;
import com.compiler.kaleidoscope.utils.Logger;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;

import static com.compiler.kaleidoscope.Token.*;

public class Parser {
    /// CurTok/getNextToken - Provide a simple token buffer.  CurTok is the current
    /// token the parser is looking at.  getNextToken reads another token from the
    /// lexer and updates CurTok with its results.
    public static int curTok;

    /// BinopPrecedence - This holds the precedence for each binary operator that is
    /// defined.
    public static Map<Integer, Integer> binopPrecedence = new HashMap<>();

    static {
        // Install standard binary operators.
        // 1 is lowest precedence.
        binopPrecedence.put((int) '=', 2);
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
    ///   ::= varexpr
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
        } else if (curTok == TOK_VAR.getValue()) {
            return parseVarExpr();
        }
        else {
            return Logger.logError("unknown token when expecting an expression");
        }
    }

    /// expression
    ///       ::= unary binoprhs
    public static ExprAST parseExpression() {
        ExprAST LHS = parseUnary();
        if (LHS == null) {
            return null;
        }
        return parseBinOpRHS(0, LHS);
    }

    /// binoprhs
    ///   ::= ('+' unary)*
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

            // Parse the unary expression after the binary operator.
            ExprAST RHS = parseUnary();
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
    ///   ::= binary LETTER number? (id, id)
    ///   ::= unary LETTER (id)
    public static PrototypeAST parsePrototype() {
        String fnName;
        int kind; // 0 = identifier, 1 = unary, 2 = binary.
        int binaryPrecedence = 30;

        if (curTok == TOK_IDENTIFIER.getValue()) {
            fnName = Lexer.identifierStr;
            kind = 0;
            getNextToken();
        } else if (curTok == TOK_BINARY.getValue()) {
            getNextToken();
            if (!CharUtils.isAscii((char) curTok)) {
                return Logger.logErrorP("Expected binary operator");
            }
            fnName = "binary";
            fnName += (char) curTok;
            kind = 2;
            getNextToken();

            // Read the precedence if present.
            if (curTok == TOK_NUMBER.getValue()) {
                if (Lexer.numVal < 1 || Lexer.numVal > 100) {
                    return Logger.logErrorP("Invalid precedence: must be 1..100");
                }
                binaryPrecedence = (int) Lexer.numVal;
                getNextToken();
            }
        } else if (curTok == TOK_UNARY.getValue()) {
            getNextToken();
            if (!CharUtils.isAscii((char) curTok)) {
                return Logger.logErrorP("Expected unary operator");
            }
            fnName = "unary";
            fnName += (char) curTok;
            kind = 1;
            getNextToken();
        } else {
            return Logger.logErrorP("Expected function name in prototype");
        }

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

        // Verify right number of names for operator.
        if (kind != 0 && argNames.size() != kind) {
            return Logger.logErrorP("Invalid number of operands for operator");
        }

        return new PrototypeAST(fnName, argNames, kind != 0, binaryPrecedence);
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

    /// unary
    ///   ::= primary
    ///   ::= '!' unary
    public static ExprAST parseUnary() {
        // If the current token is not an operator, it must be a primary expr.
        if (!CharUtils.isAscii((char) curTok) || curTok == '(' || curTok == ',') {
            return parsePrimary();
        }

        // If this is a unary operator, read it.
        int op = curTok;
        getNextToken();
        ExprAST operand = parseUnary();
        if (operand != null) {
            return new UnaryExprAST((char) op, operand);
        }
        return null;
    }

    /// varexpr ::= 'var' identifier ('=' expression)?
    //                    (',' identifier ('=' expression)?)* 'in' expression
    private static ExprAST parseVarExpr() {
        getNextToken(); // eat the var.
        List<Pair<String, ExprAST>> varNames = new ArrayList<>();

        // At least one variable name is required.
        if (curTok != TOK_IDENTIFIER.getValue()) {
            return Logger.logError("expected identifier after var");
        }

        while (true) {
            String name = Lexer.identifierStr;
            getNextToken(); // eat identifier.

            // Read the optional initializer.
            ExprAST init = null;
            if (curTok == '=') {
                getNextToken(); // eat the '='.

                init = parseExpression();
                if (init == null) {
                    return null;
                }
            }

            varNames.add(new MutablePair<>(name, init));

            // End of var list, exit loop.
            if (curTok != ',') {
                break;
            }
            getNextToken(); // eat the ','.

            if (curTok != TOK_IDENTIFIER.getValue()) {
                return Logger.logError("expected identifier list after var");
            }
        }

        // At this point, we have to have 'in'.
        if (curTok != TOK_IN.getValue()) {
            return Logger.logError("expected 'in' keyword after 'var'");
        }
        getNextToken(); // eat 'in'.

        ExprAST body = parseExpression();
        if (body == null) {
            return null;
        }

        return new VarExprAST(varNames, body);
    }

    private static int anonCount = 0;

    /// toplevelexpr ::= expression
    public static FunctionAST parseTopLevelExpr() {
        ExprAST E = parseExpression();
        if (E == null) {
            return null;
        }
        // Make an anonymous proto.
        PrototypeAST proto = new PrototypeAST("__anon_func" + anonCount++, new LinkedList<>(), false, 0);
        return new FunctionAST(proto, E);
    }
}
