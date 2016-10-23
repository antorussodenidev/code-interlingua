package com.mikesamuel.cil.ast;

import org.junit.Test;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public class PostExpressionNodeTest extends AbstractParSerTestCase {

  @Test
  public void test() {
    this.assertParsePasses(
        NodeType.PostExpression,
        "x++",
        push(PostExpressionNode.Variant.ExpressionNameIncrDecrOperator),
        push(ExpressionNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("x"),
        pop(),
        pop(),
        push(IncrDecrOperatorNode.Variant.PlsPls),
        token("++"),
        pop(),
        pop()
        );
  }
}