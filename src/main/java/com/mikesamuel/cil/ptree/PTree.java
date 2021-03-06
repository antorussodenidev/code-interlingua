package com.mikesamuel.cil.ptree;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;

/**
 * ParSers that work based on a JLS style grammar.
 */
public final class PTree {

  /** A builder for a tree of the given kind. */
  @SuppressWarnings("synthetic-access")
  public static Builder builder(Kind k) { return new Builder(k); }

  /** The kind of node in a parse tree. */
  public enum Kind {
    // Inner node kinds.
    /** Set-union of languages. */
    ALTERNATION,
    /** Concatenation of languages. */
    SEQUENCE,
    /** The concatenation of child languages or the empty string. */
    OPTIONAL,
    /** Kleene star.  A repetition of the child languages. */
    REPEATED,
    // Leaves
    /** Matches literal text. */
    LITERAL,
    /** Delegates to a named production. */
    REFERENCE,
    /** Consumes no input and passes when its body fails. */
    NEGATIVE_LOOKAHEAD,
  }

  /**
   * A parSer that tries each variant in turn.
   */
  public static ParSerable nodeWrapper(NodeType<?, ?> nodeType) {
    return new Reference(nodeType);
  }

  /**
   * A ParSer that assumes/verifies that it is dealing with a complete
   * input/output.
   * <p>
   * For parsing, this simply delegates, and then checks that there is no
   * remaining unparsed input.
   * <p>
   * For serializing, this simply delegates.  There is no need to put a
   * full-stop at the end of the output.
   * <p>
   * For matching, this simply delegates, and then checks that there are no
   * more input events.
   */
  public static ParSerable complete(ParSerable p) {
    if (p instanceof Completer) { return p; }
    return new Completer(p);
  }

  /**
   * @param pattern a regex string to match.
   * @param diagnostic string for error messages.
   */
  public static ParSer patternMatch(String pattern, String diagnostic) {
    return new PatternMatch(pattern, diagnostic);
  }

  /**
   * Builder for a PTree.
   */
  public static final class Builder {
    private final Kind kind;
    private final ImmutableList.Builder<ParSerable> parSerables
        = ImmutableList.builder();

    private Builder(Kind kind) {
      this.kind = kind;
    }

    /** Adds a child. */
    public Builder add(ParSerable parSerable) {
      parSerables.add(parSerable);
      return this;
    }

    /** Specifies that the text is matched literally. */
    public Builder leaf(String leafText, int ln, int co, int ix) {
      return leaf(leafText, false, ln, co, ix);
    }

    /** Specifies that the text is matched literally. */
    public Builder leaf(
        String leafText, boolean ignoreMergeHazards,
        int ln, int co, int ix) {
      parSerables.add(Literal.of(leafText, ignoreMergeHazards, ln, co, ix));
      return this;
    }

    /** Builds the ParSer. */
    public ParSerable build() {
      ImmutableList<ParSerable> ps = this.parSerables.build();
      switch (kind) {
        case ALTERNATION:
          return Alternation.of(ps);
        case LITERAL:
          Preconditions.checkState(ps.size() == 1);
          return ps.get(0);
        case OPTIONAL:
          return Alternation.of(
              ImmutableList.of(Concatenation.of(ps), Concatenation.EMPTY));
        case REFERENCE:
          Preconditions.checkState(ps.size() == 1);
          return ps.get(0);
        case REPEATED:
          return Repetition.of(Concatenation.of(ps));
        case SEQUENCE:
          return Concatenation.of(ps);
        case NEGATIVE_LOOKAHEAD:
          return Lookahead.of(Lookahead.Valence.NEGATIVE, Concatenation.of(ps));
      }
      throw new AssertionError(kind);
    }
  }
}
