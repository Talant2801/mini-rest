# Task: cover mini-rest with tests (spring-boot-starter-test)

Practice task for the 5 lessons on `spring-boot-starter-test`:
unit tests with Mockito → TestContext Framework → first integration test → test properties
and profiles → application context caching.

You build on the existing `mini-rest` app. The point is **not** to reach 100% coverage —
it is to reproduce every mechanism from the lessons on your own code and be able to explain
*why* each annotation is needed and *which dependency* it came from.

---

## 0. Read this first: the lessons are older than your project

The videos are recorded on Spring Boot 2.x/3.x. Your `build.gradle` is on Boot **4.1.1**,
which pulls Spring Framework **7.0.9**, JUnit Jupiter **6.0.3**, Mockito **5.23.0**.
Three things from the lessons changed:

| In the lesson | On your classpath (Boot 4.1.1) |
|---|---|
| `@MockBean` / `@SpyBean` (`org.springframework.boot.test.mock.mockito`) | **Removed.** Use `@MockitoBean` / `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito` |
| Those annotations came from `spring-boot-test` | They now live in **`spring-test`** — i.e. plain Spring Framework, not Boot |
| `@WebMvcTest`, `TestRestTemplate` in `spring-boot-test-autoconfigure` | Not there any more. The web slice moved to a separate artifact (`org.springframework.boot:spring-boot-webmvc-test`, version managed by the BOM). `MockMvc` itself is still in `spring-test`. |

Notice this *reinforces* the lesson's main point: always check which dependency an annotation
comes from. Bean overriding became a Framework feature, so it stopped being Boot-specific.

Everything else from the lessons (`@SpringBootTest`, `@ContextConfiguration`,
`ConfigDataApplicationContextInitializer`, `@ActiveProfiles`, `@TestConstructor`,
`@TestConfiguration`, `@DirtiesContext`, `TestExecutionListener`) works exactly as shown.

Your test dependencies are already wired (`spring-boot-starter-test` on `testImplementation` +
`useJUnitPlatform()`), so Stage 1 of lesson 1 is done. Verify it and move on:

```bash
./gradlew dependencies --configuration testCompileClasspath
```

Find the three Spring deps in that tree and write down, in one line each, what
`spring-test`, `spring-boot-test` and `spring-boot-test-autoconfigure` are responsible for.

---

## Stage 1 — Make the code worth unit-testing

> **Status: already implemented.** This is the only stage that is done; every test in stages
> 2–5 is yours to write. The spec is kept below in full so you can read the refactor against
> it — treat it as a code review, not as reading material. If you would rather do this stage
> from scratch yourself, revert the production code and work through 1.1–1.6.

### Why this stage exists

`NotificationService` used to be a `@Getter` holder with no logic, and `MessageController`
ran the dispatch loop itself through `service.getRepository()`. There was nothing to mock and
nothing to assert — a unit test could only have verified the framework. This is the analogue
of the lesson's `CompanyService.findById()`: without it, lesson 1 has no subject.

### The spec

**1.1 — Move the business logic into the service.** `NotificationService` exposes:

```java
Message           send(NotificationRequest request);       // validate → dispatch → save → publish event
Message           update(UUID id, NotificationRequest r);  // throws MessageNotFoundException if absent
List<Message>     findAll();
Optional<Message> findById(UUID id);
void              deleteById(UUID id);                     // throws MessageNotFoundException if absent
```

**1.2 — Remove the `@Getter` from the service.** `MessageController` must not be able to reach
`service.getRepository()` or `service.getChannels()`; it only calls service methods. Leaking
the repository through a getter is precisely what made the controller untestable.

**1.3 — Add two configuration-driven behaviours worth asserting:**
- unknown / blank channel name → **fall back** to a configured default channel;
- message longer than a configured limit → reject with `IllegalArgumentException`.

**1.4 — Add a `DeliveryPolicy` bean** holding those two settings:

```java
@Component
public class DeliveryPolicy {
    public DeliveryPolicy(@Value("${notification.default-channel}") String defaultChannel,
                          @Value("${notification.max-length}") int maxLength) { ... }
}
```

Both placeholders deliberately have **no default value** — Stage 3 depends on the context
failing when properties are not loaded.

**1.5 — Add the properties** to `src/main/resources/application.yaml`:

```yaml
notification:
  default-channel: console
  max-length: 200
```

**1.6 — Map errors in the controller:** `IllegalArgumentException` → `400`,
missing id → `404`.

### What landed where

