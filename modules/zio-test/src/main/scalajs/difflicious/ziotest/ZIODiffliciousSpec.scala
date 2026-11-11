package difflicious.ziotest

import zio.test.{TestAspect, TestAspectPoly}

trait ZIODiffliciousSpec extends ZIODiffliciousSpecBase {
  // Reports are only written on the JVM
  override protected def diffliciousReporting: TestAspectPoly = TestAspect.identity
}
