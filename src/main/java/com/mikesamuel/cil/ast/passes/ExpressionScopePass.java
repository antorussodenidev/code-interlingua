package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.BaseStatementNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.PackageDeclarationNode;
import com.mikesamuel.cil.ast.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.StaticImportOnDemandDeclarationNode;
import com.mikesamuel.cil.ast.TypeNameNode;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
    .BlockExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
    .DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.traits.CallableDeclaration;
import com.mikesamuel.cil.ast.traits.ExpressionNameDeclaration;
import com.mikesamuel.cil.ast.traits.ExpressionNameScope;
import com.mikesamuel.cil.ast.traits.LimitedScopeElement;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;

/**
 * Associates expression name resolvers and position markers with scopes and
 * block statements so that a later pass can resolve expression names.
 */
public final class ExpressionScopePass extends AbstractPass<Void> {
  final TypeInfoResolver typeInfoResolver;
  final TypeNameResolver qualifiedNameResolver;

  ExpressionScopePass(TypeInfoResolver typeInfoResolver, Logger logger) {
    super(logger);
    this.typeInfoResolver = typeInfoResolver;
    this.qualifiedNameResolver =
        TypeNameResolver.Resolvers.canonicalizer(typeInfoResolver);
  }

  private DeclarationPositionMarker walk(
      BaseNode node, ExpressionNameResolver r,
      DeclarationPositionMarker m,
      Name outer) {
    ExpressionNameResolver childResolver = r;
    DeclarationPositionMarker currentMarker = m;
    Name childOuter = outer;

    if (node instanceof TypeDeclaration) {
      TypeInfo ti = ((TypeDeclaration) node).getDeclaredTypeInfo();
      if (ti != null) {
        // Could be null for (new ...) that do not declare an
        // anonymous type.
        Preconditions.checkNotNull(ti, node);
        childResolver = ExpressionNameResolver.Resolvers.forType(
            ti, typeInfoResolver);
        currentMarker = DeclarationPositionMarker.EARLIEST;
        childOuter = ((TypeDeclaration) node).getDeclaredTypeInfo().canonName;
      }
    }

    if (node instanceof CallableDeclaration) {
      CallableDeclaration cd = (CallableDeclaration) node;
      childOuter = outer.method(cd.getMethodName(), cd.getMethodDescriptor());
    }

    if (node instanceof ExpressionNameScope) {
      if (node instanceof CallableDeclaration
          || node instanceof BaseStatementNode) {
        Preconditions.checkState(childResolver == r);
        childResolver = new BlockExpressionNameResolver();
        currentMarker = DeclarationPositionMarker.EARLIEST;
      }
      ((ExpressionNameScope) node).setExpressionNameResolver(childResolver);
    }
    if (node instanceof ExpressionNameDeclaration
        && r instanceof BlockExpressionNameResolver) {
      ExpressionNameDeclaration decl = (ExpressionNameDeclaration) node;
      // TODO: disambiguate multiple uses of the same name in a block of code.
      Name declName = outer.child(
          decl.getDeclaredExpressionIdentifier(), Name.Type.LOCAL);
      decl.setDeclaredExpressionName(declName);
      currentMarker = ((BlockExpressionNameResolver) r).declare(declName);
    }

    for (BaseNode child : node.getChildren()) {
      currentMarker = walk(child, childResolver, currentMarker, childOuter);
    }

    if (node instanceof LimitedScopeElement) {
      ((LimitedScopeElement) node).setDeclarationPositionMarker(currentMarker);
    }

    return childResolver != r ? m : currentMarker;
  }

  @Override
  Void run(Iterable<? extends CompilationUnitNode> compilationUnits) {
    for (CompilationUnitNode cu : compilationUnits) {
      ExpressionNameResolver r = resolverFor(cu);
      cu.setExpressionNameResolver(r);
      walk(cu, r, DeclarationPositionMarker.LATEST, null);
    }
    return null;
  }

