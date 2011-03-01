/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package org.pitest.mutationtest.engine.gregor.mutators;

import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;

import java.util.HashMap;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.Context;
import org.pitest.mutationtest.engine.gregor.LineTrackingMethodAdapter;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;

public enum MethodCallMutator implements MethodMutatorFactory {

  METHOD_CALL_MUTATOR;

  public MethodVisitor create(final Context context,
      final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
    return new MethodCallMethodVisitor(methodInfo, context, methodVisitor);
  }

}

class MethodCallMethodVisitor extends LineTrackingMethodAdapter {

  private final static HashMap<Type, Integer> RETURN_TYPE_MAP = new HashMap<Type, Integer>();

  static {
    RETURN_TYPE_MAP.put(Type.INT_TYPE, ICONST_0);
    RETURN_TYPE_MAP.put(Type.BOOLEAN_TYPE, ICONST_0);
    RETURN_TYPE_MAP.put(Type.BYTE_TYPE, ICONST_0);
    RETURN_TYPE_MAP.put(Type.CHAR_TYPE, ICONST_0);
    RETURN_TYPE_MAP.put(Type.SHORT_TYPE, ICONST_0);
    RETURN_TYPE_MAP.put(Type.LONG_TYPE, LCONST_0);
    RETURN_TYPE_MAP.put(Type.FLOAT_TYPE, FCONST_0);
    RETURN_TYPE_MAP.put(Type.DOUBLE_TYPE, DCONST_0);
  }

  public MethodCallMethodVisitor(final MethodInfo methodInfo,
      final Context context, final MethodVisitor writer) {
    super(methodInfo, context, writer);
  }

  @Override
  public void visitMethodInsn(final int opcode, final String owner,
      final String name, final String desc) {

    if (isCallToSuperOrOwnConstructor(name, owner)) {
      this.mv.visitMethodInsn(opcode, owner, name, desc);
    } else {
      final MutationIdentifier newId = this.context.registerMutation(
          MethodCallMutator.class, "removed call to " + owner + "::" + name);

      if (this.context.shouldMutate(newId)) {

        popStack(desc, name);
        popThisIfNotStatic(opcode);
        putReturnValueOnStack(desc, name);

      } else {
        this.mv.visitMethodInsn(opcode, owner, name, desc);
      }
    }

  }

  private boolean isCallToSuperOrOwnConstructor(final String name,
      final String owner) {
    return this.methodInfo.isConstructor()
        && MethodInfo.isConstructor(name)
        && (owner.equals(this.context.getClassInfo().getName()) || this.context
            .getClassInfo().getSuperName().equals(owner));
  }

  private void popThisIfNotStatic(final int opcode) {
    if (!isStatic(opcode)) {
      this.mv.visitInsn(POP);
    }
  }

  private void popStack(final String desc, final String name) {
    final Type[] argTypes = Type.getArgumentTypes(desc);
    for (int i = argTypes.length - 1; i >= 0; i--) {
      final Type argumentType = argTypes[i];
      if (argumentType.getSize() != 1) {
        this.mv.visitInsn(POP2);
      } else {
        this.mv.visitInsn(POP);
      }
    }

    if (MethodInfo.isConstructor(name)) {
      this.mv.visitInsn(POP);
    }
  }

  private static boolean isStatic(final int opcode) {
    return INVOKESTATIC == opcode;
  }

  private void putReturnValueOnStack(final String desc, final String name) {
    final Type returnType = Type.getReturnType(desc);
    if (!returnType.equals(Type.VOID_TYPE)) {
      final Integer opCode = RETURN_TYPE_MAP.get(returnType);
      if (opCode == null) {
        this.mv.visitInsn(Opcodes.ACONST_NULL);
      } else {
        this.mv.visitInsn(opCode);
      }
    } else if (MethodInfo.isConstructor(name)) {
      this.mv.visitInsn(Opcodes.ACONST_NULL);
    }
  }

}