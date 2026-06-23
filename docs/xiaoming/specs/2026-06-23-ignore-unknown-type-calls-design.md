# 2026-06-23 Ignore Unknown Type Calls Design

This document details the design for ignoring method calls where the target class type is resolved as `"Unknown"` during static code analysis.

## Background

In the current code-analysis platform:
1. When parsing Java source code, some field variables or expressions (e.g. from local variables, chained method results, or external libraries) cannot be fully resolved to their source definitions, resulting in their `objectType` being set to `"Unknown"`.
2. Showing these `"Unknown"` class nodes in the frontend mind map (e.g., calls like `Unknown.status()`, `Unknown.orderType()`) adds technical noise and degrades user experience.
3. The user wants to ignore and filter out all method calls whose resolved type is `"Unknown"`.

---

## Proposed Changes

### 1. Update Filtering Logic in `JavaSourceParser.java`

We will update [JavaSourceParser.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/code/analyst/parser/JavaSourceParser.java):
* In `isIgnoredCall(String objectName, String objectType, String methodName)`:
  Add an immediate early return returning `true` if `objectType` is `"Unknown"`.

---

## Verification Plan

### Automated Tests
* Run `mvn test` in the terminal to verify that all existing tests in `JavaSourceParserTest` pass successfully.

### Manual Verification
* Start the Spring Boot application.
* Load a calling tree that previously displayed `Unknown` class method calls, and verify that those `Unknown` nodes are no longer visible on the UI mind map.
