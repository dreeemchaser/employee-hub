# Workflow — How We Work

Process conventions for this repo. Stack/structure/product rules live in the other steering files; this file is about the working loop.

## Git & Branching

- Never commit or push unless explicitly asked.
- One branch per feature: `feature/<slug>` (e.g. `feature/auth-refresh-tokens`), branched off `master`.
- One PR per feature. Keep PR titles under ~70 chars; put detail in the body (summary, what was tested, blockers).
- Stage specific files by name — avoid `git add .` / `git add -A`.
- Never force-push, reset --hard, or amend pushed commits unless explicitly asked.

## Verify Before Done

- Backend: run `mvnw verify` (or `mvnw test`) and confirm green before declaring a change complete. A command exiting 0 is not proof on its own — check the reported test counts.
- Never claim a task is done without stating what was verified.

### JDK for the build (Windows)
- The project targets **Java 21** (Spring Boot 3.5.x supports 17–24). Build with a JDK in that range.
- `JAVA_HOME` may be unset in fresh shells. Installed JDK 21 lives at `C:\Program Files\Java\jdk-21.0.12.1`. Set it per-session before Maven: `$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.12.1"`.
- Do NOT build with the JDK 27 also on this machine — Lombok + `maven-compiler-plugin` here crash on it (`ExceptionInInitializerError: com.sun.tools.javac.tree.EndPosTable`). A Java 8 JRE is also present but has no compiler.
- Maven output gets mangled by the shell echo; pipe to a log file (`... 2>&1 | Out-File target\x.log`) and read that instead.

## Progress Tracking

- The roadmap and progress log live in `docs/next-gen-features.md` (Gap Analysis → Progress Log + section B status markers).
- When a `B.x` item merges to `master`, update its status marker there (✅ DONE) and add a Progress Log row. This is easy to forget — do it as part of the merge, not later.
- Actual controllers are the source of truth for the API, not `docs/api-contract.md` (which is aspirational).

## Environment Quirks (Windows)

- Git lives at `C:\Program Files\Git\cmd`. If a fresh shell reports `git` not found, prepend it for the session: `$env:Path += ";C:\Program Files\Git\cmd"`, or invoke directly: `& "C:\Program Files\Git\cmd\git.exe" ...`.
- This PowerShell echoes the command back mangled in stdout and often reports `Exit Code: -1` even on success. Judge success by the actual command output, not the exit code.
- Use `;` as the command separator, not `&&`.
