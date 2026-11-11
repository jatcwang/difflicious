package difflicious.ziotest

import difflicious.utils.SeqLike
import zio.NonEmptyChunk

// Only provides the SeqLike. Core's Differ.seqDiffer builds the differ from it, and so gives way to any differ a user
// defines or imports. This is a def so that a differ built while a spec initialises doesn't capture null.
trait ZIOInstances {
  implicit def nonEmptyChunkAsSeq: SeqLike[NonEmptyChunk] = ZIOInstances.nonEmptyChunkAsSeq
}

private object ZIOInstances {
  val nonEmptyChunkAsSeq: SeqLike[NonEmptyChunk] = new SeqLike[NonEmptyChunk] {
    override def asSeq[A](f: NonEmptyChunk[A]): Seq[A] = f.toChunk
  }
}
