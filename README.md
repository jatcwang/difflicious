# Difflicious

# TODO:
Must have:
- Replace differ at path
- Swap two fields of the same type! (or a subtype to a super type)
- Dynamic ignore (add new field ignores at runtime)
  
Nice to have:
- Print a single value (i.e. no need for pprint if you want nice printout)
- Nice string diff
- Undo
- Customizable "equals"? (e.g. cats.Eq)
- Custom value output (e.g. JPG)
- Modify map keys

# Design

- Different types should still show fields?

## Set

- Match by full value initially

Operations:
- Match entries by field (e.g. ID)

## Product (case class)

Compare field by field

## Map

Operations:
- Swap two values of key

## List

- Initially compare by index

Operations:
- Match entries by field (e.g. ID)
- Swap two index

# API

## Result API

Example expected:

[
  {
    (CaseCls)
    id: "1"
    f1: {
      str: "a"
    }
  }
]

compare with obtained

[
  {
    (CaseCls)
    id: "1"
    f1: {
      str: "b"
    }
  },
  {
    (CaseCls)
    id: "2",
    f1: {
      str: "c"
    }
  }
]

type: matching
archetype: list
items: 
  - type: matching
    archetype: record
    fields: 
      f1: 
        type: matching // redundant?
        cls: F
        archetype: record
        fields:
          str:
            archetype: string
            obtained: b
            expected: a
    
  - type: obtainedOnly
    value:
      cls: CaseCls
      archetype: record
      fields:
        f1:
          type: obtainedOnly
          cls: F
          archetype: record
          fields:
            str:
              type: obtainedOnly
              archetype: string
              obtained: c
    
# Set
Similar to list, just different available operations
```
archetype: set
entries:

```

# Map

Allow swapping two keys or values, as well as deleting an entry
Adding an entry is more difficult

```
archetype: map
entries:
  - key:
      archetype: string
      obtained: k1
    value:
      archetype: record
      type: matching
      fields:
        ...
  - key:
      archetype: string
      obtained: k2
    value:
      archetype: record
      type: obtainedOnly
      fields:
```
  
map / list / set

## Operation API


