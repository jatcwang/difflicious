package example

import cats.effect.IO
import difflicious.Differ
import difflicious.weaver.WeaverDiffliciousSuite
import weaver.SimpleIOSuite

object WeaverExampleSuite extends SimpleIOSuite with WeaverDiffliciousSuite[IO] {
  pureTest("service configuration matches") {
    Differ[ServiceConfig].assertNoDiff(
      obtained = ServiceConfig(image = "checkout:1.4.1", replicas = 2),
      expected = ServiceConfig(image = "checkout:1.5.0", replicas = 3),
    )
  }

  final case class ServiceConfig(image: String, replicas: Int) derives Differ
}
