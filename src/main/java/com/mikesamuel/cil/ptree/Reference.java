package com.mikesamuel.cil.ptree;

import java.util.EnumSet;
import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.LeftRecursion.Stage;
import com.mikesamuel.cil.parser.Lookahead1;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.RatPack;
import com.mikesamuel.cil.parser.RatPack.ParseCacheEntry;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Reference extends PTParSer {
  final String name;
  final Class<? extends Enum<? extends ParSerable>> variantClass;
  private NodeType nodeType;
  private ImmutableList<NodeVariant> variants;

  Reference(
      String name, Class<? extends Enum<? extends ParSerable>> variantClass) {
    this.name = name;
    this.variantClass = Preconditions.checkNotNull(variantClass);
  }


  void initLazy() {
    if (nodeType == null) {
      ImmutableList.Builder<NodeVariant> variantsBuilder =
          ImmutableList.builder();
      NodeType nt = null;
      for (Enum<?> e : variantClass.getEnumConstants()) {
        NodeVariant nv = (NodeVariant) e;
        variantsBuilder.add(nv);
        if (nt == null) {
          nt = nv.getNodeType();
        } else {
          Preconditions.checkState(nt == nv.getNodeType());
        }
      }
      this.nodeType = Preconditions.checkNotNull(nt);
      this.variants = variantsBuilder.build();
      Preconditions.checkState(name.equals(nodeType.name()));
    }
  }

  /** The production referred to. */
  public NodeType getNodeType() {
    initLazy();
    return nodeType;
  }

  ImmutableList<NodeVariant> getVariants() {
    initLazy();
    return this.variants;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  Kind getKind() {
    return Kind.REF;
  }

  // HACK DEBUG: Not thread safe
  private static final boolean DEBUG = false;
  private static final boolean DEBUG_LR = false;
  private static int depth = 0;
  private static String indent() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; ++i) {
      sb.append("  ");
    }
    return sb.toString();
  }
  private static void indent(int delta) {
    Preconditions.checkState(depth + delta >= 0);
    depth += delta;
  }
  private static String lastInputSeen = "";
  private static String dumpInput(String inp) {
    if (inp.equals(lastInputSeen)) {
      return null;
    }
    lastInputSeen = inp;
    return inp;
  }
  // END HACK

  private static ImmutableList<MatchEvent> lastOutputSeen = ImmutableList.of();
  private static String dumpOutput(Chain<MatchEvent> out) {
    ImmutableList<MatchEvent> lastList = lastOutputSeen;
    ImmutableList<MatchEvent> outList = ImmutableList.copyOf(
        Chain.forwardIterable(out));
    lastOutputSeen = outList;
    if (!lastList.isEmpty() && outList.size() >= lastList.size()
        && outList.subList(0, lastList.size()).equals(lastList)) {
      if (lastList.size() == outList.size()) {
        return null;
      }
      StringBuilder sb = new StringBuilder("[...");
      for (MatchEvent e
           : outList.subList(lastList.size(), outList.size())) {
        sb.append(", ").append(e);
      }
      return sb.append(']').toString();
    }
    return outList.toString();
  }

  @Override
  public ParseResult parse(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
    initLazy();

    ParseCacheEntry cachedParse = state.input.ratPack.getCachedParse(
        nodeType, state.index);
    if (cachedParse.wasTried()) {
      if (cachedParse.passed()) {
        if (DEBUG) {
          System.err.println(indent() + "Using cached success for " + nodeType);
        }
        return ParseResult.success(
            cachedParse.apply(state), ImmutableSet.of());
      } else {
        if (DEBUG) {
          System.err.println(indent() + "Using cached failure for " + nodeType);
        }
        return ParseResult.failure();
      }
    }

    Profile.count(nodeType);

    LeftRecursion.Stage stage = lr.stageForProductionAt(nodeType, state.index);
    switch (stage) {
      case GROWING:
        if (DEBUG) {
          System.err.println(
              indent() + "Found LR Growing " + nodeType + " @ " + state.index);
        }
        return ParseResult.success(
            state.appendOutput(MatchEvent.leftRecursionSuffixEnd(nodeType)),
            // Checked to make sure that the growing does not accidentally take
            // a non-left recursing path.
            Sets.immutableEnumSet(nodeType)
            );
      case NOT_ON_STACK:
        break;
      case SEEDING:
        // Do not cache this failure since it is not a foregone conclusion.
        if (DEBUG) {
          System.err.println(
              indent() + "Found LR seeding " + nodeType + " @ " + state.index);
        }
        return ParseResult.failure(Sets.immutableEnumSet(nodeType));
    }

    if (DEBUG) {
      System.err.println(indent() + "Entered " + nodeType + " @ " + state.index);
      String din = dumpInput(state.input.content.substring(state.index));
      if (din != null) {
        System.err.println(indent() + ". . input=`" + din + "`");
      }
      String dout = dumpOutput(state.output);
      if (dout != null && false) {
        System.err.println(indent() + ". . output=" + dout);
      }
    }

    EnumSet<NodeType> allExclusionsTriggered =
        EnumSet.noneOf(NodeType.class);
    ParseResult result = parseVariants(
        state, lr, err, LeftRecursion.Stage.SEEDING,
        allExclusionsTriggered);
    allExclusionsTriggered.addAll(result.lrExclusionsTriggered);

    boolean wasLrTriggered = allExclusionsTriggered.remove(nodeType);

    if (wasLrTriggered && result.synopsis == ParseResult.Synopsis.SUCCESS) {
      ParseState afterSeed = result.next();
      if (DEBUG) {
        System.err.println(
            indent() + "AfterSeed " + nodeType
            + "\n" + indent() + ". . input=`"
            + afterSeed.input.content.substring(afterSeed.index) + "`"
            + "\n" + indent() + ". . output="
            + ImmutableList.copyOf(Chain.forwardIterable(afterSeed.output)));
      }

      ParseState grown = afterSeed;

      grow_the_seed:
      while (true) {
        ParseResult growResult = parseVariants(
            grown.appendOutput(MatchEvent.leftRecursionSuffixStart()),
            lr, err, LeftRecursion.Stage.GROWING,
            allExclusionsTriggered);
        allExclusionsTriggered.addAll(growResult.lrExclusionsTriggered);
        switch (growResult.synopsis) {
          case FAILURE:
          case FAILURE_DUE_TO_LR_EXCLUSION:
            // Use the last successful growing.
            break grow_the_seed;
          case SUCCESS:
            if (!growResult.lrExclusionsTriggered.contains(nodeType)) {
              // TODO: check that left-recursion occurred at grown.index.
              // We could walk and check that there is in fact an LREnd on the
              // output, but it would be more efficient for lr to keep track of
              // this.
              break grow_the_seed;
            }
            ParseState next = growResult.next();
            if (next.index == grown.index) {
              // no progress made.
              break grow_the_seed;
            }
            Preconditions.checkState(next.index > grown.index);
            grown = next;
            continue;
        }
      }

      allExclusionsTriggered.remove(nodeType);
      LRRewriter rewriter = new LRRewriter(nodeType);
      result = ParseResult.success(
          grown.withOutput(rewriter.rewrite(grown.output)),
          allExclusionsTriggered);
    }

    boolean canCache = true;
    for (NodeType nt : allExclusionsTriggered) {
      if (nt != nodeType &&
          lr.stageForProductionAt(nt, state.index)
          != LeftRecursion.Stage.NOT_ON_STACK) {
        canCache = false;
        break;
      }
    }

    switch (result.synopsis) {
      case FAILURE:
      case FAILURE_DUE_TO_LR_EXCLUSION:
        RatPack.ParseCacheEntry e = state.input.ratPack.getCachedParse(
            nodeType, state.index);
        if (false && e.wasTried() && e.passed()) {
          // TODO: Is this necessary?
          // If so, our ratpack should be a growable map not an evicting cache.
          System.err.println(
              indent() + "Passing " + nodeType
              + " @ " + state.index + " due to cached result");
          return ParseResult.success(e.apply(state), allExclusionsTriggered);
        }
        if (canCache) {
          state.input.ratPack.cacheFailure(state.index, nodeType);
        }
        if (DEBUG) {
          System.err.println(
              indent() + "Fail " + nodeType + " @ " + state.index);
        }
        return ParseResult.failure(allExclusionsTriggered);
      case SUCCESS:
        ParseState next = result.next();
        if (canCache) {
          state.input.ratPack.cacheSuccess(
              state.index, next.index, nodeType, next.output);
        }
        if (DEBUG) {
          System.err.println(
              indent() + "Pass " + nodeType + " @ " + state.index
              + " -> " + next.index);
        }
        return ParseResult.success(next, allExclusionsTriggered);
    }
    throw new AssertionError(result.synopsis);
  }

  private ParseResult parseVariants(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err, Stage stage,
      EnumSet<NodeType> failureExclusionsTriggered) {
    if (DEBUG) { indent(1); }

    try {
      for (NodeVariant variant : variants) {
        if (stage == Stage.SEEDING) {
          Lookahead1 la1 = variant.getLookahead1();
          if (!(la1 == null || la1.canFollow(state))) {
            continue;
          }
        }

        try (LeftRecursion.VariantScope scope = lr.enter(
                 variant, state.index, stage)) {
          ParseResult result = variant.getParSer().parse(
              state.appendOutput(MatchEvent.push(variant)), lr, err);
          switch (result.synopsis) {
            case FAILURE:
            case FAILURE_DUE_TO_LR_EXCLUSION:
              failureExclusionsTriggered.addAll(result.lrExclusionsTriggered);
              continue;
            case SUCCESS:
              MatchEvent.Pop pop = DEBUG
                  ? MatchEvent.pop(variant) : MatchEvent.pop();
              return ParseResult.success(
                  result.next().appendOutput(pop),
                  result.lrExclusionsTriggered);
          }
          throw new AssertionError(result.synopsis);
        }
      }
    } finally {
      if (DEBUG) { indent(-1); }
    }

    return ParseResult.failure();
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    initLazy();
    return Alternation.of(variants).getParSer().unparse(state, err);
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err) {
    initLazy();
    return Alternation.of(variants).getParSer().match(state, err);
  }


  private static final class LRRewriter {
    private final MatchEvent.LREnd toPushback;
    private final List<MatchEvent> pushback = Lists.newArrayList();
    private int popDepth;

    LRRewriter(NodeType nodeType) {
      this.toPushback = MatchEvent.leftRecursionSuffixEnd(nodeType);
    }

    Chain<MatchEvent> rewrite(Chain<MatchEvent> out) {
      if (DEBUG_LR) {
        @SuppressWarnings("synthetic-access")
        String indent = indent();
        System.err.println(indent + "before rewriteLR " + toPushback);
        dumpEvents(indent, out);
      }
      List<MatchEvent.Pop> pops = Lists.newArrayList();
      Chain<MatchEvent> withPushbackAndPops = pushback(out);
      for (MatchEvent pop : pops) {
        withPushbackAndPops = Chain.append(withPushbackAndPops, pop);
      }
      Preconditions.checkState(pushback.isEmpty());
      if (DEBUG_LR) {
        @SuppressWarnings("synthetic-access")
        String indent = indent();
        System.err.println(indent + "after rewriteLR " + toPushback);
        dumpEvents(indent, withPushbackAndPops);
      }
      return withPushbackAndPops;
    }

    /**
     * Scan until we find the push of the seed and distribute pushes and pops
     * from LRSuffix events.
     */
    private Chain<MatchEvent> pushback(Chain<MatchEvent> out) {
      if (DEBUG_LR) {
        @SuppressWarnings("synthetic-access")
        String indent = indent();
        System.err.println(
            indent + "pushback(" + (out != null ? "...," + out.x : "<null>")
            + ", popDepth=" + popDepth + ", pushback=" + pushback + ")");
      }
      Preconditions.checkNotNull(out);

      MatchEvent e = out.x;

      Chain<MatchEvent> pushedBack;
      if (e instanceof MatchEvent.Pop) {
        ++popDepth;
        pushedBack = Chain.append(pushback(out.prev), e);
      } else if (e instanceof MatchEvent.Push) {
        MatchEvent.Push push = (MatchEvent.Push) e;
        Preconditions.checkState(popDepth != 0);  // pop required above.
        --popDepth;
        if (popDepth == 0) {
          Preconditions.checkState(
              toPushback.nodeType == push.variant.getNodeType());
          pushedBack = out.prev;
          for (MatchEvent pb : Lists.reverse(pushback)) {
            pushedBack = Chain.append(pushedBack, pb);
          }
          pushback.clear();
          pushedBack = Chain.append(pushedBack, e);
        } else {
          pushedBack = Chain.append(pushback(out.prev), e);
        }
      } else if (toPushback.equals(e)) {
        int pushCount = 0;
        int popCount = 0;

        pushedBack = null;
        boolean foundStart = false;
        for (Chain<MatchEvent> c = out.prev; c != null; c = c.prev) {
          MatchEvent ce = c.x;
          if (ce instanceof MatchEvent.LRStart) {
            Preconditions.checkState(pushCount >= popCount);
            popDepth += popCount - pushCount;
            Preconditions.checkState(popDepth >= 0);
            pushedBack = pushback(c.prev);
            foundStart = true;
            break;
          } else if (ce instanceof MatchEvent.Pop) {
            pushback.add(ce);
            ++popCount;
          } else if (ce instanceof MatchEvent.Push) {
            pushback.add(ce);
            ++pushCount;
          } else {
            throw new AssertionError("Non push/pop on path to LR invocation");
          }
        }
        Preconditions.checkState(foundStart);
      } else {
        pushedBack = Chain.append(pushback(out.prev), e);
      }
      return pushedBack;
    }
  }

  private static void dumpEvents(String indent, Chain<MatchEvent> events) {
    StringBuilder sb = new StringBuilder(indent).append(". ");
    int pushDepth = 0;
    for (MatchEvent e : Chain.forwardIterable(events)) {
      if (e instanceof MatchEvent.Pop) {
        if (pushDepth > 0) {
          --pushDepth;
          sb.setLength(sb.length() - 2);
        }
      }
      int len = sb.length();
      sb.append(e);
      System.err.println(sb);
      sb.setLength(len);
      if (e instanceof MatchEvent.Push) {
        ++pushDepth;
        sb.append(". ");
      }
    }
  }

}
