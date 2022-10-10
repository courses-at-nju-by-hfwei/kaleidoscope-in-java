package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.Common;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.*;


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
        LLVMValueRef condV = cond.codegen();
        if (condV == null) {
            return null;
        }

        // Convert condition to a bool by comparing non-equal to 0.0.
        condV = LLVMBuildFCmp(CodeGenerator.builder, LLVMRealONE, condV, Common.ZERO,"ifcond");

        LLVMValueRef theFunction = LLVMGetBasicBlockParent(LLVMGetInsertBlock(CodeGenerator.builder));

        // Create blocks for the then and else cases.  Insert the 'then' block at the
        // end of the function.
        LLVMBasicBlockRef thenBB = LLVMAppendBasicBlock(theFunction, "then");
        LLVMBasicBlockRef elseBB = LLVMAppendBasicBlock(theFunction, "else");
        LLVMBasicBlockRef mergeBB = LLVMAppendBasicBlock(theFunction, "ifcont");

        LLVMBuildCondBr(CodeGenerator.builder, condV, thenBB, elseBB);

        // Emit then value.
        LLVMPositionBuilderAtEnd(CodeGenerator.builder, thenBB);

        LLVMValueRef thenV = then.codegen();
        if (thenV == null) {
            return null;
        }

        LLVMBuildBr(CodeGenerator.builder, mergeBB);
        // Codegen of 'Then' can change the current block, update ThenBB for the PHI.
        thenBB = LLVMGetInsertBlock(CodeGenerator.builder);

        // Emit else block.
        LLVMPositionBuilderAtEnd(CodeGenerator.builder, elseBB);

        LLVMValueRef elseV = Else.codegen();
        if (elseV == null) {
            return null;
        }

        LLVMBuildBr(CodeGenerator.builder, mergeBB);
        // codegen of 'Else' can change the current block, update ElseBB for the PHI.
        elseBB = LLVMGetInsertBlock(CodeGenerator.builder);

        // Emit merge block.
        LLVMPositionBuilderAtEnd(CodeGenerator.builder, mergeBB);
        LLVMValueRef phi = LLVMBuildPhi(CodeGenerator.builder, Common.DOUBLE_TYPE, "iftmp");

        PointerPointer<Pointer> phiValues = new PointerPointer<>(2)
                .put(0, thenV)
                .put(1, elseV);
        PointerPointer<Pointer> phiBlocks = new PointerPointer<>(2)
                .put(0, thenBB)
                .put(1, elseBB);
        LLVMAddIncoming(phi, phiValues, phiBlocks, 2);

        return phi;
    }
}
