package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.AssignmentNode;
import com.mikesamuel.cil.ast.j8.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.j8.BlockNode;
import com.mikesamuel.cil.ast.j8.BlockStatementNode;
import com.mikesamuel.cil.ast.j8.BlockStatementsNode;
import com.mikesamuel.cil.ast.j8.ClassMemberDeclarationNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionStatementNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.InstanceInitializerNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameScope;
import com.mikesamuel.cil.ast.j8.J8MemberDeclaration;
import com.mikesamuel.cil.ast.j8.LeftHandSideNode;
import com.mikesamuel.cil.ast.j8.LocalVariableDeclarationNode;
import com.mikesamuel.cil.ast.j8.LocalVariableDeclarationStatementNode;
import com.mikesamuel.cil.ast.j8.MarkerAnnotationNode;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.j8.StatementNode;
import com.mikesamuel.cil.ast.j8.StaticInitializerNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorListNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorNode;
import com.mikesamuel.cil.ast.j8.VariableInitializerNode;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.Synthetic;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass.Parent;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.util.LogUtils;

/**
 * Utilities for collecting single-write temporary variables declared
 * by other passes and making sure there are declarations in appropriate
 * scopes.
 * <p>
 * The declarations are not added when declared, since there are some
 * cases that complicate this.
 * </p>
 * {@code
 * class C {
 *   int i = complex(expression);
 * }
 * }
 * <p>
 * If the complex expression requires temporaries, then we don't want to
 * introduce fields in {@code class C} so we move the initialization into
 * a class initializer thus:
 * </p>
 * {@code
 * class C {
 *   int i;
 *   { i = complex(expression); }
 * }
 * }
 * </p>
 *
 * <h2>Caveats</h2>
 * <p>
 * We assume that clients reliably initialize temporaries before reading.
 * It's possible that some ASTs might reenter an expression without leaving
 * the declaring scope.  For example, since the loop body below is not a block,
 * the nearest containing scope to {@code tmp} is the outer block, so the
 * expression may be reinitialized.
 * </p>
 * {@code
 * {
 *   for (int i = 0; i < 10; ++i) f(tmp = ++x, i, tmp);
 * }}
 * <p>TODO: Temporaries in explicit constructor invocations should not be
 * declared in the block statements in the constructor body.  We need to
 * find a different place for them.  Delegate to a private constructor that
 * takes those arguments.
 */
public final class Temporaries {

  /**
   * Information about a temporary that is used in the AST but may not have
   * yet been declared.
   */
  public static class Temporary {
    /** The field type. */
    public final TypeSpecification type;
    /** A non-colliding field name as generated by {@link NameAllocator}. */
    public final String allocatedName;
    /**
     * A location close to where the temporary will be used.
     * We walk ancestor-ward from here to find a scope to add the temporary,
     * so it does not need to refer to the exact use, but should be in an
     * expression or statement in the same scope, and should not be removed
     * from the node before the call to
     */
    public final @Nullable SList<Parent> useSite;

    /** */
    public Temporary(
        TypeSpecification type, String allocatedName, SList<Parent> useSite) {
      this.type = Preconditions.checkNotNull(type);
      this.allocatedName = Preconditions.checkNotNull(allocatedName);
      this.useSite = Preconditions.checkNotNull(useSite);
    }
  }

  private final Logger logger;
  private final TypePool typePool;
  private final TypeNodeFactory factory;

  /** */
  public Temporaries(Logger logger, TypePool typePool) {
    this.logger = logger;
    this.typePool = typePool;
    this.factory = new TypeNodeFactory(logger, typePool);
  }

  /**
   * Adds declarations to the given roots for the given temporaries.
   * <p>
   * After this happens, common passes should be rerun since otherwise, the
   * AST metadata is incomplete.
   */
  public void declareTemporaries(
      Iterable<? extends Temporary> temporaries) {
    declareTemporaries(
        ImmutableList.copyOf(temporaries));
  }

  private void declareTemporaries(
      ImmutableList<Temporary> temporaries) {
    Map<SList<Parent>, List<Temporary>> groupedByScope = new LinkedHashMap<>();
    groupTemporariesInto(temporaries, groupedByScope);
    for (Map.Entry<SList<Parent>, List<Temporary>> e
        : groupedByScope.entrySet()) {
      SList<Parent> scope = e.getKey();
      List<Temporary> tmps = e.getValue();
      Collections.sort(
          tmps,
          new Comparator<Temporary>() {
            @Override
            public int compare(Temporary a, Temporary b) {
              return a.allocatedName.compareTo(b.allocatedName);
            }
          });
      if (!declareTemporariesIn(ImmutableList.copyOf(tmps), scope)) {
        List<String> names = new ArrayList<>();
        for (Temporary t : tmps) {
          names.add(t.allocatedName);
        }
        LogUtils.log(
            logger, Level.SEVERE, scope.x.parent,
            "Failed to declare temporaries " + names, null);
      }
    }
  }

