package io.codemodder.providers.sarif.semgrep;

import com.contrastsecurity.sarif.Region;
import com.contrastsecurity.sarif.Result;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import io.codemodder.*;
import java.util.List;
import java.util.Objects;

/** Provides base functionality for making JavaParser-based changes with Semgrep. */
public abstract class SemgrepJavaParserChanger<T extends Node> implements JavaParserChanger {

  protected final RuleSarif sarif;
  private final Class<? extends Node> nodeType;
  private final RegionExtractor regionExtractor;
  private final RegionNodeMatcher regionNodeMatcher;

  protected SemgrepJavaParserChanger(
      final RuleSarif semgrepSarif, final Class<? extends Node> nodeType) {
    this(
        semgrepSarif, nodeType, RegionExtractor.FROM_FIRST_LOCATION, RegionNodeMatcher.EXACT_MATCH);
  }

  protected SemgrepJavaParserChanger(
      final RuleSarif semgrepSarif,
      final Class<? extends Node> nodeType,
      final RegionExtractor regionExtractor) {
    this(semgrepSarif, nodeType, regionExtractor, RegionNodeMatcher.EXACT_MATCH);
  }

  protected SemgrepJavaParserChanger(
      final RuleSarif semgrepSarif,
      final Class<? extends Node> nodeType,
      final RegionNodeMatcher regionNodeMatcher) {
    this(semgrepSarif, nodeType, RegionExtractor.FROM_FIRST_LOCATION, regionNodeMatcher);
  }

  protected SemgrepJavaParserChanger(
      final RuleSarif semgrepSarif,
      final Class<? extends Node> nodeType,
      final RegionExtractor regionExtractor,
      final RegionNodeMatcher regionNodeMatcher) {
    this.sarif = Objects.requireNonNull(semgrepSarif);
    this.nodeType = Objects.requireNonNull(nodeType);
    this.regionExtractor = Objects.requireNonNull(regionExtractor);
    this.regionNodeMatcher = Objects.requireNonNull(regionNodeMatcher);
  }

  @Override
  public final void visit(final CodemodInvocationContext context, final CompilationUnit cu) {

    List<Result> results = sarif.getResultsByPath(context.path());
    List<? extends Node> allNodes = cu.findAll(nodeType);

    /*
     * We have an interesting scenario we have to handle whereby we could accidentally feed two results that should be
     * applied only once. Consider this real-world example where a SARIF says we have 1 problem on line 101, column 3:
     *
     * 100. public void foo() {
     * 101.   Runtime.getRuntime().exec(...);
     * 102. }
     *
     * There are actually 2 different JavaParser nodes that match the position reported in SARIF:
     * - getRuntime()
     * - exec(...)
     *
     * If we apply the change to both nodes, we'll end up with a broken AST. So we need to keep track of the nodes we've
     * already applied changes to, and skip them if we encounter them again. Deciding which of the nodes we want to act
     * on is unfortunately a job for the subclass -- they should just "return false" if the event didn't make sense, but
     * we should invest into a general solution if this doesn't scale.
     */
    for (Result result : results) {
      for (Node node : allNodes) {
        Region region = regionExtractor.from(result);
        if (!nodeType.isAssignableFrom(node.getClass())) {
          continue;
        }
        FileWeavingContext changeRecorder = context.changeRecorder();
        if (changeRecorder.isLineIncluded(region.getStartLine())) {
          if (node.getRange().isPresent()) {
            Range range = node.getRange().get();
            if (regionNodeMatcher.matches(region, range)) {
              boolean changeSuccessful = onSemgrepResultFound(context, cu, (T) node, result);
              if (changeSuccessful) {
                changeRecorder.addWeave(
                    Weave.from(region.getStartLine(), context.codemodId(), dependenciesRequired()));
              }
            }
          }
        }
      }
    }
  }

  /**
   * Creates a visitor for the given context and locations.
   *
   * @param context the context of this files transformation
   * @param cu the parsed model of the file being transformed
   * @param node the node to act on
   * @param result the given SARIF result to act on
   * @return true, if the change was made, false otherwise
   */
  public abstract boolean onSemgrepResultFound(
      CodemodInvocationContext context, CompilationUnit cu, T node, Result result);
}
