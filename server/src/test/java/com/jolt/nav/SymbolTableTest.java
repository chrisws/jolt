package com.jolt.nav;

import com.jolt.model.*;  
import org.junit.jupiter.api.BeforeEach;  
import org.junit.jupiter.api.Test;  

import java.util.List;  

import static org.junit.jupiter.api.Assertions.*;  

class SymbolTableTest {  

  private SymbolTable table;  

  @BeforeEach  
  void setUp() { table = new SymbolTable(); }  

  private ClassSymbol makeClass(String fqn, String superClass, List<String> interfaces) {
    String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
    var cs = new ClassSymbol(fqn, simpleName, SymbolKind.CLASS, "src/" + simpleName + ".java");
    cs.superClass = superClass;
    if (interfaces != null) cs.interfaces.addAll(interfaces);
    return cs;
  }  

  @Test  
  void findDefinitionReturnsAddedClass() {    
    var cs = makeClass("com.foo.Bar", null, null);   
    table.addClass(cs);   

    assertTrue(table.findDefinition("com.foo.Bar").isPresent());   
    assertEquals("Bar", table.findDefinition("com.foo.Bar").get().name);  
  }  

  @Test  
  void findDefinitionReturnsEmptyForUnknown() {    
    assertTrue(table.findDefinition("nonexistent.Class").isEmpty());  
  }  

  @Test  
  void getSuperChainWalksUpHierarchy() {    
    var grandparent = makeClass("com.base.Base", null, null);   
    var parent = makeClass("com.mid.Mid", "com.base.Base", null);   
    var child = makeClass("com.leaf.Leaf", "com.mid.Mid", null);   

    table.addAll(List.of(grandparent, parent, child));

    List<String> chain = table.getSuperChain("com.leaf.Leaf");
    assertEquals(2, chain.size());
    assertEquals("com.mid.Mid", chain.get(0));
    assertEquals("com.base.Base", chain.get(1));
  }

  @Test  
  void getSubTypesFindsDirectChildren() {    
    var parent = makeClass("com.parent.P", null, List.of());   
    var child1 = makeClass("com.child.C1", "com.parent.P", null);   
    var child2 = makeClass("com.child.C2", "com.parent.P", null);   
    var unrelated = makeClass("com.other.O", null, null);   

    table.addAll(List.of(parent, child1, child2, unrelated));

    List<ClassSymbol> subs = table.getSubTypes("com.parent.P");
    assertEquals(2, subs.size());
  }

  @Test  
  void addReferenceAndFindReferences() {    
    table.addReference("com.foo.Bar", "src/Baz.java", 42);   
    table.addReference("com.foo.Bar", "src/Qux.java", 7);   

    var refs = table.findReferences("com.foo.Bar");   
    assertEquals(2, refs.size());     
    assertEquals("src/Baz.java", refs.get(0).filePath());     
    assertEquals(42, refs.get(0).line());  
  }  

  @Test  
  void clearRemovesAllEntries() {    
    table.addClass(makeClass("com.a.A", null, null));   
    assertEquals(1, table.size());   

    table.clear();   
    assertEquals(0, table.size());   
    assertTrue(table.findDefinition("com.a.A").isEmpty());  
  }  

}