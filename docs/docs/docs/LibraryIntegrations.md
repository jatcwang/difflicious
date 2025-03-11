---
layout: docs
title:  "Library Integrations"
permalink: docs/library-integrations
---

# Library Integrations

Difflicious integrates seamlessly with popular Scala testing frameworks and libraries. This page explains how to use these integrations in your projects.

## Testing Framework Integrations

### MUnit

[MUnit](https://scalameta.org/munit/) is a lightweight testing library for Scala with a focus on actionable errors and a simple API.

#### Installation

Add this to your SBT build:
```scala
libraryDependencies += "com.github.jatcwang" %% "difflicious-munit" % "{{ site.version }}" % Test
```

For Mill:
```scala
ivy"com.github.jatcwang::difflicious-munit:{{ site.version }}"
```

#### Usage

Import the MUnit integration and use `assertNoDiff` on any `Differ`:

```scala mdoc:nest
import munit.FunSuite
import difflicious.munit.MUnitDiff._
import difflicious._
import difflicious.implicits._

class MyTest extends FunSuite {
  case class Person(name: String, age: Int)
  implicit val personDiffer: Differ[Person] = Differ.derived[Person]
  
  test("compare people correctly") { 
    Differ[List[Person]].assertNoDiff(
      List(Person("Alice", 30), Person("Bob", 25)),
      List(Person("Alice", 30), Person("Bob", 40))
    )
  }
}
```

If the test fails, MUnit will display the diff with color highlighting:

<pre class="diff-render">
List(
  Person(
    name: "Alice",
    age: 30
  ),
  Person(
    name: "Bob",
    age: <span style="color: red;">25</span> -> <span style="color: green;">40</span>
  )
)
</pre>

#### IDE Setup for MUnit

If you are running tests using **IntelliJ IDEA**'s test runner, you will want 
to turn off the red text coloring it uses for test failure outputs because
it interferes with difflicious' color outputs.

In **File | Settings | Editor | Color Scheme | Console Colors | Console | Error Output**, uncheck the red foreground color.

### ScalaTest

[ScalaTest](https://www.scalatest.org/) is a flexible and comprehensive testing framework for Scala.

#### Installation

Add this to your SBT build:
```scala
libraryDependencies += "com.github.jatcwang" %% "difflicious-scalatest" % "{{ site.version }}" % Test
```

For Mill:
```scala
ivy"com.github.jatcwang::difflicious-scalatest:{{ site.version }}"
```

#### Usage

Import the ScalaTest integration and use `assertNoDiff` on any `Differ`:

```scala mdoc:nest
import org.scalatest.funsuite.AnyFunSuite
import difflicious.scalatest.ScalatestDiff._
import difflicious._
import difflicious.implicits._

class MyTest extends AnyFunSuite {
  case class Person(name: String, age: Int)
  implicit val personDiffer: Differ[Person] = Differ.derived[Person]
  
  test("compare people correctly") { 
    Differ[List[Person]].assertNoDiff(
      List(Person("Alice", 30), Person("Bob", 25)),
      List(Person("Alice", 30), Person("Bob", 40))
    )
  }
}
```

#### ScalaTest Color Configuration

Tests should be run with the `-oW` option to disable ScalaTest from coloring test failures all red, as it interferes with 
difflicious' color display:

```
testOnly -- -oW
```

You can add this to your build.sbt to make it the default:

```scala
Test / testOptions += Tests.Argument("-oW")
```

### Weaver

[Weaver](https://disneystreaming.github.io/weaver-test/) is a test framework that leverages Cats Effect for concurrent test execution.

#### Installation

Add this to your SBT build:
```scala
libraryDependencies += "com.github.jatcwang" %% "difflicious-weaver" % "{{ site.version }}" % Test
```

For Mill:
```scala
ivy"com.github.jatcwang::difflicious-weaver:{{ site.version }}"
```

#### Usage

Import the Weaver integration and use `assertNoDiff` on any `Differ`:

```scala mdoc:nest
import weaver.SimpleIOSuite
import difflicious.weaver.WeaverDiff._
import difflicious._
import difflicious.implicits._

object MyTest extends SimpleIOSuite {
  case class Person(name: String, age: Int)
  implicit val personDiffer: Differ[Person] = Differ.derived[Person]
  
  pureTest("compare people correctly") { 
    Differ[List[Person]].assertNoDiff(
      List(Person("Alice", 30), Person("Bob", 25)),
      List(Person("Alice", 30), Person("Bob", 40))
    )
  }
}
```

#### Using with Effect Types

For tests that use effect types like `IO`, you can still use Difflicious:

```scala mdoc:nest
import weaver.IOSuite
import cats.effect.IO
import difflicious.weaver.WeaverDiff._
import difflicious._
import difflicious.implicits._

object MyEffectTest extends IOSuite {
  case class Person(name: String, age: Int)
  implicit val personDiffer: Differ[Person] = Differ.derived[Person]
  
  type Res = Unit
  def sharedResource: Resource[IO, Res] = Resource.pure(())
  
  test("compare async results") { _ =>
    for {
      actual <- IO.pure(List(Person("Alice", 30)))
      expected <- IO.pure(List(Person("Alice", 35)))
      result = Differ[List[Person]].assertNoDiff(actual, expected)
    } yield result
  }
}
```

## Library Integrations

### Cats

[Cats](https://typelevel.org/cats/) is a library providing abstractions for functional programming in Scala.

#### Installation

Add this to your SBT build:
```scala
libraryDependencies += "com.github.jatcwang" %% "difflicious-cats" % "{{ site.version }}" % Test
```

For Mill:
```scala
ivy"com.github.jatcwang::difflicious-cats:{{ site.version }}"
```

#### Usage

Import the Cats integration to get access to differs for Cats data structures:

```scala mdoc:nest
import difflicious._
import difflicious.cats.implicits._
import cats.data.{NonEmptyList, NonEmptyMap, Chain}
import scala.collection.immutable.SortedMap

// Now you can use Differs for Cats data structures
val nelDiffer: Differ[NonEmptyList[Int]] = Differ[NonEmptyList[Int]]
val nemDiffer: Differ[NonEmptyMap[String, Int]] = Differ[NonEmptyMap[String, Int]]
val chainDiffer: Differ[Chain[String]] = Differ[Chain[String]]

// Use them in tests
val result = nelDiffer.diff(
  NonEmptyList.of(1, 2, 3),
  NonEmptyList.of(1, 5, 3)
)
```

#### Supported Cats Data Structures

The `difflicious-cats` module provides differs for common Cats data structures. The exact set may vary by version, but typically includes support for:

* `NonEmptyList`
* `NonEmptyVector`
* `Chain`
* `Validated`
* And others

Check the source code or scaladoc for the complete list of supported types.

### Integration with Circe (Custom Example)

While Difflicious doesn't currently include a built-in module for [Circe](https://circe.github.io/circe/), you can easily create your own integration:

```scala
import difflicious._
import io.circe.{Json, JsonObject}

trait CirceInstances {
  implicit val jsonDiffer: Differ[Json] = new Differ[Json] {
    // Implementation details...
  }
  
  implicit val jsonObjectDiffer: Differ[JsonObject] = new Differ[JsonObject] {
    // Implementation details...
  }
}

object CirceInstances extends CirceInstances
```

## Creating Your Own Integrations

If you need to integrate Difflicious with other libraries or frameworks, the process is straightforward:

1. Create an extension method on `Differ` for your assertion function
2. Use `differ.diff` to compare values
3. Format the output using `DiffResultPrinter.consoleOutput`

Here's a simple example for a hypothetical test framework:

```scala
import difflicious.{Differ, DiffResultPrinter}

trait MyFrameworkDiff {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A)(implicit loc: SourceLocation): TestResult = {
      val result = differ.diff(obtained, expected)
      if (!result.isOk)
        TestResult.failure(DiffResultPrinter.consoleOutput(result, 0).render)
      else
        TestResult.success
    }
  }
}

object MyFrameworkDiff extends MyFrameworkDiff
```

## Integration Patterns

Beyond the direct integrations, here are patterns for using Difflicious with other common libraries.

### With Http4s

When testing Http4s services:

```scala
import cats.effect.IO
import org.http4s._
import org.http4s.implicits._
import difflicious._
import difflicious.implicits._
import difflicious.munit.MUnitDiff._
import io.circe.generic.auto._
import org.http4s.circe._

case class User(id: String, name: String)
implicit val userDiffer: Differ[User] = Differ.derived[User]

// An Http4s service
val service = HttpRoutes.of[IO] {
  case GET -> Root / "users" / id => 
    // Return a user
    IO.pure(Response(status = Status.Ok).withEntity(User(id, "Test User")))
}

// Test the service
test("GET user returns correct user") {
  val request = Request[IO](Method.GET, uri"/users/123")
  val response = service.orNotFound(request).unsafeRunSync()
  
  // Extract and compare the response body
  val actualUser = response.as[User].unsafeRunSync()
  val expectedUser = User("123", "Test User")
  
  userDiffer.assertNoDiff(actualUser, expectedUser)
}
```

### With Slick (Database Testing)

When testing database interactions:

```scala
import slick.jdbc.H2Profile.api._
import difflicious._
import difflicious.implicits._
import difflicious.munit.MUnitDiff._

case class DbUser(id: Int, name: String, email: String)
implicit val dbUserDiffer: Differ[DbUser] = Differ.derived[DbUser]

class UsersTable(tag: Tag) extends Table[DbUser](tag, "users") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def email = column[String]("email")
  def * = (id, name, email).mapTo[DbUser]
}

val users = TableQuery[UsersTable]

// Test database operations
test("query returns expected users") {
  val db = Database.forConfig("h2mem")
  
  // Initialize data
  val setup = DBIO.seq(
    users.schema.create,
    users += DbUser(1, "Alice", "alice@example.com"),
    users += DbUser(2, "Bob", "bob@example.com")
  )
  
  db.run(setup).futureValue
  
  // Run the query
  val query = users.filter(_.name === "Alice")
  val result = db.run(query.result).futureValue
  
  // Compare with expected
  val expected = Seq(DbUser(1, "Alice", "alice@example.com"))
  Differ[Seq[DbUser]].assertNoDiff(result, expected)
}
```

### With Doobie

For Doobie, a pure functional JDBC layer:

```scala
import doobie._
import doobie.implicits._
import cats.effect.IO
import difflicious._
import difflicious.implicits._
import difflicious.munit.MUnitDiff._

case class Customer(id: Int, name: String, active: Boolean)
implicit val customerDiffer: Differ[Customer] = Differ.derived[Customer]

// Test a Doobie query
test("doobie query returns expected results") {
  val xa = Transactor.fromDriverManager[IO](
    "org.h2.Driver",
    "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
    "sa", ""
  )
  
  // Create schema and insert data
  val setup = for {
    _ <- sql"""
      CREATE TABLE customers (
        id SERIAL PRIMARY KEY,
        name VARCHAR NOT NULL,
        active BOOLEAN NOT NULL
      )""".update.run
    _ <- sql"""
      INSERT INTO customers (name, active) VALUES 
      ('Alice', true), ('Bob', false), ('Charlie', true)
      """.update.run
  } yield ()
  
  setup.transact(xa).unsafeRunSync()
  
  // Run the query we want to test
  val query = sql"SELECT id, name, active FROM customers WHERE active = true"
    .query[Customer]
    .to[List]
    
  val results = query.transact(xa).unsafeRunSync()
  
  // Compare with expected results
  val expected = List(
    Customer(1, "Alice", true),
    Customer(3, "Charlie", true)
  )
  
  // Use Difflicious to compare
  Differ[List[Customer]]
    .pairBy(_.id) // Match by ID regardless of order
    .assertNoDiff(results, expected)
}
```

### With Akka Actors

For testing Akka actor behavior:

```scala
import akka.actor.{ActorSystem, Props, Actor}
import akka.testkit.{TestKit, ImplicitSender, TestProbe}
import scala.concurrent.duration._
import difflicious._
import difflicious.implicits._
import difflicious.munit.MUnitDiff._

case class Message(id: String, payload: String)
case class Response(originalId: String, result: String, timestamp: Long)

implicit val messageDiffer: Differ[Message] = Differ.derived[Message]
implicit val responseDiffer: Differ[Response] = Differ.derived[Response]

// Create a differ that ignores non-deterministic timestamps
val responseTimestampIgnoredDiffer = responseDiffer.ignoreAt(_.timestamp)

class MyActor extends Actor {
  def receive = {
    case Message(id, payload) => 
      sender() ! Response(id, payload.toUpperCase, System.currentTimeMillis())
  }
}

// Test the actor
class MyActorSpec extends TestKit(ActorSystem("test-system")) 
  with ImplicitSender {
  
  test("actor transforms message correctly") {
    val actor = system.actorOf(Props[MyActor])
    val message = Message("123", "hello")
    
    actor ! message
    val response = expectMsgType[Response](3.seconds)
    
    // Expected response (ignore the timestamp)
    val expected = Response("123", "HELLO", 0) // timestamp will be ignored
    
    // Compare with Difflicious
    responseTimestampIgnoredDiffer.assertNoDiff(response, expected)
  }
}
```

### With ZIO

For testing ZIO applications:

```scala
import zio._
import zio.test._
import difflicious._
import difflicious.implicits._

case class UserProfile(id: String, name: String, lastLogin: Long)
implicit val profileDiffer: Differ[UserProfile] = Differ.derived[UserProfile]

// A ZIO service that returns user profiles
trait UserService {
  def getProfile(id: String): Task[UserProfile]
}

// Test implementation
val testUserService = new UserService {
  def getProfile(id: String): Task[UserProfile] = 
    ZIO.succeed(UserProfile(id, "Test User", System.currentTimeMillis()))
}

// Test spec
object UserServiceSpec extends ZIOSpecDefault {
  
  // Create a differ that ignores the timestamp
  val profileDifferIgnoringTimestamp = profileDiffer.ignoreAt(_.lastLogin)
  
  def spec = suite("UserService")(
    test("getProfile returns the correct profile") {
      for {
        profile <- testUserService.getProfile("123")
        expected = UserProfile("123", "Test User", 0) // timestamp will be ignored
        result = profileDifferIgnoringTimestamp.diff(profile, expected)
      } yield assertTrue(result.isOk)
    }
  )
}
```

These integration patterns demonstrate how to effectively use Difflicious with various Scala libraries and frameworks to create more readable and maintainable tests.

## Using Multiple Testing Frameworks

If your project uses multiple testing frameworks, you can import the specific integration you need in each test file:

```scala
// In a file using MUnit
import difflicious.munit.MUnitDiff._

// In a file using ScalaTest
import difflicious.scalatest.ScalatestDiff._
```

## Conclusion

Difflicious integrates well with popular Scala testing frameworks and libraries, making it easy to add powerful diffing capabilities to your tests. By using these integrations, you can produce clear, actionable test failure messages that help you understand and fix issues quickly.

If you need integration with a library that's not currently supported, consider contributing to the project or creating your own integration as shown above.