| Spec item | File |
|---|---|
| 1.1 service API, dispatch, validation, event publishing | `service/NotificationService.java` |
| 1.2 thin controller, no repository access | `controller/MessageController.java` |
| 1.3 fallback + length limit | `NotificationService#resolveChannel`, `#validate` |
| 1.4 policy bean | `config/DeliveryPolicy.java` |
| 1.5 properties | `src/main/resources/application.yaml` |
| 1.6 error mapping | `MessageController` `@ExceptionHandler` methods, `dto/ErrorResponse.java` |

Beyond the spec: `event/MessageSentEvent` (so you have a publisher interaction to `verify`,
like the lesson does with its event publisher), `exception/MessageNotFoundException`, and the
three channels now log through SLF4J instead of `System.out` — keeps test output readable and
makes `verify(spy).send(...)` meaningful in Stage 5. The empty stub
`org.nikita.minirest.notification.NotificationServiceTest` was deleted (a `@SpringBootTest`
with no tests). The JSON contract is unchanged, so the React frontend still works.

### Acceptance criteria

- [x] `./gradlew build` green, `contextLoads` still passes.
- [x] `MessageController` contains no `for` loop over channels and no `getRepository()` call.
- [x] All five endpoints work over HTTP: fallback verified (`channel:"sms"` →
      `channelUsed:["console"]`), both `400` cases, `correlationId` preserved across `PUT`,
      `404` on unknown id and on a repeated `DELETE`.

### Review checklist — do this before Stage 2

1. Read `NotificationService` and name all four constructor collaborators and why each exists.
2. `resolveChannel` returns *one* channel, but `Message.channelUsed` is a `List`. Look at what
   the old controller did and decide whether the single-element list is right, or whether you
   want to restore multi-channel dispatch. If you change it, Stage 2's assertions change with it.
3. `Message.channel` stores what was *requested*; `channelUsed` stores what was *actually* used.
   Confirm you can see why those differ only on fallback — this is the assertion in Stage 2 test 2.
4. `deleteById` now does a `findById` first so it can 404. That is an extra repository
   interaction — remember it when you write `verifyNoMoreInteractions`.
5. `validate` runs before channel resolution in both `send` and `update`. Predict which mocks
   stay untouched on a rejected message; that prediction is Stage 2 test 3.

---

## Stage 2 — Pure unit test with Mockito (lesson 1)

Create `src/test/java/org/nikita/minirest/service/NotificationServiceTest.java`.

Requirements:

- `@ExtendWith(MockitoExtension.class)` — no Spring context at all. If your test starts an
  application context, you did it wrong.
- `@Mock` the `NotificationRepository`, each `Channel`, the `DeliveryPolicy` and the
  `ApplicationEventPublisher`.
- Stub with `doReturn(...).when(...)` / `when(...).thenReturn(...)`, use static-imported
  `assertTrue` / `assertEquals`, then close with `verify(...)` and
  `verifyNoMoreInteractions(...)` exactly as in the lesson.
- Also assert on a mock that must **not** be touched: `verifyNoInteractions(slackChannel)`
  when the request targets `email`.
- Verify the event exactly as the lesson verifies its `eventPublisher`:
  `verify(eventPublisher).publishEvent(any(MessageSentEvent.class))`. Then do it properly with
  an `ArgumentCaptor` and assert the captured event's `channel` and `correlationId`.

Tests to write, at minimum:

1. `send()` dispatches to the matching channel only, saves once, publishes one event, and
   returns a `Message` whose `channelUsed` holds that one channel name.
2. `send()` with an unknown channel name falls back to the default channel — note that the
   returned `Message.channel` is what was *requested* while `channelUsed` is what was
   *actually used*. Assert both.
3. `send()` with an over-long message throws `IllegalArgumentException` **and** never calls
   `repository.save(...)`, any channel, or the publisher (`verifyNoInteractions`).
4. `update()` on a missing id throws `MessageNotFoundException` and dispatches nothing.
5. `update()` on an existing id keeps the original `correlationId`.
6. `findById()` returns empty when the repository returns empty.

**Watch out — two traps, both deliberate:**

1. `@InjectMocks` cannot populate a `List<Channel>` constructor parameter with your `@Mock`
   channels — Mockito will hand you an empty list or null and the test will pass for the wrong
   reason. Build the service yourself in `@BeforeEach`:
   `new NotificationService(repository, List.of(consoleChannel, emailChannel, slackChannel), policy, eventPublisher)`.
   Work out *why* `@InjectMocks` fails here and write the answer in a comment — that is half the
   exercise.
