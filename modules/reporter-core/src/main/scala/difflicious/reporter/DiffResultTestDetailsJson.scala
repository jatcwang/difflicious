package difflicious.reporter

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import difflicious.DiffResult

import scala.annotation.nowarn

// Keep the configured macro call separate from the model companion to avoid intermittent Scala 2 compiler failures.
// See https://github.com/plokhotnyuk/jsoniter-scala/blob/master/README.md#known-issues (item 2).
private[reporter] object DiffResultTestDetailsJson {
  @nowarn("msg=match may not be exhaustive")
  implicit lazy val jsonValueCodec: JsonValueCodec[DiffResultTestDetails] = {
    implicit val diffResultJsonValueCodec: JsonValueCodec[DiffResult] =
      DiffResultJson.diffResultJsonValueCodec

    JsonCodecMaker.make[DiffResultTestDetails](
      CodecMakerConfig
        .withTransientNone(false)
        .withTransientDefault(false),
    )
  }
}
