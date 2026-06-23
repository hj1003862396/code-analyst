# 2026-06-23 Ignore Lock Method Calls Design

This document details the design for ignoring lock-related method calls (specifically methods named `lock` and `unlock`) during the static code analysis of Java method calling trees.

## Background

In the current code-analysis platform:
1. We parse Java files using JavaParser to construct the method calling chain tree.
2. We filter out common Java standard library calls, logging calls, getters, setters, utility classes, and custom exception classes via `JavaSourceParser.isIgnoredCall` to minimize noise in the generated mind map tree.
3. Lock-related operations (such as calling `lockClient.lock()` or `lockClient.unlock()`) are technical/infra-level operations rather than business logic calling paths. Displaying these calls in the mind map creates unnecessary noise for business analyst workflows.
4. The user wants to ignore any method calls named `lock` or `unlock` regardless of the class name.

---

## Proposed Changes

### 1. Update Filtering Logic in `JavaSourceParser.java`

We will update [JavaSourceParser.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/code/analyst/parser/JavaSourceParser.java):
* In `isIgnoredCall(String objectName, String objectType, String methodName)`:
  Add checking for `methodName.equals("lock")` and `methodName.equals("unlock")` into the general list of ignored method names.

---

### 2. Supplement Test Cases in `JavaSourceParserTest.java`

We will update [JavaSourceParserTest.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/test/java/com/code/analyst/parser/JavaSourceParserTest.java):
* In `testIgnoredCallFiltering()`:
  - Add standard mock field declaration: `private LockClient lockClient;`
  - In `doSomething()` method body, add statements calling `lockClient.lock();` and `lockClient.unlock();`.
  - Assert that the parser successfully ignores these calls and still yields only the target business method calls.

---

## Verification Plan

### Automated Tests
* Run `mvn test` in the terminal to verify that all tests in `JavaSourceParserTest` pass successfully.

### Manual Verification
* Start the Spring Boot application.
* Load a calling tree that performs lock operations, and verify that the `lock` or `unlock` method calls are not displayed as nodes in the UI mind map.
