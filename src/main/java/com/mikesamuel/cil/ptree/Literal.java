package com.mikesamuel.cil.ptree;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.TokenMergeGuard;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Literal extends PTParSer {
  final String text;
  final Optional<TokenMergeGuard> tokenMergeGuard;
  final int ln, co, ix;
  final MatchEvent.TokenMatchEvent event;

  private Literal(
      String text, Optional<TokenMergeGuard> tokenMergeGuard,
      int ln, int co, int ix) {
    this.text = text;
    this.tokenMergeGuard = tokenMergeGuard;
    this.ln = ln;
    this.co = co;
    this.ix = ix;
    event = MatchEvent.token(text);
  }

  static PTParSer of(
      String text, boolean ignoreTokenMergeHazards, int ln, int co, int ix) {
    if (text.isEmpty()) { return Concatenation.EMPTY; }
    Optional<TokenMergeGuard> tokenMergeGuard = Optional.absent();
    if (!ignoreTokenMergeHazards) {
      if (Character.isJavaIdentifierStart(text.codePointAt(0))) {
        tokenMergeGuard = Optional.of(WordMergeGuard.INSTANCE);
      } else {
        ImmutableCollection<String> suffixes = Tokens.punctuationSuffixes(text);
        if (!suffixes.isEmpty()) {
          StringBuilder sb = new StringBuilder();
          sb.append("(?!");
          String sep = "";
          for (String suffix : suffixes) {
            sb.append(sep).append(Pattern.quote(suffix));
            sep = "|";
          }
          sb.append(")");
        }
      }
    }
    return new Literal(text, tokenMergeGuard, ln, co, ix);
  }

  @Override
  public Optional<ParseState> parse(
      ParseState state, ParseErrorReceiver err) {
    if (state.startsWith(text, this.tokenMergeGuard)) {
      return Optional.of(state.advance(text.length(), true)
          .appendOutput(event));
    }
    err.error(state, "Expected `" + text + "`");
    return Optional.absent();
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    return Optional.of(state.append(text));
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err) {
    return state.expectEvent(MatchEvent.content(this.text), err);
  }

  @Override
  Kind getKind() {
    return Kind.LIT;
  }

  @Override
  public String toString() {
    return "\"" + this.text + "\"";  // TODO escape
  }

  @Override
  RangeSet<Integer> computeLookahead1() {
    return text.isEmpty()
        ? null
        : ImmutableRangeSet.of(
            Range.<Integer>singleton((int) text.charAt(0)));
  }


  static final class PuncMergeGuard implements TokenMergeGuard {
    final Pattern p;

    PuncMergeGuard(String token) {
      ImmutableCollection<String> suffixes = Tokens.punctuationSuffixes(token);
      if (suffixes.isEmpty()) {
        p = null;
      } else {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (String suffix : suffixes) {
          Preconditions.checkState(
              suffix.startsWith(token) && !suffix.equals(token));
          sb.append(sep)
              .append(Pattern.quote(suffix.substring(token.length())));
          sep = "|";
        }
        p = Pattern.compile(sb.toString());
      }
    }

    @Override
    public boolean isHazard(String input, int start, int end) {
      if (p != null) {
        Matcher m = p.matcher(input);
        m.region(end, input.length());
        m.useTransparentBounds(false);
        m.useAnchoringBounds(true);
        return m.find();
      }
      return false;
    }

  }

  static final class WordMergeGuard implements TokenMergeGuard {

    static final WordMergeGuard INSTANCE = new WordMergeGuard();

    @Override
    public boolean isHazard(String content, int start, int end) {
      if (end < content.length()) {
        int cp = content.codePointAt(end);
        if (Character.isJavaIdentifierPart(cp)) {
          return true;
        }
      }
      return false;
    }
  }
}