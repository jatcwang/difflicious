package difflicious

import scala.annotation.nowarn

package object testutils {
  implicit class EitherExtensions[A, B](val either: Either[A, B]) extends AnyVal {
    @nowarn("msg=.*deprecated.*")
    def unsafeGet: B = either.right.get
  }
}
