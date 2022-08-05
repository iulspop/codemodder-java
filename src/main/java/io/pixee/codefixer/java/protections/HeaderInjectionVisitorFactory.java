package io.pixee.codefixer.java.protections;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import io.pixee.codefixer.java.DependencyGAV;
import io.pixee.codefixer.java.FileWeavingContext;
import io.pixee.codefixer.java.MethodCallPredicateFactory;
import io.pixee.codefixer.java.MethodCallTransformingModifierVisitor;
import io.pixee.codefixer.java.Transformer;
import io.pixee.codefixer.java.VisitorFactory;
import io.pixee.codefixer.java.Weave;
import io.pixee.security.Newlines;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class HeaderInjectionVisitorFactory implements VisitorFactory {

  @Override
  public ModifierVisitor<FileWeavingContext> createJavaCodeVisitorFor(
      final File javaFile, final CompilationUnit cu) {

    List<Predicate<MethodCallExpr>> predicates =
        List.of(
            MethodCallPredicateFactory.withName("setHeader"),
            MethodCallPredicateFactory.withArgumentCount(2),
            MethodCallPredicateFactory.withScopeType(cu, "javax.servlet.http.HttpServletResponse"),
            MethodCallPredicateFactory.withArgumentType(cu, 1, "java.lang.String"),
            MethodCallPredicateFactory.withArgumentNodeType(1, StringLiteralExpr.class).negate(),
            MethodCallPredicateFactory.withScreamingSnakeCaseVariableNameForArgument(1).negate());

    Transformer<MethodCallExpr, MethodCallExpr> transformer =
        new Transformer<>() {
          @Override
          public TransformationResult<MethodCallExpr> transform(
              final MethodCallExpr methodCallExpr, final FileWeavingContext context) {
            ASTs.addImportIfMissing(cu, Newlines.class);
            MethodCallExpr stripNewlinesCall = new MethodCallExpr(callbackClass, "stripAll");
            Expression argument = methodCallExpr.getArgument(1);
            stripNewlinesCall.setArguments(NodeList.nodeList(argument));
            methodCallExpr.setArguments(
                NodeList.nodeList(methodCallExpr.getArgument(0), stripNewlinesCall));
            Weave weave =
                Weave.from(
                    methodCallExpr.getRange().get().begin.line,
                    stripHeaderRuleId,
                    DependencyGAV.OPENPIXEE_JAVA_SECURITY_TOOLKIT);
            return new TransformationResult<>(Optional.empty(), weave);
          }
        };

    return new MethodCallTransformingModifierVisitor(cu, predicates, transformer);
  }

  @Override
  public String ruleId() {
    return stripHeaderRuleId;
  }

  private static final NameExpr callbackClass = new NameExpr(Newlines.class.getSimpleName());
  private static final String stripHeaderRuleId = "pixee:java/strip-http-header-newlines";
}
