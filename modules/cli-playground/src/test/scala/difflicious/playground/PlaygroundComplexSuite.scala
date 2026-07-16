package difflicious.playground

import cats.data.NonEmptyList
import difflicious.Differ
import difflicious.cats.implicits._
import difflicious.scalatest.ScalatestDiff._
import org.scalatest.funsuite.AnyFunSuite

final class PlaygroundComplexSuite extends AnyFunSuite {
  import PlaygroundComplexSuite._

  test("deep order invoice snapshot has nested differences") {
    Differ[OrderSnapshot].assertNoDiff(
      obtained = OrderSnapshot(
        order = Order(
          id = "order-1001",
          customer = Customer(
            id = "customer-42",
            name = "Ada Lovelace",
            email = "ada@example.com",
            loyaltyTier = "gold",
          ),
          shippingAddress = Address(
            line1 = "12 Analytical Engine Way",
            city = "London",
            country = "GB",
            postalCode = "NW1 6XE",
          ),
          lines = List(
            OrderLine(sku = "BOOK-ALG", description = "Algorithms Field Guide", quantity = 1, unitCents = 4500),
            OrderLine(sku = "PEN-BLK", description = "Black fountain pen", quantity = 2, unitCents = 1200),
          ),
          discounts = List(Discount(code = "LOYALTY10", cents = 690)),
        ),
        invoice = Invoice(
          number = "INV-2026-0001",
          status = "issued",
          totals = InvoiceTotals(subtotalCents = 6900, discountCents = 690, taxCents = 1242, grandTotalCents = 7452),
          payments = List(Payment(method = "card", reference = "auth-001", capturedCents = 7452)),
        ),
        fulfillment = Fulfillment(
          warehouse = "LHR-1",
          inventoryReservations = Map(
            "BOOK-ALG" -> InventoryReservation(warehouse = "LHR-1", reservedQuantity = 1, status = "allocated"),
            "PEN-BLK" -> InventoryReservation(warehouse = "LHR-1", reservedQuantity = 2, status = "allocated"),
          ),
          packages = List(
            ShipmentPackage(
              trackingNumber = "TRACK-001",
              items = NonEmptyList.of(
                PackedItem(sku = "BOOK-ALG", quantity = 1),
                PackedItem(sku = "PEN-BLK", quantity = 2),
              ),
            ),
          ),
        ),
        complianceReview = ComplianceReview(
          reviewer = Reviewer(id = "reviewer-7", name = "Mary Somerville", team = "risk"),
          checks = List(
            ReviewCheck(name = "billing-address", passed = true, notes = "Address verified"),
            ReviewCheck(name = "payment-risk", passed = true, notes = "Low risk card authorization"),
          ),
          decision = ReviewDecision(status = "approved", reason = "All automated and manual checks passed"),
        ),
        auditTrail = AuditTrail(
          createdBy = "checkout-api",
          updatedBy = "warehouse-sync",
          revision = 12,
        ),
        operationsMemo = OperationsMemo(
          note = "Customer called to confirm delivery window",
          priority = "normal",
          visibleToSupport = true,
        ),
      ),
      expected = OrderSnapshot(
        order = Order(
          id = "order-1001",
          customer = Customer(
            id = "customer-42",
            name = "Ada Lovelace",
            email = "ada.lovelace@example.com",
            loyaltyTier = "platinum",
          ),
          shippingAddress = Address(
            line1 = "12 Analytical Engine Way",
            city = "London",
            country = "GB",
            postalCode = "NW1 6XE",
          ),
          lines = List(
            OrderLine(sku = "BOOK-ALG", description = "Algorithms Field Guide", quantity = 2, unitCents = 4500),
            OrderLine(sku = "NOTE-A5", description = "A5 notebook", quantity = 1, unitCents = 900),
            OrderLine(sku = "PEN-BLK", description = "Black fountain pen", quantity = 2, unitCents = 1200),
          ),
          discounts = List(Discount(code = "LOYALTY15", cents = 1710)),
        ),
        invoice = Invoice(
          number = "INV-2026-0001",
          status = "paid",
          totals = InvoiceTotals(subtotalCents = 12300, discountCents = 1710, taxCents = 2118, grandTotalCents = 12708),
          payments = List(
            Payment(method = "card", reference = "auth-001", capturedCents = 7452),
            Payment(method = "store-credit", reference = "credit-779", capturedCents = 5256),
          ),
        ),
        fulfillment = Fulfillment(
          warehouse = "LHR-2",
          inventoryReservations = Map(
            "BOOK-ALG" -> InventoryReservation(warehouse = "LHR-2", reservedQuantity = 2, status = "allocated"),
            "NOTE-A5" -> InventoryReservation(warehouse = "LHR-2", reservedQuantity = 1, status = "backordered"),
            "PEN-BLK" -> InventoryReservation(warehouse = "LHR-1", reservedQuantity = 2, status = "allocated"),
          ),
          packages = List(
            ShipmentPackage(
              trackingNumber = "TRACK-001",
              items = NonEmptyList.of(
                PackedItem(sku = "BOOK-ALG", quantity = 2),
                PackedItem(sku = "PEN-BLK", quantity = 2),
              ),
            ),
            ShipmentPackage(
              trackingNumber = "TRACK-002",
              items = NonEmptyList.of(PackedItem(sku = "NOTE-A5", quantity = 1)),
            ),
          ),
        ),
        complianceReview = ComplianceReview(
          reviewer = Reviewer(id = "reviewer-7", name = "Mary Somerville", team = "risk"),
          checks = List(
            ReviewCheck(name = "billing-address", passed = true, notes = "Address verified"),
            ReviewCheck(name = "payment-risk", passed = true, notes = "Low risk card authorization"),
          ),
          decision = ReviewDecision(status = "approved", reason = "All automated and manual checks passed"),
        ),
        auditTrail = AuditTrail(
          createdBy = "checkout-worker",
          updatedBy = "billing-sync",
          revision = 19,
        ),
        operationsMemo = OperationsMemo(
          note = "Customer requested invoice copy",
          priority = "high",
          visibleToSupport = false,
        ),
      ),
    )
  }
}

