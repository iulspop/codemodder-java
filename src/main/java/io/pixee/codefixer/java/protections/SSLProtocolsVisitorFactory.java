package io.pixee.codefixer.java.protections;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import io.pixee.codefixer.java.FileWeavingContext;
import io.pixee.codefixer.java.MethodCallTransformingModifierVisitor;
import io.pixee.codefixer.java.NodePredicateFactory;
import io.pixee.codefixer.java.Transformer;
import io.pixee.codefixer.java.VisitorFactory;
import io.pixee.codefixer.java.Weave;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static io.pixee.codefixer.java.protections.SSLProtocols.hasUnsafeArrayArgument;
import static io.pixee.codefixer.java.protections.SSLProtocols.hasUnsafeArrayArgumentVariable;

/**
 * This visitor prevents the use of SSL protocols that are considered unsafe by modern standards.
 */
abstract class SSLProtocolsVisitorFactory implements VisitorFactory {

  private final String methodName;
  private final String typeName;
  private final String fullyQualifiedTypeName;

  SSLProtocolsVisitorFactory(final String methodName, final String typeName, final String fullyQualifiedTypeName) {
    this.methodName = Objects.requireNonNull(methodName);
    this.typeName = Objects.requireNonNull(typeName);
    this.fullyQualifiedTypeName = Objects.requireNonNull(fullyQualifiedTypeName);
  }

  @Override
  public ModifierVisitor<FileWeavingContext> createJavaCodeVisitorFor(
      final File file, final CompilationUnit cu) {

    Set<Predicate<MethodCallExpr>> predicates = Set.of(
            NodePredicateFactory.withMethodName(methodName),
            NodePredicateFactory.withArgumentCount(1),
            NodePredicateFactory.withScopeType(cu, typeName).or(NodePredicateFactory.withScopeType(cu, fullyQualifiedTypeName)),
            (NodePredicateFactory.withArgumentNodeType(0, ArrayCreationExpr.class).and(hasUnsafeArrayArgument)).or
                    (NodePredicateFactory.withArgumentNodeType(0, NameExpr.class).and(hasUnsafeArrayArgumentVariable))

    );

    Transformer<MethodCallExpr> transformer = new Transformer<>() {
      @Override
      public TransformationResult<MethodCallExpr> transform(final MethodCallExpr methodCallExpr, final FileWeavingContext context) {
        final ArrayCreationExpr safeArgument =
                new ArrayCreationExpr(new ClassOrInterfaceType("String"));
        safeArgument.setLevels(NodeList.nodeList(new ArrayCreationLevel()));
        safeArgument.setInitializer(
                new ArrayInitializerExpr(NodeList.nodeList(new StringLiteralExpr(SSLProtocols.safeTlsVersion))));
        methodCallExpr.setArguments(NodeList.nodeList(safeArgument));
        Weave weave =
                Weave.from(methodCallExpr.getRange().get().begin.line, ruleId());
        return new TransformationResult<>(Optional.empty(), weave);
      }
    };

    return new MethodCallTransformingModifierVisitor(cu, predicates, transformer);
  }

}
