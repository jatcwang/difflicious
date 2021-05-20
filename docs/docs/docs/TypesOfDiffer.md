---
layout: docs
title:  "Types of Differ"
permalink: docs/typesofdiffer
---

# Types of Differs

There are many types of basic Differs, each producing different kind of results.

# Seq Differ

Differ for sequences (`Differ.seqDiffer`) allow diffing immutable sequences like `Seq`, `List`, and `Vector`.

By default, Seq Differs will match elements by their index in the sequence.

<pre class="diff-render">
List(
  Person(
    name: <span style="color: red;">"Alice"</span> -> <span style="color: green;">"Bob"</span>,
    age: <span style="color: red;">30</span> -> <span style="color: green;">25</span>,
  ),
  Person(
    name: <span style="color: red;">"Bob"</span> -> <span style="color: green;">"Alice"</span>,
    age: <span style="color: red;">25</span> -> <span style="color: green;">30</span>,
  ),
)</pre>

# Map differ

Map Differ
