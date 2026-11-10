package com.jolt.parse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpringXmlParserTest {

  @TempDir
  Path tempDir;

  private SpringXmlParser parser;

  @BeforeEach
  void setUp() {
    parser = new SpringXmlParser();
  }

  private Path writeXml(String name, String content) throws IOException {
    Path p = tempDir.resolve(name);
    Files.writeString(p, content);
    return p;
  }

  @Test
  void parse_emptyBeans_returnsEmptyList() throws IOException {
    Path p = writeXml("empty.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans">
        </beans>""");

    var config = parser.parse(p);
    assertEquals(p, config.filePath());
    assertTrue(config.beans().isEmpty());
    assertTrue(config.componentScanPaths().isEmpty());
    assertTrue(config.importedFiles().isEmpty());
  }

  @Test
  void parse_singleBean_extractsIdAndClass() throws IOException {
    Path p = writeXml("single.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans">
          <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource"/>
        </beans>""");

    var config = parser.parse(p);
    assertEquals(1, config.beans().size());

    var bean = config.beans().get(0);
    assertEquals("dataSource", bean.id());
    assertEquals("org.apache.commons.dbcp.BasicDataSource", bean.className());
    assertEquals("singleton", bean.scope()); // default
  }

  @Test
  void parse_beanWithScope() throws IOException {
    Path p = writeXml("scope.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans">
          <bean id="requestBean" class="com.example.RequestBean" scope="request"/>
        </beans>""");

    var config = parser.parse(p);
    assertEquals("request", config.beans().get(0).scope());
  }

  @Test
  void parse_beanWithConstructorArgRef() throws IOException {
    Path p = writeXml("deps.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans">
          <bean id="service" class="com.example.MyService">
            <constructor-arg ref="repository"/>
            <constructor-arg ref="cache"/>
          </bean>
          <bean id="repository" class="com.example.MyRepository"/>
          <bean id="cache" class="com.example.MyCache"/>
        </beans>""");

    var config = parser.parse(p);

    var serviceBean = config.beans().stream()
        .filter(b -> b.id().equals("service"))
        .findFirst().orElseThrow();

    assertEquals(List.of("repository", "cache"), serviceBean.dependsOn());
  }

  @Test
  void parse_beanWithPropertyRef() throws IOException {
    Path p = writeXml("propref.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans">
          <bean id="controller" class="com.example.Controller">
            <property name="service" ref="myService"/>
          </bean>
          <bean id="myService" class="com.example.Service"/>
        </beans>""");

    var config = parser.parse(p);

    var controller = config.beans().stream()
        .filter(b -> b.id().equals("controller"))
        .findFirst().orElseThrow();

    assertEquals(List.of("myService"), controller.dependsOn());
  }

  @Test
  void parse_componentScan_extractsPaths() throws IOException {
    Path p = writeXml("scan.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans"
               xmlns:context="http://www.springframework.org/schema/context">
          <context:component-scan base-package="com.example.web"/>
          <context:component-scan base-package="com.example.service"/>
        </beans>""");

    var config = parser.parse(p);
    assertEquals(List.of("com.example.web", "com.example.service"), config.componentScanPaths());
  }

  @Test
  void parse_import_extractsResources() throws IOException {
    Path p = writeXml("import.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans">
          <import resource="classpath:db-config.xml"/>
          <import resource="classpath:security.xml"/>
        </beans>""");

    var config = parser.parse(p);
    assertEquals(List.of("classpath:db-config.xml", "classpath:security.xml"), config.importedFiles());
  }

  @Test
  void parse_multipleBeans_allDetected() throws IOException {
    Path p = writeXml("multi.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans">
          <bean id="a" class="com.example.A"/>
          <bean id="b" class="com.example.B"/>
          <bean id="c" class="com.example.C"/>
        </beans>""");

    var config = parser.parse(p);
    assertEquals(3, config.beans().size());

    List<String> ids = config.beans().stream().map(SpringXmlParser.BeanDef::id).toList();
    assertTrue(ids.contains("a"));
    assertTrue(ids.contains("b"));
    assertTrue(ids.contains("c"));
  }

  @Test
  void parse_nestedListWithRefs() throws IOException {
    Path p = writeXml("nested.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans">
          <bean id="factory" class="com.example.Factory">
            <property name="handlers">
              <list>
                <ref bean="handler1"/>
                <ref bean="handler2"/>
              </list>
            </property>
          </bean>
          <bean id="handler1" class="com.example.Handler1"/>
          <bean id="handler2" class="com.example.Handler2"/>
        </beans>""");

    var config = parser.parse(p);

    var factory = config.beans().stream()
        .filter(b -> b.id().equals("factory"))
        .findFirst().orElseThrow();

    assertEquals(List.of("handler1", "handler2"), factory.dependsOn());
  }

  @Test
  void parse_nonExistentFile_returnsEmpty() {
    Path p = tempDir.resolve("missing.xml");
    var config = parser.parse(p);
    assertTrue(config.beans().isEmpty());
  }

  @Test
  void parse_invalidXml_returnsEmpty() throws IOException {
    Path p = writeXml("bad.xml", "this is not xml at all <<<>>>");
    var config = parser.parse(p);
    assertTrue(config.beans().isEmpty());
  }

}
