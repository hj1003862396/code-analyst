# Spec - Fix Hanging Initialize Endpoint and Optimize Implementation Resolution

We will resolve the performance issues and endpoint hanging under `/api/tree/initialize` by optimizing how implementations are resolved and improving call expression filtering.

## 1. Problem Statement
The `/api/tree/initialize` and `/api/tree/expand` endpoints hang when parsing methods containing calls to non-interfaces (e.g. local controller methods like `nuonuoInvoiceApply(req)`, framework utility classes like `ContextUtils`, or libraries like `StrUtil`). This is because `ApiController.resolveImplementation` performs a full directory recursion (`Files.walk`) to search for implements patterns in files every time a class does not end with "Impl".

## 2. Proposed Changes

### 2.1 ApiController Implementation Optimization

- In `resolveImplementation`:
  - Check if the target class is an interface. If it is a class, skip searching and return the class name immediately.
- In `findImplementationBySearch`:
  - Retrieve the candidate Java paths from memory-cached `javaFileByNameCache` instead of executing a recursive walk (`Files.walk`) on the file system.

### 2.2 JavaSourceParser Filtering Optimization

- In `isIgnoredCall`:
  - Add filters for utility classes, exceptions, contexts, configs, constants, and helper classes by checking suffixes (e.g., `util`, `utils`, `helper`, `helpers`, `context`, `contexts`, `config`, `configs`, `constant`, `constants`, `exception`, `exceptions`).

## 3. Verification

### 3.1 Automated Verification
- Run existing and new JUnit tests.
- Add a test verifying utility, config, and exception calls are ignored by the parser.

### 3.2 Manual Verification
- Execute curl requests against the initialization API to ensure response is instant.
