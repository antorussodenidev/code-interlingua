package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public final class PrimaryNodeTest extends AbstractParSerTestCase {

  @Test
  public final void testClassLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "Object.class",
        /**/push(PrimaryNode.Variant.PrimaryNoNewArray),
        /*..*/push(PrimaryNoNewArrayNode.Variant.ClassLiteral),
        /*....*/push(ClassLiteralNode.Variant.TypeNameLsRsDotClass),
        /*......*/push(TypeNameNode.Variant.Identifier),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("Object"),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token("."),
        /*......*/token("class"),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public final void testStringLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "\"foo\\nbar\\0\"",
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.StringLiteral),
        push(StringLiteralNode.Variant.Builtin),
        content("\"foo\\nbar\\0\""),
        pop(),
        pop(),
        pop(),
        pop()
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "\"foo"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "\"foo\\\""
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "\"\n\""
        );
  }

  @Test
  public final void testBooleanLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "false",
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.BooleanLiteral),
        push(BooleanLiteralNode.Variant.False),
        token("false"),
        pop(),
        pop(),
        pop(),
        pop()
        );
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "true",
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.BooleanLiteral),
        push(BooleanLiteralNode.Variant.True),
        token("true"),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public final void testCharacterLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "'x'",
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.CharacterLiteral),
        push(CharacterLiteralNode.Variant.Builtin),
        content("'x'"),
        pop(),
        pop(),
        pop(),
        pop()
        );
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "'\\r'",
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.CharacterLiteral),
        push(CharacterLiteralNode.Variant.Builtin),
        content("'\\r'"),
        pop(),
        pop(),
        pop(),
        pop()
        );
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "'\"'",
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.CharacterLiteral),
        push(CharacterLiteralNode.Variant.Builtin),
        content("'\"'"),
        pop(),
        pop(),
        pop(),
        pop()
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "''"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "'''"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "'\\'"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "'xx'"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "'x"
        );
  }

  @Test
  public final void testNullLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "null",
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.NullLiteral),
        push(NullLiteralNode.Variant.Null),
        token("null"),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public final void testNumericLiteralTest() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "1",
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.IntegerLiteral),
        push(IntegerLiteralNode.Variant.Builtin),
        content("1"),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }


  @Test
  public void testSimpleFieldAccess() {
    // TODO: This fails because the JLS chapter 19 is wrong.
    // http://stackoverflow.com/q/40184060/20394
    if (false)
    this.assertParsePasses(
        PTree.complete(NodeType.UnaryExpression),
        "o.f",
        push(UnaryExpressionNode.Variant.PreExpression),
        // TODO
        pop());
  }
}