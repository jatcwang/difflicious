package difflicious

trait DifferGen:

  inline def derived[T]: Differ[T] = ${ DifferMacros.deriveImpl[T] }

  /** Derives a [[Differ]] for `T`, recursively deriving any missing field [[Differ]] instances.
    *
    * Unlike [[derived]], this does not require every field's [[Differ]] to already be in implicit scope. If a field
    * instance is available, that instance is used. Otherwise, Difflicious attempts to derive the missing field instance
    * and then lets ordinary implicit search build the field [[Differ]]. This means existing instances such as
    * collection, option, either, tuple, or user-defined instances still take precedence over structural derivation of
    * those types.
    *
    * Use this when you want semi-automatic derivation at a specific call site without enabling
    * `difflicious.generic.auto` for the surrounding scope.
    */
  inline def derivedDeep[T]: Differ[T] = ${ DifferMacros.deriveDeepImpl[T] }
