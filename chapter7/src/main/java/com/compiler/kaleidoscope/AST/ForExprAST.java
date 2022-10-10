package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.Common;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import static org.bytedeco.llvm.global.LLVM.*;

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
        // Emit the start code first, without 'variable' in scope.
        LLVMValueRef startVal = start.codegen();
        if (startVal == null) {
            return null;
        }

        // Make the new basic block for the loop header, inserting after current
        // block.
        LLVMValueRef theFunction = LLVMGetBasicBlockParent(LLVMGetInsertBlock(CodeGenerator.builder));
        LLVMBasicBlockRef preheaderBB = LLVMGetInsertBlock(CodeGenerator.builder);
        LLVMBasicBlockRef loopBB = LLVMAppendBasicBlock(theFunction, "loop");

        // Insert an explicit fall through from the current block to the LoopBB.
        LLVMBuildBr(CodeGenerator.builder, loopBB);

        // Start insertion in LoopBB.
        LLVMPositionBuilderAtEnd(CodeGenerator.builder, loopBB);

        // Start the PHI node with an entry for Start.
        LLVMValueRef variable = LLVMBuildPhi(CodeGenerator.builder, Common.DOUBLE_TYPE, varName);

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

        // Compute the end condition.
        LLVMValueRef endCond = end.codegen();
        if (endCond == null) {
            return null;
        }

        // Convert condition to a bool by comparing non-equal to 0.0.
        endCond = LLVMBuildFCmp(CodeGenerator.builder, LLVMRealONE, endCond, Common.ZERO,"loopcond");

        // Create the "after loop" block and insert it.
        LLVMBasicBlockRef loopEndBB = LLVMGetInsertBlock(CodeGenerator.builder);
        LLVMBasicBlockRef afterBB = LLVMAppendBasicBlock(theFunction, "afterloop");

        // Insert the conditional branch into the end of LoopEndBB.
        LLVMBuildCondBr(CodeGenerator.builder, endCond, loopBB, afterBB);

        // Any new code will be inserted in AfterBB.
        LLVMPositionBuilderAtEnd(CodeGenerator.builder, afterBB);

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
        return Common.ZERO;
    }
}