  private void groupTemporariesInto(
      ImmutableList<Temporary> temporaries,
      Map<SList<Parent>, List<Temporary>> groupedByScope) {
    for (Temporary t : temporaries) {
      SList<Parent> scope = t.useSite;
      while (true) {
        J8BaseNode anc = scope.x.parent;
        if (anc instanceof J8ExpressionNameScope
            // If we reach a member declaration, its probably
            // a Field or Constant Declaration that we need to yank
            // the initializers from.
            || anc instanceof J8MemberDeclaration) {
          List<Temporary> sameScope = groupedByScope.get(scope);
          if (sameScope == null) {
            groupedByScope.put(scope, sameScope = new ArrayList<>());
          }
          sameScope.add(t);
        } else if (scope.prev != null) {
          scope = scope.prev;
          continue;
        } else {
          LogUtils.log(
              logger, Level.SEVERE, t.useSite.x.parent,
              "Cannot find a scope in which to declare temporary "
              + t.type + " " + t.allocatedName, null);
        }
        break;
      }
    }
  }

  private boolean declareTemporariesIn(
      ImmutableList<Temporary> temporaries, SList<Parent> scope) {
    J8BaseInnerNode scopeNode = scope.x.parent;
    J8BaseNode blockStatementsContainer;
    if (!(scopeNode instanceof J8ExpressionNameScope)) {
      Preconditions.checkArgument(scopeNode instanceof J8MemberDeclaration);
      J8MemberDeclaration memberDecl = (J8MemberDeclaration) scopeNode;
      ImmutableList<MemberInfo> mis = memberDecl.getMemberInfo();
      if (mis == null || mis.isEmpty()) { return false; }
      MemberInfo mi0 = mis.get(0);
      if (!(mi0 instanceof FieldInfo)) { return false; }
      boolean isStatic = Modifier.isStatic(mi0.modifiers);
      TypeSpecification declType = ((FieldInfo) mi0).getValueType();
      VariableDeclaratorListNode decls = scopeNode.firstChildWithType(
          VariableDeclaratorListNode.class);
      SList<Parent> pscope = scope.prev;
      SList<Parent> gpscope = pscope != null ? pscope.prev : null;
      if (decls == null || pscope == null || gpscope == null) {
        return false;
      }
      BlockStatementsNode stmts = BlockStatementsNode.Variant
          .BlockStatementBlockStatementBlockTypeScope.buildNode();
      blockStatementsContainer = BlockNode.Variant.LcBlockStatementsRc
          .buildNode(stmts);
      J8BaseInnerNode initializer =
          isStatic
          ? StaticInitializerNode.Variant.StaticBlock.buildNode()
          : InstanceInitializerNode.Variant.Block.buildNode();
      initializer.add(blockStatementsContainer);
      J8BaseNode newMemberDecl;
      switch (gpscope.x.parent.getNodeType()) {
        case ClassBody:
        case EnumBody:
          newMemberDecl =
              (isStatic
               ? ClassMemberDeclarationNode.Variant.StaticInitializer
               : ClassMemberDeclarationNode.Variant.InstanceInitializer)
              .buildNode(initializer);
          break;
        // No way to declare static initializers in InterfaceBody
        // but we shouldn't be inserting temporaries into
        // ConstantDeclarations.
        default:
          return false;
      }
      int indexInParent = gpscope.x.parent.getChildren().indexOf(
          pscope.x.parent);
      gpscope.x.parent.add(indexInParent + 1, newMemberDecl);

      for (int i = decls.getNChildren(); --i >= 0;) {
        J8BaseNode c = decls.getChild(i);
        if (!(c instanceof VariableDeclaratorNode)) { continue; }
        VariableDeclaratorNode decl = (VariableDeclaratorNode) c;
        VariableDeclaratorIdNode declId = decl.firstChildWithType(
            VariableDeclaratorIdNode.class);
        IdentifierNode name = declId != null
            ? declId.firstChildWithType(IdentifierNode.class)
            : null;
        int initIndex = decl.finder(VariableInitializerNode.class).indexOf();
        if (name == null || initIndex < 0) { continue; }
        VariableInitializerNode varInitializer =
            (VariableInitializerNode) decl.getChild(initIndex);
        ExpressionNode initExpr = toExpression(declType, varInitializer);
        if (initExpr == null) { continue; }
        decl.remove(initIndex);

        stmts.add(BlockStatementNode.Variant.Statement.buildNode(
            StatementNode.Variant.ExpressionStatement.buildNode(
                ExpressionStatementNode.Variant.StatementExpressionSem
                .buildNode(
                    StatementExpressionNode.Variant.Assignment.buildNode(
                        AssignmentNode.Variant
                        .LeftHandSideAssignmentOperatorExpression.buildNode(
                            LeftHandSideNode.Variant.FreeField.buildNode(
                                ExpressionAtomNode.Variant.FreeField
                                .buildNode(
                                    FieldNameNode.Variant.Identifier
                                    .buildNode(name.deepClone()))),
                            AssignmentOperatorNode.Variant.Eq.buildNode(),
                            initExpr))))));
      }
    } else {
      // TODO: if we wait until the block statements node, then any
      // temporaries in ExplicitConstructorInvocations won't find
      // the declaration.
      blockStatementsContainer = scopeNode;
    }
    BlockStatementsNode stmts = blockStatementsContainer.firstChildWithType(
        BlockStatementsNode.class);
    if (stmts == null) {
      stmts = BlockStatementsNode.Variant
          .BlockStatementBlockStatementBlockTypeScope.buildNode();
    } else if (stmts.getVariant()
               == BlockStatementsNode.Variant.BlockTypeScope) {
      stmts.setVariant(BlockStatementsNode.Variant
          .BlockStatementBlockStatementBlockTypeScope);
    }
    int insertionPt = 0;
    for (Temporary t : temporaries) {
      StaticType st = typePool.type(t.type, null, logger);
      LocalVariableDeclarationNode localDecl =
          LocalVariableDeclarationNode.Variant.Declaration.buildNode(
              factory.toUnannTypeNode(st),
              VariableDeclaratorListNode.Variant
              .VariableDeclaratorComVariableDeclarator.buildNode(
                  VariableDeclaratorNode.Variant
                  .VariableDeclaratorIdEqVariableInitializer.buildNode(
                      VariableDeclaratorIdNode.Variant.IdentifierDims
                      .buildNode(
                          IdentifierNode.Variant.Builtin.buildNode(
                              t.allocatedName)))));
      ModifierNode mod = makeSyntheticAnnotationModifier();
      if (mod != null) {
        localDecl.add(0, mod);
      }
      stmts.add(
          insertionPt,
          BlockStatementNode.Variant.LocalVariableDeclarationStatement
          .buildNode(
              LocalVariableDeclarationStatementNode.Variant
              .LocalVariableDeclarationSem.buildNode(localDecl)));
      ++insertionPt;
    }
    return true;
  }

