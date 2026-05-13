---
name: Korean Path + JDK 17 Encoding Trap
description: JDK 17 cannot run Gradle tests when the project sits under a Korean path; compile/assemble works fine
type: feedback
---

**Rule**: When a Java project lives under a path containing 한글, `gradle test` (and any task that forks a JVM with `@argfile` for the classpath) will fail with `ClassNotFoundException` for the test classes themselves. The SamhanLogis project was moved to **`C:\dev\SamhanLogis`** on 2026-05-04 specifically to escape this trap; the OneDrive 한국어 path copy is deprecated and may be deleted by the user.

**Why**: JDK 17's launcher reads `@argfile` using the Windows ANSI code page. On ko-KR Windows that's CP949. Gradle writes the file in UTF-8. Korean path bytes get reinterpreted, producing a non-existent classpath entry — the JVM can find every JAR under `~/.gradle/caches\...` (no Korean) but **not** `build/classes/java/test/...` (Korean in the project path). `-Dfile.encoding=UTF-8` doesn't help because the launcher parses `@argfile` before any `-D` flag is applied. JEP 400 (UTF-8 by default) ships in JDK 21+, not 17.

**How to apply**:
- For local validation, run `./gradlew assemble` (compiles + bootJar) — works under the Korean path. Avoid `./gradlew build` and `:test` locally.
- If the user wants to actually run unit tests locally, recommend one of:
  1. Move project to ASCII path (e.g. `C:\dev\SamhanLogis`) — cleanest
  2. Upgrade JDK to 21+
  3. Run tests in a Linux container or CI/CD only
- Don't waste cycles on `-Dsun.jnu.encoding=UTF-8` or test-task system properties — they fire after the corruption.
- Compile-only deprecation warnings on Korean paths are normal (`Note: ... uses or overrides a deprecated API` rendered with garbled chars in the terminal); the underlying compile is still correct.
