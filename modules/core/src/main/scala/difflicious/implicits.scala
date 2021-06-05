package difflicious

import difflicious.internal.ToPairByOps
import difflicious.utils.{Eachable2Instances, EachableInstances, ToEachableOps, PairableInstances, ToSubTypeOp}

object implicits
    extends ToEachableOps
    with EachableInstances
    with Eachable2Instances
    with PairableInstances
    with ToSubTypeOp
    with ToPairByOps
