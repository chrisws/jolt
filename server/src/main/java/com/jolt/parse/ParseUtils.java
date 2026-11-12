/*
 * This file is part of Jolt
 *
 * Copyright (C) 2026 Chris Warren-Smith. All rights reserved.
*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
*
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.jolt.parse;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import java.util.List;

public final class ParseUtils {

  private ParseUtils() {}

  public static String buildFqn(String pkg, String name) {
    return pkg.isEmpty() ? name : pkg + "." + name;
  }

  public static String buildMethodSignature(MethodDeclaration md) {
    StringBuilder sb = new StringBuilder();
    if (!md.isConstructor()) {
      sb.append(md.getReturnType().toString()).append(" ");
    } else {
      sb.append("<init>");
    }
    sb.append("(");
    List<?> params = md.parameters();
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) sb.append(", ");
      SingleVariableDeclaration p = (SingleVariableDeclaration) params.get(i);
      sb.append(p.getType().toString());
      String varName = p.getName().getIdentifier();
      if (!varName.isEmpty() && !varName.equals("this")) {
        sb.append(" ").append(varName);
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public static void collectAnnotations(List<IExtendedModifier> modifiers, List<String> target) {
    for (IExtendedModifier m : modifiers) {
      if (m instanceof Annotation a) {
        target.add(a.getTypeName().getFullyQualifiedName());
      }
    }
  }

  public static boolean isStatic(List<IExtendedModifier> mods) {
    for (IExtendedModifier m : mods) {
      if (m instanceof Modifier mod && mod.isStatic()) return true;
    }
    return false;
  }

  public static boolean isFinal(List<IExtendedModifier> mods) {
    for (IExtendedModifier m : mods) {
      if (m instanceof Modifier mod && mod.isFinal()) return true;
    }
    return false;
  }

  public static boolean isAbstract(List<IExtendedModifier> mods) {
    for (IExtendedModifier m : mods) {
      if (m instanceof Modifier mod && mod.isAbstract()) return true;
    }
    return false;
  }

  public static String accessLevel(List<IExtendedModifier> mods) {
    for (IExtendedModifier m : mods) {
      if (m instanceof Modifier mod) {
        if (mod.isPublic()) return "public";
        if (mod.isPrivate()) return "private";
        if (mod.isProtected()) return "protected";
      }
    }
    return "package";
  }

  public static int offsetToLine(String source, int offset) {
    int clamped = Math.max(0, Math.min(offset, source.length()));
    int line = 1;
    for (int i = 0; i < clamped; i++) {
      if (source.charAt(i) == '\n') line++;
    }
    return line;
  }

}