  @SuppressWarnings({ "static-method", "unused" })
  private ExpressionNode toExpression(
      TypeSpecification declType, VariableInitializerNode vi) {
    switch (vi.getVariant()) {
      case ArrayInitializer:
        // TODO: use declType to convert to an array constructor expression
        throw new Error("TODO");
      case Expression:
        return vi.firstChildWithType(ExpressionNode.class);
    }
    throw new AssertionError(vi.getVariant());
  }

  private ModifierNode makeSyntheticAnnotationModifier() {
    Optional<TypeInfo> synTiOpt = typePool.r.resolve(
        Temporaries.SYNTHETIC_ANNOTATION_NAME);
    if (synTiOpt.isPresent()) {
      return ModifierNode.Variant.Annotation.buildNode(
          AnnotationNode.Variant.MarkerAnnotation.buildNode(
              MarkerAnnotationNode.Variant.AtTypeName.buildNode(
                  factory.toTypeNameNode(
                      synTiOpt.get()))));
    }
    return null;
  }

  /** THe name for {@link Synthetic} */
  public static final Name SYNTHETIC_ANNOTATION_NAME;
  static {
    Name nm = Name.DEFAULT_PACKAGE;
    String[] parts = Synthetic.class.getName().split("[.]");
    for (int i = 0, n = parts.length; i < n; ++i) {
      nm = nm.child(
          parts[i], i + 1 == n ? Name.Type.CLASS : Name.Type.PACKAGE);
    }
    SYNTHETIC_ANNOTATION_NAME = nm;
  }

}
