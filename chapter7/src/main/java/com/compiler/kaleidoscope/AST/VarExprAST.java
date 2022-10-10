package com.compiler.kaleidoscope.AST;

import com.compiler.kaleidoscope.CodeGenerator;
import com.compiler.kaleidoscope.utils.AllocaHelper;
import com.compiler.kaleidoscope.utils.Common;
import org.apache.commons.lang3.tuple.Pair;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.llvm.global.LLVM.*;

/// VarExprAST - Expression class for var/in
public class VarExprAST extends ExprAST{
    List<Pair<String, ExprAST>> varNames;
    ExprAST body;

    public VarExprAST(List<Pair<String, ExprAST>> varNames, ExprAST body) {
        this.varNames = varNames;
        this.body = body;
    }

    @Override
    public LLVMValueRef codegen() {
        List<LLVMValueRef> oldBindings = new ArrayList<>();

        LLVMValueRef theFunction = LLVMGetBasicBlockParent(LLVMGetInsertBlock(CodeGenerator.builder));

        // Register all variables and emit their initializer.
        for (int i = 0, e = varNames.size(); i < e; ++i) {
            String varName = varNames.get(i).getLeft();
            ExprAST init = varNames.get(i).getRight();

            // Emit the initializer before adding the variable to scope, this prevents
            // the initializer from referencing the variable itself, and permits stuff
            // like this:
            //  var a = 1 in
            //    var a = a in ...   # refers to outer 'a'.
            LLVMValueRef initVal;
            if (init != null) {
                initVal = init.codegen();
                if (initVal == null) {
                    return null;
                }
            } else {
                initVal = Common.ZERO;
            }
            LLVMValueRef alloca = AllocaHelper.createEntryBlockAlloca(theFunction, varName);
            LLVMBuildStore(CodeGenerator.builder, initVal, alloca);

            // Remember the old variable binding so that we can restore the binding when
            // we unrecurse.
            oldBindings.add(CodeGenerator.namedValues.getOrDefault(varName, null));

            // Remember this binding.
            CodeGenerator.namedValues.put(varName, alloca);
        }

        // Codegen the body, now that all vars are in scope.
        LLVMValueRef bodyVal = body.codegen();
        if (bodyVal == null) {
            return null;
        }

        // Pop all our variables from scope.
        for (int i = 0, e = varNames.size(); i < e; ++i) {
            CodeGenerator.namedValues.put(varNames.get(i).getLeft(), oldBindings.get(i));
        }
        return bodyVal;
    }
}
