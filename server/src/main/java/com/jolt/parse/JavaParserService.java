package com.jolt.parse;

import com.jolt.model.ClassSymbol;
import com.jolt.model.FieldSymbol;
import com.jolt.model.MethodSymbol;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaParserService {

  private final Path filePath;
  private final String source;

  public JavaParserService(Path path) throws IOException {
    this.filePath = path.toAbsolutePath();
    this.source = Files.readString(this.filePath);
  }

  public ClassSymbol parseFile() {
    CompilationUnit cu = parseSource(source);
    String pkg = cu.getPackage().getName().getFullyQualifiedName();
    var types = cu.types();
    for (int i = 0; i < types.size(); i++) {
      AbstractTypeDeclaration atd = (AbstractTypeDeclaration) types.get(i);
      return extractType(atd, pkg);
    }
    return null;
  }

  private CompilationUnit parseSource(String src) {
    ASTParser parser = ASTParser.newParser(AST.JLS21);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setResolveBindings(true);
    parser.setEnvironment(new String[0], null, null, false);
    parser.setSource(src.toCharArray());
    ASTNode node = parser.createAST(null);
    return (CompilationUnit) node;
  }

  private ClassSymbol extractType(AbstractTypeDeclaration atd, String pkg) {
    ClassSymbol cs = new ClassSymbol();
    cs.fqn = ParseUtils.buildFqn(pkg, atd.getName().getIdentifier());
    cs.simpleName = atd.getName().getIdentifier();
    cs.filePath = filePath.toString();
    cs.lineStart = lineAt(atd.getStartPosition());
    cs.lineEnd = lineAt(atd.getStartPosition() + atd.getLength() - 1);
    ParseUtils.collectAnnotations(atd.modifiers(), cs.annotations);

    if (atd instanceof TypeDeclaration td) {
      var sc = td.getSuperclass();
      if (sc != null) cs.superClass = sc.toString();
      var ifaces = td.superInterfaceTypes();
      for (int i = 0; i < ifaces.size(); i++) {
        cs.interfaces.add(ifaces.get(i).toString());
      }
    }

    var bodies = atd.bodyDeclarations();
    for (int i = 0; i < bodies.size(); i++) {
      BodyDeclaration bd = (BodyDeclaration) bodies.get(i);
      if (bd instanceof MethodDeclaration md) {
        extractMethod(md, cs);
      } else if (bd instanceof FieldDeclaration fd) {
        extractField(fd, cs);
      }
    }
    return cs;
  }

  private void extractMethod(MethodDeclaration md, ClassSymbol cs) {
    MethodSymbol ms = new MethodSymbol();
    String name = md.isConstructor() ? "<init>" : md.getName().getIdentifier();
    ms.name = name;
    ms.fqn = cs.fqn + "#" + name;
    ms.signature = ParseUtils.buildMethodSignature(md);
    ms.returnType = md.isConstructor() ? "" : md.getReturnType().toString();

    var params = md.parameters();
    for (int i = 0; i < params.size(); i++) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) params.get(i);
      ms.params.add(p.getType().toString());
    }

    ms.isStatic = ParseUtils.isStatic(md.modifiers());
    ms.isAbstract = ParseUtils.isAbstract(md.modifiers());
    ms.isFinal = ParseUtils.isFinal(md.modifiers());
    ms.lineStart = lineAt(md.getStartPosition());
    ms.lineEnd = lineAt(md.getStartPosition() + md.getLength() - 1);

    var exceptions = md.thrownExceptionTypes();
    for (int i = 0; i < exceptions.size(); i++) {
      ms.exceptions.add(exceptions.get(i).toString());
    }

    cs.methods.put(ms.signature, ms);
  }

  private void extractField(FieldDeclaration fd, ClassSymbol cs) {
    var fragments = fd.fragments();
    for (int i = 0; i < fragments.size(); i++) {
      SingleVariableDeclaration svd = (SingleVariableDeclaration) fragments.get(i);
      FieldSymbol fs = new FieldSymbol();
      fs.name = svd.getName().getIdentifier();
      fs.type = svd.getType().toString();
      fs.access = ParseUtils.accessLevel(fd.modifiers());
      fs.isStatic = ParseUtils.isStatic(fd.modifiers());
      fs.isFinal = ParseUtils.isFinal(fd.modifiers());
      fs.lineStart = lineAt(svd.getStartPosition());
      fs.lineEnd = lineAt(svd.getStartPosition() + svd.getLength() - 1);
      ParseUtils.collectAnnotations(fd.modifiers(), fs.annotations);
      cs.fields.put(fs.name, fs);
    }
  }

  private int lineAt(int offset) {
    return ParseUtils.offsetToLine(source, offset);
  }

}
