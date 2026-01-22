## General
* Avoid code duplication; prefer composition over inheritance.
* Write clear and concise code with meaningful variable and function names.
* Follow SOLID principles for object-oriented design.
* Prefer imports over qualified names for better readability.
* Research the Hytale API in `/decompiled` folder before implementing features.
* `/dev-server` is the recommended environment for testing server-side mods locally.

## Kotlin
* Use idiomatic Kotlin constructs and libraries.
* Prefer `val` over `var` for immutability.
* Use data classes for simple data holders.
* Leverage Kotlin's null safety features.
* **NEVER use Java-style nullability annotations** (`@Nonnull`, `@Nullable`) - use Kotlin's built-in nullable syntax (`?`) instead.
  - Non-null types are the default: `fun foo(bar: String)` (bar cannot be null)
  - Nullable types use `?`: `fun foo(bar: String?)` (bar can be null)
  - Nullable return types: `fun getBar(): String?` (can return null)
* Use extension functions to add functionality to existing classes.
* Prefer higher-order functions and lambdas for functional programming.
* Use coroutines for asynchronous programming.
* Follow Kotlin coding conventions for naming and formatting.
* Utilize sealed classes for representing restricted class hierarchies.
* Use the `when` expression for conditional logic instead of multiple `if-else` statements.
* Prefer `List` and `Map` over arrays for collections.
* Use string templates for string concatenation.
* Leverage Kotlin's standard library functions for collections (e.g., `map`, `filter`, `reduce`).
* Use `object` declarations for singletons.
* Prefer `by lazy` for lazy initialization.
* Use `companion object` for static members.
* Follow best practices for exception handling using `try-catch` blocks and custom exceptions.