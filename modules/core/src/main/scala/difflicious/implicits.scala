package difflicious

import difflicious.internal.ToPairByOps
import difflicious.utils.{ToSubTypeOp, EachableInstances, ToEachableOps, PairableInstances}

object implicits extends ToEachableOps with EachableInstances with PairableInstances with ToSubTypeOp with ToPairByOps
