package com.mikesamuel.cil.ast;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * A node in a Java AST.
 */
public abstract class BaseNode implements NodeOrBuilder {
  private final NodeVariant variant;
  private final ImmutableList<BaseNode> children;
  private final @Nullable String literalValue;
  private @Nullable SourcePosition sourcePosition;

  BaseNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String literalValue) {
    this.variant = Preconditions.checkNotNull(variant);

    NodeType type = variant.getNodeType();
    Preconditions.checkState(type.getNodeBaseType().isInstance(this));

    this.children = ImmutableList.copyOf(children);
    this.literalValue = literalValue;
  }


  /** The particular variant within the production. */
  @Override
  public NodeVariant getVariant() {
    return variant;
  }

  /** The production's node type. */
  @Override
  public final NodeType getNodeType() {
    return getVariant().getNodeType();
  }

  /** Child nodes. */
  @Override
  public final ImmutableList<BaseNode> getChildren() {
    return children;
  }

  @Override
  public final int getNChildren() {
    return children.size();
  }

  @Override
  public final BaseNode getChild(int i) {
    return children.get(i);
  }

  /** The value if any. */
  @Override
  public final @Nullable String getValue() {
    return literalValue;
  }

  /** The source position.  Non-normative. */
  @Override
  public final @Nullable SourcePosition getSourcePosition() {
    return sourcePosition;
  }

  /**
   * @see #getSourcePosition()
   */
  public final void setSourcePosition(SourcePosition newSourcePosition) {
    this.sourcePosition = newSourcePosition;
  }

  @Override
  public final BaseNode toBaseNode() {
    return this;
  }

  /**
   * Allows building nodes.
   */
  public static abstract
  class Builder<N extends BaseNode, V extends NodeVariant>
  implements NodeOrBuilder {
    private V newNodeVariant;
    private SourcePosition sourcePosition;
    /** True if it changed from its parent. */
    private boolean changed;

    protected Builder(V variant) {
      this.newNodeVariant = Preconditions.checkNotNull(variant);
      this.changed = true;
    }

    protected Builder(N source) {
      @SuppressWarnings("unchecked")
      // Unsound but safe is subclassing follows discipline.  For this reason
      // we keep constructors package-private.
      V sourceVariant = (V) source.getVariant();
      this.newNodeVariant = sourceVariant;
      this.sourcePosition = source.getSourcePosition();
      this.copyMetadataFrom(source);
      this.changed = false;
    }

    protected Builder(Builder<N, V> source) {
      // Unsound but safe is subclassing follows discipline.  For this reason
      // we keep constructors package-private.
      V sourceVariant = source.getVariant();
      this.newNodeVariant = sourceVariant;
      this.sourcePosition = source.getSourcePosition();
      this.copyMetadataFrom(source);
      this.changed = source.changed;
    }

    @Override
    public V getVariant() {
      return newNodeVariant;
    }

    @Override
    public @Nullable SourcePosition getSourcePosition() {
      return sourcePosition;
    }

    /**
     * Specifies the variant that will be built.
     */
    public Builder<N, V> variant(V newVariant) {
      if (newVariant != this.newNodeVariant) {
        this.newNodeVariant = Preconditions.checkNotNull(newVariant);
        markChanged();
      }
      return this;
    }

    /** Any nodes built will have the same meta-data as the given node. */
    public Builder<N, V> copyMetadataFrom(N source) {
      SourcePosition pos = source.getSourcePosition();
      if (pos != null) {
        setSourcePosition(pos);
      }
      return this;
    }

    /** Any nodes built will have the same meta-data as the given node. */
    public Builder<N, V> copyMetadataFrom(Builder<N, V> source) {
      SourcePosition pos = source.getSourcePosition();
      if (pos != null) {
        setSourcePosition(pos);
      }
      return this;
    }

    /**
     * Specifies the source position for the built node.
     */
    public Builder<N, V> setSourcePosition(SourcePosition newSourcePosition) {
      if (!(sourcePosition == null ? newSourcePosition == null
          : sourcePosition.equals(newSourcePosition))) {
        this.sourcePosition = newSourcePosition;
        markChanged();
      }
      return this;
    }

    /** Builds a complete node. */
    public abstract N build();

    @Override
    public final N toBaseNode() {
      return build();
    }


    protected void markChanged() {
      this.changed = true;
    }

    /**
     * True iff the builder was derived from a node and no changes were
     * made to that node.
     */
    public boolean changed() {
      return this.changed;
    }
  }

  /**
   * A builder for inner nodes.
   */
  public static abstract
  class InnerBuilder<N extends BaseNode, V extends NodeVariant>
  extends Builder<N, V> {
    private final List<BaseNode> newNodeChildren = Lists.newArrayList();

    protected InnerBuilder(V variant) {
      super(variant);
    }

    protected InnerBuilder(N source) {
      super(source);
      newNodeChildren.addAll(source.getChildren());
    }

    protected InnerBuilder(Builder<N, V> source) {
      super(source);
      newNodeChildren.addAll(source.getChildren());
    }

    /** The count of children thus far. */
    @Override
    public int getNChildren() {
      return newNodeChildren.size();
    }

    /** The child at index i */
    @Override
    public BaseNode getChild(int i) {
      return newNodeChildren.get(i);
    }

    @Override
    public ImmutableList<BaseNode> getChildren() {
      return ImmutableList.copyOf(newNodeChildren);
    }

    @Override
    public @Nullable String getValue() {
      return null;
    }

    /** Adds a child node. */
    public InnerBuilder<N, V> add(BaseNode child) {
      return add(newNodeChildren.size(), child);
    }

    /** Adds a child node at the given index. */
    public InnerBuilder<N, V> add(int index, BaseNode child) {
      this.newNodeChildren.add(index, Preconditions.checkNotNull(child));
      this.markChanged();
      return this;
    }

    /** Adds a child node at the given index. */
    public InnerBuilder<N, V> replace(int index, BaseNode child) {
      BaseNode old = this.newNodeChildren.set(
          index, Preconditions.checkNotNull(child));
      if (old != child) {
        this.markChanged();
      }
      return this;
    }

    /** Adds a child node at the given index. */
    public InnerBuilder<N, V> remove(int index) {
      this.newNodeChildren.remove(index);
      this.markChanged();
      return this;
    }
  }

  /**
   * A builder for inner nodes.
   */
  public static abstract
  class LeafBuilder<N extends BaseNode, V extends NodeVariant>
  extends Builder<N, V> {
    private Optional<String> newLiteralValue = Optional.absent();

    protected LeafBuilder(V variant) {
      super(variant);
    }

    protected LeafBuilder(N source) {
      super(source);
      newLiteralValue = Optional.fromNullable(source.getValue());
    }

    protected LeafBuilder(Builder<N, V> source) {
      super(source);
      newLiteralValue = Optional.fromNullable(source.getValue());
    }

    @Override
    public final String getValue() {
      return newLiteralValue.orNull();
    }

    @Override
    public ImmutableList<BaseNode> getChildren() {
      return ImmutableList.of();
    }

    @Override
    public final int getNChildren() {
      return 0;
    }

    @Override
    public final BaseNode getChild(int i) {
      throw new IndexOutOfBoundsException("" + i);
    }


    /** Specifies the value. */
    public LeafBuilder<N, V> leaf(String leafLiteralValue) {
      Optional<String> newValueOpt = Optional.of(leafLiteralValue);
      if (!newLiteralValue.equals(newValueOpt)) {
        this.newLiteralValue = newValueOpt;
        this.markChanged();
      }
      return this;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    appendToStringBuilder(sb);
    return sb.toString();
  }

  @Override
  public final int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((children == null) ? 0 : children.hashCode());
    if (!variant.isIgnorable()) {
      result = prime * result
          + ((literalValue == null) ? 0 : literalValue.hashCode());
    }
    result = prime * result + variant.hashCode();
    return result;
  }

  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    BaseNode other = (BaseNode) obj;
    if (children == null) {
      if (other.children != null) {
        return false;
      }
    } else if (!children.equals(other.children)) {
      return false;
    }
    if (!variant.equals(other.variant)) {
      return false;
    }
    if (!variant.isIgnorable()) {
      if (literalValue == null) {
        if (other.literalValue != null) {
          return false;
        }
      } else if (!literalValue.equals(other.literalValue)) {
        return false;
      }
    }
    return true;
  }


  /**
   * A finder rooted at this that returns results of the given node type or
   * trait.
   */
  public <T> Finder<T> finder(Class<T> resultType) {
    return new Finder<>(this, resultType);
  }


  /** Searches the subtree rooted at {@code BaseNode.this}. */
  public static final class Finder<T> {
    private final BaseNode root;
    private final Class<? extends T> matchType;
    private Predicate<? super BaseNode> match;
    private Predicate<? super BaseNode> doNotEnter;
    private boolean allowNonStandard = false;

    Finder(BaseNode root, Class<? extends T> matchType) {
      this.root = root;
      this.matchType = matchType;
      match = Predicates.instanceOf(matchType);
      doNotEnter = Predicates.alwaysFalse();
    }

    /**
     * Restricts matched nodes to those with a node type among those given.
     *
     * @return {@code this} to enable chaining.
     */
    public Finder<T> match(NodeType nt, NodeType... nts) {
      match = Predicates.and(match, new HasNodeTypeIn(nt, nts));
      return this;
    }

    /**
     * Restricts nodes recursively searched by excluding them to ones with a
     * node type among those given.
     *
     * @return {@code this} to enable chaining.
     */
    public Finder<T> exclude(NodeType nt, NodeType... nts) {
      doNotEnter = Predicates.or(doNotEnter, new HasNodeTypeIn(nt, nts));
      return this;
    }

    /**
     * Restricts nodes recursively searched by excluding them to ones with a
     * node type among those given.
     *
     * @return {@code this} to enable chaining.
     */
    public Finder<T> exclude(Class<? extends NodeOrBuilder> cl) {
      doNotEnter = Predicates.or(doNotEnter, Predicates.instanceOf(cl));
      return this;
    }

    /**
     * Sets whether the finder will recurse into
     * {@linkplain NodeTypeTables#NONSTANDARD nonstandard} productions.
     * Defaults to false.
     */
    public Finder<T> allowNonStandard(boolean b) {
      this.allowNonStandard = b;
      return this;
    }

    /**
     * Performs a search and returns the results.
     */
    public ImmutableList<T> find() {
      ImmutableList.Builder<T> results = ImmutableList.builder();
      find(root, results);
      return results.build();
    }

    /**
     * Performs a search and returns the sole result or panics if there is more
     * than one result.
     * <p>
     * With the default assumption that find does not descend into template
     * instructions, this helps with assumptions that there is one node.
     */
    public Optional<T> findOne() {
      ImmutableList<T> results = find();
      if (results.size() == 1) {
        return Optional.of(results.get(0));
      }
      Preconditions.checkState(results.isEmpty(), results);
      return Optional.absent();
    }

    private void find(BaseNode node, ImmutableList.Builder<T> results) {
      if (match.apply(node)) {
        results.add(Preconditions.checkNotNull(matchType.cast(node)));
      }
      if (!doNotEnter.apply(node)
          && (allowNonStandard
              || !NodeTypeTables.NONSTANDARD.contains(node.getNodeType()))) {
        for (int i = 0, n = node.getNChildren(); i < n; ++i) {
          BaseNode child = node.getChild(i);
          find(child, results);
        }
      }
    }
  }

  private static final class HasNodeTypeIn implements Predicate<BaseNode> {
    final Set<NodeType> nodeTypes;

    HasNodeTypeIn(NodeType nt, NodeType... nts) {
      this.nodeTypes = Sets.immutableEnumSet(nt, nts);
    }

    @Override
    public boolean apply(BaseNode node) {
      return node != null && nodeTypes.contains(node.getNodeType());
    }
  }
}
