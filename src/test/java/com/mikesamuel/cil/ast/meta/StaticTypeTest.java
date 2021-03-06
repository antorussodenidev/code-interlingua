package com.mikesamuel.cil.ast.meta;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mikesamuel.cil.ast.meta.StaticType.Cast;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ClassOrInterfaceType;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.ast.meta.TypeSpecification.Variance;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class StaticTypeTest extends TestCase {

  StaticType.TypePool pool;
  Logger logger;

  @Before @Override
  public void setUp() throws Exception {
    super.setUp();
    pool = new StaticType.TypePool(
        TypeInfoResolver.Resolvers.forClassLoader(getClass().getClassLoader()));
    logger = Logger.getAnonymousLogger();
  }

  @After @Override
  public void tearDown() throws Exception {
    pool = null;
    super.tearDown();
  }


  @Test
  public static void testPrimitiveCasts() {
    ImmutableList<StaticType> specs = ImmutableList.of(
        StaticType.T_VOID,
        StaticType.ERROR_TYPE,
        StaticType.T_BOOLEAN,
        StaticType.T_BYTE,
        StaticType.T_CHAR,
        StaticType.T_SHORT,
        StaticType.T_INT,
        StaticType.T_LONG,
        StaticType.T_FLOAT,
        StaticType.T_DOUBLE
        );

    Cast s = Cast.SAME;
    Cast l = Cast.CONVERTING_LOSSLESS;
    Cast y = Cast.CONVERTING_LOSSY;
    Cast x = Cast.BOX;
    Cast u = Cast.UNBOX;
    Cast d = Cast.DISJOINT;

    Cast[][] want =
      {
         // V  E  B  B  C  S  I  L  F  D from   to
          { s, d, d, d, d, d, d, d, d, d },  // V
          { d, s, u, u, u, u, u, u, u, u },  // E
          { d, x, s, d, d, d, d, d, d, d },  // B
          { d, x, d, s, l, l, l, l, l, l },  // B
          { d, x, d, y, s, y, l, l, l, l },  // C
          { d, x, d, y, y, s, l, l, l, l },  // S
          { d, x, d, y, y, y, s, l, l, l },  // I
          { d, x, d, y, y, y, y, s, l, l },  // L
          { d, x, d, y, y, y, y, y, s, l },  // F
          { d, x, d, y, y, y, y, y, y, s },  // D
    };

    for (int j = 0, n = specs.size(); j < n; ++j) {
      StaticType tj = specs.get(j);
      for (int i = 0; i < n; ++i) {
        StaticType ti = specs.get(i);
        Cast c = want[j][i];
        assertEquals(
            ti + ".assignableFrom(" + tj + ")",
            c, ti.assignableFrom(tj));
      }
    }
  }

  private StaticType type(String name, TypeBinding... bindings) {
    return type(name, 0, bindings);
  }

  private StaticType type(String name, int nDims, TypeBinding... bindings) {
    return pool.type(spec(name, nDims, bindings), null, logger);
  }

  private static TypeSpecification spec(String name, TypeBinding... bindings) {
    return spec(name, 0, bindings);
  }

  private static TypeSpecification spec(
      String name, int nDims, TypeBinding... bindings) {
    Name nm = Name.DEFAULT_PACKAGE;
    int hash = name.lastIndexOf('#');
    String prefix = hash >= 0 ? name.substring(0, hash) : name;
    String[] parts = prefix.split("[.]");
    for (int i = 0, n = parts.length; i < n; ++i) {
      nm = nm.child(parts[i], i + 1 == n ? Name.Type.CLASS : Name.Type.PACKAGE);
    }
    if (hash >= 0) {
      nm = nm.child(name.substring(hash + 1), Name.Type.FIELD);
    }
    return TypeSpecification.unparameterized(nm)
        .withBindings(ImmutableList.copyOf(bindings))
        .withNDims(nDims);
  }

  private static TypeBinding is(String s) {
    return new TypeBinding(Variance.INVARIANT, spec(s));
  }

  private static TypeBinding ext(String s) {
    return new TypeBinding(Variance.EXTENDS, spec(s));
  }

  private static TypeBinding sup(String s) {
    return new TypeBinding(Variance.SUPER, spec(s));
  }

  @Test
  public void testInterfaces() {
    StaticType object = type("java.lang.Object");
    StaticType charSequence = type("java.lang.CharSequence");
    assertCasts(
        Cast.CONFIRM_CHECKED,
        object,
        charSequence,
        Cast.CONFIRM_SAFE);
  }

  @Test
  public void testCharSequenceArrays() {
    StaticType object = type("java.lang.Object");
    StaticType objectArray = type("java.lang.Object", 1);
    StaticType objectArrayArray = type("java.lang.Object", 2);
    StaticType stringArray = type("java.lang.String", 1);
    StaticType charSequenceArray = type("java.lang.CharSequence", 1);

    assertCasts(
        Cast.SAME,
        object,
        object,
        Cast.SAME);

    assertCasts(
        Cast.SAME,
        objectArray,
        objectArray,
        Cast.SAME);

    assertCasts(
        Cast.CONFIRM_CHECKED,
        object,
        objectArray,
        Cast.CONFIRM_SAFE);

    assertCasts(
        Cast.CONFIRM_CHECKED,
        objectArray,
        stringArray,
        Cast.CONFIRM_SAFE);

    assertCasts(
        Cast.CONFIRM_CHECKED,
        objectArray,
        charSequenceArray,
        Cast.CONFIRM_SAFE);

    assertCasts(
        Cast.CONFIRM_CHECKED,
        type("java.io.Serializable"),
        objectArray,
        Cast.CONFIRM_SAFE);

    assertCasts(
        Cast.CONFIRM_CHECKED,
        type("java.lang.Cloneable"),
        objectArray,
        Cast.CONFIRM_SAFE);

    assertCasts(
        Cast.CONFIRM_CHECKED,
        objectArray,
        objectArrayArray,
        Cast.CONFIRM_SAFE);

    assertCasts(
        Cast.CONFIRM_CHECKED,
        objectArray,
        objectArrayArray,
        Cast.CONFIRM_SAFE);
  }

  @Test
  public void testPrimitiveArrays() {
    StaticType intType = type("java.lang.Integer#TYPE", 0);
    StaticType intArray = type("java.lang.Integer#TYPE", 1);
    StaticType intArrayArray = type("java.lang.Integer#TYPE", 2);
    StaticType longType = type("java.lang.Long#TYPE", 0);
    StaticType longArray = type("java.lang.Long#TYPE", 1);
    StaticType object = type("java.lang.Object", 0);
    StaticType objectArray = type("java.lang.Object", 1);
    StaticType IntegerArray = type("java.lang.Integer", 1);

    assertCasts(
        Cast.CONVERTING_LOSSLESS,
        intType,
        longType,
        Cast.CONVERTING_LOSSY);

    assertCasts(
        Cast.DISJOINT,
        intArray,
        longArray,
        Cast.DISJOINT);

    assertCasts(
        Cast.DISJOINT,
        intArray,
        longArray,
        Cast.DISJOINT);

    assertCasts(
        Cast.DISJOINT,
        intArray,
        IntegerArray,
        Cast.DISJOINT);

    assertCasts(
        Cast.DISJOINT,
        intArray,
        objectArray,
        Cast.DISJOINT);

    assertCasts(
        Cast.CONFIRM_SAFE,
        IntegerArray,
        objectArray,
        Cast.CONFIRM_CHECKED);

    assertCasts(
        Cast.DISJOINT,
        intArray,
        intArrayArray,
        Cast.DISJOINT);

    assertCasts(
        Cast.CONFIRM_SAFE,
        intArray,
        object,
        Cast.CONFIRM_CHECKED);
  }

  @Test
  public void testVoid() {
    assertCasts(
        Cast.SAME,
        StaticType.T_VOID,
        StaticType.T_VOID,
        Cast.SAME);

    // void does not engage in type promotion.
    assertCasts(
        Cast.DISJOINT,
        StaticType.T_VOID,
        StaticType.T_INT,
        Cast.DISJOINT);
    // void is not truthy or falsey even though both are represented internally
    // as one-off types.
    assertCasts(
        Cast.DISJOINT,
        StaticType.T_VOID,
        StaticType.T_BOOLEAN,
        Cast.DISJOINT);

    // void does not engage in boxing behavior.
    // It's a return type, not a full-fledged type.
    assertCasts(
        Cast.DISJOINT,
        StaticType.T_VOID,
        type("java.lang.Void"),
        Cast.DISJOINT);
    assertCasts(
        Cast.DISJOINT,
        StaticType.T_VOID,
        type("java.lang.Object"),
        Cast.DISJOINT);
  }

  @Test
  public void testBoolean() {
    StaticType t = StaticType.T_BOOLEAN;
    StaticType w = type("java.lang.Boolean");
    StaticType obj = type("java.lang.Object");
    assertCasts(Cast.SAME, t, t, Cast.SAME);
    assertCasts(Cast.SAME, t, t, Cast.SAME);
    assertCasts(Cast.BOX, t, w, Cast.UNBOX);
    assertCasts(Cast.BOX, t, obj, Cast.DISJOINT);
    assertCasts(Cast.DISJOINT, t, StaticType.T_INT, Cast.DISJOINT);
  }

  @Test
  public void testNumericBoxing() {
    StaticType object = type("java.lang.Object");
    StaticType number = type("java.lang.Number");
    StaticType comparable = type("java.lang.Comparable");
    StaticType serializable = type("java.io.Serializable");
    StaticType collection = type("java.util.Collection");
    StaticType objectArray = type("java.lang.Object", 1);

    ImmutableMap<String, String> primitiveToWrapper =
        ImmutableMap.<String, String>builder()
        .put("byte", "java.lang.Byte")
        .put("char", "java.lang.Character")
        .put("short", "java.lang.Short")
        .put("int", "java.lang.Integer")
        .put("long", "java.lang.Long")
        .put("float", "java.lang.Float")
        .put("double", "java.lang.Double")
        .build();

    for (StaticType.NumericType t : new StaticType.NumericType[] {
        StaticType.T_BYTE, StaticType.T_SHORT,
        StaticType.T_CHAR, StaticType.T_INT, StaticType.T_LONG,
        StaticType.T_FLOAT, StaticType.T_DOUBLE,
    }) {
      assertCasts(Cast.SAME, t, t, Cast.SAME);
      assertCasts(
          Cast.BOX,
          t,
          type(primitiveToWrapper.get(t.name)),
          Cast.UNBOX);

      assertCasts(
          // char is an integral type, but Character does not extend Number.
          t == StaticType.T_CHAR ? Cast.DISJOINT : Cast.BOX,
          t,
          number,
          Cast.DISJOINT);

      assertCasts(
          Cast.BOX,
          t,
          object,
          Cast.DISJOINT);

      assertCasts(
          Cast.BOX,
          t,
          serializable,
          Cast.DISJOINT);

      assertCasts(
          Cast.BOX,
          t,
          comparable,
          Cast.DISJOINT);

      assertCasts(
          Cast.DISJOINT,
          t,
          objectArray,
          Cast.DISJOINT);

      assertCasts(
          Cast.DISJOINT,
          t,
          collection,
          Cast.DISJOINT);

      // TODO: non-raw comparable
    }
  }

  @Test
  public void testPooling() {
    assertSame(type("java.util.Date"), type("java.util.Date"));
    assertSame(type("java.util.Date", 1), type("java.util.Date", 1));
    assertSame(StaticType.T_INT, type("java.lang.Integer#TYPE"));
  }

  @Test
  public void testGenericWithNonGenericSubclass() {
    StaticType optional = type("java.util.Optional");
    StaticType optionalString = type(
        "java.util.Optional", is("java.lang.String"));
    StaticType optional2String = type(
        "java.util.Optional",
        new TypeBinding(optionalString.typeSpecification));
    StaticType optionalEString = type(
        "java.util.Optional", ext("java.lang.String"));
    StaticType optionalSString = type(
        "java.util.Optional", sup("java.lang.String"));
    StaticType optionalCharSequence = type(
        "java.util.Optional", is("java.lang.CharSequence"));
    StaticType optionalECharSequence = type(
        "java.util.Optional", ext("java.lang.CharSequence"));
    StaticType optionalE = type(
        "java.util.Optional", ext("java.lang.Object"));

    assertCasts(
        Cast.DISJOINT,
        type("java.lang.String"),
        optional,
        Cast.DISJOINT);
    assertCasts(
        Cast.DISJOINT,
        type("java.lang.String"),
        optionalString,
        Cast.DISJOINT);
    assertCasts(
        Cast.DISJOINT,
        optionalString,
        optional2String,
        Cast.DISJOINT);
    assertCasts(
        Cast.DISJOINT,
        optionalString,
        optionalCharSequence,
        Cast.DISJOINT);
    assertCasts(
        Cast.CONFIRM_SAFE,
        optionalString,
        optionalECharSequence,
        Cast.CONFIRM_UNCHECKED);
    assertCasts(
        Cast.CONFIRM_SAFE,
        optionalCharSequence,
        optionalSString,
        Cast.CONFIRM_UNCHECKED);
    assertCasts(
        Cast.CONFIRM_SAFE,
        optionalString,
        optionalSString,
        Cast.CONFIRM_UNCHECKED);
    assertCasts(
        Cast.CONFIRM_SAFE,
        optionalCharSequence,
        optionalECharSequence,
        Cast.CONFIRM_UNCHECKED);
    assertCasts(
        Cast.CONFIRM_UNCHECKED,
        optionalSString,
        optionalEString,
        Cast.CONFIRM_UNCHECKED);
    assertCasts(
        Cast.CONFIRM_UNCHECKED,
        optionalE,
        optionalSString,
        Cast.CONFIRM_SAFE);
    assertCasts(
        Cast.CONFIRM_UNCHECKED,
        optionalECharSequence,
        optionalSString,
        Cast.CONFIRM_UNCHECKED);
  }

  @Test
  public void testMultipleParameters() {
    StaticType mapStrInt = type(
        "java.util.Map", is("java.lang.String"), is("java.lang.Integer"));
    StaticType mapGeneric = type(
        "java.util.Map", ext("java.lang.Object"), ext("java.lang.Object"));
    StaticType mapAnyNumInt = type(
        "java.util.Map", ext("java.lang.Number"), is("java.lang.Integer"));
    StaticType mapStrAnyCharSequence = type(
        "java.util.Map", is("java.lang.String"), ext("java.lang.CharSequence"));

    assertCasts(
        Cast.CONFIRM_SAFE,
        mapStrInt,
        mapGeneric,
        Cast.CONFIRM_UNCHECKED);
    assertCasts(
        Cast.DISJOINT,
        mapStrInt,
        mapAnyNumInt,
        Cast.DISJOINT);
    assertCasts(
        Cast.DISJOINT,
        mapStrInt,
        mapStrAnyCharSequence,
        Cast.DISJOINT);
  }

  @Test
  public void testGenericArrayElements() {
    StaticType arrayOfGenericLists = type(
        "java.util.List", 1, is("java.lang.String"));
    StaticType arrayOfRawLists = type("java.util.List", 1);
    assertCasts(
        Cast.CONFIRM_SAFE,
        arrayOfGenericLists,
        arrayOfRawLists,
        Cast.CONFIRM_UNCHECKED);
  }

  @Test
  public void testTwoRaw() {
    assertCasts(
        Cast.CONFIRM_CHECKED,
        type("java.lang.Iterable"),
        type("java.util.Collection"),
        Cast.CONFIRM_SAFE);
  }

  @Test
  public void testNullType() {
    StaticType bool = type("java.lang.Boolean");
    StaticType obj = type("java.lang.Object");
    assertCasts(
        Cast.SAME,
        pool.T_NULL,
        pool.T_NULL,
        Cast.SAME);
    assertCasts(
        Cast.CONFIRM_UNCHECKED,
        StaticType.ERROR_TYPE,
        pool.T_NULL,
        Cast.CONFIRM_UNCHECKED);
    assertCasts(
        Cast.DISJOINT,
        StaticType.T_BOOLEAN,
        pool.T_NULL,
        Cast.DISJOINT);
    assertCasts(
        Cast.DISJOINT,
        StaticType.T_VOID,
        pool.T_NULL,
        Cast.DISJOINT);
    assertCasts(
        Cast.DISJOINT,
        StaticType.T_INT,
        pool.T_NULL,
        Cast.DISJOINT);
    assertCasts(
        Cast.CONFIRM_CHECKED,
        bool,
        pool.T_NULL,
        Cast.CONFIRM_SAFE);
    assertCasts(
        Cast.CONFIRM_CHECKED,
        obj,
        pool.T_NULL,
        Cast.CONFIRM_SAFE);
  }


  /** An outer class that assists testing multiply-parameterized types. */
  public static final class Outer<O> {
    /** An inner class that inherits its outer types parameters. */
    public final class Inner<I> {
      O o;
      I i;
    }
  }


  private static TypeSpecification outerInner(TypeBinding o, TypeBinding i) {
    TypeSpecification testClass = spec(StaticTypeTest.class.getName());
    TypeSpecification outer = new TypeSpecification(
        testClass, "Outer", Name.Type.CLASS,
        o != null ? ImmutableList.of(o) : ImmutableList.of(),
        0);
    TypeSpecification inner = new TypeSpecification(
        outer, "Inner", Name.Type.CLASS,
        i != null ? ImmutableList.of(i) : ImmutableList.of(),
        0);
    return inner;
  }

  @Test
  public final void testInnerTypes() {
    StaticType fooIntBarStr = pool.type(
        outerInner(is("java.lang.Integer"), is("java.lang.String")),
        null, logger);
    StaticType fooNumBarStr = pool.type(
        outerInner(ext("java.lang.Number"), is("java.lang.String")),
        null, logger);
    assertCasts(
        Cast.CONFIRM_SAFE,
        fooIntBarStr,
        fooNumBarStr,
        Cast.CONFIRM_UNCHECKED);

    StaticType fooNumBarNum = pool.type(
        outerInner(ext("java.lang.Number"), is("java.lang.Number")),
        null, logger);
    assertCasts(
        Cast.DISJOINT,
        fooNumBarNum,
        fooNumBarStr,
        Cast.DISJOINT);

    StaticType fooBarNum = pool.type(
        outerInner(null, is("java.lang.Number")),
        null, logger);
    assertCasts(
        Cast.CONFIRM_SAFE,
        fooNumBarNum,
        fooBarNum,
        Cast.CONFIRM_UNCHECKED);
  }

  @Test
  public final void testSuperTypeWithRawName() {
    ClassOrInterfaceType arrayListString = (ClassOrInterfaceType)
        type("java.util.ArrayList", is("java.lang.String"));
    assertEquals(
        Optional.of(type("java.util.List", is("java.lang.String"))),
        arrayListString.superTypeWithRawName(spec("java.util.List").rawName));
    assertEquals(
        Optional.of(type("java.util.Collection", is("java.lang.String"))),
        arrayListString.superTypeWithRawName(spec("java.util.Collection").rawName));
    assertEquals(
        Optional.of(type("java.lang.Iterable", is("java.lang.String"))),
        arrayListString.superTypeWithRawName(spec("java.lang.Iterable").rawName));
    assertEquals(
        Optional.of(type("java.io.Serializable")),
        arrayListString.superTypeWithRawName(spec("java.io.Serializable").rawName));
    assertEquals(
        Optional.of(type("java.lang.Object")),
        arrayListString.superTypeWithRawName(spec("java.lang.Object").rawName));
  }


  /**
   * @param bToA The cast kind needed for code like
   *     {@code B b = ...; A a = (CAST) b;}.
   * @param aToB The cast kind needed for code like
   *     {@code A a = ...; B b = (CAST) a;}.
   */
  private static void assertCasts(
      Cast aToB, StaticType a, StaticType b, Cast bToA) {
    String aStr = a.toString();
    String bStr = b.toString();
    assertEquals(
        aStr + " a = ...; " + bStr + " b = (CAST) a;",
        aToB, b.assignableFrom(a));
    assertEquals(
        bStr + " b = ...; " + aStr + " a = (CAST) b;",
        bToA, a.assignableFrom(b));
  }
}
