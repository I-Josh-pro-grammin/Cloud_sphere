# Testing

A test calls your code and checks a result you promised. The aim is evidence: the business rule is right, the pieces work together, and the critical journey still completes. A high coverage number is not that evidence.

You will write three levels. The level is defined by the boundary the test crosses, not by the annotation on the class. `@SpringBootTest` loads Spring. It is an integration test until the test drives a running application through its real external interface, and a browser test until it drives the React app.

| | Unit | Integration | End to end |
|---|---|---|---|
| Proves | One rule | Components across a boundary | One business journey |
| Spring | Absent | A slice, or the full context | The process you would ship, or the browser |
| Database | None, or a mock | PostgreSQL 16, the engine this app ships with | The same, plus the shop UI |
| Speed | Seconds for the whole unit set | A container start, then HTTP | Slowest |
| Failure looks like | Wrong total, wrong exception | SQL, JSON names, security, a rolled-back commit | Proxy, token storage, a route guard |

Put each behaviour at the lowest level that can go red for that bug. Add a wider test only where the boundary itself can be wrong.

Companion for the Mockito service shape (save, list, find, delete): [Unit Testing Using Spring Boot, JUnit and Mockito](https://dev.to/olymahmud/unit-testing-using-spring-boot-junit-and-mockito-2l0b). Do the calculator below first, then recognise the same `when` and `verify` in that article.

---

## 1. What to test at each level

**Unit.** Calculations, validation, state transitions, edge values, expected exceptions. `Pricing`, `OrderStateMachine`, `WordCounter`.

**Integration.** Flyway against PostgreSQL, HTTP status and JSON, Spring Security, Redis blacklist, a transaction that must commit. `AuthAndCatalogTests`, `StorefrontFlowTests`.

**End to end.** The few journeys a customer actually completes: sign in, add a published product, read the cart, pay, and the guest who is sent from `/wishlist` to `/login`. This repository has no browser suite yet. Section 8 says what that suite must do, and what it still will not see.

Every snippet below is Arrange, then Act, then Assert. `assertEquals` and AssertJ’s `assertThat` both take the expected value as the thing you compare the actual result to. JUnit’s `assertEquals(expected, actual)` puts expected first. AssertJ reads left to right: `assertThat(actual).isEqualTo(expected)`.

---

## 2. Dependencies, names, and how to run one level

`pom.xml` already depends on the Boot 4 test starters `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`, and `spring-boot-starter-actuator-test`, plus `spring-boot-testcontainers`, Testcontainers `junit-jupiter`, and Testcontainers `postgresql` (1.21.3). Those bring:

| Library | You use it for |
|---|---|
| JUnit 5 | `@Test`, `@Tag`, `assertEquals`, `assertThrows` |
| Mockito | `@Mock`, `when`, `verify` |
| AssertJ | `assertThat` |
| MockMvc | HTTP against the Spring context, no listen port |
| Spring Test | `@SpringBootTest` and the slice annotations |
| Testcontainers | Disposable PostgreSQL and Redis |

A unit test of `Calculator` needs only JUnit. Surefire runs classes named `*Test`, `*Tests`, or `*TestCase`. A class named `CalculatorIT` is compiled and skipped. This project does not use the Failsafe `*IT` split. The split is a JUnit tag.

`@Tag` marks the whole class. Surefire reads `-Dgroups` and runs only the matching tag. An untagged method in a tagged class still carries the class tag.

| Tag | Classes |
|---|---|
| `unit` | `PricingTest`, `OrderStateMachineTest`, `DiscountCodeTest`, `AuthPolicyTest`, `ProductFlagsTest`, `CartTotalsTest`, `WishlistItemIdTest`, `RestExceptionHandlerTest`, `DataInitializerTest`, `SecurityComponentTest` |
| `integration` | `CloudsphereApplicationTests`, `AuthAndCatalogTests`, `StorefrontFlowTests`, `AdminControllerTests` |
| `e2e` | `AdminJourneyTests` |

```bash
./mvnw -q -Dgroups=unit test
./mvnw -q -Dgroups=integration test
./mvnw -q -Dgroups=e2e test
./mvnw -q -Dtest=PricingTest test
./mvnw verify
```

`test` runs the selected tests and writes a JaCoCo report. `verify` runs every tag and fails the build under 85% line coverage. A unit-only run is not supposed to pass that gate: most of the controllers never executed. Docker must be running for `integration` and for `verify`. Section 6 explains why, and what a stopped daemon hides.

GitLab runs these same commands. Section 10.

---

## 3. Unit test of one method

### 3.1 The two classes

```java
public final class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("divisor must not be zero");
        }
        return a / b;
    }
}
```

```java
public final class WordCounter {

    /** How many words are in text. Blank input counts as zero. */
    public int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
```

`Pricing.calculateTotal` is the same kind of method: numbers in, a number out, no Spring.

### 3.2 Annotations on a unit test

| Annotation | Effect |
|---|---|
| `@Test` | JUnit runs this method. A method without it is invisible to the runner. |
| `@Tag("unit")` | Puts the class in the `unit` group. `-Dgroups=unit` selects it. `-Dgroups=integration` skips it. |
| `@ExtendWith(MockitoExtension.class)` | Used only when the test has mocks. Section 4. Absent on `CalculatorTest`. |

`@Test` does not start Spring, open a port, or roll back a transaction. It calls the method.

### 3.3 How to name the method

The name is the sentence a failure will print. Write it in three parts, separated by underscores:

```text
subject _ condition _ outcome
```

| Part | What it names | Example |
|---|---|---|
| subject | The method, route, or type under test | `add`, `count`, `calculateTotal`, `login`, `health` |
| condition | The input or state that matters | `twoAndThree`, `byZero`, `fiveItems`, `wrongPassword` |
| outcome | The result you will assert | `returnsFive`, `throwsIllegalArgument`, `hidesVersion`, `locksTheAccount` |

`add_twoAndThree_returnsFive` reads as: add, given two and three, returns five. You can check that sentence without opening the body.

Use camelCase inside each part. Do not prefix the method with `test`. JUnit 5 does not need it.

One outcome per method. A name that needs “and” to join two results is two methods: `login_validPassword_returnsToken` and `login_wrongPassword_returnsGenericDetail`. Two inputs that share one result can stay together when the name says so: `count_blankOrNull_returnsZero`.

The same three parts are used for a unit test, an integration test, and an end-to-end test. `health_anyCaller_hidesVersion` is integration because of its tag and its Spring context, not because the name looks different.

| Too thin to keep | Why it fails the sentence | Name to use |
|---|---|---|
| `test1` | No subject, no condition, no result | `add_twoAndThree_returnsFive` |
| `addTest` | No condition and no result | `add_zero_returnsTheOtherNumber` |
| `divide_doesNotRoundUp` | No input, so the failure does not say which call | `divide_fiveByTwo_returnsTwo` |
| `contextLoads` | Does not say what started | `context_withPostgresAndRedis_starts` |
| `rejectsDeliveredToPlaced` | Does not name the method | `assertTransition_deliveredToPlaced_throws` |

### 3.4 First test

One sentence, and it is the method name: adding 2 and 3 returns 5. A sentence that needs “and also” is two tests.

```java
@Tag("unit")
class CalculatorTest {

    @Test
    void add_twoAndThree_returnsFive() {
        // Arrange
        Calculator calculator = new Calculator();

        // Act
        int sum = calculator.add(2, 3);

        // Assert
        assertEquals(5, sum);
    }
}
```

Run it. Green means this example matches. Add the neighbours as their own methods.

```java
@Test
void add_zero_returnsTheOtherNumber() {
    // Arrange
    Calculator calculator = new Calculator();

    // Act
    int withRightZero = calculator.add(7, 0);
    int withLeftZero = calculator.add(0, 7);

    // Assert
    assertEquals(7, withRightZero);
    assertEquals(7, withLeftZero);
}

@Test
void add_negatives_returnsTheArithmeticSum() {
    // Arrange
    Calculator calculator = new Calculator();

    // Act
    int bothNegative = calculator.add(-2, -3);
    int mixed = calculator.add(-2, 3);

    // Assert
    assertEquals(-5, bothNegative);
    assertEquals(1, mixed);
}
```

### 3.5 Word count

A field on the test class is still Arrange. JUnit builds a new instance of the test class for every method, so tests do not share a mutated counter.

```java
@Tag("unit")
class WordCounterTest {

    private final WordCounter counter = new WordCounter();

    @Test
    void count_twoWords_returnsTwo() {
        // Arrange
        String text = "hello world";

        // Act
        int n = counter.count(text);

        // Assert
        assertEquals(2, n);
    }
}
```

### 3.6 Edge cases

An edge sits on the line where the result changes. Test both sides, and the line.

| Input | Why it is the edge | Expected |
|---|---|---|
| `"hello"` | smallest text that has a word | 1 |
| `"hello world"` | the first space | 2 |
| `"  hello   world  "` | extra spaces must not become words | 2 |
| `""` | empty | 0 |
| `"   "` | blank | 0 |
| `null` | no string | 0 |

```java
@Test
void count_blankOrNull_returnsZero() {
    // Arrange
    WordCounter counter = new WordCounter();

    // Act
    int fromNull = counter.count(null);
    int fromEmpty = counter.count("");
    int fromBlank = counter.count("   ");

    // Assert
    assertEquals(0, fromNull);
    assertEquals(0, fromEmpty);
    assertEquals(0, fromBlank);
}

@Test
void count_extraSpaces_returnsTwo() {
    // Arrange
    WordCounter counter = new WordCounter();

    // Act
    int n = counter.count("  hello   world  ");

    // Assert
    assertEquals(2, n);
}
```

`null` and double spaces fail for different bugs. A method that only calls `text.isEmpty()` throws on `null` and counts the gaps.

Integer division truncates. `5 / 2` is `2`.

```java
@Test
void divide_fiveByTwo_returnsTwo() {
    // Arrange
    Calculator calculator = new Calculator();

    // Act
    int quotient = calculator.divide(5, 2);

    // Assert
    assertEquals(2, quotient);
}
```

`Pricing` changes at quantity 5 and at 50_000 RWF.

| Input | Expected |
|---|---|
| 4 items at 100 | 400 |
| 5 items at 100 | 450 |
| standard shipping on 49_999 | 2_000 |
| standard shipping on 50_000 | 0 |
| gold shipping on 30_000 | 0 |

Those rows are methods on `PricingTest`, which is tagged `unit`. The file uses a one-line assert. Read it as the three blocks:

```java
@Test
void calculateTotal_fiveItems_appliesTenPercent() {
    // Arrange: quantity 5, unit price 100
    // Act
    long total = Pricing.calculateTotal(5, 100);
    // Assert: 5 * 100 * 90 / 100
    assertEquals(450, total);
}
```

`DiscountCode.isUsable` changes at a timestamp. `DiscountCodeTest` fixes the clock to `Instant.parse("2026-06-01T00:00:00Z")`. `validFrom` one second after that instant is not usable. `validUntil` equal to that instant still is. One second is the edge. `Thread.sleep` is not a clock.

### 3.7 Negative paths

A negative path is an input the method must refuse. Assert the exception. Keep the successful call in another method.

```java
@Test
void divide_byZero_throwsIllegalArgument() {
    // Arrange
    Calculator calculator = new Calculator();

    // Act
    IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> calculator.divide(10, 0));

    // Assert
    assertEquals("divisor must not be zero", ex.getMessage());
}

@Test
void divide_tenByTwo_returnsFive() {
    // Arrange
    Calculator calculator = new Calculator();

    // Act
    int quotient = calculator.divide(10, 2);

    // Assert
    assertEquals(5, quotient);
}
```

`assertThrows` fails when the method returns normally. Check the message when the message is part of the promise.

| Refusal | Assert | Where |
|---|---|---|
| Quantity or price below 0 | `IllegalArgumentException` | `PricingTest.calculateTotal_negativeQuantity_throws` |
| Code `null`, blank, length 4, length 11, or `SAVE-25!` | `ApiException` | `PricingTest.assertCodeFormat_wrongShape_throws` |
| `DELIVERED → PLACED` | `ApiException` | `OrderStateMachineTest.assertTransition_deliveredToPlaced_throws` |
| Wrong password | HTTP 401, detail `email or password is incorrect` | `AuthAndCatalogTests.login_wrongPassword_returnsGenericDetail` |
| Draft product | HTTP 404 | `AuthAndCatalogTests`, `StorefrontFlowTests` |

Both login failures share one detail so the API does not reveal which emails exist. Assert that detail.

### 3.8 Assert and verify

An assert and a verify both sit in the Assert block, and they check different facts.

An **assert** looks at a value you are holding after the call: the number that came back, the message on an exception, a status code. It is a JUnit method (`assertEquals`, `assertThrows`, `assertTrue`). The question is: what did this method produce?

A **verify** looks at a collaborator: which of its methods ran, with which arguments, and how many times. It is a Mockito method (`verify`, `verifyNoInteractions`). The question is: whom did this method talk to?

`Calculator.add` and `WordCounter.count` only produce a value. They talk to nobody. The test asserts, and there is nothing to verify.

```java
@Test
void add_twoAndThree_returnsFive() {
    // Arrange
    Calculator calculator = new Calculator();

    // Act
    int sum = calculator.add(2, 3);

    // Assert: the value that came back
    assertEquals(5, sum);
}

@Test
void count_twoWords_returnsTwo() {
    // Arrange
    WordCounter counter = new WordCounter();

    // Act
    int n = counter.count("hello world");

    // Assert
    assertEquals(2, n);
}
```

`./mvnw verify` is a Maven phase. It runs the tests and the coverage gate. It is not Mockito’s `verify`. The two words sit next to each other in this guide and mean different tools.

#### A method that returns a value and talks to someone

`countAndStore` does two things. It counts words, and it saves that count when the count is not zero.

```java
public class CountService {
    private final WordCounter counter;
    private final CountStore store;

    public CountService(WordCounter counter, CountStore store) {
        this.counter = counter;
        this.store = store;
    }

    public int countAndStore(String text) {
        int n = counter.count(text);
        if (n == 0) {
            return 0;
        }
        store.save(text, n);
        return n;
    }
}
```

Two promises, two checks. `assertEquals` holds the promise about the number. `verify` holds the promise about the save.

```java
@Test
void countAndStore_twoWords_returnsTwoAndSaves() {
    // Arrange
    when(counter.count("hello world")).thenReturn(2);
    CountService service = new CountService(counter, store);

    // Act
    int n = service.countAndStore("hello world");

    // Assert the value
    assertEquals(2, n);
    // Assert the conversation with the store
    verify(store, times(1)).save("hello world", 2);
}
```

Either check can fail while the other stays green. That is why both are there.

| Bug inside `countAndStore` | `assertEquals(2, n)` | `verify(store).save("hello world", 2)` |
|---|---|---|
| Returns 2, and never calls `save` | Green | Red. The store was not called. |
| Calls `save("hello world", 2)`, and returns 99 | Red. The number is wrong. | Green. The save was right. |
| Calls `save("hello world", 0)`, and returns 2 | Green | Red. The saved number is wrong. |

A test that only verifies the call will accept a method that saves the right pair and returns nonsense. A test that only asserts the return value will accept a method that counts correctly and forgets to save. Write the check that matches the promise. Write both when both are promises.

`times(1)` is Mockito’s default. Write it when “once” is the rule you care about. `never()` is the check that a call was required not to happen:

```java
@Test
void countAndStore_blank_doesNotSave() {
    // Arrange
    when(counter.count("   ")).thenReturn(0);
    CountService service = new CountService(counter, store);

    // Act
    int n = service.countAndStore("   ");

    // Assert
    assertEquals(0, n);
    verify(store, never()).save(anyString(), anyInt());
}
```

The assert says the count is zero. The verify says the blank input never became a row. `verifyNoInteractions(store)` is stronger: no method on `store` ran at all, not only `save`.

`when(...).thenReturn(...)` is Arrange. It scripts the collaborator before the act. `verify` is after the act. Swapping them checks a script you wrote, not what the method did.

#### In this codebase

`Pricing.calculateTotal` and `OrderStateMachine.assertTransition` return a number or throw. `PricingTest` and `OrderStateMachineTest` use `assertEquals` and `assertThrows` only. A `verify` there would have no collaborator to point at.

`CalculationService.recordAdd` in section 4 is `countAndStore`: it returns the saved calculation and it calls `repository.save`. The test asserts the id and the sum, then verifies `save` and `add` ran once.

`DataInitializer.run` returns void. With seed disabled, the promise is that no repository is touched. `DataInitializerTest.run_seedDisabled_touchesNothing` has no value to `assertEquals`. The whole assert is `verifyNoInteractions`.

`TokenBlacklist.revoke` returns void and writes to Redis only for a live token. `SecurityComponentTest.revoke_byExpiry_writesOnlyWhileLive` uses `verify(..., never())` for an expiry in the past and `verify(...).set(...)` for an expiry a minute ahead.

### 3.9 Static methods, private methods, the clock

Most methods look like `add` and `count`. You construct an object, pass plain values, and assert the return value. A test gets harder when the behaviour is still a plain rule, but the way you **reach** it is awkward. Three situations cause that:

1. The method belongs to the class, not to an object (static).
2. The method is hidden from other classes (private).
3. The method depends on the current time (the clock).

They are handled differently because the obstacle is different. A static method is still a direct call: there is no object to build and nothing to substitute. A private method is an implementation detail: the promise a caller can see is the public method, so that is what the test calls. The clock is an input. When the method accepts the time, the test supplies it, the same way it supplies `2` and `3`. When the method reads `Instant.now()` itself, the test cannot supply the time, so it stays several seconds away from the present instant.

The assert does not change. Arrange, Act, and Assert stay in that order. Only the call changes.

| Situation | Why it is awkward | How we handle the test |
|---|---|---|
| Public static | There is no instance. A mock would replace the rule. | Call `ClassName.method(arguments)`. |
| Package-private static | Same call, but only classes in that package may make it. | Put the test in the same package and call the method. |
| Private | No other class, including a test, is allowed to call it. | Call the public method that uses it. |
| Time passed in | Time is an argument. | Pass a fixed `Instant`. |
| Time read inside | The method asks the system clock. | Pick instants seconds away from the real now. |

Learn each row on a calculator or a word counter first. The Cloud Sphere methods at the end of each part are the same row.

#### Static methods

A static method does not use `this`. It takes arguments and returns a result, or it throws. Building an object adds nothing, and mocking the method replaces the arithmetic with a canned number.

Make `add` and `divide` static. The private constructor means there is nothing to `new`.

```java
public final class Calculator {
    private Calculator() {}

    public static int add(int a, int b) {
        return a + b;
    }

    public static int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("divisor must not be zero");
        }
        return a / b;
    }
}
```

The test calls the class. Arrange is the arguments. There is no object.

```java
@Test
void add_twoAndThree_returnsFive() {
    // Arrange: the inputs are the arguments
    // Act
    int sum = Calculator.add(2, 3);
    // Assert
    assertEquals(5, sum);
}

@Test
void divide_byZero_throwsIllegalArgument() {
    // Arrange
    int dividend = 10;
    int divisor = 0;

    // Act
    IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> Calculator.divide(dividend, divisor));

    // Assert
    assertEquals("divisor must not be zero", ex.getMessage());
}
```

A library can stub a static method by rewriting bytecode. Use that only for a static call you do not own and cannot change. `Calculator.add` is ours. A different total is a different pair of arguments, not a stub.

**Package-private** is the same kind of call, with a smaller audience. Drop `public` and only classes in the same package can see the method. The test file goes in that package. Widening the method to `public` only so a distant test can see it publishes a helper callers were never offered.

```java
final class Calculator {
    private Calculator() {}

    static int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("divisor must not be zero");
        }
        return a / b;
    }
}
```

`CalculatorTest` in the same package still calls `Calculator.divide(10, 0)`. A test in another package does not compile against that method.

**In this codebase.** `Pricing` and `OrderStateMachine` are `final`, with a private constructor and public static methods. `PricingTest` and `OrderStateMachineTest` call them.

```java
long total = Pricing.calculateTotal(5, 100);          // 450
OrderStateMachine.assertTransition(
        OrderStatus.DELIVERED, OrderStatus.PLACED);   // ApiException, HTTP 409
```

`AuthService.assertPasswordPolicy` is static and package-private. `AuthPolicyTest` is in `rw.ac.rca.cloudsphere.identity`, so this call is legal:

```java
AuthService.assertPasswordPolicy("abcdefgh");         // ApiException, HTTP 400
```

`assertPasswordPolicy_lettersOnly_throws` is that test. A letters-only password is the condition. The exception is the outcome.

#### Private methods

A private method exists so the public method can stay short. Callers, and tests, are promised the public method. The private name can be renamed or inlined without changing that promise.

`count` can hide its cleanup:

```java
public final class WordCounter {
    public int count(String text) {
        String normalised = normalise(text);
        if (normalised.isEmpty()) {
            return 0;
        }
        return normalised.split("\\s+").length;
    }

    private String normalise(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }
}
```

The tests name `count`, not `normalise`.

```java
@Test
void count_null_returnsZero() {
    // Arrange
    WordCounter counter = new WordCounter();

    // Act
    int n = counter.count(null);

    // Assert
    assertEquals(0, n);
}

@Test
void count_extraSpaces_returnsTwo() {
    // Arrange
    WordCounter counter = new WordCounter();

    // Act
    int n = counter.count("  hello   world  ");

    // Assert
    assertEquals(2, n);
}
```

`count_null_returnsZero` fails if `normalise` returns `null`, because `isEmpty` then throws. `count_extraSpaces_returnsTwo` fails if `normalise` forgets `trim`, because the extra spaces become empty words. Both bugs are in the private method. Both tests talk about words.

Reflection can call `normalise` by name. That test goes red when the method is renamed, even if `count` still returns 0 and 2. The behaviour held. The test was tied to a name callers cannot see.

**In this codebase.** `OrderService.decrement` is private. Checkout is the public behaviour. `StorefrontFlowTests` pays for an order and then reads the product’s stock. A reflection test of `decrement` would not show that checkout called it.

`AuthService.assertPasswordPolicy` sits one step above private: it is package-private, so `AuthPolicyTest` may call the rule directly. `register` is the public method that uses it (`assertPasswordPolicy(request.password())`). The two tests answer two questions.

| Test | Calls | A failure means |
|---|---|---|
| `assertPasswordPolicy_lettersOnly_throws` | The package-private rule | The rule is wrong. |
| `POST /api/v1/auth/register` with password `abcdefgh` | `register`, which calls the rule | HTTP, validation, or `register` dropped the call. |

There is no third test that reflects into a private helper.

#### The clock

Time is an input, like a divisor. The handling depends on whether the method **receives** the time or **asks** for it.

**The method receives the time.** The test picks an instant and the result does not depend on the minute the suite runs. One second is a real edge.

```java
public final class Deadline {
    public static boolean isOverdue(Instant due, Instant now) {
        return now.isAfter(due);
    }
}
```

```java
private final Instant now = Instant.parse("2026-06-01T00:00:00Z");

@Test
void isOverdue_dueOneSecondAhead_returnsFalse() {
    // Arrange
    Instant due = now.plusSeconds(1);

    // Act
    boolean overdue = Deadline.isOverdue(due, now);

    // Assert
    assertFalse(overdue);
}

@Test
void isOverdue_dueExactlyNow_returnsFalse() {
    // Arrange
    Instant due = now;

    // Act
    boolean overdue = Deadline.isOverdue(due, now);

    // Assert
    assertFalse(overdue);
}

@Test
void isOverdue_dueOneSecondAgo_returnsTrue() {
    // Arrange
    Instant due = now.minusSeconds(1);

    // Act
    boolean overdue = Deadline.isOverdue(due, now);

    // Assert
    assertTrue(overdue);
}
```

`isAfter` makes “exactly now” still on time. That is the same kind of edge as `validUntil` equal to `now` on a discount code. `Thread.sleep` until a timestamp passes is a poor substitute: a slow machine steps over the instant, and the test no longer hits the edge you wrote.

**The method asks the clock.** `stillOpen` reads `Instant.now()` itself. The test cannot pass `now` in. It chooses the deadline relative to the real clock, with a gap a slow run cannot cross.

```java
public final class Counter {
    public static boolean stillOpen(Instant closesAt) {
        return Instant.now().isBefore(closesAt);
    }
}
```

```java
@Test
void stillOpen_closedFiveSecondsAgo_returnsFalse() {
    // Arrange
    Instant closesAt = Instant.now().minusSeconds(5);

    // Act
    boolean open = Counter.stillOpen(closesAt);

    // Assert
    assertFalse(open);
}

@Test
void stillOpen_closesInSixtySeconds_returnsTrue() {
    // Arrange
    Instant closesAt = Instant.now().plusSeconds(60);

    // Act
    boolean open = Counter.stillOpen(closesAt);

    // Assert
    assertTrue(open);
}
```

A gap of one millisecond can expire between Arrange and Act. The test then flips with no code change. Five seconds and sixty seconds stay on the intended side of now.

**In this codebase.** `DiscountCode.isUsable(Instant now, int qty)` and `User.isLocked(Instant now)` take the time. `DiscountCodeTest` fixes `Instant.parse("2026-06-01T00:00:00Z")`. A `validFrom` one second later is not usable. A `validUntil` equal to that instant still is. `AuthPolicyTest.isLocked_untilInTheFuture_returnsTrue` sets `lockedUntil` thirty seconds after its own fixed instant.

`TokenBlacklist.revoke` asks the clock itself:

```java
Duration ttl = Duration.between(Instant.now(), expiresAt);
if (ttl.isNegative() || ttl.isZero()) {
    return;
}
```

`SecurityComponentTest.revoke_byExpiry_writesOnlyWhileLive` passes an expiry five seconds ago and asserts Redis is not written, then an expiry sixty seconds ahead and asserts a TTL is stored. That is `stillOpen`, with Redis as the outcome.

`AuthService.login` also calls `Instant.now()`, then passes that value into `isLocked`. The unit test of `isLocked` keeps the fixed instant. The integration test `login_threeBadPasswords_locksTheAccount` does not wait 30 minutes. It loads the user after the third 401 and asserts `status` is `LOCKED` and `lockedUntil` is set.

`CartService` calls `code.isUsable(Instant.now(), cart.totalQuantity())`. The unit test of the rule still passes its own `Instant`. A cart test that needs a live code gives that code a window wide around today. It does not sleep until the code expires.

---

## 4. Unit test of a service

The service holds the rule. The repository holds rows. Spring Data already implements `save`, `findAll`, `findById`, and `deleteById`. You do not unit-test that interface. You mock it, call the service, and check two things: the value returned, and that the repository method ran once. A mocked repository does not prove a Hibernate mapping, a Flyway script, a unique constraint, or the SQL.

The [todo service tests](https://dev.to/olymahmud/unit-testing-using-spring-boot-junit-and-mockito-2l0b) do this for create, list, find, update, and delete. Here the stored thing is a calculation, so the arithmetic stays visible.

### 4.1 Annotations

| Annotation | Effect |
|---|---|
| `@ExtendWith(MockitoExtension.class)` | Starts Mockito before each test and creates every `@Mock`. Required. `@Mock` does nothing without this extension. |
| `@Mock` | A stand-in. Unstubbed methods return a default (`null`, `0`, `false`, empty collections for some types). |
| `@InjectMocks` | Calls the constructor of the class under test and passes the mocks in. Equivalent to `new CalculationService(repository, calculator)`. |
| `@Tag("unit")` | This test does not start Spring, so it stays in the unit group. |

Cloud Sphere’s `DataInitializerTest` calls the constructor itself because the argument list is long and the test should show it. `@InjectMocks` is the same wiring when the constructor is obvious.

### 4.2 The classes

```java
public class Calculation {
    private final Long id;
    private final int result;

    public Calculation(Long id, int left, int right, int result) {
        this.id = id;
        this.result = result;
    }

    public Long getId() { return id; }
    public int getResult() { return result; }
}

public interface CalculationRepository {
    Calculation save(Calculation calculation);
    List<Calculation> findAll();
    Optional<Calculation> findById(Long id);
    void deleteById(Long id);
}

public class CalculationService {
    private final CalculationRepository repository;
    private final Calculator calculator;

    public CalculationService(CalculationRepository repository, Calculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    public Calculation recordAdd(int left, int right) {
        int result = calculator.add(left, right);
        return repository.save(new Calculation(null, left, right, result));
    }

    public List<Calculation> list() {
        return repository.findAll();
    }

    public Calculation find(Long id) {
        return repository.findById(id).orElse(null);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
```

### 4.3 The five tests

```java
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CalculationServiceTest {

    @Mock
    CalculationRepository repository;

    @Mock
    Calculator calculator;

    @InjectMocks
    CalculationService service;

    @Test
    void recordAdd_twoAndThree_savesFive() {
        // Arrange
        when(calculator.add(2, 3)).thenReturn(5);
        when(repository.save(any(Calculation.class)))
                .thenReturn(new Calculation(1L, 2, 3, 5));

        // Act
        Calculation created = service.recordAdd(2, 3);

        // Assert
        assertEquals(1L, created.getId());
        assertEquals(5, created.getResult());
        verify(repository, times(1)).save(any(Calculation.class));
        verify(calculator, times(1)).add(2, 3);
    }

    @Test
    void list_twoRows_returnsBoth() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of(
                new Calculation(1L, 2, 3, 5),
                new Calculation(2L, 1, 1, 2)));

        // Act
        List<Calculation> rows = service.list();

        // Assert
        assertEquals(2, rows.size());
        verify(repository, times(1)).findAll();
    }

    @Test
    void find_knownId_returnsTheRow() {
        // Arrange
        when(repository.findById(1L)).thenReturn(Optional.of(new Calculation(1L, 2, 3, 5)));

        // Act
        Calculation found = service.find(1L);

        // Assert
        assertEquals(1L, found.getId());
        verify(repository, times(1)).findById(1L);
    }

    @Test
    void find_missingId_returnsNull() {
        // Arrange
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // Act
        Calculation found = service.find(9L);

        // Assert
        assertNull(found);
        verify(repository, times(1)).findById(9L);
    }

    @Test
    void delete_knownId_deletesOnce() {
        // Arrange
        doNothing().when(repository).deleteById(1L);

        // Act
        service.delete(1L);

        // Assert
        verify(repository, times(1)).deleteById(1L);
    }
}
```

| Call | Role |
|---|---|
| `when(...).thenReturn(...)` | Arrange. The next matching call returns this value. |
| `doNothing().when(...)` | Arrange for a `void` method such as `deleteById`. |
| `verify(..., times(1))` | Assert. That method ran once. `times(1)` is the default; write it when “once” is the rule. |
| `verify(..., never())` | Assert. That method was required not to run. |
| `verifyNoInteractions(...)` | Assert. None of the mock’s methods ran. |

Stubbing `calculator.add(2, 3)` to return `5` does not prove addition. `CalculatorTest` proves addition. This test proves that `recordAdd` saves whatever `add` returned.

A counter that stores a tally must not touch the store for a blank string:

```java
@Test
void countAndStore_blank_doesNotSave() {
    // Arrange
    when(counter.count("   ")).thenReturn(0);

    // Act
    int n = service.countAndStore("   ");

    // Assert
    assertEquals(0, n);
    verify(store, never()).save(anyString(), anyInt());
}
```

Mock the repository, Redis, or a gateway. The class under test stays real. A mock of `CalculationService` inside `CalculationServiceTest` checks the mock.

### 4.4 The same shape in Cloud Sphere

`WishlistService.add` loads a product, answers 404 when the id is missing or unpublished, answers 409 when the product is out of stock, and calls `wishlists.save` only when that user/product pair is new. A unit test mocks `WishlistRepository`, `ProductRepository`, and `CartService`, stubs `findById`, and verifies `save` once or `never()`.

`DataInitializerTest` is the finished version of “do not call the collaborator”. It builds `DataInitializer` with mocked repositories and `Seed(false)`, calls `run`, and asserts `verifyNoInteractions` on every repository.

```java
@Test
void run_seedDisabled_touchesNothing() {
    // Arrange: CloudSphereProperties with Seed(false), and a mocked repository for each insert
    DataInitializer initializer = new DataInitializer(
            properties, users, encoder, categories, products, discountCodes);

    // Act
    initializer.run(mock(ApplicationArguments.class));

    // Assert
    verifyNoInteractions(users, encoder, categories, products, discountCodes);
}
```

`SecurityComponentTest.revoke_byExpiry_writesOnlyWhileLive` mocks `StringRedisTemplate`. An expiry in the past is not written. An expiry sixty seconds ahead is written with a TTL.

```java
@Test
void revoke_byExpiry_writesOnlyWhileLive() {
    // Arrange
    when(redis.opsForValue()).thenReturn(values);
    TokenBlacklist blacklist = new TokenBlacklist(redis);

    // Act
    blacklist.revoke("expired", Instant.now().minusSeconds(5));
    blacklist.revoke("now", Instant.now());

    // Assert: an expiry that has already passed is not written
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));

    // Act
    blacklist.revoke("live", Instant.now().plusSeconds(60));

    // Assert: a live token is stored with a TTL
    verify(values).set(eq("cloudsphere:revoked:live"), eq("1"), any(Duration.class));
}
```

Logout in `StorefrontFlowTests` is the integration form: real Redis, then `GET /api/v1/auth/me` returns 401.

---

## 5. Choosing the Spring annotation

Use the smallest context that can expose the bug.

| Annotation | Package on Boot 4 | What starts | Database |
|---|---|---|---|
| `@ExtendWith(MockitoExtension.class)` | `org.mockito.junit.jupiter` | Mockito only | None |
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure` | MVC and one controller | Not used |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` | JPA and a database | Prefer PostgreSQL |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` | Replaces one Spring bean with a mock | Depends on the slice |
| `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` | A `MockMvc` on an existing `@SpringBootTest` | Depends on the test |
| `@SpringBootTest` | `org.springframework.boot.test.context` | The application context | PostgreSQL via Testcontainers in this repo |
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` | same | A real listen port | Same |
| `@Testcontainers` | `org.testcontainers.junit.jupiter` | Docker containers declared with `@Container` | Yes, when the container is a database |

`@Mock` needs no Spring context. `@MockitoBean` is the mock you register when the context is up and one bean must be replaced. `@Autowired` asks Spring for a bean it created. It is not a Mockito annotation. On a class without `@SpringBootTest` or a slice, `@Autowired` is never filled.

### 5.1 Controller slice

`@WebMvcTest(CalcController.class)` starts MVC for that controller only. `@MockitoBean` supplies `Calculator`. The test proves the mapping. It does not prove `a + b`, because the mock returns 9 even if `add` is deleted.

```java
@WebMvcTest(CalcController.class)
@Tag("integration")
class CalcControllerSliceTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    Calculator calculator;

    @Test
    void add_stubbedSum_writesNine() throws Exception {
        // Arrange
        when(calculator.add(2, 3)).thenReturn(9);

        // Act
        var result = mvc.perform(get("/api/calc/add").param("a", "2").param("b", "3"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(9));
    }
}
```

Cloud Sphere’s HTTP tests use `@SpringBootTest`, because security, JWT, and the database are part of the behaviour. Use the slice when the controller mapping is the only question.

### 5.2 Repository slice

`@DataJpaTest` starts JPA, turns on a rollback around each test, and by default tries to replace the datasource with an embedded database. This project’s schema is Flyway SQL for PostgreSQL (`cloud_sphere`). An embedded database will not run that script. `@AutoConfigureTestDatabase(replace = NONE)` keeps the datasource you configured, which section 6 points at PostgreSQL.

`flush()` sends pending SQL. `clear()` drops the persistence context so the next read cannot be satisfied from memory.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class CalculationRepositoryTest {

    @Autowired
    CalculationRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void findById_savedRow_returnsTheResult() {
        // Arrange
        Calculation row = new Calculation(null, 2, 3, 5);
        entityManager.persistAndFlush(row);
        entityManager.clear();

        // Act
        Calculation found = repository.findById(row.getId()).orElseThrow();

        // Assert
        assertEquals(5, found.getResult());
    }
}
```

The main suite does not use this slice. The failures we care about show up when the service, the transaction, and Postgres run together. A new repository test uses the same Postgres container as `AuthAndCatalogTests`, so it executes the Flyway script the application executes.

---

## 6. Testcontainers

Production runs PostgreSQL 16 and Redis 7. The integration tests start those same engines in Docker and throw them away. They do not reuse the database from `docker compose up`, and they do not use H2. H2 accepts different types, different JSON, different sequences, and different locking. A test that passes there can still fail on the script in `src/main/resources/db/migration`.

### 6.1 What actually happens

1. JUnit sees `@Testcontainers` and starts a Testcontainers extension.
2. Every `@Container` field that is `static` starts once, before the first test in that class, and stops after the last test in that class.
3. Docker pulls `postgres:16-alpine` and `redis:7-alpine` if they are not local, creates a container, and waits until the engine accepts connections.
4. Docker publishes each container port on a random free port of the machine. `5432` inside Postgres is not `5432` on your laptop. `getJdbcUrl()` and `getMappedPort(6379)` return the published address.
5. `@DynamicPropertySource` runs before Spring builds the datasource. The JDBC URL does not exist until step 4, so it cannot be written in `application.properties` ahead of time.
6. Spring starts. Flyway applies `V1__init_cloud_sphere.sql` and `V2__product_image.sql` to the empty database. `DataInitializer` inserts the demo users, `USB-C-01`, and `SAVE2026`.
7. The test methods run against that database and that Redis.
8. The extension stops the containers. A second class (`StorefrontFlowTests`, `CloudsphereApplicationTests`) starts its own pair. Three integration classes means three Postgres containers and three Redis containers, one pair at a time per class.

A `@Container` field that is **not** static restarts for every test method. That isolates state more strictly and makes the class several times slower. These tests use `static` and isolate rows themselves: a new email, a new SKU, and no edits to `USB-C-01`, `SAVE2026`, or `demo@cloudsphere.rw`.

### 6.2 The annotations, one by one

| Annotation | Effect |
|---|---|
| `@Testcontainers` | Registers the extension. `disabledWithoutDocker = true` skips the class when the Docker daemon is down, and the build stays green. |
| `@Container` | Marks the field the extension must start. On a `static` field, once per class. |
| `@DynamicPropertySource` | A static method Spring calls while the context is starting. `registry.add(name, supplier)` overrides a property. |
| `@SpringBootTest` | Builds the full application context using those properties. |
| `@AutoConfigureMockMvc` | Adds a `MockMvc` bean. Calls go through filters and controllers in that context. Nothing listens on port 8080. |
| `@Autowired` | Injects that `MockMvc`. |
| `@Tag("integration")` | Excludes the class from `-Dgroups=unit`. |

`disabledWithoutDocker = true` is a trap. A laptop with Docker Desktop stopped, or a CI job with no daemon, skips `AuthAndCatalogTests` and `StorefrontFlowTests`. JaCoCo then measures a much smaller run, and `verify` fails the 85% gate only if `verify` still executes the check against that thin run. If someone runs `test` and glances at “BUILD SUCCESS”, the skipped containers are not a success. Start Docker, then run the integration group. The log line is a skipped container, not a passed assertion.

### 6.3 The code this repository runs

From `AuthAndCatalogTests`:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class AuthAndCatalogTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("cloudsphere")
                    .withUsername("cloudsphere")
                    .withPassword("cloudsphere");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("cloudsphere.jwt.secret", () -> "cloudsphere-test-secret-key-must-be-32b!!");
    }

    @Autowired
    MockMvc mvc;
}
```

`PostgreSQLContainer` waits until Postgres is ready and builds a JDBC URL that includes the random port. `GenericContainer` does not know Redis; `withExposedPorts(6379)` is what publishes it. The JWT secret has to be at least 32 bytes or `JwtService` refuses to start. The supplier form (`POSTGRES::getJdbcUrl`) is required because the URL is read when the context starts, which is after the container has a port.

`StorefrontFlowTests` and `CloudsphereApplicationTests` repeat this block. Each class has its own containers because a `static` container belongs to that class’s lifecycle, not to the JVM.

Spring Boot can also wire a container with `@ServiceConnection` on the `@Container` field, which replaces the datasource lines. This suite does not use it. The registry also sets Redis and the JWT secret, and those are not a JDBC service connection. One `register` method shows every property the context needs.

### 6.4 One integration test, with the three blocks

```java
@Test
void health_anyCaller_hidesVersion() throws Exception {
    // Arrange: containers are up, Flyway has migrated, MockMvc is injected

    // Act
    var response = mvc.perform(get("/api/v1/health"));

    // Assert
    response.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.version").doesNotExist());
}
```

MockMvc does not open a socket. It still runs the security filter chain, the controller, and the exception handlers. That is why a missing `Authorization` header and a bad JWT are integration tests, not unit tests.

### 6.5 Transactions

`@DataJpaTest` rolls each test method back. `@SpringBootTest` in this suite does not. A change committed by a service stays committed for later methods in that class. That is why a test that reduces stock creates its own SKU.

There is a second transaction rule. `@Transactional` on a **test** method rolls back the transaction the test itself opened. It does not roll back work a request committed on another thread. `MockMvc` calls the controller on the test thread, so a service `@Transactional` joins that thread. `RANDOM_PORT` plus `TestRestTemplate` calls a server thread. The test’s transaction and the server’s transaction are different. Assert server-side changes by reading them after the call, through the repository.

`AuthService.login` shows the rule inside one service. The method is `@Transactional`. It increments `failedAttempts` and then throws `ApiException`. Spring rolls a runtime exception back, so the increment used to disappear and the account never locked. `@Transactional(noRollbackFor = ApiException.class)` keeps the save. `StorefrontFlowTests.login_threeBadPasswords_locksTheAccount`:

1. Arrange: register a fresh user.
2. Act: `POST /api/v1/auth/login` three times with a wrong password.
3. Assert: load the user again. `status` is `LOCKED` and `lockedUntil` is set.

The entity you held before the request will not show the commit. Load it again.

### 6.6 What the container does not give you

- It is not the Compose database on port 5432. Tests that pass here can still be pointed at an old volume by a mistaken `application.properties`.
- It does not test production TLS, disk, backups, or a replica.
- Ryuk, the small container that deletes leftovers after a crash, often cannot see the Docker socket the way a GitLab service can. The CI file sets `TESTCONTAINERS_RYUK_DISABLED=true` and relies on the job ending. Locally, leave Ryuk on.
- Image pulls need a network the first time. A later run uses the local image.

---

## 7. Full context, still integration

`@SpringBootTest` without `webEnvironment` is `MOCK`. No port. `MockMvc` performs the request inside the context. `CloudsphereApplicationTests.context_withPostgresAndRedis_starts` only checks that the context starts with the containers and Flyway. It asserts nothing about a price or a status. It catches a broken bean definition. It does not catch a wrong total.

`StorefrontFlowTests` is a longer integration test: register, cart, discount, checkout, payment stub, wishlist, admin. Each `@Test` method is one behaviour. It is not an end-to-end test. The React app never runs.

A real listen port looks like this. Tag it `integration` when the client is HTTP and the browser is absent. Tag it `e2e` only when you have decided the journey includes the UI, and then this Java class is the wrong tool.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class CalcApiTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void getAdd_twoAndThree_returnsFive() {
        // Arrange
        String url = "/api/calc/add?a=2&b=3";

        // Act
        ResponseEntity<Map> response = http.getForEntity(url, Map.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("sum"));
    }
}
```

`RANDOM_PORT` avoids colliding with an API already on 8080. `TestRestTemplate` speaks HTTP to that port. Security must accept the call, or the test must send a bearer token. This repository’s integration tests stay on `MockMvc` because the filter chain is what they need to see, and a second server is extra time.

---

## 8. End to end

An end-to-end test does one customer journey through the interface a person uses.

1. `docker compose up -d`
2. `./mvnw spring-boot:run` on port 8080
3. `cd frontend && npm run dev` on port 5173
4. A browser opens `http://localhost:5173/login`, signs in as `demo@cloudsphere.rw` / `CloudSphere1`, adds a published product, opens `/cart`, checks out, and sees a paid order.

One journey. Quantity 4 and quantity 5 already have unit tests.

Journey worth automating when you add Playwright:

1. Sign in as `demo@cloudsphere.rw`.
2. Add a published product.
3. Open `/cart` and read the line and the RWF total.
4. Check out. The order is paid.
5. As a guest, open `/wishlist`. The app redirects to `/login` (`RequireAuth` in `frontend/src/App.jsx`).

`AdminJourneyTests` is tagged `e2e`. It signs in as `ops@cloudsphere.rw`, publishes a product, sets stock, and ships a paid order through to delivered. The client is `TestRestTemplate` on a random port, so the call enters `AdminController` and `AdminCatalogService` / `OrderService` write the rows. The React app does not start. The browser list above is still the suite to add when the screen itself is the question. The GitLab `e2e` job is manual on a normal pipeline and does not block the coverage gate.

```bash
./mvnw -q -Dgroups=e2e test
```

`AdminJourneyTests` is that class, so the command no longer passes `failIfNoSpecifiedTests=false`. An empty `e2e` group would fail the build instead of passing silently.

Until then, the `curl` smoke in the project README (health, login, catalogue, guest cart) is the manual check. Assert text and the URL. Leave CSS class names alone.

Prioritise, in the browser suite, a completed journey, a rejected anonymous call, a validation error the screen shows, a persisted paid order, and a repeated idempotency key that does not create a second order. The API tests already cover the last one. The browser test is there to catch the client sending a new key on every click.

---

## 9. What a test cannot prove

| You cannot prove | Why | What covers the neighbouring fact |
|---|---|---|
| A private method, by calling it through reflection | The test locks the structure. Rename the method and the test breaks while the behaviour is unchanged. | The public method that calls it |
| SQL, a foreign key, or Flyway, with a mocked repository | The mock never opens Postgres. | `AuthAndCatalogTests` against the container |
| Arithmetic, with `@WebMvcTest` and a calculator mock that returns 9 | The mock is the result. | `CalculatorTest` or `PricingTest` |
| The React route guard, `localStorage`, or the Vite proxy, with `MockMvc` | MockMvc never loads `frontend/`. | A browser journey, section 8 |
| That Docker was healthy, when `disabledWithoutDocker = true` skips the class | Skipped tests report success. | Run `-Dgroups=integration` with the daemon up and read the test count |
| A real MTN MoMo or card charge | `StubPaymentGateway` returns `SUCCESS`, `DECLINE`, or `TIMEOUT` from configuration or `X-Payment-Stub-Mode`. | The stub’s three outcomes in `StorefrontFlowTests` |
| Pixel layout, colour, or copy tone | No assertion looks at a screenshot. | A person, or a later visual test you choose to own |
| The line in `AuthService.login` that sets a `LOCKED` user back to `ACTIVE` after a successful password match | `isLocked` is already true for that status, so login rejects the request before that assignment. The branch does not run. | Leave it. A test that forces it would require a user the method cannot reach. |
| Two checkouts racing the last unit of stock | The decrement can lose an update between the check and the write. Reproducing that needs two threads and a pause inside the transaction. | The single-request conflict when stock is already too low |
| Production latency, failover, or TLS | One container on a laptop has none of those. | A staging exercise, not this suite |
| Coverage | JaCoCo counts executed lines. A test with no assert raises the number and proves nothing. | The assert in the test, and the 85% gate as a floor |

`context_withPostgresAndRedis_starts` cannot prove a rule. It proves the context started.

A green `verify` cannot prove the shop. It proves the tagged tests passed and at least 85% of lines ran.

---

## 10. GitLab CI

[`.gitlab-ci.yml`](../../.gitlab-ci.yml) has four jobs.

| Job | When | Command |
|---|---|---|
| `unit` | `TEST_LEVEL` is `all` (the default) or `unit` | `./mvnw -Dgroups=unit test` |
| `integration` | `all` or `integration` | `./mvnw -Dgroups=integration test` |
| `e2e` | `e2e`, or a manual button on `all` | `./mvnw -Dgroups=e2e test` |
| `gate` | `all`, after unit and integration | `./mvnw verify` |

Run one level from the GitLab UI: **Build → Pipelines → Run pipeline**, add a variable `TEST_LEVEL` with value `unit`, `integration`, or `e2e`. Leave it unset and the default `all` runs unit, integration, and the gate. The manual `e2e` button does not block the gate.

The integration job, the e2e job, and the gate job start a Docker daemon beside the Maven job (`docker:27-dind`). Testcontainers talks to it through `DOCKER_HOST=tcp://docker:2375`. Two variables are required there and wrong on a laptop:

- `TESTCONTAINERS_HOST_OVERRIDE=docker` makes the JDBC URL and the Redis host point at the dind service. Inside the Maven container, `localhost` is not the daemon, so a random port on localhost is a refused connection.
- `TESTCONTAINERS_RYUK_DISABLED=true` skips the reaper. Ryuk’s mount of the Docker socket does not work across that service boundary. The job’s containers die with the job.

The `unit` job has no Docker service. If a unit test starts asking for a container, the split is wrong and the tag is wrong.

The `gate` job runs the suite again. JaCoCo writes one `jacoco.exec` per Maven process. The unit job’s file and the integration job’s file are not the same file, and the 85% check would see only the job it ran in. `verify` in the gate job is the process that both executes every tag and applies the line-coverage rule. The earlier jobs exist so a failure in `PricingTest` is visible without waiting for Postgres.

JUnit XML is published from `target/surefire-reports/`. The HTML coverage report is the `gate` artifact `target/site/jacoco/`.

Local commands stay the ones in section 2. You do not need GitLab to run a single group.

---

## 11. Coverage

JaCoCo’s `check` goal is bound to `verify`. The rule is bundle line coverage at least `0.85`. Branch coverage is reported and not gated.

Use the report to find a rule with no test. Do not add a test whose only purpose is to execute a line. Mutation testing (PIT) would change a condition and check that a test fails. It is not in this build. The discipline that replaces it here is: every test asserts a value, a message, a status, or a `verify`.

---

## 12. Mistakes

| Mistake | Do this |
|---|---|
| `@SpringBootTest` for `2 + 3` | `CalculatorTest`, no Spring |
| Mocking the repository in every test | Keep the mock for the service rule. Keep Postgres for the query. |
| H2 while production is PostgreSQL | The Testcontainers block in section 6 |
| Asserting only HTTP 200 | Assert the JSON field and, when money moved, the row |
| One test that registers, checks out, and ships | One behaviour per method |
| `Thread.sleep` to wait for a timestamp | Pass an `Instant`, or use a gap of seconds, as `DiscountCodeTest` does |
| Editing `demo@cloudsphere.rw` inside a test | A fresh email. The seed user is shared. |
| Calling `StorefrontFlowTests` an end-to-end test | It never opens the React app |
| Treating a skipped container class as a pass | Section 6.2 |

---

## 13. Done when

1. The method name is `subject_condition_outcome`: `divide_byZero_throwsIllegalArgument`, `recordAdd_twoAndThree_savesFive`, `assertTransition_deliveredToPlaced_throws`.
2. The three comments are in order: Arrange, Act, Assert.
3. A boundary has the success, the line, and the refusal as separate tests.
4. The assert is a value, a message, a status, or a `verify`.
5. The tag matches section 2. A test that starts Docker is `integration` or `e2e`, never `unit`.
6. The test does not need another test method to have run first.
7. `./mvnw -q -Dgroups=unit test` is green before you spend time on containers. After backend changes, `./mvnw verify` is green.

## 14. Practice

| Step | Do this | Done when |
|---|---|---|
| 1 | Write `Calculator` and `CalculatorTest` from section 3, with the three comments in each method. | A wrong expected value prints expected versus actual. |
| 2 | Write `WordCounterTest` for `null`, `""`, `"   "`, one word, and double spaces. | `split(" ")` fails the extra-space test. |
| 3 | Write `CalculationServiceTest` from section 4. Save `left` instead of the sum. | `recordAdd_twoAndThree_savesFive` goes red, and `verify` still sees one `save`. |
| 4 | In the [todo article](https://dev.to/olymahmud/unit-testing-using-spring-boot-junit-and-mockito-2l0b), label Arrange, Act, and Assert on `createTodo_shouldSaveAndReturnTodo`. | You can point at `when`, the service call, the assert, and `verify`. |
| 5 | Map each `PricingTest` method to a row in section 3.6. | You can name qty 4 and qty 5. |
| 6 | Run `./mvnw -q -Dgroups=unit test` with Docker stopped. | The build is green and no container starts. |
| 7 | Run `./mvnw -q -Dgroups=integration test` with Docker running. | Postgres and Redis start, and health, login, the storefront class, and `AdminControllerTests` run. |
| 8 | On a branch, remove `noRollbackFor` from `AuthService.login`, run `StorefrontFlowTests#login_threeBadPasswords_locksTheAccount`, and put the annotation back. | The third failure is visible in the database after the 401 only with the annotation. |

## 15. Questions

Answer from the rules in this guide. One option is correct.

1. A unit test of `Calculator.add` starts which of these?
   - A. The Spring context
   - B. PostgreSQL
   - C. JUnit, which calls the method
   - D. The React app

2. `@SpringBootTest` plus `MockMvc` plus Testcontainers is which level?
   - A. End to end, because the context is complete
   - B. Integration
   - C. Unit, because one HTTP call is one method
   - D. A browser test

3. `StorefrontFlowTests` is which level?
   - A. End to end
   - B. Integration
   - C. A unit test of `Pricing`
   - D. A Playwright suite

4. A guest who opens `/wishlist` and is sent to `/login` is proved by which test?
   - A. `MockMvc` against `GET /api/v1/wishlist`
   - B. A browser journey through `RequireAuth`
   - C. `PricingTest`
   - D. The JaCoCo report

5. In `assertEquals(5, calculator.add(2, 3))`, what is `5`?
   - A. The actual result
   - B. The expected result
   - C. A delta
   - D. The failure message

6. In `assertThat(sum).isEqualTo(5)`, what is `sum`?
   - A. The expected result
   - B. The actual result
   - C. The tag
   - D. The message

7. Which name matches the rule `subject_condition_outcome` for “5 divided by 2 returns 2”?
   - A. `testDivide`
   - B. `divide_doesNotRoundUp`
   - C. `divide_fiveByTwo_returnsTwo`
   - D. `returnsTwo`

8. A method named `login_validPassword_returnsTokenAndWrongPassword_returns401` should be
   - A. Left as one method
   - B. Split into two methods
   - C. Tagged `e2e` only
   - D. Made private

9. `count_blankOrNull_returnsZero` may stay one method because
   - A. A class may contain only one `@Test`
   - B. Both inputs share one result, and the name says so
   - C. `null` cannot be asserted
   - D. Surefire runs only the first assert

10. Surefire’s treatment of a class named `CalculatorIT` in this project is
    - A. Run it in the Failsafe integration phase automatically
    - B. Compile it and skip it
    - C. Run it when `-Dgroups=e2e`
    - D. Run it as a unit test

11. Which command runs only the unit group?
    - A. `./mvnw verify`
    - B. `./mvnw -Dgroups=unit test`
    - C. `./mvnw -Dgroups=integration test`
    - D. `./mvnw -Dtest=CalculatorIT test`

12. `./mvnw -Dgroups=unit test` and the 85% line gate
    - A. The gate runs and must pass on that command
    - B. The gate is bound to `verify`, so this command does not apply it
    - C. The gate measures branches only
    - D. The gate skips every `@Tag("unit")` class

13. `@Tag("unit")` on the class means
    - A. Each method must repeat the tag or Surefire skips it
    - B. The methods carry the class tag
    - C. Spring starts
    - D. Docker starts

14. `@ExtendWith(MockitoExtension.class)` is on a Mockito test because
    - A. `@Mock` is not created without that extension
    - B. It starts PostgreSQL
    - C. It replaces `@Test`
    - D. It writes `jacoco.exec`

15. `@InjectMocks` does which of these?
    - A. Replaces the class under test with a mock
    - B. Builds the class under test and passes the mocks into its constructor
    - C. Starts the Spring context
    - D. Asserts a repository call

16. `when(calculator.add(2, 3)).thenReturn(5)` inside `CalculationServiceTest` proves
    - A. That `2 + 3` is `5`
    - B. That `recordAdd` saves the value `add` returned
    - C. That Flyway applied `V1__init_cloud_sphere.sql`
    - D. That `GET /api/calc/add` maps `a` and `b`

17. A mock of `CalculationService` inside `CalculationServiceTest` proves
    - A. The service rule
    - B. Nothing about the service. The test checks the mock
    - C. The SQL for `save`
    - D. The HTTP status

18. `verify(store, never()).save(...)` is
    - A. Arrange
    - B. An assert that `save` did not run
    - C. A way to ignore a failure
    - D. Required on every mock

19. `doNothing().when(repository).deleteById(1L)` is
    - A. The assert
    - B. Arrange for a `void` method
    - C. The act
    - D. A Spring rollback

20. `DataInitializerTest.run_seedDisabled_touchesNothing` asserts with
    - A. `verifyNoInteractions` on the repositories
    - B. `@SpringBootTest`
    - C. A Postgres container
    - D. `MockMvc`

21. `Pricing.calculateTotal` is tested by
    - A. Mocking the static method
    - B. Calling the static method
    - C. Reflecting into a private helper
    - D. `@WebMvcTest` alone

22. `AuthPolicyTest` can call `AuthService.assertPasswordPolicy` because
    - A. The test class is in the same package
    - B. The method is public
    - C. Mockito makes it public
    - D. The test boots Spring

23. `DiscountCodeTest` passes `Instant.parse("2026-06-01T00:00:00Z")` because
    - A. `Thread.sleep` is the clock
    - B. `isUsable` takes the time as an argument
    - C. Testcontainers freezes time
    - D. JaCoCo requires a fixed year

24. With that fixed instant, a code whose `validFrom` is one second later is
    - A. Usable
    - B. Not usable yet
    - C. A cause of `Thread.sleep`
    - D. Accepted because the date matches

25. With that fixed instant, a code whose `validUntil` equals `now` is
    - A. Expired
    - B. Still usable
    - C. A 500
    - D. Ignored

26. `Pricing.calculateTotal(4, 100)` returns
    - A. 450
    - B. 400
    - C. 360
    - D. An exception

27. `Pricing.calculateTotal(5, 100)` returns
    - A. 500
    - B. 450
    - C. 400
    - D. 0

28. `Pricing.shippingRwf(49_999, STANDARD)` returns
    - A. 0
    - B. 2_000
    - C. 49_999
    - D. An exception

29. `Pricing.shippingRwf(30_000, GOLD)` returns
    - A. 2_000
    - B. 0
    - C. 30_000
    - D. 500

30. `OrderStateMachine` on `DELIVERED → PLACED`
    - A. Allows the move
    - B. Throws `ApiException` (conflict)
    - C. Returns HTTP 200
    - D. Sets the order to `PLACED`

31. A wrong password and an unknown email on login
    - A. Use different details, so the client can tell which emails exist
    - B. Share the detail `email or password is incorrect`
    - C. Both return HTTP 200
    - D. Both return HTTP 403

32. A draft product requested by id is
    - A. HTTP 403
    - B. HTTP 404
    - C. HTTP 200 with stock 0
    - D. HTTP 401

33. A `@WebMvcTest` that stubs `calculator.add(2, 3)` to return 9
    - A. Proves that `2 + 3` is 5
    - B. Proves the controller writes the stubbed sum
    - C. Fails if `Calculator.add` is deleted
    - D. Runs Flyway

34. `@MockitoBean` belongs on a test that
    - A. Has no Spring context
    - B. Has a Spring context and must replace one bean
    - C. Calls a static method
    - D. Is tagged `unit` and uses only `@Mock`

35. `@DataJpaTest` is pointed at PostgreSQL with `replace = NONE` because
    - A. H2 runs the `cloud_sphere` Flyway scripts unchanged
    - B. Those scripts are PostgreSQL SQL
    - C. Redis supplies the datasource
    - D. `MockMvc` requires H2

36. A `static` `@Container` field
    - A. Starts once per test method
    - B. Starts once for the test class
    - C. Is shared by every test class in the same JVM
    - D. Reuses the Compose database on port 5432

37. `@DynamicPropertySource` runs
    - A. After the first request
    - B. Before Spring builds the datasource, once the container has a published port
    - C. In place of Flyway
    - D. Only when Docker is stopped

38. `disabledWithoutDocker = true` while Docker is stopped
    - A. Fails the build
    - B. Skips the class, and `test` can still report success
    - C. Switches the suite to H2
    - D. Starts Ryuk anyway

39. Integration tests in this repository use
    - A. The Compose database left running on port 5432
    - B. A Postgres container and a Redis container started for that test class
    - C. H2 for speed, Postgres only in GitLab
    - D. No database. The repositories are mocked

40. `@Transactional` on a test method rolls back
    - A. A commit made on a `RANDOM_PORT` server thread
    - B. The transaction that the test method itself opened
    - C. Flyway
    - D. Every container

41. `failedAttempts` used to vanish after a bad login because
    - A. Redis rejected the write
    - B. `login` is `@Transactional`, and the `ApiException` rolled the increment back
    - C. The password encoder cached the user
    - D. JaCoCo removed the line

42. After three bad logins, the lock is asserted by
    - A. Reading the `User` instance held before the requests
    - B. Loading the user again from the repository
    - C. Reading a `locked` field on the HTTP 401 body
    - D. Waiting 30 minutes

43. `context_withPostgresAndRedis_starts` proves
    - A. The ten-percent quantity rule
    - B. That the application context started
    - C. The cart total
    - D. The React route guard

44. A real listen port, avoiding 8080, is
    - A. `webEnvironment = MOCK`
    - B. `webEnvironment = RANDOM_PORT`
    - C. `@WebMvcTest`
    - D. `@DataJpaTest`

45. On a pipeline with `TEST_LEVEL=all`, the `e2e` job
    - A. Blocks the coverage gate
    - B. Is a manual job and does not block the gate
    - C. Runs Playwright
    - D. Runs inside the `unit` job

46. Starting a GitLab pipeline with `TEST_LEVEL=unit`
    - A. Runs `./mvnw verify` and the coverage gate
    - B. Runs the `unit` job only
    - C. Starts the Docker daemon
    - D. Runs `StorefrontFlowTests`

47. `TESTCONTAINERS_HOST_OVERRIDE=docker` is set in GitLab because
    - A. A laptop must use that host too
    - B. The JDBC URL must reach the dind service, not localhost inside the Maven container
    - C. It replaces Flyway
    - D. The `unit` job has no other way to mock Redis

48. The `gate` job runs the suite again because
    - A. Surefire discards the first result
    - B. JaCoCo writes one `jacoco.exec` per Maven process, and the 85% check must see every tag
    - C. Docker deletes unit-test results
    - D. `@Tag` values cannot be combined in one run

49. The JaCoCo rule on `verify` fails the build when
    - A. Branch coverage is under 85%
    - B. Line coverage is under 85%
    - C. A mutation survives
    - D. Instruction coverage is under 100%

50. Which fact can this suite prove?
    - A. A real MTN MoMo charge succeeded
    - B. The Vite dev server kept `X-Cart-Token` across a refresh
    - C. `calculateTotal(5, 100)` is 450
    - D. The cart page uses the right CSS class

## 16. Answers

1. **C.** JUnit calls `add`. Spring, Postgres, and the browser stay down.
2. **B.** The level is the boundary the test crosses. `MockMvc` never opens the React app, so a full Spring context is still integration.
3. **B.** The class drives the API through `MockMvc`. It does not open `frontend/`.
4. **B.** `RequireAuth` lives in `frontend/src/App.jsx`. `MockMvc` never loads that component.
5. **B.** JUnit’s `assertEquals` takes the expected value first. Swapping the arguments makes the failure message lie.
6. **B.** AssertJ reads the actual value first: `assertThat(actual).isEqualTo(expected)`.
7. **C.** The three parts are the method, the input, and the result. `divide_doesNotRoundUp` never says which call.
8. **B.** A name that joins two results with “and” is two tests: `login_validPassword_returnsToken` and `login_wrongPassword_returnsGenericDetail`.
9. **B.** Blank and `null` share the result 0, and the name says both inputs.
10. **B.** Surefire runs `*Test`, `*Tests`, and `*TestCase`. This project does not use Failsafe, so `*IT` is compiled and skipped.
11. **B.** `-Dgroups=unit` selects `@Tag("unit")`. `verify` runs every tag and then the coverage gate.
12. **B.** `test` writes a report. The 85% check is the `check` goal on `verify`. A unit-only run is not supposed to clear that gate, because most controllers never ran.
13. **B.** A tag on the class applies to every test method in it.
14. **A.** `@Mock` stays a null field until `MockitoExtension` creates it.
15. **B.** That is the same wiring as `new CalculationService(repository, calculator)`. The class under test stays real.
16. **B.** The stub fixes what `add` returns. `CalculatorTest` is the test that proves the sum.
17. **B.** The test would be asserting the stand-in, not `recordAdd`.
18. **B.** `never()` is the negative path for a collaborator: that call was required not to happen.
19. **B.** `deleteById` returns void, so `doNothing()` is how you arrange it. `verify` is the assert.
20. **A.** Seed disabled means no repository method runs. The test does not start Spring or Docker.
21. **B.** `Pricing` is a `final` class of static methods. Call `calculateTotal`. A static mock hides the rule.
22. **A.** The method is package-private. `AuthPolicyTest` sits in `rw.ac.rca.cloudsphere.identity`. Making it public only for the test is the wrong change.
23. **B.** Pass the instant in. `Thread.sleep` is not a boundary.
24. **B.** One second before `validFrom` is the edge. The code is not usable yet.
25. **B.** Usable means “not after `validUntil`”. Equal to `now` is still inside the window.
26. **B.** Quantity 4 is below the discount line, so the total is `4 * 100`.
27. **B.** Quantity 5 takes 10% off: `5 * 100 * 90 / 100 = 450`.
28. **B.** Standard shipping stays 2_000 until the subtotal reaches 50_000. 49_999 is the side of the line that still pays.
29. **B.** Gold shipping is 0 below the threshold as well.
30. **B.** `DELIVERED` is terminal. The machine throws `ApiException.conflict`.
31. **B.** Both failures look the same, so the response does not reveal which emails are registered. Assert that shared detail, not only status 401.
32. **B.** An unpublished product is hidden as 404, not refused as 403.
33. **B.** The mock returns 9, so the test stays green if `add` is deleted. It proves the mapping of `a`, `b`, and `sum`.
34. **B.** `@Mock` needs no Spring context. `@MockitoBean` replaces a bean after the context is up.
35. **B.** `@DataJpaTest` would otherwise substitute an embedded database. Flyway’s `cloud_sphere` script is written for PostgreSQL.
36. **B.** `static` means once per class, then stop. A non-static `@Container` restarts every test method. Each integration class still has its own containers.
37. **B.** The JDBC URL does not exist until Docker publishes a random port, so it cannot be fixed in `application.properties` beforehand.
38. **B.** The class is skipped. A green `test` run can hide that Postgres never started. Run `-Dgroups=integration` with the daemon up and read the test count.
39. **B.** The suite does not attach to the Compose database. `AuthAndCatalogTests` starts `postgres:16-alpine` and `redis:7-alpine` for that class.
40. **B.** The annotation rolls back the test thread’s transaction. A `RANDOM_PORT` server commits on another thread, so that rollback does not cover it. Read the row after the call.
41. **B.** Spring rolls a runtime exception back, so the increment was undone and the account never locked. `noRollbackFor = ApiException.class` keeps the save.
42. **B.** The object you held before the request does not show the commit. Load the user again and assert `LOCKED` and `lockedUntil`.
43. **B.** The test catches a broken bean definition. It does not assert a price or a status.
44. **B.** `MOCK` does not listen. `RANDOM_PORT` avoids a server already bound to 8080. That Java test is still integration until a browser drives the UI.
45. **B.** The job is manual, with `allow_failure: true`. No class is tagged `e2e` yet, and the gate does not wait for it.
46. **B.** The `unit` job’s rule matches `TEST_LEVEL=unit`. The integration job, the e2e job, and the gate do not.
47. **B.** Inside the Maven container, `localhost` is not the Docker daemon. The override sends JDBC and Redis to the `docker` service. Leave it unset on a laptop.
48. **B.** The unit job and the integration job each write their own `jacoco.exec`. `verify` in the gate job is the process that runs every tag and applies the line rule.
49. **B.** The limit is bundle line coverage `0.85`. Branch coverage is reported and is not a gate. Mutation testing is not in this build.
50. **C.** The payment gateway is a stub, `MockMvc` never runs Vite, and no test looks at CSS. `PricingTest.calculateTotal_fiveItems_appliesTenPercent` asserts 450.

## Where to look

| File | What it shows |
|---|---|
| `src/test/java/rw/ac/rca/cloudsphere/commerce/PricingTest.java` | `@Tag("unit")`, boundaries, bad quantities and codes |
| `src/test/java/rw/ac/rca/cloudsphere/commerce/DiscountCodeTest.java` | A fixed `Instant` |
| `src/test/java/rw/ac/rca/cloudsphere/order/OrderStateMachineTest.java` | Legal and illegal transitions |
| `src/test/java/rw/ac/rca/cloudsphere/identity/AuthPolicyTest.java` | A package-private static rule |
| `src/test/java/rw/ac/rca/cloudsphere/config/DataInitializerTest.java` | Mocks and `verifyNoInteractions` |
| `src/test/java/rw/ac/rca/cloudsphere/security/SecurityComponentTest.java` | Mock Redis, `verify` and `never()` |
| `src/test/java/rw/ac/rca/cloudsphere/AuthAndCatalogTests.java` | The Testcontainers block and `MockMvc` |
| `src/test/java/rw/ac/rca/cloudsphere/StorefrontFlowTests.java` | Longer integration journeys and the lockout assert |
| `.gitlab-ci.yml` | Unit, integration, e2e, and the coverage gate |

Official references for the annotations and the container lifecycle: [Spring Boot testing](https://docs.spring.io/spring-boot/reference/testing/index.html), [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html), [JUnit 5](https://junit.org/junit5/docs/current/user-guide/), [Testcontainers for Java](https://java.testcontainers.org/), [JaCoCo](https://www.jacoco.org/jacoco/).
