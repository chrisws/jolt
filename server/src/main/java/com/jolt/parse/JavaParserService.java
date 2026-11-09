package com.jolt.parse;public class JavaParserService {
    private final Path filePath;
    public JavaParserService(Path p) { this.filePath = p; }

    public List<ClassSymbol> parseFile() throws Exception {
        String src = Files.readString(filePath);
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setResolveBindings(true);
        parser.setStatementsAsParsed(false);
        parser.setSource(src.toCharArray());
        CompilationUnit cu = (CompilationUnit) parser.parse(null);
        String pkg = cu.getPackage().getName().toString();
        List<ClassSymbol> result = new ArrayList<>();
        for (TypeDeclaration td : cu.types()) {
            result.add(extract(td, pkg, src));