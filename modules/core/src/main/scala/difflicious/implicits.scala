package difflicious

import difflicious.utils.{EachableInstances, ToEachableOps, PairableInstances, ToSubTypeOp}

object implicits extends ToEachableOps with EachableInstances with PairableInstances with ToSubTypeOp
