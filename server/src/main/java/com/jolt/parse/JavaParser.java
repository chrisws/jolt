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

 import com.jolt.model.CallEdge;
 import com.jolt.model.ClassSymbol;
 import com.jolt.model.FieldSymbol;
 import com.jolt.model.MethodSymbol;
 import com.jolt.model.SymbolKind;
 import com.jolt.nav.CallGraph;
 import org.eclipse.jdt.core.dom.AST;
 import org.eclipse.jdt.core.dom.ASTNode;
 import org.eclipse.jdt.core.dom.ASTParser;
 import org.eclipse.jdt.core.dom.CompilationUnit;
 import org.eclipse.jdt.core.dom.EnumDeclaration;
 import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
 import org.eclipse.jdt.core.dom.FieldDeclaration;
 import org.eclipse.jdt.core.dom.MethodDeclaration;
 import org.eclipse.jdt.core.dom.MethodInvocation;
 import org.eclipse.jdt.core.dom.RecordDeclaration;
 import org.eclipse.jdt.core.dom.TypeDeclaration;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import java.io.IOException;
 import java.nio.charset.StandardCharsets;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.util.ArrayList;
 import java.util.List;

 /** Parses .java files using the JDT AST to extract type symbols. */  
 public class JavaParser {  

   private static final Logger log = LoggerFactory.getLogger(JavaParser.class);  

   private CallGraph callGraph; // nullable — set via setCallGraph()

   /** Set the call graph to populate during parsing. */  
   public void setCallGraph(CallGraph cg) { this.callGraph = cg; }  

   /** Parse a .java file and extract all top-level type symbols. */  
   public List<ClassSymbol> parse(Path filePath) {  
     try {  
       String source = readFile(filePath);  
       return doParse(source, filePath);  
     } catch (IOException e) {  
       log.warn("Failed to read {}: {}", filePath, e.getMessage());  
       return List.of();  
     } catch (Exception e) {  
       log.debug("Parse error in {}: {}", filePath, e.getMessage());  
       return List.of(); // graceful degradation — caller indexes as raw text  
     }  
   }  

  private List<ClassSymbol> doParse(String source, Path filePath) {  
    ASTParser parser = ASTParser.newParser(AST.JLS21);  
    parser.setKind(ASTParser.K_COMPILATION_UNIT);  
    parser.setSource(source.toCharArray());  
    parser.setResolveBindings(false);   // no classpath needed for structural extraction  

    CompilationUnit cu = (CompilationUnit) parser.createAST(null);  

    String pkg = "";
    if (cu.getPackage() != null && cu.getPackage().getName() != null) {
      pkg = cu.getPackage().getName().getFullyQualifiedName();
    }

    List<ClassSymbol> results = new ArrayList<>();
    
    // Iterate top-level types using only confirmed-available node types
    List<?> topLevelTypes = cu.types();
    for (int i = 0; i < topLevelTypes.size(); i++) {
      Object nodeObj = topLevelTypes.get(i);
      
      if (nodeObj instanceof TypeDeclaration td) {
        SymbolKind kind = detectTypeKind(td, source);  
        if (kind == SymbolKind.INTERFACE) {
          extractInterface(td, pkg, source, filePath.toString(), results);
        } else {
          extractClass(td, pkg, source, filePath.toString(), results);
        }
      } else if (nodeObj instanceof EnumDeclaration ed) {
        extractEnum(ed, pkg, source, filePath.toString(), results);
      } else if (nodeObj instanceof RecordDeclaration rd) {
        extractRecord(rd, pkg, source, filePath.toString(), results);
      } else {
        log.debug("Unhandled top-level type: {}", nodeObj.getClass().getSimpleName());
      }  
    }  

    return results;  
  }  

  /** Detect interface vs class by scanning source text backward from type name for keyword. */  
  private static SymbolKind detectTypeKind(TypeDeclaration td, String source) {  
    int namePos = td.getName().getStartPosition();  
    // Scan back up to ~50 chars for "interface" or "class" keyword  
    int searchStart = Math.max(0, namePos - 50);  
    String prefix = source.substring(searchStart, namePos);  
    if (prefix.contains("interface")) return SymbolKind.INTERFACE;  
    return SymbolKind.CLASS;  
  }  

  private void extractClass(TypeDeclaration td, String pkg, String source, String filePathStr, List<ClassSymbol> out) {  
    doExtractType(td.getName().getIdentifier(), td.bodyDeclarations(), 
                  td.getSuperclassType() != null ? td.getSuperclassType().toString() : null,
                  superInterfacesOf(td), SymbolKind.CLASS, pkg, source, filePathStr, out); 
  }  

  private void extractEnum(EnumDeclaration ed, String pkg, String source, String filePathStr, List<ClassSymbol> out) {  
    doExtractType(ed.getName().getIdentifier(), ed.bodyDeclarations(), 
                  null, superInterfacesOf(ed), SymbolKind.ENUM, pkg, source, filePathStr, out);  
  }  

  private void extractRecord(RecordDeclaration rd, String pkg, String source, String filePathStr, List<ClassSymbol> out) {  
    doExtractType(rd.getName().getIdentifier(), rd.bodyDeclarations(), 
                  null, superInterfacesOf(rd), SymbolKind.RECORD, pkg, source, filePathStr, out);  
  }  

  private void extractInterface(TypeDeclaration td, String pkg, String source, String filePathStr, List<ClassSymbol> out) {  
    doExtractType(td.getName().getIdentifier(), td.bodyDeclarations(), 
                  null, superInterfacesOf(td), SymbolKind.INTERFACE, pkg, source, filePathStr, out);  
  }  

  @SuppressWarnings("unchecked")
  private static List<String> superInterfacesOf(org.eclipse.jdt.core.dom.AbstractTypeDeclaration atd) {
    // AbstractTypeDeclaration has superInterfaceTypes() returning List<Type>
    try {
      var types = (List<?>) atd.getClass().getMethod("superInterfaceTypes").invoke(atd);
      List<String> result = new ArrayList<>();
      for (Object t : types) {
        result.add(t.toString());
      }
      return result;
    } catch (Exception e) {
      return List.of();
    }
  }

  private void doExtractType(String name, List<?> bodyDecls, String superClassStr, 
                             List<String> interfaces, SymbolKind kind, 
                             String pkg, String source, String filePathStr, List<ClassSymbol> out) {  
    String fqn = ParseUtils.buildFqn(pkg, name);  
    ClassSymbol cs = new ClassSymbol(fqn, name, kind, filePathStr);  

    int startOffset = -1;  
    int endOffset = -1;  
    // We need the ASTNode to get position — pass it through differently.  
    // For now use line 0 as placeholder since we don't have direct node access here.  
    // Phase 2+: refactor to pass ASTNode directly.  
    cs.lineStart = 0;  
    cs.lineEnd = 0;  

    if (superClassStr != null) cs.superClass = superClassStr;  
    cs.interfaces.addAll(interfaces);  

    // Process members from body declarations — isolate errors per member so one failure doesn't kill the whole class  
    for (int i = 0; i < bodyDecls.size(); i++) {  
      Object memberObj = bodyDecls.get(i);  

      try {
        if (memberObj instanceof MethodDeclaration md) {  
          handleMethod(md, fqn, cs, source);  
        } else if (memberObj instanceof FieldDeclaration fd) {  
          handleField(fd, cs, source);  
        } else if (memberObj instanceof TypeDeclaration nestedTd) { 
          // Detect interface vs class via source text  
          SymbolKind nkind = detectTypeKind(nestedTd, source);  
          String nsuper = (nkind == SymbolKind.CLASS && nestedTd.getSuperclassType() != null) 
                          ? nestedTd.getSuperclassType().toString() : null;  
          doExtractType(nestedTd.getName().getIdentifier(), nestedTd.bodyDeclarations(),
                        nsuper, superInterfacesOf(nestedTd), nkind,
                        pkgForNested(fqn), source, filePathStr, out); 
      } else if (memberObj instanceof EnumDeclaration ne) {  
        doExtractType(ne.getName().getIdentifier(), ne.bodyDeclarations(),
                      null, superInterfacesOf(ne), SymbolKind.ENUM,
                      pkgForNested(fqn), source, filePathStr, out);
      } else if (memberObj instanceof RecordDeclaration nr) {  
        doExtractType(nr.getName().getIdentifier(), nr.bodyDeclarations(),
                      null, superInterfacesOf(nr), SymbolKind.RECORD,
                      pkgForNested(fqn), source, filePathStr, out);
        }
      } catch (Exception e) {  
        log.debug("Error extracting member [{}]: {}", i, e.getMessage());  
      } 
    }  

    out.add(cs); 
  }  

  /** For nested types we use the outer FQN as pseudo-package so buildFqn produces Outer.Inner */  
  private static String pkgForNested(String outerFqn) { return outerFqn; }  

  private void handleMethod(MethodDeclaration md, String classFqn, ClassSymbol cs, String source) {  
    boolean isCtor = md.isConstructor();  
    String methodName = isCtor ? "<init>" : md.getName().getIdentifier();  

    String returnType = "void";  
    if (!isCtor) {
      // Bypass getReturnType() which triggers binding lookup in JDT 3.38 with resolveBindings=false; read field directly
      try {
        var rtField = MethodDeclaration.class.getDeclaredField("returnType");
        rtField.setAccessible(true);
        Object rtObj = rtField.get(md);
        if (rtObj != null) {
          returnType = rtObj.toString();
        }
      } catch (Exception e) {
        log.debug("Could not extract return type for {}: {}", methodName, e.getMessage());
      }
    }

    List<String> params = new ArrayList<>();  
    List<?> paramList;
    try { paramList = md.parameters(); } 
    catch (Exception e) { log.debug("parameters() failed for {}: {}", methodName, e.getMessage()); return; }

    for (int pi = 0; pi < paramList.size(); pi++) {  
      Object paramObj = paramList.get(pi);
      try {
        var svd = (org.eclipse.jdt.core.dom.SingleVariableDeclaration) paramObj;
        params.add(svd.getType().toString());
      } catch (Exception e) {
        log.debug("Param [{}] extraction issue: {}", pi, e.getMessage());
        params.add("?");
      }
    }  

    String access = ParseUtils.accessLevel(md.modifiers());  
    String signature = buildSignature(access, returnType, methodName, params);  

    int lineStart = ParseUtils.offsetToLine(source, md.getStartPosition());  
    int endOffset = md.getStartPosition() + md.getLength();  
    int lineEnd = ParseUtils.offsetToLine(source, endOffset);  

    MethodSymbol ms = new MethodSymbol(
        classFqn + "#" + methodName,
        methodName,
        signature,
        returnType,
        params,
        lineStart,
        lineEnd,
        access
    );  

    // Key by name+params to support overloads: "name(T1,T2)" 
    String key = methodName + "(" + String.join(",", params) + ")";  
    cs.methods.put(key, ms);  

    // Extract call sites for the call graph  
    if (callGraph != null && md.getBody() != null) {  
      extractCallSites(md.getBody(), classFqn + "#" + methodName, source);  
    }  
  }  

  /** Walk the method body AST collecting MethodInvocation nodes. */  
  private void extractCallSites(ASTNode body, String callerFqn, String source) {  
    body.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {  
      @Override  
      public boolean visit(MethodInvocation node) {  
        try {  
          String calleeName = node.getName().getIdentifier();  
          String receiver = "";  
          if (node.getExpression() != null) {  
            // Try to get the receiver type name from the expression text  
            String exprText = node.getExpression().toString().trim();  
            // If it's a simple name (variable), use it as-is; if it's "this", skip  
            if (!exprText.equals("this") && !exprText.isEmpty()) {  
              // Take just the first token (type name hint)  
              receiver = exprText.split("\\.")[0];  
            }  
          } else {  
            // No receiver — likely a method in the same class or inherited  
            receiver = "";  
          }  

          int line = ParseUtils.offsetToLine(source, node.getStartPosition());  
          callGraph.addEdge(new CallEdge(callerFqn, calleeName, receiver, line));  
        } catch (Exception e) {  
          // Skip malformed nodes silently  
        }  
        return true; // continue visiting children  
      }  
    });  
  }  

  private void handleField(FieldDeclaration fd, ClassSymbol cs, String source) {  
    String typeStr = fd.getType().toString();  
    String access = ParseUtils.accessLevel(fd.modifiers());  

    int lineStart = ParseUtils.offsetToLine(source, fd.getStartPosition());  
    int endOffset = fd.getStartPosition() + fd.getLength();  
    int lineEnd = ParseUtils.offsetToLine(source, endOffset);  

    for (Object fragmentObj : fd.fragments()) {  
      try {
        var svd = (org.eclipse.jdt.core.dom.VariableDeclarationFragment) fragmentObj;
        FieldSymbol fs = new FieldSymbol(
            svd.getName().getIdentifier(),
            typeStr,
            lineStart,
            lineEnd,
            access
        );  
        cs.fields.put(fs.name(), fs);  
      } catch (ClassCastException e) {  
        log.debug("Unexpected field fragment type: {}", fragmentObj.getClass().getName());  
      }
    }  
  }  

  private static String buildSignature(String access, String retType, String name, List<String> params) {  
    StringBuilder sb = new StringBuilder();  
    if (!"package".equals(access)) sb.append(access).append(' ');  
    if (!name.equals("<init>") && !retType.isEmpty()) sb.append(retType).append(' ');  
    sb.append(name).append('(');  
    sb.append(String.join(", ", params));  
    sb.append(')');  
    return sb.toString();  
  }  

  /** Read file content using NIO. */  
  private static String readFile(Path path) throws IOException {  
    byte[] bytes = Files.readAllBytes(path);  
    return new String(bytes, StandardCharsets.UTF_8);  
  }  

}

