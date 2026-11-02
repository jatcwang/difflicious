package example

import difflicious.Differ
import difflicious.ziotest.ZIODiffliciousSpec
import zio.test.ZIOSpecDefault

object ZIOTestExampleSpec extends ZIOSpecDefault with ZIODiffliciousSpec {
  def spec = suite("ZIOTestExampleSpec")(
    suite("inventory")(
      test("stock levels match") {
        Differ[Stock].assertNoDiff(
          obtained = Stock(sku = "KB-104", warehouse = "LHR-2", quantity = 12),
          expected = Stock(sku = "KB-104", warehouse = "LHR-1", quantity = 15),
        )
      },
      test("transfer moves stock between warehouses") {
        Differ[Stock].assertNoDiff(
          obtained = Stock(sku = "KB-104", warehouse = "LHR-1", quantity = 15),
          expected = Stock(sku = "KB-104", warehouse = "LHR-1", quantity = 12),
        ) && Differ[Stock].assertNoDiff(
          obtained = Stock(sku = "KB-104", warehouse = "LHR-2", quantity = 3),
          expected = Stock(sku = "KB-104", warehouse = "LHR-2", quantity = 6),
        )
      },
    ),
  )

  final case class Stock(sku: String, warehouse: String, quantity: Int) derives Differ
}
