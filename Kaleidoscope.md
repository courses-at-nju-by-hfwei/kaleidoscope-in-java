# 1. Kaleidoscope: Kaleidoscope Introduction and the Lexer

## 1.1. The Kaleidoscope Language

This tutorial is illustrated with a toy language called “[Kaleidoscope](http://en.wikipedia.org/wiki/Kaleidoscope)” (derived from “meaning beautiful, form, and view”). Kaleidoscope is a procedural language that allows you to define functions, use conditionals, math, etc. Over the course of the tutorial, we’ll extend Kaleidoscope to support the if/then/else construct, a for loop, user defined operators, JIT compilation with a simple command line interface, debug info, etc.

We want to keep things simple, so the **only** datatype in Kaleidoscope is a 64-bit floating point type (aka ‘double’ in C parlance). As such, all values are implicitly double precision and the language doesn’t require type declarations. This gives the language a very nice and simple syntax. For example, the following simple example computes [Fibonacci numbers:](http://en.wikipedia.org/wiki/Fibonacci_number)

```
# Compute the x'th fibonacci number.
def fib(x)
  if x < 3 then
    1
  else
    fib(x-1)+fib(x-2)

# This expression will compute the 40th number.
fib(40)
```

We also allow Kaleidoscope to call into standard library functions - the LLVM JIT makes this really easy. This means that you can use the ‘extern’ keyword to define a function before you use it (this is also useful for mutually recursive functions). For example:

```
extern sin(arg);
extern cos(arg);
extern atan2(arg1 arg2);

atan2(sin(.4), cos(42))
```

A more interesting example is included in Chapter 6 where we write a little Kaleidoscope application that [displays a Mandelbrot Set](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl06.html#kicking-the-tires) at various levels of magnification.

Let’s dive into the implementation of this language!

## 1.2. The Lexer

When it comes to implementing a language, the first thing needed is the ability to process a text file and recognize what it says. The traditional way to do this is to use a “[lexer](http://en.wikipedia.org/wiki/Lexical_analysis)” (aka ‘scanner’) to break the input up into “tokens”. Each token returned by the lexer includes a token code and potentially some metadata (e.g. the numeric value of a number). First, we define the possibilities:

```java
public enum Token {
    TOK_EOF(-1),

    // commands
    TOK_DEF(-2),
    TOK_EXTERN(-3),

    // primary
    TOK_IDENTIFIER(-4),
    TOK_NUMBER(-5);
    private final int value;

    private Token(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }
}
```

```java
public static String identifierStr; // Filled in if tok_identifier
public static double numVal;        // Filled in if tok_number
```

Each token returned by our lexer will either be one of the Token enum values or it will be an ‘unknown’ character like ‘+’, which is returned as its ASCII value. If the current token is an identifier, the `identifierStr` global variable holds the name of the identifier. If the current token is a numeric literal (like 1.0), `numVal` holds its value. We use global variables for simplicity, but this is not the best choice for a real language implementation :).

The actual implementation of the lexer is a single function named `getTok`. The `getTok` function is called to return the next token from standard input. Its definition starts as:

```java
private static InputStream inputStream = System.in;
private static int lastChar = ' ';
/// getTok - Return the next token from standard input.
public static int getTok() throws IOException {
    // Skip any whitespace.
    while (lastChar == ' ' || lastChar == '\n') {
        lastChar = inputStream.read();
    }
```

`getTok` works by calling the  `InputStream.read()` function to read characters one at a time from standard input. It eats them as it recognizes them and stores the last character read, but not processed, in `lastChar`. The first thing that it has to do is ignore whitespace between tokens. This is accomplished with the loop above.

The next thing `getTok` needs to do is recognize identifiers and specific keywords like “def”. Kaleidoscope does this with this simple loop:

```java
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
    return TOK_IDENTIFIER.getValue();
}
```

Note that this code sets the ‘`identifierStr`’ global whenever it lexes an identifier. Also, since language keywords are matched by the same loop, we handle them here inline. Numeric values are similar:

```java
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
```

This is all pretty straightforward code for processing input. When reading a numeric value from input, we use the `Double.parseDouble` function to convert it to a numeric value that we store in `numVal`. Note that this isn’t doing sufficient error checking: it will incorrectly read “1.23.45.67” and handle it as if you typed in “1.23”. Feel free to extend it! Next we handle comments:

```java
if (lastChar == '#') {
    // Comment until end of line.
    do {
        lastChar = inputStream.read();
    } while (lastChar != EOF && lastChar != '\n' && lastChar != '\r');

    if (lastChar != EOF) {
        return getTok();
    }
}
```

We handle comments by skipping to the end of the line and then return the next token. Finally, if the input doesn’t match one of the above cases, it is either an operator character like ‘+’ or the end of the file. These are handled with this code:

```java
    // Check for end of file.  Don't eat the EOF.
    if (lastChar == EOF) {
        return TOK_EOF.getValue();
    }

    // Otherwise, just return the character as its ascii value.
    int thisChar = lastChar;
    lastChar = inputStream.read();
    return thisChar;
}
```

With this, we have the complete lexer for the basic Kaleidoscope language (the [full code listing](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl02.html#full-code-listing) for the Lexer is available in the [next chapter](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl02.html) of the tutorial). Next we’ll [build a simple parser that uses this to build an Abstract Syntax Tree](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl02.html). When we have that, we’ll include a driver so that you can use the lexer and parser together.

# 2. Kaleidoscope: Implementing a Parser and AST

## 2.1. Chapter 2 Introduction

Welcome to Chapter 2 of the “[Implementing a language with LLVM](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/index.html)” tutorial. This chapter shows you how to use the lexer, built in [Chapter 1](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl01.html), to build a full [parser](http://en.wikipedia.org/wiki/Parsing) for our Kaleidoscope language. Once we have a parser, we’ll define and build an [Abstract Syntax Tree](http://en.wikipedia.org/wiki/Abstract_syntax_tree) (AST).

The parser we will build uses a combination of [Recursive Descent Parsing](http://en.wikipedia.org/wiki/Recursive_descent_parser) and [Operator-Precedence Parsing](http://en.wikipedia.org/wiki/Operator-precedence_parser) to parse the Kaleidoscope language (the latter for binary expressions and the former for everything else). Before we get to parsing though, let’s talk about the output of the parser: the Abstract Syntax Tree.

## 2.2. The Abstract Syntax Tree (AST)

The AST for a program captures its behavior in such a way that it is easy for later stages of the compiler (e.g. code generation) to interpret. We basically want one object for each construct in the language, and the AST should closely model the language. In Kaleidoscope, we have **expressions, a prototype, and a function object**. We’ll start with expressions first:

```java
/// ExprAST - Base class for all expression nodes.
public abstract class ExprAST {
}

/// NumberExprAST - Expression class for numeric literals like "1.0".
public class NumberExprAST extends ExprAST {
    double val;

    public NumberExprAST(double val) {
        this.val = val;
    }
}
```

The code above shows the definition of the base ExprAST class and one subclass which we use for numeric literals. The important thing to note about this code is that the NumberExprAST class captures the numeric value of the literal as an instance variable. This allows later phases of the compiler to know what the stored numeric value is.

Right now we only create the AST, so there are no useful accessor methods on them. It would be very easy to add a method to pretty print the code, for example. Here are the other expression AST node definitions that we’ll use in the basic form of the Kaleidoscope language:

```java
/// VariableExprAST - Expression class for referencing a variable, like "a".
public class VariableExprAST extends ExprAST {
    String name;

    public VariableExprAST(String name) {
        this.name = name;
    }
}

/// BinaryExprAST - Expression class for a binary operator.
public class BinaryExprAST extends ExprAST {
    char op;
    ExprAST LHS, RHS;

    public BinaryExprAST(char op, ExprAST LHS, ExprAST RHS) {
        this.op = op;
        this.LHS = LHS;
        this.RHS = RHS;
    }
}

/// CallExprAST - Expression class for function calls.
public class CallExprAST extends ExprAST{
    String callee;
    List<ExprAST> args;

    public CallExprAST(String callee, List<ExprAST> args) {
        this.callee = callee;
        this.args = args;
    }
}
```

This is all (intentionally) rather straight-forward: variables capture the variable name, binary operators capture their opcode (e.g. ‘+’), and calls capture a function name as well as a list of any argument expressions. One thing that is nice about our AST is that it captures the language features without talking about the syntax of the language. Note that there is no discussion about precedence of binary operators, lexical structure, etc.

For our basic language, these are all of the expression nodes we’ll define. Because it doesn’t have conditional control flow, it isn’t Turing-complete; we’ll fix that in a later installment. The two things we need next are a way to talk about the interface to a function, and a way to talk about functions themselves:

```java
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
```

In Kaleidoscope, **functions are typed with just a count of their arguments. Since all values are double precision floating point**, the type of each argument doesn’t need to be stored anywhere. In a more aggressive and realistic language, the “ExprAST” class would probably have a type field.

With this scaffolding, we can now talk about parsing expressions and function bodies in Kaleidoscope.

## 2.3. Parser Basics

Now that we have an AST to build, we need to define the parser code to build it. The idea here is that we want to parse something like “x+y” (which is returned as three tokens by the lexer) into an AST that could be generated with calls like this:

```java
VariableExprAST LHS = new VariableExprAST("x");
VariableExprAST RHS = new VariableExprAST("y");
BinaryExprAST result = new BinaryExprAST('+', LHS, RHS);
```

In order to do this, we’ll start by defining some basic helper routines:

```java
/// curTok/getNextToken - Provide a simple token buffer.  curTok is the current
/// token the parser is looking at.  getNextToken reads another token from the
/// lexer and updates curTok with its results.
public static int curTok;

static int getNextToken() {
    try {
        curTok = Lexer.getTok();
    } catch (IOException ioException) {
        ioException.printStackTrace();
    }
    return curTok;
}
```

This implements a simple token buffer around the lexer. This allows us to look one token ahead at what the lexer is returning. Every function in our parser will assume that `curTok` is the current token that needs to be parsed.

```java
/// logError* - These are little helper functions for error handling.
static ExprAST logError(String str) {
    System.err.printf("logError: %s\n", str);
    return null;
}

static PrototypeAST logErrorP(String str) {
    logError(str);
    return null;
}
```

The `logError` routines are simple helper routines that our parser will use to handle errors. The error recovery in our parser will not be the best and is not particular user-friendly, but it will be enough for our tutorial. These routines make it easier to handle errors in routines that have various return types: they always return null.

With these basic helper functions, we can implement the first piece of our grammar: numeric literals.

## 2.4. Basic Expression Parsing

We start with numeric literals, because they are the simplest to process. For each production in our grammar, we’ll define a function which parses that production. For numeric literals, we have:

```java
/// numberexpr ::= number
public static ExprAST parseNumberExpr() {
    ExprAST result = new NumberExprAST(Lexer.numVal);
    getNextToken(); // consume the number
    return result;
}
```

This routine is very simple: it expects to be called when the current token is a `tok_number` token. It takes the current number value, creates a `NumberExprAST` node, advances the lexer to the next token, and finally returns.

There are some interesting aspects to this. The most important one is that **this routine eats all of the tokens that correspond to the production and returns the lexer buffer with the next token (which is not part of the grammar production) ready to go**. This is a fairly standard way to go for recursive descent parsers. For a better example, the parenthesis operator is defined like this:

```java
/// parenexpr ::= '(' expression ')'
public static ExprAST parseParenExpr() {
    getNextToken(); // eat (.
    ExprAST V = parseExpression();
    if (V == null) {
        return null;
    }
    if (curTok != ')') {
        return logError("expected ')");
    }
    getNextToken(); // eat ).
    return V;
}
```

This function illustrates a number of interesting things about the parser:

1. It shows how we use the LogError routines. When called, this function expects that the current token is a ‘(‘ token, but after parsing the subexpression, it is possible that there is no ‘)’ waiting. For example, if the user types in “(4 x” instead of “(4)”, the parser should emit an error. Because errors can occur, the parser needs a way to indicate that they happened: in our parser, we return null on an error.

2. Another interesting aspect of this function is that it uses recursion by calling `ParseExpression` (we will soon see that `ParseExpression` can call `ParseParenExpr`). This is powerful because it allows us to handle recursive grammars, and keeps each production very simple. Note that parentheses do not cause construction of AST nodes themselves. While we could do it this way, the most important role of parentheses are to guide the parser and provide grouping. Once the parser constructs the AST, parentheses are not needed.

The next simple production is for handling variable references and function calls:

```java
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
                return logError("Expected ')' or ',' in argument list");
            }
            getNextToken();
        }
    }

    // Eat the ')'.
    getNextToken();

    return new CallExprAST(idName, args);
}
```

This routine follows the same style as the other routines. (It expects to be called if the current token is a `TOK_IDENTIFIER` token). It also has recursion and error handling. One interesting aspect of this is that it uses ***look-ahead*** to determine if the current identifier is a stand alone variable reference or if it is a function call expression. It handles this by checking to see if the token after the identifier is a ‘(‘ token, constructing either a `VariableExprAST` or `CallExprAST` node as appropriate.

Now that we have all of our simple expression-parsing logic in place, we can define a helper function to wrap it together into one entry point. We call this class of expressions “primary” expressions, for reasons that will become more clear [later in the tutorial](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl06.html#user-defined-unary-operators). In order to parse an arbitrary primary expression, we need to determine what sort of expression it is:

```java
/// primary
///   ::= identifierexpr
///   ::= numberexpr
///   ::= parenexpr
public static ExprAST parsePrimary() {
    if (curTok == TOK_IDENTIFIER.getValue()) {
        return parseIdentifierExpr();
    } else if (curTok == TOK_NUMBER.getValue()) {
        return parseNumberExpr();
    } else if (curTok == '(') {
        return parseParenExpr();
    } else {
        return logError("unknown token when expecting an expression");
    }
}
```

Now that you see the definition of this function, it is more obvious why we can assume the state of `curTok` in the various functions. This uses look-ahead to determine which sort of expression is being inspected, and then parses it with a function call.

Now that basic expressions are handled, we need to handle binary expressions. They are a bit more complex.

## 2.5. Binary Expression Parsing

Binary expressions are significantly harder to parse because they are often ambiguous. For example, when given the string “x+y\*z”, the parser can choose to parse it as either “(x+y)\*z” or “x+(y\*z)”. With common definitions from mathematics, we expect the later parse, because “*” (multiplication) has higher *precedence* than “+” (addition).

There are many ways to handle this, but an elegant and efficient way is to use [Operator-Precedence Parsing](http://en.wikipedia.org/wiki/Operator-precedence_parser). This parsing technique uses the precedence of binary operators to guide recursion. To start with, we need a table of precedences:

```java
/// BinopPrecedence - This holds the precedence for each binary operator that is
/// defined.
static Map<Integer, Integer> binopPrecedence = new HashMap<>();

/// GetTokPrecedence - Get the precedence of the pending binary operator token.
static int getTokPrecedence() {
    if (curTok < 0 || curTok > 127) {
        return -1;
    }

    return binopPrecedence.getOrDefault(curTok, -1);
}

static {
    // Install standard binary operators.
    // 1 is lowest precedence.
    binopPrecedence.put((int) '<', 10);
    binopPrecedence.put((int) '+', 20);
    binopPrecedence.put((int) '-', 20);
    binopPrecedence.put((int) '*', 40); // highest.
}
```

For the basic form of Kaleidoscope, we will only support 4 binary operators (this can obviously be extended by you, our brave and intrepid reader). The `getTokPrecedence` function returns the precedence for the current token, or -1 if the token is not a binary operator. Having a map makes it easy to add new operators and makes it clear that the algorithm doesn’t depend on the specific operators involved, but it would be easy enough to eliminate the map and do the comparisons in the `getTokPrecedence` function. (Or just use a fixed-size array).

With the helper above defined, we can now start parsing binary expressions. The basic idea of operator precedence parsing is to break down an expression with potentially ambiguous binary operators into pieces. Consider, for example, the expression “a+b+(c+d)\*e\*f+g”. Operator precedence parsing considers this as a stream of primary expressions separated by binary operators. As such, it will first parse the leading primary expression “a”, then it will see the pairs [+, b] [+, (c+d)] [\*, e] [\*, f] and [+, g]. Note that because parentheses are primary expressions, the binary expression parser doesn’t need to worry about nested subexpressions like (c+d) at all.

To start, an expression is a primary expression potentially followed by a sequence of [binop,primaryexpr] pairs:

```java
/// expression
///       ::= primary binoprhs
public static ExprAST parseExpression() {
    ExprAST LHS = parsePrimary();
    if (LHS == null) {
        return null;
    }
    return parseBinOpRHS(0, LHS);
}
```

`parseBinOpRHS` is the function that parses the sequence of pairs for us. It takes a precedence and a pointer to an expression for the part that has been parsed so far. Note that “x” is a perfectly valid expression: As such, “binoprhs” is allowed to be empty, in which case it returns the expression that is passed into it. In our example above, the code passes the expression for “a” into `parseBinOpRHS` and the current token is “+”.

The precedence value passed into `parseBinOpRHS` indicates **the *minimal operator precedence* that the function is allowed to eat**. For example, if the current pair stream is [+, x] and `ParseBinOpRHS` is passed in a precedence of 40, it will not consume any tokens (because the precedence of ‘+’ is only 20). With this in mind, `parseBinOpRHS` starts with:

```java
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
```

This code gets the precedence of the current token and checks to see if if is too low. Because we defined invalid tokens to have a precedence of -1, this check implicitly knows that the pair-stream ends when the token stream runs out of binary operators. If this check succeeds, we know that the token is a binary operator and that it will be included in this expression:

```java
// Okay, we know this is a binop.
int binOp = curTok;
getNextToken(); // eat binop

// Parse the primary expression after the binary operator.
ExprAST RHS = parsePrimary();
if (RHS == null) {
    return null;
}
```

As such, this code eats (and remembers) the binary operator and then parses the primary expression that follows. This builds up the whole pair, the first of which is [+, b] for the running example.

Now that we parsed the left-hand side of an expression and one pair of the RHS sequence, we have to decide which way the expression associates. In particular, we could have “(a+b) binop unparsed” or “a + (b binop unparsed)”. To determine this, we look ahead at “binop” to determine its precedence and compare it to BinOp’s precedence (which is ‘+’ in this case):

```java
// If BinOp binds less tightly with RHS than the operator after RHS, let
// the pending operator take RHS as its LHS.
int nextPrec = getTokPrecedence();
if (tokPrec < nextPrec) {
```

If the precedence of the binop to the right of “RHS” is lower or equal to the precedence of our current operator, then we know that the parentheses associate as “(a+b) binop …”. In our example, the current operator is “+” and the next operator is “+”, we know that they have the same precedence. In this case we’ll create the AST node for “a+b”, and then continue parsing:

```java
     ... if body omitted ...
    }

    // Merge LHS/RHS.
    LHS = new BinaryExprAST((char) binOp, LHS, RHS);
} // loop around to the top of the while loop.
```

In our example above, this will turn “a+b+” into “(a+b)” and execute the next iteration of the loop, with “+” as the current token. The code above will eat, remember, and parse “(c+d)” as the primary expression, which makes the current pair equal to [+, (c+d)]. It will then evaluate the ‘if’ conditional above with “\*” as the binop to the right of the primary. In this case, the precedence of “*” is higher than the precedence of “+” so the if condition will be entered.

The critical question left here is “how can the if condition parse the right hand side in full”? In particular, to build the AST correctly for our example, it needs to get all of “(c+d)\*e\*f” as the RHS expression variable. The code to do this is surprisingly simple (code from the above two blocks duplicated for context):

```java
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
```

At this point, we know that the binary operator to the RHS of our primary has higher precedence than the binop we are currently parsing. As such, we know that any sequence of pairs whose operators are all higher precedence than “+” should be parsed together and returned as “RHS”. To do this, we recursively invoke the `parseBinOpRHS` function specifying “tokPrec+1” as the minimum precedence required for it to continue. In our example above, this will cause it to return the AST node for “(c+d)\*e\*f” as RHS, which is then set as the RHS of the ‘+’ expression.

Finally, on the next iteration of the while loop, the “+g” piece is parsed and added to the AST. With this little bit of code (14 non-trivial lines), we correctly handle fully general binary expression parsing in a very elegant way. This was a whirlwind tour of this code, and it is somewhat subtle. I recommend running through it with a few tough examples to see how it works.

This wraps up handling of expressions. At this point, we can point the parser at an arbitrary token stream and build an expression from it, stopping at the first token that is not part of the expression. Next up we need to handle function definitions, etc.

## 2.6. Parsing the Rest

The next thing missing is handling of function prototypes. In Kaleidoscope, these are used both for ‘extern’ function declarations as well as function body definitions. The code to do this is straight-forward and not very interesting (once you’ve survived expressions):

```java
/// prototype
///   ::= id '(' id* ')'
public static PrototypeAST parsePrototype() {
    if (curTok != TOK_IDENTIFIER.getValue()) {
        return logErrorP("Expected function in prototype");
    }

    String fnName = Lexer.identifierStr;
    getNextToken();

    if (curTok != '(') {
        return logErrorP("Expected '(' in prototype");
    }

    // Read the list of argument names.
    List<String> argNames = new LinkedList<>();
    while (getNextToken() == TOK_IDENTIFIER.getValue()) {
        argNames.add(Lexer.identifierStr);
    }
    if (curTok != ')') {
        return logErrorP("Expected ')' in prototype");
    }

    // success.
    getNextToken(); // eat ')'.
    
    return new PrototypeAST(fnName, argNames);
}
```

Given this, a function definition is very simple, just a prototype plus an expression to implement the body:

```java
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
```

In addition, we support ‘extern’ to declare functions like ‘sin’ and ‘cos’ as well as to support forward declaration of user functions. These ‘extern’s are just prototypes with no body:

```java
/// external ::= 'extern' prototype
public static PrototypeAST parseExtern() {
    getNextToken(); // eat extern.
    return parsePrototype();
}
```

Finally, we’ll also let the user type in arbitrary top-level expressions and evaluate them on the fly. We will handle this by defining anonymous nullary (zero argument) functions for them:

## 2.7. The Driver

The driver for this simply invokes all of the parsing pieces with a top-level dispatch loop. There isn’t much interesting here, so I’ll just include the top-level loop. See [below](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl02.html#full-code-listing) for full code in the “Top-Level Parsing” section.

```java
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
```

The most interesting part of this is that we ignore top-level semicolons. Why is this, you ask? The basic reason is that if you type “4 + 5” at the command line, the parser doesn’t know whether that is the end of what you will type or not. For example, on the next line you could type “def foo…” in which case 4+5 is the end of a top-level expression. Alternatively you could type “* 6”, which would continue the expression. Having top-level semicolons allows you to type “4+5;”, and the parser will know you are done.