# Difflicious

# TODO:
Must have:
- Customizable "equals"? (e.g. cats.Eq)
- Undo
  
Nice to have:
- Nice string diff

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
