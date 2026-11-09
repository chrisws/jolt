package com.jolt.parse;

import com.jolt.model.ClassSymbol;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserServiceTest {

    @Test
    void parsesClassSymbolFile() throws Exception {
        Path target = Path.of("src/main/java/com/jolt/model/ClassSymbol.java");
        var parser = new JavaParserService(target);
        ClassSymbol cs = parser.parseFile();

        assertNotNull(cs);
        assertEquals("com.jolt.model.ClassSymbol", cs.fqn);
        assertEquals("ClassSymbol", cs.simpleName);
        assertTrue(cs.lineStart > 0);
        assertTrue(cs.lineEnd > cs.lineStart);
    }

    @Test
    void extractsMethods() throws Exception {
        Path target = Path.of("src/main/java/com/jolt/model/ClassSymbol.java");
        var parser = new JavaParserService(target);
        ClassSymbol cs = parser.parseFile();

        // ClassSymbol has a toString() method
        assertFalse(cs.methods.isEmpty());
        assertTrue(cs.methods.values().stream().anyMatch(m -> m.name.equals("toString")));
    }

    @Test
    void extractsFields() throws Exception {
        Path target = Path.of("src/main/java/com/jolt/model/FieldSymbol.java");
        var parser = new JavaParserService(target);
        ClassSymbol cs = parser.parseFile();

        assertNotNull(cs);
        assertEquals("com.jolt.model.FieldSymbol", cs.fqn);

        // FieldSymbol has: name, type, access, isStatic, isFinal, lineStart, lineEnd, annotations
        assertTrue(cs.fields.containsKey("name"));
        assertTrue(cs.fields.containsKey("type"));
        assertTrue(cs.fields.containsKey("isStatic"));
        
        var staticField = cs.fields.get("isStatic");
        assertNotNull(staticField);
        assertEquals("boolean", staticField.type);
    }

}
