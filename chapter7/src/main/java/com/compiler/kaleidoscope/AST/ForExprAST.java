package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.AllocaHelper;
import com.compiler.kaleidoscope.utils.Common;
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
        LLVMValueRef theFunction = LLVMGetBasicBlockParent(LLVMGetInsertBlock(CodeGenerator.builder));

        // Create an alloca for the variable in the entry block.
        LLVMValueRef alloca = AllocaHelper.createEntryBlockAlloca(theFunction, varName);

        // Emit the start code first, without 'variable' in scope.
        LLVMValueRef startVal = start.codegen();
        if (startVal == null) {
            return null;
        }

        // Store the value into the alloca.
        LLVMBuildStore(CodeGenerator.builder, startVal, alloca);

        // Make the new basic block for the loop header, inserting after current
        // block.
        LLVMBasicBlockRef preheaderBB = LLVMGetInsertBlock(CodeGenerator.builder);
        LLVMBasicBlockRef loopBB = LLVMAppendBasicBlock(theFunction, "loop");

        // Insert an explicit fall through from the current block to the LoopBB.
        LLVMBuildBr(CodeGenerator.builder, loopBB);

        // Start insertion in LoopBB.
        LLVMPositionBuilderAtEnd(CodeGenerator.builder, loopBB);

        // Within the loop, the variable is defined equal to the PHI node.  If it
        // shadows an existing variable, we have to restore it, so save it now.
        LLVMValueRef oldVal = CodeGenerator.namedValues.get(varName);
        CodeGenerator.namedValues.put(varName, alloca);

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
            stepVal = Common.ONE;
        }

        // Compute the end condition.
        LLVMValueRef endCond = end.codegen();
        if (endCond == null) {
            return null;
        }

        // Reload, increment, and restore the alloca.  This handles the case where
        // the body of the loop mutates the variable.
        LLVMValueRef curVar = LLVMBuildLoad2(CodeGenerator.builder, Common.DOUBLE_TYPE, alloca, varName);
        LLVMValueRef nextVar = LLVMBuildFAdd(CodeGenerator.builder, curVar, stepVal, "nextvar");
        LLVMBuildStore(CodeGenerator.builder, nextVar, alloca);

        // Convert condition to a bool by comparing non-equal to 0.0.
        endCond = LLVMBuildFCmp(CodeGenerator.builder, LLVMRealONE, endCond, Common.ZERO,"loopcond");

        // Create the "after loop" block and insert it.
        LLVMBasicBlockRef loopEndBB = LLVMGetInsertBlock(CodeGenerator.builder);
        LLVMBasicBlockRef afterBB = LLVMAppendBasicBlock(theFunction, "afterloop");

        // Insert the conditional branch into the end of LoopEndBB.
        LLVMBuildCondBr(CodeGenerator.builder, endCond, loopBB, afterBB);

        // Any new code will be inserted in AfterBB.
        LLVMPositionBuilderAtEnd(CodeGenerator.builder, afterBB);

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