2. `resolveChannel` streams the list and short-circuits on `findFirst()`, so `getName()` is
   **not** called on channels after the match. Stub `getName()` on all three in `@BeforeEach`
   and `MockitoExtension`'s default strictness fails the test with
   `UnnecessaryStubbingException`. Understand the message before you reach for
   `@MockitoSettings(strictness = LENIENT)` — the better fix is usually stubbing per test, or
   ordering the mocks so the stub is actually used. Both approaches are worth trying once.

**Acceptance:** `./gradlew test --tests '*NotificationServiceTest'` is green and the output
contains no Spring banner / no `Starting ... Application` line.

---

## Stage 3 — First integration test the hard way (lessons 2 & 3)

Create `src/test/java/org/nikita/minirest/integration/service/NotificationServiceIT.java`.
Go through the three steps in order and **record what happens at each one** — do not jump
straight to `@SpringBootTest`.

**3a.** Annotate with only:
```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MiniRestApplication.class)
```
Run it. It must fail — I confirmed it does, with
`BeanCreationException` → `PlaceholderResolutionException` while creating `deliveryPolicy`.
Read the real stack trace yourself and explain it: which bean, which placeholder, and why
`application.yaml` was *not* read (hint: plain `spring-test` knows `.properties`; YAML support
is Boot's, and `@TestPropertySource` will not save you here).

**3b.** Fix it *without* `@SpringBootTest`, by adding the initializer from the lesson:
```java
@ContextConfiguration(classes = MiniRestApplication.class,
                      initializers = ConfigDataApplicationContextInitializer.class)
```
Put a breakpoint in `ConfigDataEnvironmentPostProcessor` (or just log
`environment.getPropertySources()`) and confirm you can see `application.yaml` appear in the
list of property sources, as in the video.

**3c.** Now replace both annotations with a single `@SpringBootTest` and confirm the test still
passes. Then open `@SpringBootTest` and answer in writing:
- which two annotations it is meta-annotated with;
- how it finds `MiniRestApplication` without you naming it in `classes`;
- why that makes `@SpringBootApplication` placement important.

**3d.** Write the actual test body: `@Autowired NotificationService` (field injection is fine
for now), send a notification, read it back through `findById`, assert it round-tripped.
No mocks here — the repository must be the real `InMemmoryNotificationRepository` bean.

**3e.** Find `DependencyInjectionTestExecutionListener` and identify the two methods it
overrides, and the two `AutowireCapableBeanFactory` calls that actually inject your
`@Autowired` fields. One sentence is enough — but find it in the real source, not from memory.

**Look at this while you're here:** your `ApplicationConfig` is an `@AutoConfiguration` that
carries `@ComponentScan("org.nikita.minirest")`, and it is registered through
`META-INF/spring/...AutoConfiguration.imports`. With `@SpringBootTest`, `MiniRestApplication`
also component-scans the same package. Print the bean count and check whether anything is
registered twice, and read why Spring's docs tell you never to put `@ComponentScan` on an
auto-configuration class. Decide whether to keep that file — and justify the decision.

---

## Stage 4 — Test properties, profiles and a composed annotation (lesson 4)

**4a.** Create `src/test/resources/application-test.yaml` overriding *only* what tests need:
```yaml
notification:
  default-channel: email
  max-length: 10
```
Do **not** create `src/test/resources/application.yaml` — first understand (and be able to
explain) why a same-named file in test resources would shadow the main one entirely instead
of merging with it.

**4b.** Activate it with `@ActiveProfiles("test")` on the IT. Note that this annotation comes
from `spring-test`, not from Boot.

**4c.** Prove the override took effect *through behaviour*, not just by reading a field:
in the integration test, sending a 20-character message must now be rejected, while the same
message is accepted in production config. Also `@Autowired` the `DeliveryPolicy` bean and
assert `defaultChannel == "email"`.

**4d.** Extract a composed annotation, as the lesson does — create
`src/test/java/org/nikita/minirest/integration/annotation/IntegrationTest.java`:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
public @interface IntegrationTest { }
```
Replace both annotations on your IT with `@IntegrationTest`.

**4e.** Switch the test to constructor injection: drop `@Autowired`, make the fields `final`,
add `@RequiredArgsConstructor` (Lombok is already on the project via `io.freefair.lombok`),
and add `@TestConstructor(autowireMode = AutowireMode.ALL)`. Confirm it works, then remove
`@TestConstructor` and set the reserved key globally instead — create
`src/test/resources/spring.properties`:
```properties
spring.test.constructor.autowire.mode = all
```
Confirm the test still passes with no constructor-related annotation anywhere.
(The key name is not folklore — it is
`TestConstructor.TEST_CONSTRUCTOR_AUTOWIRE_MODE_PROPERTY_NAME`; go look at it.)

**Acceptance:** `NotificationServiceIT` carries exactly one annotation — `@IntegrationTest` —
plus `@RequiredArgsConstructor`, and is green.

---

## Stage 5 — Context caching (lesson 5)

**5a.** Add a second integration test, `MessageControllerIT`, in the same
`integration` package tree, autowiring `MessageController` (or `NotificationService`) with one
trivial test method. Annotate it with plain `@SpringBootTest` at first — deliberately a
*different* configuration from `@IntegrationTest`.

Turn on the cache log before running:
```yaml
# src/test/resources/application-test.yaml
logging:
  level:
    org.springframework.test.context.cache: debug
```
Run `./gradlew test` and count how many times the application context is created, and read the
cache statistics line (`size`, `hitCount`, `missCount`).

**5b.** Change `MessageControllerIT` to `@IntegrationTest`. Re-run. You should now see **one**
context and a cache hit. Explain, in terms of the lesson, what the cache key is made of.

**5c.** Now break it on purpose, the way the lesson does. Add to `MessageControllerIT`:
```java
@MockitoSpyBean(name = "emailChannel")
EmailChannel emailChannel;
```
Re-run and confirm you are back to two contexts. Explain why a bean override makes the context
non-reusable. Then use the spy for something real: assert `send()` actually called
`emailChannel.send("...")` via `verify`.

**5d.** Make the spy shared so the cache survives — and here the lesson's recipe does **not**
port directly. In Spring 7, `@MockitoSpyBean` is only honoured on the **test class hierarchy**
(field or type level, including superclasses/interfaces and meta-annotations), *not* on fields
of a `@TestConfiguration` class. So pick one of:

- move the spy declaration up to the type level of your composed annotation:
  `@MockitoSpyBean(types = EmailChannel.class)` on `@IntegrationTest`; or
- write `TestMiniRestApplication` annotated `@TestConfiguration` that declares
  `@Bean EmailChannel emailChannel() { return Mockito.spy(new EmailChannel()); }`, and point
  the composed annotation at it via `@SpringBootTest(classes = TestMiniRestApplication.class)`.

Do it **both** ways, keep the one you prefer, and write down the difference — in particular
which one still needs `@MockitoSpyBean` on individual tests and which one resets the spy
between test methods (check `MockitoSpyBean.reset()`'s default and
`MockitoResetTestExecutionListener`). Verify that after this change both ITs share **one**
context.

**5e.** Finally, take manual control: put `@DirtiesContext` on one test method, then at class
level with `classMode = AFTER_EACH_TEST_METHOD`, add a second `@Test` method, and predict the
context count *before* running. Then run and check your prediction. Remove `@DirtiesContext`
afterwards — the lesson's advice is that you should only need it when you dirty the context by
hand.

**Acceptance:** with all tests green, `./gradlew test` creates exactly **one** application
context, and you can point at the log line that proves it.

---

## Optional stretch (beyond the five lessons)

- Custom `TestExecutionListener` (e.g. logging each test's name) registered via
  `@TestExecutionListeners(mergeMode = MERGE_WITH_DEFAULTS)` — the lesson mentions this exists
  but calls it rare. Doing it once makes Stage 3e click.
- Web-layer test. `MockMvc` is available from `spring-test` today, so you can already do
  `MockMvcBuilders.standaloneSetup(new MessageController(serviceMock)).build()` as a *unit*
  test of JSON/status mapping. For the real slice (`@WebMvcTest`) you would have to add
  `testImplementation 'org.springframework.boot:spring-boot-webmvc-test'` — the coordinate
  resolves through the Boot BOM. Check the artifact's contents before relying on it.
- Parameterised tests (`@ParameterizedTest` + `@CsvSource`) for the channel-fallback matrix.

---

## Self-check questions (answer without looking)

1. Which of these come from `spring-test` and which from `spring-boot-test`:
   `@SpringBootTest`, `@ActiveProfiles`, `@ContextConfiguration`, `@TestConfiguration`,
   `@DirtiesContext`, `@MockitoBean`, `ConfigDataApplicationContextInitializer`,
   `@TestConstructor`?
2. How many `TestContextManager` instances exist when you run 2 test classes with 3 test
   methods each? How many `TestContext` objects? How many test class instances?
3. `SpringExtension` implements which JUnit callbacks, and which one injects your dependencies?
4. Why is `TestContextManager.getTestContext()` used instead of reading the field directly?
5. Name two ways to make a cached context non-reusable, one accidental and one deliberate.
