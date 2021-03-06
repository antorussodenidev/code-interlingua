package com.mikesamuel.cil.format.java;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.TokenStrings;
import com.mikesamuel.cil.ast.j8.Tokens;
import com.mikesamuel.cil.ast.jmin.JminNodeType;
import com.mikesamuel.cil.format.TokenBreak;
import com.mikesamuel.cil.format.TokenBreaker;
import com.mikesamuel.cil.format.java.Java8TokenClassifier.Classification;
import com.mikesamuel.cil.parser.SList;

/**
 * Determines which tokens should have spaces between them based on the need to
 * avoid lexical ambiguity and a desire to format token streams the way a human
 * might.
 */
final class Java8TokenBreaker
implements TokenBreaker<SList<NodeVariant<?, ?>>> {

  @Override
  public TokenBreak breakBetween(
      String left,  @Nullable SList<NodeVariant<?, ?>> leftStack,
      String right, @Nullable SList<NodeVariant<?, ?>> rightStack) {
    Classification lc = Java8TokenClassifier.classify(left);
    Classification rc = Java8TokenClassifier.classify(right);

    // Handle all the MUST cases first.
    if ((lc == Classification.IDENTIFIER_CHARS
        || lc == Classification.NUMBER_LITERAL)
        && (rc == Classification.IDENTIFIER_CHARS
            || rc == Classification.NUMBER_LITERAL)) {
      return TokenBreak.MUST;
    }

    if (lc == Classification.NUMBER_LITERAL
        && rc == Classification.PUNCTUATION && right.startsWith(".")) {
      return TokenBreak.MUST;
    }
    if (lc == Classification.PUNCTUATION
        && rc == Classification.NUMBER_LITERAL
        && left.endsWith(".")) {
      return TokenBreak.MUST;
    }

    if (lc == Classification.PUNCTUATION && rc == Classification.PUNCTUATION) {
      // Test that the two tokens don't merge into a larger one.
      // For example:  (x - -1) should not be rendered (x--1) since (x--) has
      // an independent meaning.
      String extended = left + right.charAt(0);
      if (TokenStrings.PUNCTUATION.contains(extended)
          || !Tokens.punctuationSuffixes(extended).isEmpty()) {
        return TokenBreak.MUST;
      }
      if (extended.contains("//") || extended.contains("/*")) {
        return TokenBreak.MUST;
      }
    }

    if (lc == Classification.PUNCTUATION
        && "/".equals(left)
        && (rc == Classification.BLOCK_COMMENT
            || rc == Classification.LINE_COMMENT)) {
      // Avoid "/" and "/*" from becoming a line comment.
      return TokenBreak.MUST;
    }

    if (lc == Classification.PUNCTUATION) {
      switch (left) {
        case ".": return TokenBreak.SHOULD_NOT;
        case "[":
        case "(": return TokenBreak.SHOULD_NOT;
        case ")":
          if (";".equals(right) || ")".equals(right) || ".".equals(right)
              || "]".equals(right) || ",".equals(right)) {
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case "{":
          if ("}".equals(right)) { return TokenBreak.SHOULD_NOT; }
          return TokenBreak.SHOULD;
        case ",":
        case ";":
          if (")".equals(right) || ";".equals(right)) {
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case "!": case "~":
          return TokenBreak.SHOULD_NOT;
        case "-": case "+":
          if (inPrefixOperatorContext(leftStack)) {
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case "--": case "++":
          if (inPrefixOperatorContext(leftStack)) {
            return TokenBreak.SHOULD_NOT;
          }
          break;
        case "]":
          if (rc == Classification.IDENTIFIER_CHARS || "{".equals(right)) {
            return TokenBreak.SHOULD;
          }
          break;
        case "}":
          if (")".equals(right) || ";".equals(right) || ",".equals(right)) {
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case "?":
          if (">".equals(right)) {
            return TokenBreak.SHOULD_NOT;
          }
          //$FALL-THROUGH$
        case ":":
        case "...":
          return TokenBreak.SHOULD;
        case "*":
          if (";".equals(right)) {  // Special use in wildcard imports.
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case "<":
          if (leftStack != null) {
            NodeType<?, ?> nt = leftStack.x.getNodeType();
            if (nt == J8NodeType.TypeParameters
                || nt == JminNodeType.TypeParameters
                || nt == J8NodeType.TypeArguments
                || nt == JminNodeType.TypeArguments
                || nt == J8NodeType.Diamond) {
              return TokenBreak.SHOULD_NOT;
            }
          }
          return TokenBreak.SHOULD;
        default:
          if (isBinaryOperator(left)) {
            return TokenBreak.SHOULD;
          }
      }
    }

    if (lc == Classification.IDENTIFIER_CHARS) {
      if (rc == Classification.STRING_LITERAL) {
        return TokenBreak.SHOULD;
      }
    }

    if (rc == Classification.PUNCTUATION) {
      switch (right) {
        case ".": return TokenBreak.SHOULD_NOT;
        case "(":
          if (isKeyword(left)) { return TokenBreak.SHOULD; }
          return TokenBreak.SHOULD_NOT;
        case ")": case "]":
          return TokenBreak.SHOULD_NOT;
        case "}":
          if (",".equals(left)) { return TokenBreak.SHOULD_NOT; }
          return TokenBreak.SHOULD;
        case ",":
        case ";":
          return TokenBreak.SHOULD_NOT;
        case ":": case "?":
          return TokenBreak.SHOULD;
        case "--": case "++":
          if (inPostfixOperatorContext(rightStack)) {
            return TokenBreak.SHOULD_NOT;
          }
          break;
        case "{":
          if (!isKeyword(left)) {
            return TokenBreak.SHOULD;
          }
          break;
        case "<":
          if (isKeyword(left)) {
            return TokenBreak.SHOULD;
          }
          //$FALL-THROUGH$
        case ">":
          if (rightStack != null) {
            NodeType<?, ?> nt = rightStack.x.getNodeType();
            if (nt == J8NodeType.TypeParameters
                || nt == JminNodeType.TypeParameters
                || nt == J8NodeType.TypeArguments
                || nt == JminNodeType.TypeArguments) {
              return TokenBreak.SHOULD_NOT;
            }
          }
          return TokenBreak.SHOULD;
        default:
          if (isBinaryOperator(right)) {
            return TokenBreak.SHOULD;
          }
      }
    }

    return TokenBreak.SHOULD_NOT;
  }

  private static boolean inPostfixOperatorContext(
      SList<NodeVariant<?, ?>> stack) {
    if (stack == null || stack.prev == null) { return false; }
    NodeType<?, ?> nt = stack.prev.x.getNodeType();
    return nt == J8NodeType.PostExpression
        || nt == JminNodeType.PostExpression;
  }

  private static boolean inPrefixOperatorContext(
      SList<NodeVariant<?, ?>> stack) {
    if (stack == null) { return false; }
    NodeType<?, ?> nt = stack.x.getNodeType();
    if (nt == J8NodeType.PrefixOperator || nt == JminNodeType.PrefixOperator) {
      return true;
    }
    if (stack.prev != null) {
      NodeType<?, ?> pnt = stack.prev.x.getNodeType();
      return pnt == J8NodeType.PreExpression
          || pnt == JminNodeType.PreExpression;
    }
    return false;
  }

  @Override
  public TokenBreak lineBetween(
      String left,  @Nullable SList<NodeVariant<?, ?>> leftStack,
      String right, @Nullable SList<NodeVariant<?, ?>> rightStack) {

    // Handle all the MUST cases first.
    Classification lc = Java8TokenClassifier.classify(left);
    if (lc == Classification.LINE_COMMENT) {
      return TokenBreak.MUST;
    }

    if (lc == Classification.BLOCK_COMMENT) {
      if (hasSpace(left)) { return TokenBreak.SHOULD; }
    }
    Classification rc = Java8TokenClassifier.classify(right);
    if (rc == Classification.BLOCK_COMMENT) {
      if (hasSpace(right)) { return TokenBreak.SHOULD; }
    }

    if (lc == Classification.PUNCTUATION) {
      switch (left) {
        case "{":
          if (!right.equals("}")) {
            return TokenBreak.SHOULD;
          }
          break;
        case "}":
          if ("else".equals(right) || "catch".equals(right)
              || "finally".equals(right) || ";".equals(right)) {
            // Also "while" inside a DoStatement
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case ";":
          if (leftStack != null) {
            NodeType<?, ?> nt = leftStack.x.getNodeType();
            if (nt == J8NodeType.TryWithResourcesStatement
                || nt == JminNodeType.TryStatement
                || nt == J8NodeType.BasicForStatement
                || nt == JminNodeType.BasicForStatement) {
              return TokenBreak.SHOULD_NOT;
            }
          }
          return TokenBreak.SHOULD;
      }
    }
    // http://www.oracle.com/technetwork/java/javase/documentation/
    // codeconventions-136091.html#248
    // > When an expression will not fit on a single line, break it according to
    // > these general principles:
    // > * Break after a comma.
    // > * Break before an operator.
    // > ...
    if (rc == Classification.PUNCTUATION) {
      switch (right) {
        case ",": return TokenBreak.SHOULD_NOT;
        case "}": return TokenBreak.SHOULD;
      }
    }
    if (lc == Classification.PUNCTUATION) {
      if (isBinaryOperator(left) || isUnaryOperator(left)) {
        // Prefer to break before binary operators
        //    foo
        //    && bar
        // and not between a unary operator and its operand:
        //    -4
        return TokenBreak.SHOULD_NOT;
      }
    }
    return TokenBreak.MAY;
  }

  private static boolean isKeyword(String tok) {
    return TokenStrings.RESERVED.contains(tok);
  }

  private static final ImmutableSet<String> BINARY_OPERATORS = ImmutableSet.of(
      "||", "&&", "|", "&", "^", "==", "!=",
      "<=", ">=","<", ">",
      "<<", ">>>", ">>",
      "+", "-",  // Ambiguous with PrefixOperator
      "*", "/", "%",
      "=", "*=", "/=", "%=", "+=", "-=", "<<=", ">>=", ">>>=", "&=", "^=", "|=",
      "->"  // Not really
      );

  private static boolean isBinaryOperator(String tok) {
    return BINARY_OPERATORS.contains(tok);
  }

  private static final ImmutableSet<String> UNARY_OPERATORS = ImmutableSet.of(
      "+", "-", "++", "--", "~", "!"
      );

  private static boolean isUnaryOperator(String tok) {
    return UNARY_OPERATORS.contains(tok);
  }

  private static final boolean hasSpace(String s) {
    for (int i = 0, n = s.length(); i < n; ++i) {
      if (s.charAt(i) <= 0x20) { return true; }
    }
    return false;
  }
}
