package com.mikesamuel.cil.parser;

import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

/** A singly-linked list built in reverse. */
public final class Chain<T> {
  /** The content. */
  public final T x;
  /** The previous element in the chain. */
  public final @Nullable Chain<T> prev;

  private Chain(T x, @Nullable Chain<T> prev) {
    this.x = x;
    this.prev = prev;
  }

  /** Iterates in reverse order. */
  public static <T> Iterable<T> reverseIterable(
      @Nullable final Chain<? extends T> c) {
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          Chain<? extends T> ch = c;

          @Override
          public boolean hasNext() {
            return ch != null;
          }

          @Override
          public T next() {
            if (ch == null) { throw new NoSuchElementException(); }
            T next = ch.x;
            ch = ch.prev;
            return next;
          }
        };
      }
    };
  }

  /** Iterates from farthest back to the current. */
  public static <T> Iterable<T> forwardIterable(@Nullable Chain<? extends T> c) {
    ImmutableList.Builder<T> b = ImmutableList.builder();
    for (Chain<? extends T> ch = c; ch != null; ch = ch.prev) {
      b.add(ch.x);
    }
    return b.build().reverse();
  }

  /**
   * The chain with next following prev.
   */
  public static <T> Chain<T> append(@Nullable Chain<T> prev, T next) {
    return new Chain<>(next, prev);
  }

  /** A chain of the same length as ls with the elements in reverse order. */
  public static <T> Chain<T> reverse(@Nullable Chain<T> ls) {
    Chain<T> rev = null;
    for (Chain<T> rest = ls; rest != null; rest = rest.prev) {
      rev = append(rev, rest.x);
    }
    return rev;
  }

  /**
   * The chain that has all the elements of prev followed by
   * {@link #reverse reverse}{@code (next)}.
   */
  public static <T> Chain<T> revAppendAll(
      @Nullable Chain<T> prev, @Nullable Chain<T> next) {
    Chain<T> out = prev;
    for (Chain<T> c = next; c != null; c = c.prev) {
      out = append(out, c.x);
    }
    return out;
  }
}
