package difflicious.cli

import difflicious.Differ
import munit.FunSuite
import snapshot4s.generated.*
import snapshot4s.munit.SnapshotAssertions

import RendererSpec.*

class RendererSpec extends FunSuite with SnapshotAssertions {
  test("json renderer") {
    val run = orderDiff

    assertRendererSnapshot(
      JsonRenderer.renderString(run.result, run.changes),
      "RendererSpec/json.snap",
    )
  }

  test("plain renderer for a raw comparison") {
    val run = orderDiff

    assertRendererSnapshot(
      PlainRenderer.render(run.result, run.changes),
      "RendererSpec/plain-raw-comparison.snap",
    )
  }

  test("plain renderer for a listener report"):
    assertRendererSnapshot(PlainRenderer.render(exampleReport), "RendererSpec/plain-listener-report.snap")

  test("plain renderer for an empty report"):
    assertRendererSnapshot(PlainRenderer.render(DiffReport(Vector.empty)), "RendererSpec/plain-empty-report.snap")

  private def exampleReport: DiffReport = {
    val run = orderDiff

    DiffReport(
      Vector(
        run.copy(
          metadata = Some(
            DiffRunMetadata(
              runId = "01ARZ3NDEKTSV4RRFFQ69G5FAV",
              testId = "01ARZ3NDEKTSV4RRFFQ69G5FAW",
              suiteName = "OrderSuite",
              suiteId = "example.OrderSuite",
              suiteClassName = Some("example.OrderSuite"),
              testName = "order snapshot matches",
              testText = "order snapshot matches",
              testHierarchy = Vector("order snapshot matches"),
              fileName = "OrderSuite.scala",
              filePath = "/workspace/OrderSuite.scala",
              lineNumber = 37,
            ),
          ),
        ),
      ),
    )
  }

  private def orderDiff: DiffRun =
    DiffRun.fromResult(Differ[OrderSnapshot].diff(obtainedOrder, expectedOrder), metadata = None)

  private def assertRendererSnapshot(rendered: String, path: String): Unit =
    assertFileSnapshot(rendered.stripSuffix("\n") + "\n", path)
}

private object RendererSpec {
  final case class Customer(id: String, name: String, email: String, loyaltyTier: String) derives Differ
  final case class Address(line1: String, city: String, country: String, postalCode: String) derives Differ
  final case class OrderLine(sku: String, description: String, quantity: Int, unitCents: Int) derives Differ
  final case class OrderSnapshot(
    orderId: String,
    customer: Customer,
    shipping: Address,
    lines: List[OrderLine],
    status: String,
    notes: List[String],
  ) derives Differ

  val obtainedOrder: OrderSnapshot =
    OrderSnapshot(
      orderId = "ORD-1042",
      customer = Customer("CUS-7", "Alice", "alice@old.example", "gold"),
      shipping = Address("10 Market Street", "London", "UK", "EC1A 1BB"),
      lines = List(
        OrderLine("SKU-RED", "Red travel mug", quantity = 1, unitCents = 1899),
        OrderLine("SKU-OLD", "Discontinued lid", quantity = 1, unitCents = 499),
      ),
      status = "processing",
      notes = Nil,
    )

  val expectedOrder: OrderSnapshot =
    OrderSnapshot(
      orderId = "ORD-1042",
      customer = Customer("CUS-7", "Alice", "alice@example.com", "gold"),
      shipping = Address("10 Market Street", "Bristol", "UK", "EC1A 1BB"),
      lines = List(
        OrderLine("SKU-RED", "Red travel mug", quantity = 2, unitCents = 1899),
      ),
      status = "shipped",
      notes = List("Leave with reception"),
    )
}