object PlaygroundComplexSuite {
  final case class OrderSnapshot(
    order: Order,
    invoice: Invoice,
    fulfillment: Fulfillment,
    complianceReview: ComplianceReview,
    auditTrail: AuditTrail,
    operationsMemo: OperationsMemo,
  )
  
  object OrderSnapshot {
    given Differ[OrderSnapshot] = Differ.derived[OrderSnapshot].ignoreAt(_.auditTrail)
  }

  final case class Order(
    id: String,
    customer: Customer,
    shippingAddress: Address,
    lines: List[OrderLine],
    discounts: List[Discount],
  ) derives Differ

  final case class Customer(id: String, name: String, email: String, loyaltyTier: String) derives Differ

  final case class Address(line1: String, city: String, country: String, postalCode: String) derives Differ

  final case class OrderLine(sku: String, description: String, quantity: Int, unitCents: Int) derives Differ

  final case class Discount(code: String, cents: Int) derives Differ

  final case class Invoice(number: String, status: String, totals: InvoiceTotals, payments: List[Payment])
      derives Differ

  final case class InvoiceTotals(subtotalCents: Int, discountCents: Int, taxCents: Int, grandTotalCents: Int)
      derives Differ

  final case class Payment(method: String, reference: String, capturedCents: Int) derives Differ

  final case class Fulfillment(
    warehouse: String,
    inventoryReservations: Map[String, InventoryReservation],
    packages: List[ShipmentPackage],
  ) derives Differ

  final case class InventoryReservation(warehouse: String, reservedQuantity: Int, status: String) derives Differ

  final case class ShipmentPackage(trackingNumber: String, items: NonEmptyList[PackedItem]) derives Differ

  final case class PackedItem(sku: String, quantity: Int) derives Differ

  final case class ComplianceReview(
    reviewer: Reviewer,
    checks: List[ReviewCheck],
    decision: ReviewDecision,
  ) derives Differ

  final case class Reviewer(id: String, name: String, team: String) derives Differ

  final case class ReviewCheck(name: String, passed: Boolean, notes: String) derives Differ

  final case class ReviewDecision(status: String, reason: String) derives Differ

  final case class AuditTrail(createdBy: String, updatedBy: String, revision: Int) derives Differ

  final case class OperationsMemo(note: String, priority: String, visibleToSupport: Boolean)

  object OperationsMemo {
    given Differ[OperationsMemo] = Differ.derived[OperationsMemo].ignore
  }
}