  private ExpressionNameResolver resolverFor(CompilationUnitNode cu) {
    PackageDeclarationNode pkgNode = cu.firstChildWithType(
        PackageDeclarationNode.class);
    Name packageName = Name.DEFAULT_PACKAGE;
    if (pkgNode != null) {
      for (IdentifierNode ident
          : pkgNode.finder(IdentifierNode.class)
            .exclude(NodeType.Annotation)
            .find()) {
        packageName = packageName.child(ident.getValue(), Name.Type.PACKAGE);
      }
    }

    ImmutableList.Builder<Name> explicit = ImmutableList.builder();
    for (SingleStaticImportDeclarationNode idecl
        : cu.finder(SingleStaticImportDeclarationNode.class)
          .exclude(NodeType.TypeDeclaration)
          .find()) {
      TypeNameNode typeName = idecl.firstChildWithType(TypeNameNode.class);
      if (typeName == null) {  // Maybe part of a template
        continue;
      }
      IdentifierNode fieldOrMethodName = idecl.firstChildWithType(
          IdentifierNode.class);
      if (fieldOrMethodName == null) {
        continue;
      }
      Optional<TypeInfo> tiOpt = lookupType(typeName);
      if (tiOpt.isPresent()) {
        String possibleFieldName = fieldOrMethodName.getValue();
        TypeInfo ti = tiOpt.get();
        boolean hasAccessibleStaticFieldNamed = ti.memberMatching(
            this.typeInfoResolver,
            new Predicate<MemberInfo>() {
              @Override
              public boolean apply(MemberInfo mi) {
                int mods = mi.modifiers;
                return !Modifier.isPrivate(mods)
                    // TODO: Do we need to check package access here.
                    && Modifier.isStatic(mods)
                    && mi instanceof FieldInfo
                    && mi.canonName.identifier.equals(possibleFieldName);
              }

            })
            .isPresent();
        if (hasAccessibleStaticFieldNamed) {
          explicit.add(ti.canonName.child(
              possibleFieldName, Name.Type.FIELD));
        }
      } else {
        error(typeName, "Unknown type " + typeName.getTextContent("."));
      }
    }

    ImmutableList.Builder<TypeInfo> wildcards = ImmutableList.builder();
    for (StaticImportOnDemandDeclarationNode idecl
        : cu.finder(StaticImportOnDemandDeclarationNode.class)
          .exclude(NodeType.TypeDeclaration)
          .find()) {
      TypeNameNode typeName = idecl.firstChildWithType(TypeNameNode.class);
      if (typeName == null) {  // Maybe part of a template
        continue;
      }
      Optional<TypeInfo> tiOpt = lookupType(typeName);
      if (tiOpt.isPresent()) {
        wildcards.add(tiOpt.get());
      } else {
        error(
            typeName,
            "Cannot resolve static import of " + typeName.getTextContent("."));
      }
    }
    return ExpressionNameResolver.Resolvers.forImports(
        explicit.build(), wildcards.build(), typeInfoResolver,
        packageName, logger);
  }

  private static Name ambiguousNameFor(TypeNameNode tn) {
    Name ambig = null;
    for (IdentifierNode ident : tn.finder(IdentifierNode.class)
        .exclude(
            NodeType.Annotation,
            NodeType.TypeArgumentsOrDiamond, NodeType.TypeArguments)
        .find()) {
      if (ambig == null) {
        ambig = Name.root(ident.getValue(), Name.Type.AMBIGUOUS);
      } else {
        ambig = ambig.child(ident.getValue(), Name.Type.AMBIGUOUS);
      }
    }
    return ambig;
  }

  private Optional<TypeInfo> lookupType(TypeNameNode typeName) {
    ImmutableList<Name> names = qualifiedNameResolver.lookupTypeName(
            ambiguousNameFor(typeName));
    switch (names.size()) {
      case 0:
        error(typeName, "Cannot resolve name " + typeName.getTextContent("."));
        return Optional.absent();
      case 1:
        return typeInfoResolver.resolve(names.get(0));
      default:
        error(typeName, "Ambiguous name " + typeName.getTextContent(".")
              + ":" + names);
        return Optional.absent();
    }
  }

}
