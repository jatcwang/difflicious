package difflicious

import difflicious.internal.ToPairByOps
import difflicious.utils.{EachableInstances, ToEachableOps, PairableInstances, ToSubTypeOp}

object implicits extends ToEachableOps with EachableInstances with PairableInstances with ToSubTypeOp with ToPairByOps
