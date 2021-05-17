---
layout: docs
title:  "Introduction"
permalink: docs/introduction
---

# Motivation

The most used assertion in software testing is asserting that two objects in memory are equal to each other - that
the **obtained** value from the code you're testing is equal to some **expected** value.

In most testing libraries they use `equals` method (`==`). 

While this works well initially, for more complex problem domains we discovered that:

- Failure diffs for large models (e.g. case classes, large lists) are completely unreadable - it's impossible to tell what is different at a glance .
- We sometimes want to ignore some fields in the comparison because
  - They are values generated external to your system e.g. database-generated IDs in an integration test
  - They are not relevant to the current test
  
We want to spend our time actually fixing code, not deciphering big diffs. So we wrote **Difflicious**!
