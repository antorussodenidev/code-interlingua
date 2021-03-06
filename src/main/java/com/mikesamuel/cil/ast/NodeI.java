package com.mikesamuel.cil.ast;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Strings;
import com.mikesamuel.cil.ast.meta.MetadataBridge;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Base interface for nodes and node mixins.
 */
public interface NodeI<
    BASE_NODE extends BaseNode<BASE_NODE, NODE_TYPE, NODE_VARIANT>,
    NODE_TYPE extends Enum<NODE_TYPE> & NodeType<BASE_NODE, NODE_TYPE>,
    NODE_VARIANT extends NodeVariant<BASE_NODE, NODE_TYPE>>
extends Positioned {

  /**
   * Copies metadata from the source node.
   *
   * @param bridge receives the value from node and can transform it.
   */
  void copyMetadataFrom(NodeI<?, ?, ?> node, MetadataBridge bridge);

  /**
   * Copies metadata from the source node using the identity bridge.
   */
  default void copyMetadataFrom(NodeI<?, ?, ?> node) {
    copyMetadataFrom(node, MetadataBridge.Bridges.IDENTITY);
  }

  /**
   * The variant of the node.
   */
  NODE_VARIANT getVariant();

  /**
   * The source position of the node if known.
   */
  @Nullable @Override SourcePosition getSourcePosition();

  /**
   * @see #getSourcePosition()
   */
  void setSourcePosition(SourcePosition newSourcePosition);

  /**
   * The type of node.
   */
  default NODE_TYPE getNodeType() {
    return getVariant().getNodeType();
  }

  /**
   * Immutable list of children of this node.  Empty if a leaf.
   */
  List<? extends BASE_NODE> getChildren();

  /** Like {@code getChildren().size()} but doesn't require wrapping. */
  int getNChildren();

  /** Like {@code getChildren().get(i)} but doesn't require wrapping. */
  BASE_NODE getChild(int i);

  /**
   * The value if any.  {@code null} if an inner node.
   */
  String getValue();



  /** The first child with the given type or null. */
  public @Nullable default BASE_NODE firstChildWithType(NODE_TYPE nt) {
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      BaseNode<?, ?, ?> child = getChild(i);
      if (child.getNodeType() == nt) {
        return nt.getNodeBaseType().cast(child);
      }
    }
    return null;
  }

  /** The first child with the given type or null. */
  public default @Nullable <T>
  T firstChildWithType(Class<? extends T> cl) {
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      BaseNode<?, ?, ?> child = getChild(i);
      if (cl.isInstance(child)) {
        return cl.cast(child);
      }
    }
    return null;
  }

  /**
   * The content of descendant leaf nodes separated by the given string.
   */
  public default String getTextContent(String separator) {
    StringBuilder sb = new StringBuilder();
    NodeIHelpers.appendTextContent(this, sb, separator);
    return sb.toString();
  }

  /**
   * Appends a LISPy form.
   */
  public default void appendToStringBuilder(StringBuilder sb) {
    sb.append('(');
    sb.append(getVariant());
    String literalValue = getValue();
    if (literalValue != null) {
      sb.append(" `").append(literalValue).append('`');
    }
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      BaseNode<?, ?, ?> child = getChild(i);
      sb.append(' ');
      child.appendToStringBuilder(sb);
    }
    sb.append(')');
  }

  /**
   * A diagnostic string describing the structure of the tree.
   *
   * @param prefix a string to place to the left on each line.
   *     Can be used to indent.
   * @param decorator called for each node to return a string that is displayed
   *     to the right on the same line.  A return value of null means no
   *     decoration to display.  A value of null is equivalent to
   *     {@code Functions.constant(null)}.
   */
  public default String toAsciiArt(
      String prefix,
      @Nullable Function<? super NodeI<?, ?, ?>, ? extends String> decorator) {
    StringBuilder out = new StringBuilder();
    StringBuilder indentation = new StringBuilder();
    indentation.append(prefix);
    NodeIHelpers.appendAsciiArt(this, indentation, out, decorator);
    return out.toString();
  }

  /**
   * Like {@link #toAsciiArt(String, Function)} but does not decorate.
   */
  public default String toAsciiArt(String prefix) {
    return toAsciiArt(prefix, Functions.constant(null));
  }

  /** A clone that has the same children. */
  BASE_NODE shallowClone();

  /** A clone whose children are deep clones of this node's children. */
  BASE_NODE deepClone();

}

final class NodeIHelpers {

  static void appendTextContent(
      NodeI<?, ?, ?> node, StringBuilder sb, String sep) {
    String literalValue = node.getValue();
    if (!Strings.isNullOrEmpty(literalValue)) {
      if (sep != null && sb.length() != 0) {
        sb.append(sep);
      }
      sb.append(literalValue);
    } else {
      for (int i = 0, n = node.getNChildren(); i < n; ++i) {
        appendTextContent(node.getChild(i), sb, sep);
      }
    }
  }

  static void appendAsciiArt(
      NodeI<?, ?, ?> node, StringBuilder prefix, StringBuilder out,
      @Nullable Function<? super NodeI<?, ?, ?>, ? extends String> decorator) {
    out.append(prefix);
    appendNodeHeader(node, out);
    String decoration = decorator != null ? decorator.apply(node) : null;
    if (decoration != null) {
      out.append(" : ").append(decoration);
    }
    int prefixLength = prefix.length();
    prefix.append("  ");
    for (int i = 0, n = node.getNChildren(); i < n; ++i) {
      BaseNode<?, ?, ?> child = node.getChild(i);
      out.append('\n');
      appendAsciiArt(child, prefix, out, decorator);
    }
    prefix.setLength(prefixLength);
  }

  private static void appendNodeHeader(NodeI<?, ?, ?> node, StringBuilder out) {
    out.append(node.getVariant());
    String literalValue = node.getValue();
    if (literalValue != null) {
      out.append(' ').append(literalValue);
    }
  }
}
