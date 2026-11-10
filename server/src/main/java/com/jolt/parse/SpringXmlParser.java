package com.jolt.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses Spring XML configuration files to extract bean definitions,
 * their dependencies (constructor-arg, property refs), and component-scan paths.
 */
public class SpringXmlParser {

  private static final Logger log = LoggerFactory.getLogger(SpringXmlParser.class);

  public record BeanDef(
      String id,           // bean id
      String className,    // fully qualified class name (from class attr)
      String scope,        // singleton, prototype, etc.
      List<String> dependsOn,  // other bean ids this depends on (via ref)
      int line             // approximate line (0 if unknown)
  ) {}

  public record SpringConfig(
      Path filePath,
      List<BeanDef> beans,
      List<String> componentScanPaths,  // from <context:component-scan base-package="..."/>
      List<String> importedFiles       // from <import resource="..."/> or <beans profile="..."> imports
  ) {}

  /** Parse a Spring XML config file. */
  public SpringConfig parse(Path filePath) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE protection
      DocumentBuilder builder = factory.newDocumentBuilder();
      var doc = builder.parse(filePath.toFile());

      List<BeanDef> beans = new ArrayList<>();
      List<String> componentScans = new ArrayList<>();
      List<String> imports = new ArrayList<>();

      // Extract beans
      var beanNodes = doc.getElementsByTagName("bean");
      for (int i = 0; i < beanNodes.getLength(); i++) {
        var node = beanNodes.item(i);
        if (!(node instanceof org.w3c.dom.Element el)) continue;

        String id = el.getAttribute("id");
        String className = el.getAttribute("class");
        String scope = el.getAttribute("scope");
        if (scope.isEmpty()) scope = "singleton";

        // Find all <ref bean="..."/> in constructor-arg and property elements
        List<String> deps = new ArrayList<>();
        collectRefs(el, deps);

        if (!id.isEmpty() || !className.isEmpty()) {
          beans.add(new BeanDef(id, className, scope, deps, 0));
        }
      }

      // Extract component-scan paths
      var scanNodes = doc.getElementsByTagName("context:component-scan");
      for (int i = 0; i < scanNodes.getLength(); i++) {
        var node = scanNodes.item(i);
        if (node instanceof org.w3c.dom.Element el) {
          String basePkg = el.getAttribute("base-package");
          if (!basePkg.isEmpty()) componentScans.add(basePkg);
        }
      }

      // Also check for component-scan without namespace prefix (some configs use default ns)
      var scanNodes2 = doc.getElementsByTagName("component-scan");
      for (int i = 0; i < scanNodes2.getLength(); i++) {
        var node = scanNodes2.item(i);
        if (node instanceof org.w3c.dom.Element el) {
          String basePkg = el.getAttribute("base-package");
          if (!basePkg.isEmpty() && !componentScans.contains(basePkg)) {
            componentScans.add(basePkg);
          }
        }
      }

      // Extract imports
      var importNodes = doc.getElementsByTagName("import");
      for (int i = 0; i < importNodes.getLength(); i++) {
        var node = importNodes.item(i);
        if (node instanceof org.w3c.dom.Element el) {
          String resource = el.getAttribute("resource");
          if (!resource.isEmpty()) imports.add(resource);
        }
      }

      return new SpringConfig(filePath, beans, componentScans, imports);

    } catch (Exception e) {
      log.debug("Spring XML parse error in {}: {}", filePath, e.getMessage());
      return new SpringConfig(filePath, List.of(), List.of(), List.of());
    }
  }

  /** Recursively collect all ref values from a bean element. */
  private void collectRefs(org.w3c.dom.Element beanEl, List<String> deps) {
    var children = beanEl.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      var child = children.item(i);
      if (!(child instanceof org.w3c.dom.Element el)) continue;

      // Shorthand: <constructor-arg ref="..."/> or <property ref="..."/>
      if (el.getTagName().equals("constructor-arg") || el.getTagName().equals("property")) {
        String refAttr = el.getAttribute("ref");
        if (!refAttr.isEmpty()) deps.add(refAttr);
        collectRefs(el, deps);
      }

      // Nested form: <ref bean="..."/> as a child element
      if (el.getTagName().equals("ref")) {
        String refBean = el.getAttribute("bean");
        if (!refBean.isEmpty()) deps.add(refBean);
      }

      // Handle <list>, <map>, <set> containers inside property/constructor-arg
      if (el.getTagName().equals("list") || el.getTagName().equals("map") || el.getTagName().equals("set")) {
        collectRefs(el, deps);
      }
    }
  }

}
