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

# 3. Kaleidoscope: Code generation to LLVM IR

> 在进入第三章之前，我建议先看[这篇文章](https://www.pauladamsmith.com/blog/2015/01/how-to-get-started-with-llvm-c-api.html)来对LLVM提供的API有一个初步的认识。
>
> 由于LLVM没有官方提供的jar包，这里和实验都引入了依赖[org.bytedeco.llvm-platform](https://github.com/bytedeco/javacpp-presets/tree/master/llvm)作为代替。但是这个java api是基于llvm-c提供的api，而这个教程原文是基于llvm c++提供的api，所以从这章开始，kaleidoscope-in-java会和kaleidoscope-in-cpp有很大的不同。
>
> 注意：本文使用的是llvm13.0.1提供的api，其他版本的api可能会有变化。

## 3.1. Chapter 3 Introduction

Welcome to Chapter 3 of the “[Implementing a language with LLVM](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/index.html)” tutorial. This chapter shows you how to transform the [Abstract Syntax Tree](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl02.html), built in Chapter 2, into LLVM IR. This will teach you a little bit about how LLVM does things, as well as demonstrate how easy it is to use. It’s much more work to build a lexer and parser than it is to generate LLVM IR code. :)

**Please note**: the code in this chapter and later require LLVM 3.7 or later. LLVM 3.6 and before will not work with it. Also note that you need to use a version of this tutorial that matches your LLVM release: If you are using an official LLVM release, use the version of the documentation included with your release or on the [llvm.org releases page](https://llvm.org/releases/).

## 3.2. Code Generation Setup

In order to generate LLVM IR, we want some simple setup to get started. First we define abstract code generation (codegen) methods in each AST class:

```java
/// ExprAST - Base class for all expression nodes.
public abstract class ExprAST {
    public abstract LLVMValueRef codegen();
}

/// NumberExprAST - Expression class for numeric literals like "1.0".
public class NumberExprAST extends ExprAST {
    double val;

    public NumberExprAST(double val) {
        this.val = val;
    }

    @Override
    public LLVMValueRef codegen() {
        // todo
    }
}
```

The codegen() method says to emit IR for that AST node along with all the things it depends on, and they all return an LLVM Value object(`LLVMValueRef`). “Value” is the class used to represent a “[Static Single Assignment (SSA)](http://en.wikipedia.org/wiki/Static_single_assignment_form) register” or “SSA value” in LLVM. The most distinct aspect of SSA values is that their value is computed as the related instruction executes, and it does not get a new value until (and if) the instruction re-executes. In other words, there is no way to “change” an SSA value. For more information, please read up on [Static Single Assignment](http://en.wikipedia.org/wiki/Static_single_assignment_form) - the concepts are really quite natural once you grok them.

Note that instead of adding virtual methods to the ExprAST class hierarchy, it could also make sense to use a [visitor pattern](http://en.wikipedia.org/wiki/Visitor_pattern)(in lab) or some other way to model this. Again, this tutorial won’t dwell on good software engineering practices: for our purposes, adding a virtual method is simplest.

The second thing we want is a “LogError” method like we used for the parser, which will be used to report errors found during code generation (for example, use of an undeclared parameter):

```java
public static LLVMContextRef theContext;
public static LLVMBuilderRef builder;
public static LLVMModuleRef theModule;
public static Map<String, LLVMValueRef> namedValues;

public static LLVMValueRef logErrorV(String str) {
    logError(str);
    return null;
}
```

The static variables will be used during code generation. `theContext` is an opaque object that owns a lot of core LLVM data structures, such as the type and constant value tables. We don’t need to understand it in detail, we just need a single instance to pass into APIs that require it.

The `builder` object is a helper object that makes it easy to generate LLVM instructions. Instances of the [IRBuilder](https://llvm.org/doxygen/IRBuilder_8h_source.html)(`LLVMBuilderRef`) class template keep track of the current place to insert instructions and has methods to create new instructions.

`theModule` is an LLVM construct that contains functions and global variables. In many ways, it is the top-level structure that the LLVM IR uses to contain code. It will own the memory for all of the IR that we generate, which is why the codegen() method returns a raw Value*, rather than a unique_ptr\<Value\>.(in cpp)

The `namedValues` map keeps track of which values are defined in the current scope and what their LLVM representation is. (In other words, it is a **symbol table** for the code). **In this form of Kaleidoscope, the only things that can be referenced are function parameters.** As such, function parameters will be in this map when generating code for their function body.

With these basics in place, we can start talking about how to generate code for each expression. Note that this assumes that the `builder` has been set up to generate code *into* something. For now, we’ll assume that this has already been done, and we’ll just use it to emit code.

## 3.3. Expression Code Generation

Generating LLVM code for expression nodes is very straightforward: less than 45 lines of commented code for all four of our expression nodes. First we’ll do numeric literals:

```java
// cpp
Value *NumberExprAST::codegen() {
  return ConstantFP::get(TheContext, APFloat(Val));
}

// java
@Override
public LLVMValueRef codegen() {
    return LLVMConstReal(LLVMDoubleType(), val);
}
```

In the LLVM IR, numeric constants are represented with the `ConstantFP` class, which holds the numeric value in an `APFloat` internally (`APFloat` has the capability of holding floating point constants of Arbitrary Precision). This code basically just creates and returns a `ConstantFP`. Note that in the LLVM IR that constants are all uniqued together and shared. For this reason, the API uses the “foo::get(…)” idiom instead of “new foo(..)” or “foo::Create(..)”.

> 在llvm-java中，c++中的类和方法会被全局静态方法代替，大部分API都位于`org.bytedeco.llvm.global.LLVM`。比如`llvm::ConstantFP::get`这个方法在java中就是`LLVMConstReal(LLVMTypeRef RealTy, double N)`。
>
> APFloat没有找到对应的方法，但是不影响。

```java
@Override
public LLVMValueRef codegen() {
    LLVMValueRef L = LHS.codegen();
    LLVMValueRef R = RHS.codegen();
    if (L == null || R == null) {
        return null;
    }

    switch (op) {
        case '+':
            return LLVMBuildFAdd(CodeGenerator.builder, L, R, "addtmp");
        case '-':
            return LLVMBuildFSub(CodeGenerator.builder, L, R, "subtmp");
        case '*':
            return LLVMBuildFMul(CodeGenerator.builder, L, R, "multmp");
        case '<':
            L = LLVMBuildFCmp(CodeGenerator.builder, LLVMRealOLT, L, R,"cmptmp");
            // Convert bool 0/1 to double 0.0 or 1.0
            return LLVMBuildUIToFP(CodeGenerator.builder, L, LLVMDoubleTypeInContext(CodeGenerator.theContext),"booltmp");
        default:
            return Logger.logErrorV("invalid binary operator");
    }
}
```

Binary operators start to get more interesting. The basic idea here is that we recursively emit code for the left-hand side of the expression, then the right-hand side, then we compute the result of the binary expression. In this code, we do a simple switch on the opcode to create the right LLVM instruction.

In the example above, the LLVM builder class is starting to show its value. IRBuilder(`LLVMBuilderRef`) knows where to insert the newly created instruction, all you have to do is specify what instruction to create (e.g. with `LLVMBuildFAdd`), which operands to use (`L` and `R` here) and optionally provide a name for the generated instruction.

One nice thing about LLVM is that the name is just a hint. For instance, **if the code above emits multiple “addtmp” variables, LLVM will automatically provide each one with an increasing, unique numeric suffix**. Local value names for instructions are purely optional, but it makes it much easier to read the IR dumps.

[LLVM instructions](https://llvm.org/docs/LangRef.html#instruction-reference) are constrained by strict rules: for example, the Left and Right operands of an [add instruction](https://llvm.org/docs/LangRef.html#add-instruction) must have the same type, and the result type of the add must match the operand types. Because all values in Kaleidoscope are doubles, this makes for very simple code for add, sub and mul.

On the other hand, LLVM specifies that the [fcmp instruction](https://llvm.org/docs/LangRef.html#fcmp-instruction) **always returns an ‘i1’ value (a one bit integer)**. The problem with this is that Kaleidoscope wants the value to be a 0.0 or 1.0 value. In order to get these semantics, we combine the fcmp instruction with a [uitofp instruction](https://llvm.org/docs/LangRef.html#uitofp-to-instruction). This instruction converts its input integer into a floating point value by treating the input as an unsigned value. In contrast, if we used the [sitofp instruction](https://llvm.org/docs/LangRef.html#sitofp-to-instruction), the Kaleidoscope ‘<’ operator would return 0.0 and -1.0, depending on the input value.

```java
@Override
public LLVMValueRef codegen() {
    // Look up the name in the global module table.
    LLVMValueRef calleeF = LLVMGetNamedFunction(CodeGenerator.theModule, callee);
    if (calleeF == null) {
        return Logger.logErrorV("Unknown function referenced");
    }

    // If argument mismatch error.
    if (LLVMCountParams(calleeF) != args.size()) {
        return Logger.logErrorV("Incorrect # arguments passed");
    }

    int argCount = args.size();
    PointerPointer<LLVMValueRef> argsv = new PointerPointer<>(argCount);
    for (int i = 0; i < argCount; ++i) {
        argsv.put(i, args.get(i).codegen());
        if (argsv.get(i) == null) {
            return null;
        }
    }

    return LLVMBuildCall(CodeGenerator.builder, calleeF, argsv, args.size(),"calltmp");
}
```

Code generation for function calls is quite straightforward with LLVM. The code above initially does a function name lookup in the LLVM Module’s symbol table. Recall that the LLVM Module is the container that holds the functions we are JIT’ing. By giving each function the same name as what the user specifies, we can use the LLVM symbol table to resolve function names for us.

Once we have the function to call, we recursively codegen each argument that is to be passed in, and create an LLVM [call instruction](https://llvm.org/docs/LangRef.html#call-instruction). Note that LLVM uses the native C calling conventions by default, allowing these calls to also call into standard library functions like “sin” and “cos”, with no additional effort.

This wraps up our handling of the four basic expressions that we have so far in Kaleidoscope. Feel free to go in and add some more. For example, by browsing the [LLVM language reference](https://llvm.org/docs/LangRef.html) you’ll find several other interesting instructions that are really easy to plug into our basic framework.

## 3.4. Function Code Generation

Code generation for prototypes and functions must handle a number of details, which make their code less beautiful than expression code generation, but allows us to illustrate some important points. First, let’s talk about code generation for prototypes: they are used both for function bodies and external function declarations. The code starts with:

```java
// cpp
Function *PrototypeAST::codegen() {
  // Make the function type:  double(double,double) etc.
  std::vector<Type*> Doubles(Args.size(),
                             Type::getDoubleTy(TheContext));
  FunctionType *FT =
    FunctionType::get(Type::getDoubleTy(TheContext), Doubles, false);

  Function *F =
    Function::Create(FT, Function::ExternalLinkage, Name, TheModule.get());

// java
public LLVMValueRef codegen() {
    // Make the function type:  double(double,double) etc.
    int argCount = args.size();
    PointerPointer<LLVMTypeRef> paramTypes = new PointerPointer<>(argCount);
    for (int i = 0; i < argCount; ++i) {
        paramTypes.put(i, LLVMDoubleTypeInContext(CodeGenerator.theContext));
    }

    LLVMTypeRef retType = LLVMFunctionType(LLVMDoubleTypeInContext(CodeGenerator.theContext), paramTypes, argCount, 0);

    LLVMValueRef function = LLVMAddFunction(CodeGenerator.theModule, name, retType);
```

This code packs a lot of power into a few lines. Note first that this function returns a “Function\*” instead of a “Value*”.(java中仍是LLVMValueRef) Because a “prototype” really talks about the external interface for a function (not the value computed by an expression), it makes sense for it to return the LLVM Function it corresponds to when codegen’d.

The call to `FunctionType::get`(`LLVMFunctionType()`) creates the `FunctionType`(`LLVMValueRef`) that should be used for a given Prototype. Since all function arguments in Kaleidoscope are of type double, the 16-19 lines create a vector of “N” LLVM double types. It then uses the `Functiontype::get`(`LLVMFunctionType`) method to create a function type that takes “N” doubles as arguments, returns one double as a result, and that is not vararg (the false(0) parameter indicates this). Note that Types in LLVM are uniqued just like Constants are, so you don’t “new” a type, you “get” it.

The final line above actually creates the IR Function corresponding to the Prototype. This indicates the type, linkage and name to use, as well as which module to insert into. “[external linkage](https://llvm.org/docs/LangRef.html#linkage)” means that the function may be defined outside the current module and/or that it is callable by functions outside the module. The Name passed in is the name the user specified: since “`theModule`” is specified, this name is registered in “`theModule`”s symbol table.

```java
// Set names for all arguments.
for (int i = 0; i < argCount; ++i) {
    LLVMValueRef arg = LLVMGetParam(function, i);
    String argName = args.get(i);
    LLVMSetValueName2(arg, argName, argName.length());
}
```

Finally, we set the name of each of the function’s arguments according to the names given in the Prototype. This step isn’t strictly necessary, but keeping the names consistent makes the IR more readable, and allows subsequent code to refer directly to the arguments for their names, rather than having to look up them up in the Prototype AST.

At this point we have a function prototype with no body. This is how LLVM IR represents function declarations. For extern statements in Kaleidoscope, this is as far as we need to go. For function definitions however, we need to codegen and attach a function body.

```java
public LLVMValueRef codegen() {
    // First, check for an existing function from a previous 'extern' declaration.
    LLVMValueRef theFunction = LLVMGetNamedFunction(CodeGenerator.theModule, proto.getName());

    if (theFunction == null) {
        theFunction = proto.codegen();
    }

    if (theFunction == null) {
        return null;
    }

    /*   todo
     *   if (!TheFunction->empty())
     *     return (Function*)LogErrorV("Function cannot be redefined.");
     */
```

For function definitions, we start by searching TheModule’s symbol table for an existing version of this function, in case one has already been created using an ‘extern’ statement. If Module::getFunction returns null then no previous version exists, so we’ll codegen one from the Prototype. In either case, we want to assert that the function is empty (i.e. has no body yet) before we start.

```java
// Create a new basic block to start insertion into.
LLVMBasicBlockRef entry = LLVMAppendBasicBlock(theFunction, "entry");
LLVMPositionBuilderAtEnd(CodeGenerator.builder, entry);

// Record the function arguments in the NamedValues map.
CodeGenerator.namedValues.clear();
int argCount = LLVMCountParams(theFunction);
for (int i = 0; i < argCount; ++i) {
    LLVMValueRef arg = LLVMGetParam(theFunction, i);
    String name = proto.args.get(i);
    CodeGenerator.namedValues.put(name, arg);
}
```

Now we get to the point where the `Builder` is set up. The first line creates a new [basic block](http://en.wikipedia.org/wiki/Basic_block) (named “entry”), which is inserted into `theFunction`. The second line then tells the builder that new instructions should be inserted into the end of the new basic block. Basic blocks in LLVM are an important part of functions that define the [Control Flow Graph](http://en.wikipedia.org/wiki/Control_flow_graph). Since we don’t have any control flow, our functions will only contain one block at this point. We’ll fix this in [Chapter 5](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl05.html) :).

Next we add the function arguments to the NamedValues map (after first clearing it out) so that they’re accessible to `VariableExprAST` nodes.

```java
LLVMValueRef retVal = body.codegen();

// Error reading body, remove function.
if (retVal == null) {
    LLVMDeleteFunction(theFunction);
    return null;
}

// Finish off the function.
LLVMBuildRet(CodeGenerator.builder, retVal);

// Validate the generated code, checking for consistency.
LLVMVerifyFunction(theFunction, LLVMAbortProcessAction);

return theFunction;
```

Once the insertion point has been set up and the NamedValues map populated, we call the `codegen()` method for the root expression of the function. If no error happens, this emits code to compute the expression into the entry block and returns the value that was computed. Assuming no error, we then create an LLVM [ret instruction](https://llvm.org/docs/LangRef.html#ret-instruction), which completes the function. Once the function is built, we call `verifyFunction`, which is provided by LLVM. **This function does a variety of consistency checks on the generated code, to determine if our compiler is doing everything right.** Using this is important: **it can catch a lot of bugs. Once the function is finished and validated, we return it.**

The only piece left here is handling of the error case. For simplicity, we handle this by merely deleting the function we produced with the `eraseFromParent` (`LLVMDeleteFunction`)method. This allows the user to redefine a function that they incorrectly typed in before: if we didn’t delete it, it would live in the symbol table, with a body, preventing future redefinition.

This code does have a bug, though: If the `FunctionAST::codegen()` method finds an existing IR Function, it does not validate its signature against the definition’s own prototype. This means that an earlier ‘extern’ declaration will take precedence over the function definition’s signature, which can cause codegen to fail, for instance if the function arguments are named differently. There are a number of ways to fix this bug, see what you can come up with! Here is a testcase:

```
extern foo(a);     # ok, defines foo.
def foo(b) b;      # Error: Unknown variable name. (decl using 'a' takes precedence).
```

## 3.5. Driver Changes and Closing Thoughts

For now, code generation to LLVM doesn’t really get us much, except that we can look at the pretty IR calls. The sample code inserts calls to codegen into the “`HandleDefinition`”, “`HandleExtern`” etc functions, and then dumps out the LLVM IR. This gives a nice way to look at the LLVM IR for simple functions. For example:

```
ready> 4+5;
Read top-level expression:
define double @0() {
entry:
  ret double 9.000000e+00
}
```

Note how the parser turns the top-level expression into anonymous functions for us. This will be handy when we add [JIT support](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl04.html#adding-a-jit-compiler) in the next chapter. Also note that the code is very literally transcribed, no optimizations are being performed except simple constant folding done by IRBuilder. We will [add optimizations](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl04.html#trivial-constant-folding) explicitly in the next chapter.

```
ready> def foo(a b) a*a + 2*a*b + b*b;
Read function definition:
define double @foo(double %a, double %b) {
entry:
  %multmp = fmul double %a, %a
  %multmp1 = fmul double 2.000000e+00, %a
  %multmp2 = fmul double %multmp1, %b
  %addtmp = fadd double %multmp, %multmp2
  %multmp3 = fmul double %b, %b
  %addtmp4 = fadd double %addtmp, %multmp3
  ret double %addtmp4
}
```

This shows some simple arithmetic. Notice the striking similarity to the LLVM builder calls that we use to create the instructions.

```
ready> def bar(a) foo(a, 4.0) + bar(31337);
Read function definition:
define double @bar(double %a) {
entry:
  %calltmp = call double @foo(double %a, double 4.000000e+00)
  %calltmp1 = call double @bar(double 3.133700e+04)
  %addtmp = fadd double %calltmp, %calltmp1
  ret double %addtmp
}
```

This shows some function calls. Note that this function will take a long time to execute if you call it. In the future we’ll add conditional control flow to actually make recursion useful :).

```
ready> extern cos(x);
Read extern:
declare double @cos(double)

ready> cos(1.234);
Read top-level expression:
define double @1() {
entry:
  %calltmp = call double @cos(double 1.234000e+00)
  ret double %calltmp
}
```

This shows an extern for the libm “cos” function, and a call to it.

```
ready> ^D
; ModuleID = 'my cool jit'

define double @0() {
entry:
  %addtmp = fadd double 4.000000e+00, 5.000000e+00
  ret double %addtmp
}

define double @foo(double %a, double %b) {
entry:
  %multmp = fmul double %a, %a
  %multmp1 = fmul double 2.000000e+00, %a
  %multmp2 = fmul double %multmp1, %b
  %addtmp = fadd double %multmp, %multmp2
  %multmp3 = fmul double %b, %b
  %addtmp4 = fadd double %addtmp, %multmp3
  ret double %addtmp4
}

define double @bar(double %a) {
entry:
  %calltmp = call double @foo(double %a, double 4.000000e+00)
  %calltmp1 = call double @bar(double 3.133700e+04)
  %addtmp = fadd double %calltmp, %calltmp1
  ret double %addtmp
}

declare double @cos(double)

define double @1() {
entry:
  %calltmp = call double @cos(double 1.234000e+00)
  ret double %calltmp
}
```

When you quit the current demo (by sending an EOF via CTRL+D on Linux or CTRL+Z and ENTER on Windows), it dumps out the IR for the entire module generated. Here you can see the big picture with all the functions referencing each other.

This wraps up the third chapter of the Kaleidoscope tutorial. Up next, we’ll describe how to [add JIT codegen and optimizer support](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl04.html) to this so we can actually start running code!

## 3.7. Output IR

在原教程中，在第四章才能看到这章生成的IR。为了展示一些这章的成果，我修改了一下`handleDefinition`, `handleExtern`, `handleTopLevelExpression`，在解析完AST后通过调用`xxxAST.codegen()`生成IR，并在程序结束前dump出来。

```java
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

public static void main(String[] args) {
    // Prime the first token.
    getNextToken();

    // Run the main "interpreter loop" now.
    mainLoop();

    LLVMDumpModule(CodeGenerator.theModule);
}
```

# 4. Kaleidoscope: Adding JIT and Optimizer Support

## 4.1. Chapter 4 Introduction

Welcome to Chapter 4 of the “[Implementing a language with LLVM](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/index.html)” tutorial. Chapters 1-3 described the implementation of a simple language and added support for generating LLVM IR. This chapter describes two new techniques: adding optimizer support to your language, and adding JIT compiler support. These additions will demonstrate how to get nice, efficient code for the Kaleidoscope language.

## 4.2. Trivial Constant Folding

Our demonstration for Chapter 3 is elegant and easy to extend. Unfortunately, it does not produce wonderful code. The IRBuilder, however, does give us obvious optimizations when compiling simple code:

```
ready> def test(x) 1+2+x;
Read function definition:
define double @test(double %x) {
entry:
        %addtmp = fadd double 3.000000e+00, %x
        ret double %addtmp
}
```

This code is not a literal transcription of the AST built by parsing the input. That would be:

```
ready> def test(x) 1+2+x;
Read function definition:
define double @test(double %x) {
entry:
        %addtmp = fadd double 2.000000e+00, 1.000000e+00
        %addtmp1 = fadd double %addtmp, %x
        ret double %addtmp1
}
```

Constant folding, as seen above, in particular, is a very common and very important optimization: so much so that many language implementors implement constant folding support in their AST representation.

With LLVM, you don’t need this support in the AST. Since all calls to build LLVM IR go through the LLVM IR builder, the builder itself checked to see if there was a constant folding opportunity when you call it. If so, it just does the constant fold and return the constant instead of creating an instruction.

Well, that was easy :). In practice, we recommend always using `IRBuilder` when generating code like this. It has no “syntactic overhead” for its use (you don’t have to uglify your compiler with constant checks everywhere) and it can dramatically reduce the amount of LLVM IR that is generated in some cases (particular for languages with a macro preprocessor or that use a lot of constants).

On the other hand, the `IRBuilder` is limited by the fact that it does all of its analysis inline with the code as it is built. If you take a slightly more complex example:

```
ready> def test(x) (1+2+x)*(x+(1+2));
ready> Read function definition:
define double @test(double %x) {
entry:
        %addtmp = fadd double 3.000000e+00, %x
        %addtmp1 = fadd double %x, 3.000000e+00
        %multmp = fmul double %addtmp, %addtmp1
        ret double %multmp
}
```

In this case, the LHS and RHS of the multiplication are the same value. We’d really like to see this generate “`tmp = x+3; result = tmp*tmp;`” instead of computing “`x+3`” twice.

Unfortunately, no amount of local analysis will be able to detect and correct this. This requires two transformations: reassociation of expressions (to make the add’s lexically identical) and Common Subexpression Elimination (CSE) to delete the redundant add instruction. Fortunately, LLVM provides a broad range of optimizations that you can use, in the form of “passes”.

## 4.3. LLVM Optimization Passes

LLVM provides many optimization passes, which do many different sorts of things and have different tradeoffs. Unlike other systems, LLVM doesn’t hold to the mistaken notion that one set of optimizations is right for all languages and for all situations. LLVM allows a compiler implementor to make complete decisions about what optimizations to use, in which order, and in what situation.

As a concrete example, LLVM supports both “whole module” passes, which look across as large of body of code as they can (often a whole file, but if run at link time, this can be a substantial portion of the whole program). It also supports and includes “per-function” passes which just operate on a single function at a time, without looking at other functions. For more information on passes and how they are run, see the [How to Write a Pass](https://llvm.org/docs/WritingAnLLVMPass.html) document and the [List of LLVM Passes](https://llvm.org/docs/Passes.html).

For Kaleidoscope, we are currently generating functions on the fly, one at a time, as the user types them in. We aren’t shooting for the ultimate optimization experience in this setting, but we also want to catch the easy and quick stuff where possible. As such, we will choose to run a few per-function optimizations as the user types the function in. If we wanted to make a “static Kaleidoscope compiler”, we would use exactly the code we have now, except that we would defer running the optimizer until the entire file has been parsed.

In order to get per-function optimizations going, we need to set up a [FunctionPassManager](https://llvm.org/docs/WritingAnLLVMPass.html#what-passmanager-doesr) to hold and organize the LLVM optimizations that we want to run. Once we have that, we can add a set of optimizations to run. We’ll need a new FunctionPassManager for each module that we want to optimize, so we’ll write a function to create and initialize both the module and pass manager for us:

```java
// Open a new module.
theModule = LLVMModuleCreateWithNameInContext("my cool jit", theContext);

// Create a new pass manager attached to it.
theFPM = LLVMCreateFunctionPassManagerForModule(theModule);

// Do simple "peephole" optimizations and bit-twiddling optzns.
LLVMAddInstructionCombiningPass(theFPM);
// Reassociate expressions.
LLVMAddReassociatePass(theFPM);
// Eliminate Common SubExpressions.
LLVMAddGVNPass(theFPM);
// Simplify the control flow graph (deleting unreachable blocks, etc).
LLVMAddCFGSimplificationPass(theFPM);

LLVMInitializeFunctionPassManager(theFPM);
```

This code initializes the global module `theModule`, and the function pass manager `theFPM`, which is attached to `theModule`. Once the pass manager is set up, we use a series of “add” calls to add a bunch of LLVM passes.

In this case, we choose to add four optimization passes. The passes we choose here are a pretty standard set of “cleanup” optimizations that are useful for a wide variety of code. I won’t delve into what they do but, believe me, they are a good starting place :).

Once the PassManager is set up, we need to make use of it. We do this by running it after our newly created function is constructed (in `FunctionAST.codegen()`), but before it is returned to the client:

```java
// Finish off the function.
LLVMBuildRet(CodeGenerator.builder, retVal);

// Validate the generated code, checking for consistency.
LLVMVerifyFunction(theFunction, LLVMAbortProcessAction);

// Optimize the function.
LLVMRunFunctionPassManager(CodeGenerator.theFPM, theFunction);

return theFunction;
```

As you can see, this is pretty straightforward. The `FunctionPassManager` optimizes and updates the LLVM Function* in place, improving (hopefully) its body. With this in place, we can try our test above again:

```
ready> def test(x) (1+2+x)*(x+(1+2));
ready> Read function definition:
define double @test(double %x) {
entry:
        %addtmp = fadd double %x, 3.000000e+00
        %multmp = fmul double %addtmp, %addtmp
        ret double %multmp
}
```

As expected, we now get our nicely optimized code, saving a floating point add instruction from every execution of this function.

LLVM provides a wide variety of optimizations that can be used in certain circumstances. Some [documentation about the various passes](https://llvm.org/docs/Passes.html) is available, but it isn’t very complete. Another good source of ideas can come from looking at the passes that `Clang` runs to get started. The “`opt`” tool allows you to experiment with passes from the command line, so you can see if they do anything.

Now that we have reasonable code coming out of our front-end, let’s talk about executing it!

## 4.4. Adding a JIT Compiler

> 本章节原文使用了`llvm-src/examples/Kaleidoscope/include/KaleidoscopeJIT.h`这个头文件，在java版本中没有，所以对本章节做了简化。

Code that is available in LLVM IR can have a wide variety of tools applied to it. For example, you can run optimizations on it (as we did above), you can dump it out in textual or binary forms, you can compile the code to an assembly file (.s) for some target, or you can JIT compile it. The nice thing about the LLVM IR representation is that it is the “common currency” between many different parts of the compiler.

In this section, we’ll add JIT compiler support to our interpreter. **The basic idea that we want for Kaleidoscope is to have the user enter function bodies as they do now, but immediately evaluate the top-level expressions they type in.** For example, if they type in “1 + 2;”, we should evaluate and print out 3. If they define a function, they should be able to call it from the command line.

为了达到这种效果，我们在Main中添加一个`ExecutionEngine`，初始化并使用它来执行生成的代码。

```java
public class Main {
    static LLVMExecutionEngineRef engine = new LLVMExecutionEngineRef();
    
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
    
    public static void main(String[] args) {
        // Initialize LLVM components
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMInitializeNativeTarget();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMLinkInMCJIT();
        ...
    }
}
```

这里会有一个bug：当同一个ExecutionEngine执行两次函数时会触发JVM崩溃。所以13-16行在每次运行前会创建一个ExecutionEngine。截止目前还未定位到bug触发的原因。如果读者有解决方法欢迎提交pr。: )

# 5. Kaleidoscope: Extending the Language: Control Flow

## 5.1. Chapter 5 Introduction

Welcome to Chapter 5 of the “[Implementing a language with LLVM](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/index.html)” tutorial. Parts 1-4 described the implementation of the simple Kaleidoscope language and included support for generating LLVM IR, followed by optimizations and a JIT compiler. Unfortunately, as presented, Kaleidoscope is mostly useless: it has no control flow other than call and return. This means that you can’t have conditional branches in the code, significantly limiting its power. In this episode of “build that compiler”, we’ll extend Kaleidoscope to have an if/then/else expression plus a simple ‘for’ loop.

## 5.2. If/Then/Else

Extending Kaleidoscope to support if/then/else is quite straightforward. It basically requires adding support for this “new” concept to the lexer, parser, AST, and LLVM code emitter. This example is nice, because it shows how easy it is to “grow” a language over time, incrementally extending it as new ideas are discovered.

Before we get going on “how” we add this extension, let’s talk about “what” we want. The basic idea is that we want to be able to write this sort of thing:

```
def fib(x)
  if x < 3 then
    1
  else
    fib(x-1)+fib(x-2);
```

In Kaleidoscope, every construct is an expression: there are no statements. As such, the if/then/else expression needs to return a value like any other. Since we’re using a mostly functional form, we’ll have it evaluate its conditional, then return the ‘then’ or ‘else’ value based on how the condition was resolved. This is very similar to the C “?:” expression.

The semantics of the if/then/else expression is that it evaluates the condition to a boolean equality value: **0.0 is considered to be false and everything else is considered to be true.** If the condition is true, the first subexpression is evaluated and returned, if the condition is false, the second subexpression is evaluated and returned. Since Kaleidoscope allows side-effects, this behavior is important to nail down.

Now that we know what we “want”, let’s break this down into its constituent pieces.

### 5.2.1. Lexer Extensions for If/Then/Else

The lexer extensions are straightforward. First we add new enum values for the relevant tokens:

```java
// control
TOK_IF(-6),
TOK_THEN(-7),
TOK_ELSE(-8)
```

Once we have that, we recognize the new keywords in the lexer. This is pretty simple stuff:

```java
if (identifierStr.equals("if")) {
    return TOK_IF.getValue();
}
if (identifierStr.equals("then")) {
    return TOK_THEN.getValue();
}
if (identifierStr.equals("else")) {
    return TOK_ELSE.getValue();
}
```

### 5.2.2. AST Extensions for If/Then/Else

To represent the new expression we add a new AST node for it:

```java
/// IfExprAST - Expression class for if/then/else.
public class IfExprAST extends ExprAST{
    ExprAST cond;
    ExprAST then;
    ExprAST Else;

    public IfExprAST(ExprAST cond, ExprAST then, ExprAST Else) {
        this.cond = cond;
        this.then = then;
        this.Else = Else;
    }

    @Override
    public LLVMValueRef codegen() {
    	...
    }
```

The AST node just has pointers to the various subexpressions.

### 5.2.3. Parser Extensions for If/Then/Else

Now that we have the relevant tokens coming from the lexer and we have the AST node to build, our parsing logic is relatively straightforward. First we define a new parsing function:

```java
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
```

Next we hook it up as a primary expression:

```java
/// primary
///   ::= identifierexpr
///   ::= numberexpr
///   ::= parenexpr
///   ::= ifexpr
public static ExprAST parsePrimary() {
    if (curTok == TOK_IDENTIFIER.getValue()) {
        return parseIdentifierExpr();
    } else if (curTok == TOK_NUMBER.getValue()) {
        return parseNumberExpr();
    } else if (curTok == '(') {
        return parseParenExpr();
    } else if (curTok == TOK_IF.getValue()){
        return parseIfExpr();
    } else {
        return Logger.logError("unknown token when expecting an expression");
    }
}
```

### 5.2.4. LLVM IR for If/Then/Else

Now that we have it parsing and building the AST, the final piece is adding LLVM code generation support. This is the most interesting part of the if/then/else example, because this is where it starts to introduce new concepts. All of the code above has been thoroughly described in previous chapters.

To motivate the code we want to produce, let’s take a look at a simple example. Consider:

```
extern foo();
extern bar();
def baz(x) if x then foo() else bar();
```

If you disable optimizations, the code you’ll (soon) get from Kaleidoscope looks like this:

```
declare double @foo()

declare double @bar()

define double @baz(double %x) {
entry:
  %ifcond = fcmp one double %x, 0.000000e+00
  br i1 %ifcond, label %then, label %else

then:       ; preds = %entry
  %calltmp = call double @foo()
  br label %ifcont

else:       ; preds = %entry
  %calltmp1 = call double @bar()
  br label %ifcont

ifcont:     ; preds = %else, %then
  %iftmp = phi double [ %calltmp, %then ], [ %calltmp1, %else ]
  ret double %iftmp
}
```

To visualize the control flow graph, you can use a nifty feature of the LLVM ‘[opt](https://llvm.org/cmds/opt.html)’ tool. If you put this LLVM IR into “t.ll” and run “`llvm-as < t.ll | opt -passes=view-cfg`”, [a window will pop up](https://llvm.org/docs/ProgrammersManual.html#viewing-graphs-while-debugging-code) and you’ll see this graph:

![Example CFG](https://draco-picbed.oss-cn-shanghai.aliyuncs.com/img/LangImpl05-cfg.png)

Another way to get this is to call “`F->viewCFG()`” or “`F->viewCFGOnly()`” (where F is a “`Function*`”) either by inserting actual calls into the code and recompiling or by calling these in the debugger. LLVM has many nice features for visualizing various graphs.

Getting back to the generated code, it is fairly simple: the entry block evaluates the conditional expression (“x” in our case here) and compares the result to 0.0 with the “`fcmp one`” instruction (‘one’ is “Ordered and Not Equal”). Based on the result of this expression, the code jumps to either the “then” or “else” blocks, which contain the expressions for the true/false cases.

Once the then/else blocks are finished executing, they both branch back to the ‘ifcont’ block to execute the code that happens after the if/then/else. In this case the only thing left to do is to return to the caller of the function. The question then becomes: how does the code know which expression to return?

The answer to this question involves an important SSA operation: the [Phi operation](http://en.wikipedia.org/wiki/Static_single_assignment_form). If you’re not familiar with SSA, [the wikipedia article](http://en.wikipedia.org/wiki/Static_single_assignment_form) is a good introduction and there are various other introductions to it available on your favorite search engine. The short version is that “execution” of the Phi operation requires “remembering” which block control came from. The Phi operation takes on the value corresponding to the input control block. In this case, **if control comes in from the “then” block, it gets the value of “calltmp”. If control comes from the “else” block, it gets the value of “calltmp1”.**

At this point, you are probably starting to think “Oh no! This means my simple and elegant front-end will have to start generating SSA form in order to use LLVM!”. Fortunately, this is not the case, and we strongly advise *not* implementing an SSA construction algorithm in your front-end unless there is an amazingly good reason to do so. In practice, there are two sorts of values that float around in code written for your average imperative programming language that might need Phi nodes:

1. Code that involves user variables: `x = 1; x = x + 1;`
2. Values that are implicit in the structure of your AST, such as the Phi node in this case.

In [Chapter 7](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl07.html) of this tutorial (“mutable variables”), we’ll talk about #1 in depth. For now, just believe me that you don’t need SSA construction to handle this case. For #2, you have the choice of using the techniques that we will describe for #1, or you can insert Phi nodes directly, if convenient. In this case, it is really easy to generate the Phi node, so we choose to do it directly.

Okay, enough of the motivation and overview, let’s generate code!

### 5.2.5. Code Generation for If/Then/Else

In order to generate code for this, we implement the `codegen` method for `IfExprAST`:

```java
@Override
public LLVMValueRef codegen() {
    LLVMValueRef condV = cond.codegen();
    if (condV == null) {
        return null;
    }

    // Convert condition to a bool by comparing non-equal to 0.0.
    LLVMValueRef zero = LLVMConstReal(LLVMDoubleTypeInContext(CodeGenerator.theContext), 0);
    condV = LLVMBuildFCmp(CodeGenerator.builder, LLVMRealONE, condV, zero,"cmptmp");
```

This code is straightforward and similar to what we saw before. We emit the expression for the condition, then compare that value to zero to get a truth value as a 1-bit (bool) value.

```java
LLVMValueRef theFunction = LLVMGetBasicBlockParent(LLVMGetInsertBlock(CodeGenerator.builder));

// Create blocks for the then and else cases.  Insert the 'then' block at the
// end of the function.
LLVMBasicBlockRef thenBB = LLVMAppendBasicBlock(theFunction, "then");
LLVMBasicBlockRef elseBB = LLVMAppendBasicBlock(theFunction, "else");
LLVMBasicBlockRef mergeBB = LLVMAppendBasicBlock(theFunction, "ifcont");

LLVMBuildCondBr(CodeGenerator.builder, condV, thenBB, elseBB);
```

This code creates the basic blocks that are related to the if/then/else statement, and correspond directly to the blocks in the example above. The first line gets the current Function object that is being built. It gets this by asking the builder for the current BasicBlock, and asking that block for its “parent” (the function it is currently embedded into).

Once it has that, it creates three blocks. Note that it passes “theFunction” into the constructor for the “then” block. This causes the constructor to automatically insert the new block into the end of the specified function. The other two blocks are created, but aren’t yet inserted into the function.

Once the blocks are created, we can emit the conditional branch that chooses between them. **Note that creating new blocks does not implicitly affect the IRBuilder, so it is still inserting into the block that the condition went into.** Also note that it is creating a branch to the “then” block and the “else” block, even though the “else” block isn’t inserted into the function yet. This is all ok: it is the standard way that LLVM supports forward references.

```java
// Emit then value.
LLVMPositionBuilderAtEnd(CodeGenerator.builder, thenBB);

LLVMValueRef thenV = then.codegen();
if (thenV == null) {
    return null;
}

LLVMBuildBr(CodeGenerator.builder, mergeBB);
// Codegen of 'Then' can change the current block, update ThenBB for the PHI.
thenBB = LLVMGetInsertBlock(CodeGenerator.builder);
```

After the conditional branch is inserted, we move the builder to start inserting into the “then” block. Strictly speaking, this call moves the insertion point to be at the end of the specified block. However, since the “then” block is empty, it also starts out by inserting at the beginning of the block. :)

Once the insertion point is set, we recursively codegen the “then” expression from the AST. To finish off the “then” block, we create an unconditional branch to the merge block. One interesting (and very important) aspect of the LLVM IR is that **it [requires all basic blocks to be “terminated”](https://llvm.org/docs/LangRef.html#functionstructure) with a [control flow instruction](https://llvm.org/docs/LangRef.html#terminators) such as return or branch.** This means that all control flow, *including fall throughs* must be made explicit in the LLVM IR. If you violate this rule, the verifier will emit an error.

The final line here is quite subtle, but is very important. The basic issue is that when we create the Phi node in the merge block, we need to set up the block/value pairs that indicate how the Phi will work. Importantly, the Phi node expects to have an entry for each predecessor of the block in the CFG. Why then, are we getting the current block when we just set it to thenBB 5 lines above? The problem is that the “Then” expression may actually itself change the block that the Builder is emitting into if, for example, it contains a nested “if/then/else” expression. Because calling `codegen()` recursively could arbitrarily change the notion of the current block, we are required to get an up-to-date value for code that will set up the Phi node.

```java
// Emit else block.
LLVMPositionBuilderAtEnd(CodeGenerator.builder, elseBB);

LLVMValueRef elseV = Else.codegen();
if (elseV == null) {
    return null;
}

LLVMBuildBr(CodeGenerator.builder, mergeBB);
// codegen of 'Else' can change the current block, update ElseBB for the PHI.
elseBB = LLVMGetInsertBlock(CodeGenerator.builder);
```

Code generation for the ‘else’ block is basically identical to codegen for the ‘then’ block. The only significant difference is the first line, which adds the ‘else’ block to the function. Recall previously that the ‘else’ block was created, but not added to the function. Now that the ‘then’ and ‘else’ blocks are emitted, we can finish up with the merge code:

```java
    // Emit merge block.
    LLVMPositionBuilderAtEnd(CodeGenerator.builder, mergeBB);
    LLVMValueRef phi = LLVMBuildPhi(CodeGenerator.builder, LLVMDoubleTypeInContext(CodeGenerator.theContext), "iftmp");

    PointerPointer<Pointer> phiValues = new PointerPointer<>(2)
            .put(0, thenV)
            .put(1, elseV);
    PointerPointer<Pointer> phiBlocks = new PointerPointer<>(2)
            .put(0, thenBB)
            .put(1, elseBB);
    LLVMAddIncoming(phi, phiValues, phiBlocks, 2);

    return phi;
}
```

The first two lines here are now familiar: the first adds the “merge” block to the Function object (it was previously floating, like the else block above). The second changes the insertion point so that newly created code will go into the “merge” block. Once that is done, we need to create the PHI node and set up the block/value pairs for the PHI.

Finally, the CodeGen function returns the phi node as the value computed by the if/then/else expression. In our example above, this returned value will feed into the code for the top-level function, which will create the return instruction.

Overall, we now have the ability to execute conditional code in Kaleidoscope. With this extension, Kaleidoscope is a fairly complete language that can calculate a wide variety of numeric functions. Next up we’ll add another useful expression that is familiar from non-functional languages…

## 5.3. ‘for’ Loop Expression

> 名为for，实际上是do while。

Now that we know how to add basic control flow constructs to the language, we have the tools to add more powerful things. Let’s add something more aggressive, a ‘for’ expression:

```
extern putchard(char);
def printstar(n)
  for i = 1, i < n, 1.0 in
    putchard(42);  # ascii 42 = '*'

# print 100 '*' characters
printstar(100);
```

This expression defines a new variable (“i” in this case) which iterates from a starting value, while the condition (“i < n” in this case) is true, incrementing by an optional step value (“1.0” in this case). If the step value is omitted, it defaults to 1.0. While the loop is true, it executes its body expression. Because we don’t have anything better to return, we’ll just define the loop as always returning 0.0. In the future when we have mutable variables, it will get more useful.

As before, let’s talk about the changes that we need to Kaleidoscope to support this.

### 5.3.1. Lexer Extensions for the ‘for’ Loop

The lexer extensions are the same sort of thing as for if/then/else:

```java
TOK_FOR(-9),
TOK_IN(-10);
```

```java
if (identifierStr.equals("for")) {
    return TOK_FOR.getValue();
}
if (identifierStr.equals("in")) {
    return TOK_IN.getValue();
}
```

### 5.3.2. AST Extensions for the ‘for’ Loop

The AST node is just as simple. It basically boils down to capturing the variable name and the constituent expressions in the node.

```java
/// ForExprAST - Expression class for for/in.
public class ForExprAST extends ExprAST{
    String varName;
    ExprAST start;
    ExprAST end;
    ExprAST step;
    ExprAST body;

    public ForExprAST(String varName, ExprAST start, ExprAST end, ExprAST step, ExprAST body) {
        this.varName = varName;
        this.start = start;
        this.end = end;
        this.step = step;
        this.body = body;
    }

    @Override
    public LLVMValueRef codegen() {
    	...
    }
```

### 5.3.3. Parser Extensions for the ‘for’ Loop

The parser code is also fairly standard. The only interesting thing here is handling of the optional step value. The parser code handles it by checking to see if the second comma is present. If not, it sets the step value to null in the AST node:

```java
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
```

And again we hook it up as a primary expression:

```java
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
```

### 5.3.4. LLVM IR for the ‘for’ Loop

Now we get to the good part: the LLVM IR we want to generate for this thing. With the simple example above, we get this LLVM IR (note that this dump is generated with optimizations disabled for clarity):

```
declare double @putchard(double)

define double @printstar(double %n) {
entry:
  ; initial value = 1.0 (inlined into phi)
  br label %loop

loop:       ; preds = %loop, %entry
  %i = phi double [ 1.000000e+00, %entry ], [ %nextvar, %loop ]
  ; body
  %calltmp = call double @putchard(double 4.200000e+01)
  ; increment
  %nextvar = fadd double %i, 1.000000e+00

  ; termination test
  %cmptmp = fcmp ult double %i, %n
  %booltmp = uitofp i1 %cmptmp to double
  %loopcond = fcmp one double %booltmp, 0.000000e+00
  br i1 %loopcond, label %loop, label %afterloop

afterloop:      ; preds = %loop
  ; loop always returns 0.0
  ret double 0.000000e+00
}
```

> 注意：这里的for在执行完entry块后会直接跳转到loop块内，并没有做一次判断。所以实际上是有初始化的do while。

This loop contains all the same constructs we saw before: a phi node, several expressions, and some basic blocks. Let’s see how this fits together.

### 5.3.5. Code Generation for the ‘for’ Loop

The first part of codegen is very simple: we just output the start expression for the loop value:

```java
@Override
public LLVMValueRef codegen() {
    // Emit the start code first, without 'variable' in scope.
    LLVMValueRef startVal = start.codegen();
    if (startVal == null) {
        return null;
    }
```

With this out of the way, the next step is to set up the LLVM basic block for the start of the loop body. In the case above, the whole loop body is one block, but remember that the body code itself could consist of multiple blocks (e.g. if it contains an if/then/else or a for/in expression).

```java
// Make the new basic block for the loop header, inserting after current
// block.
LLVMValueRef theFunction = LLVMGetBasicBlockParent(LLVMGetInsertBlock(CodeGenerator.builder));
LLVMBasicBlockRef preheaderBB = LLVMGetInsertBlock(CodeGenerator.builder);
LLVMBasicBlockRef loopBB = LLVMAppendBasicBlock(theFunction, "loop");

// Insert an explicit fall through from the current block to the LoopBB.
LLVMBuildBr(CodeGenerator.builder, loopBB);
```

This code is similar to what we saw for if/then/else. Because we will need it to create the Phi node, we remember the block that falls through into the loop. Once we have that, we create the actual block that starts the loop and create an unconditional branch for the fall-through between the two blocks.

```java
// Start insertion in LoopBB.
LLVMPositionBuilderAtEnd(CodeGenerator.builder, loopBB);

// Start the PHI node with an entry for Start.
LLVMValueRef variable = LLVMBuildPhi(CodeGenerator.builder, LLVMDoubleTypeInContext(CodeGenerator.theContext), varName);
```

Now that the “preheader” for the loop is set up, we switch to emitting code for the loop body. To begin with, we move the insertion point and create the PHI node for the loop induction variable. Note that the Phi will eventually get a second value for the backedge, but we can’t set it up yet (because the second doesn’t exist!).

```java
// Within the loop, the variable is defined equal to the PHI node.  If it
// shadows an existing variable, we have to restore it, so save it now.
LLVMValueRef oldVal = CodeGenerator.namedValues.get(varName);
CodeGenerator.namedValues.put(varName, variable);

// Emit the body of the loop.  This, like any other expr, can change the
// current BB.  Note that we ignore the value computed by the body, but don't
// allow an error.
LLVMValueRef bodyVal = body.codegen();
if (bodyVal == null) {
    return null;
}
```

Now the code starts to get more interesting. Our ‘for’ loop introduces a new variable to the symbol table. This means that our symbol table can now contain either function arguments or loop variables. To handle this, before we codegen the body of the loop, we add the loop variable as the current value for its name. Note that it is possible that there is a variable of the same name in the outer scope. It would be easy to make this an error (emit an error and return null if there is already an entry for VarName) but we choose to allow shadowing of variables. In order to handle this correctly, we remember the Value that we are potentially shadowing in `OldVal` (which will be null if there is no shadowed variable).

Once the loop variable is set into the symbol table, the code recursively codegen’s the body. This allows the body to use the loop variable: any references to it will naturally find it in the symbol table.

```java
// Emit the step value.
LLVMValueRef stepVal;
if (step != null) {
    stepVal = step.codegen();
    if (stepVal == null) {
        return null;
    }
} else {
    // If not specified, use 1.0.
    stepVal = LLVMConstReal(LLVMDoubleTypeInContext(CodeGenerator.theContext), 1);
}

LLVMValueRef nextVar = LLVMBuildFAdd(CodeGenerator.builder, variable, stepVal, "nextvar");
```

Now that the body is emitted, we compute the next value of the iteration variable by adding the step value, or 1.0 if it isn’t present. ‘`NextVar`’ will be the value of the loop variable on the next iteration of the loop.

```java
// Compute the end condition.
LLVMValueRef endCond = end.codegen();
if (endCond == null) {
    return null;
}

// Convert condition to a bool by comparing non-equal to 0.0.
LLVMValueRef zero = LLVMConstReal(LLVMDoubleTypeInContext(CodeGenerator.theContext), 0);
endCond = LLVMBuildFCmp(CodeGenerator.builder, LLVMRealONE, endCond, zero,"loopcond");
```

Finally, we evaluate the exit value of the loop, to determine whether the loop should exit. This mirrors the condition evaluation for the if/then/else statement.

```java
// Create the "after loop" block and insert it.
LLVMBasicBlockRef loopEndBB = LLVMGetInsertBlock(CodeGenerator.builder);
LLVMBasicBlockRef afterBB = LLVMAppendBasicBlock(theFunction, "afterloop");

// Insert the conditional branch into the end of LoopEndBB.
LLVMBuildCondBr(CodeGenerator.builder, endCond, loopBB, afterBB);

// Any new code will be inserted in AfterBB.
LLVMPositionBuilderAtEnd(CodeGenerator.builder, afterBB);
```

With the code for the body of the loop complete, we just need to finish up the control flow for it. This code remembers the end block (for the phi node), then creates the block for the loop exit (“afterloop”). Based on the value of the exit condition, it creates a conditional branch that chooses between executing the loop again and exiting the loop. Any future code is emitted in the “afterloop” block, so it sets the insertion position to it.

```java
    // Add a new entry to the PHI node for the backedge.
    PointerPointer<Pointer> phiValues = new PointerPointer<>(2)
            .put(0, startVal)
            .put(1, nextVar);
    PointerPointer<Pointer> phiBlocks = new PointerPointer<>(2)
            .put(0, preheaderBB)
            .put(1, loopEndBB);
    LLVMAddIncoming(variable, phiValues, phiBlocks, 2);

    // Restore the unshadowed variable.
    if (oldVal != null) {
        CodeGenerator.namedValues.put(varName, oldVal);
    } else {
        CodeGenerator.namedValues.remove(varName);
    }

    // for expr always returns 0.0.
    return zero;
}
```

The final code handles various cleanups: now that we have the “NextVar” value, we can add the incoming value to the loop PHI node. After that, we remove the loop variable from the symbol table, so that it isn’t in scope after the for loop. Finally, code generation of the for loop always returns 0.0, so that is what we return from `ForExprAST.codegen()`.

With this, we conclude the “adding control flow to Kaleidoscope” chapter of the tutorial. In this chapter we added two control flow constructs, and used them to motivate a couple of aspects of the LLVM IR that are important for front-end implementors to know. In the next chapter of our saga, we will get a bit crazier and add [user-defined operators](https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl06.html) to our poor innocent language.
