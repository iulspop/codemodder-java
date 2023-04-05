package io.openpixee.java;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.codemodder.DefaultRuleSetting;
import io.codemodder.RuleContext;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VisitorAssemblerTest {

  @Test
  void it_filters_java_factories_based_on_configuration() {
    VisitorAssembler assembler = VisitorAssembler.createDefault();
    RuleContext everythingButJakarta =
        RuleContext.of(
            DefaultRuleSetting.ENABLED, List.of("pixee:java/validate-jakarta-forward-path"));
    RuleContext everything = RuleContext.of(DefaultRuleSetting.ENABLED, List.of());
    File repositoryRoot = new File(".");
    List<VisitorFactory> allFactories =
        assembler.assembleJavaCodeScanningVisitorFactories(repositoryRoot, everything, List.of());
    List<VisitorFactory> allButOne =
        assembler.assembleJavaCodeScanningVisitorFactories(
            repositoryRoot, everythingButJakarta, List.of());

    // just make sure we're getting a reasonable number of factories
    assertThat(allFactories.isEmpty(), is(false));

    // make sure that only disabling one allows it to
    assertThat(allFactories.size() - allButOne.size(), equalTo(1));
  }

  //
  @Test
  void it_filters_file_visitors_based_on_configuration() {
    VisitorAssembler assembler = VisitorAssembler.createDefault();
    RuleContext everythingButDependencyInjection =
        RuleContext.of(DefaultRuleSetting.ENABLED, List.of("pixee:java/mvn-dependency-injection"));
    File repositoryRoot = new File(".");
    RuleContext everything = RuleContext.of(DefaultRuleSetting.ENABLED, List.of());
    List<FileBasedVisitor> allVisitors =
        assembler.assembleFileVisitors(repositoryRoot, everything, Collections.emptyList());
    List<FileBasedVisitor> allButOne =
        assembler.assembleFileVisitors(
            repositoryRoot, everythingButDependencyInjection, Collections.emptyList());

    assertThat(allVisitors.size(), equalTo(1));
    assertThat(allButOne.size(), equalTo(0));
  }
}