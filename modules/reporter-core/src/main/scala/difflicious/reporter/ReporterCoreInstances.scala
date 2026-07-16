package difflicious.reporter

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import difflicious.DiffResult

trait ReporterCoreInstances {
  implicit val diffResultJsonValueCodec: JsonValueCodec[DiffResult] =
    DiffResultJson.diffResultJsonValueCodec
}

object ReporterCoreInstances extends ReporterCoreInstances
