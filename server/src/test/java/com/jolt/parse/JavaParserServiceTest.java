package com.jolt.parse;

import com.jolt.model.ClassSymbol;
import com.jolt.model.FieldSymbol;
import com.jolt.model.MethodSymbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserServiceTest {

  @TempDir Path tempDir;
  private ClassSymbol cs;

  @BeforeEach
  void setup() throws Exception {
    String src = """
      package com.example;

      import java.util.List;

      public class Sample {
        private int count;
        protected List<String> names;
        public static final String VERSION = "1.0";

        public int getCount() { return count; }

        public void setName(String name) throws IllegalArgumentException {}

        private <T> T identity(T obj) { return obj; }
      }
      """;  
    Path file = tempDir.resolve("Sample.java");  
    Files.writeString(file, src);  

    List<ClassSymbol> symbols = new JavaParserService(file).parseFile();  
    assertFalse(symbols.isEmpty(), "Expected at least one top-level type");  
    cs = symbols.get(0);  
  }  

  @Test  
  void parsesFqnAndName() {  
    assertEquals("com.example.Sample", cs.fqn);  
    assertEquals("Sample", cs.name);  
  }  

  @Test  
  void parsesFields() {  
    assertTrue(cs.fields.containsKey("count"));  
    FieldSymbol countField = cs.fields.get("count");  
    assertNotNull(countField);  
    assertEquals("int", countField.type());  
    assertEquals("private", countField.access());  

    FieldSymbol versionField = cs.fields.get("VERSION");  
    assertNotNull(versionField);  
    assertEquals("String", versionField.type());  
    assertEquals("public", versionField.access());  

    // names field should be protected
    FieldSymbol namesField = cs.fields.get("names");
    assertNotNull(namesField);
    assertEquals("protected", namesField.access());
  }

  @Test
  void parsesMethods() {
    // Our key format is "name(params)" to support overloads
    MethodSymbol getCount = findMethod(cs, "getCount");
    assertNotNull(getCount, "getCount method not found");
    assertEquals("int", getCount.returnType());
    assertEquals("public", getCount.access());

    MethodSymbol setName = findMethod(cs, "setName");
    assertNotNull(setName, "setName method not found");
    assertEquals("void", setName.returnType());

    MethodSymbol identity = findMethod(cs, "identity");
    assertNotNull(identity, "identity method not found");
    assertEquals("T", identity.returnType());
  }

  @Test  
  void detectsConstructorIfPresent() throws Exception {  
    String srcWithCtor = """  
      package com.example;  

      public class WithCtor {  
        private int x;  
        public WithCtor(int x) { this.x = x; }  
      }  
      """;  
    Path file = tempDir.resolve("WithCtor.java");  
    Files.writeString(file, srcWithCtor);  

    List<ClassSymbol> symbols = new JavaParserService(file).parseFile();  
    ClassSymbol wcs = symbols.get(0);  

    MethodSymbol ctor = findMethod(wcs, "<init>");  
    assertNotNull(ctor, "Constructor should be extracted as <init>");  
    assertEquals("public", ctor.access());  
  }  

  /** Helper: find a method by simple name from the methods map (keys are "name(params)"). */  
  private static MethodSymbol findMethod(ClassSymbol cs, String name) {  
    for (var entry : cs.methods.entrySet()) {  
      if (entry.getKey().startsWith(name + "(")) {
        return entry.getValue();
      }
    } 
    return null; 
  } 

}

