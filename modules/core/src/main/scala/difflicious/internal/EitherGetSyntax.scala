package difflicious.internal

import scala.annotation.nowarn

private[difflicious] object EitherGetSyntax {
  implicit class EitherExts[A, B](val e: Either[A, B]) extends AnyVal {
    @nowarn("msg=.*deprecated.*")
    def unsafeGet: B = e.right.get
  }
}
