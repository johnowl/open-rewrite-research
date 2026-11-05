# OpenRewrite security research

A demo of fixing real Spring vulnerabilities with one OpenRewrite YAML recipe per CVE, using JUnit
tests to prove each issue is there before the fix and gone after it.

> [!WARNING]
> `vulnerable-app` is **deliberately vulnerable** and uses throwaway credentials. Run it locally for
> testing only and never deploy it. Dependency scanners will flag this repository; that's the point.

| Branch | State |
|---|---|
| [`main`](../../tree/main) | Vulnerable: Spring Boot 4.0.3 and Spring AI 2.0.0 |
| [`fixed`](../../tree/fixed) | `main` plus exactly what the six recipes changed ([see the diff](../../compare/main...fixed)) |

## Run the demo

You need JDK 21 and Maven. If your default JDK is a different version, set `JAVA_HOME`.

```sh
./demo.sh prove                  # run the tests and show which CVEs are present
./demo.sh fix CVE-2026-47851     # apply one CVE's recipe
./demo.sh fix                    # or apply all six, in ascending CVE order
./demo.sh prove                  # run the same tests again
./demo.sh reset                  # undo the recipes' changes, so you can run the demo again
```

Before any fix:

```text
Spring Boot 4.0.3
  CVE-2026-22732   VULNERABLE
  CVE-2026-22753   VULNERABLE
  CVE-2026-40976   VULNERABLE
  CVE-2026-41707   VULNERABLE
  CVE-2026-47849   VULNERABLE
  CVE-2026-47851   VULNERABLE
```

After `./demo.sh fix`:

```text
Spring Boot 4.0.6
  CVE-2026-22732   not vulnerable
  CVE-2026-22753   not vulnerable
  CVE-2026-40976   not vulnerable
  CVE-2026-41707   not vulnerable
  CVE-2026-47849   not vulnerable
  CVE-2026-47851   not vulnerable
```

To run the tests without the script, use `mvn test` in `vulnerable-app`. On `main` the build fails
because the tests fail, and that failure is the proof.

## The vulnerabilities

| CVE | Severity | Component | Fixed in |
|---|---|---|---|
| [CVE-2026-40976](#cve-2026-40976-default-security-allows-all-requests) | Critical | Spring Boot | 4.0.6 |
| [CVE-2026-22732](#cve-2026-22732-security-headers-are-not-written) | Critical | Spring Security | 7.0.4 |
| [CVE-2026-22753](#cve-2026-22753-the-admin-security-chain-never-runs) | High | Spring Security | 7.0.5 |
| [CVE-2026-41707](#cve-2026-41707-a-stolen-dpop-proof-can-be-replayed) | High | Spring Security | 7.0.7 |
| [CVE-2026-47849](#cve-2026-47849-json-patch-overwrites-another-users-record) | High | Spring Data REST | 5.0.7 |
| [CVE-2026-47851](#cve-2026-47851-a-crafted-pdf-overflows-the-stack) | High | Spring AI | 2.0.1 |

Severities and fixed versions come from the Spring advisories, linked below. Fixed versions are the
open source releases for the lines this app uses.

### CVE-2026-40976: default security allows all requests

[Advisory](https://spring.io/security/cve-2026-40976)

- **Issue:** when a servlet app has Actuator but not `spring-boot-health` and relies on Boot's default
  security, the default filter chain has no authorization rules, so every endpoint is open.
- **In this app:** `pom.xml` depends on `spring-boot-actuator-autoconfigure` without
  `spring-boot-health`, and the default profile has no security configuration.
- **Test:** an anonymous `GET /report` must return 401. On `main` it returns the report.

### CVE-2026-22732: security headers are not written

[Advisory](https://spring.io/security/cve-2026-22732)

- **Issue:** Spring Security writes its headers when the response commits, but it didn't notice
  `setHeader("Content-Length", ...)`. A response could commit without the headers, which can expose
  sensitive data through caching.
- **In this app:** nothing unusual is needed. `ReportController.report()` is a plain controller that
  returns a `String`.
- **Test:** the response must include `Cache-Control`. On `main` it doesn't.

### CVE-2026-22753: the admin security chain never runs

[Advisory](https://spring.io/security/cve-2026-22753)

- **Issue:** a `PathPatternRequestMatcher.Builder` bean that adds the servlet path is ignored when
  matching a filter chain. The chain doesn't match its requests, so its security rules don't run.
- **In this app:** `AdminSecurityConfig` protects `/admin/**` under the servlet path `/api`, and
  everything else is public. Requests fall through to the public chain.
- **Test:** an anonymous `GET /api/admin/report` must return 401. On `main` it returns the admin report.
- **Note:** the advisory names `securityMatchers(String)`. This test reproduces it with
  `securityMatcher(String)`, the method that reads the ignored builder. The
  `securityMatchers { it.requestMatchers(...) }` form we tried first did not reproduce it.

### CVE-2026-41707: a stolen DPoP proof can be replayed

[Advisory](https://spring.io/security/cve-2026-41707)

- **Issue:** each DPoP proof should work only once, but used proofs are remembered in a cache limited
  to 1,000 entries. An attacker can flood it to evict a stolen proof, then use that proof again.
- **In this app:** `DpopProofVerifier` uses Spring Security's default validation.
- **Test:** use a proof, send 1,100 other proofs, then replay the first one. It must be rejected. On
  `main` it's accepted.

### CVE-2026-47849: JSON Patch overwrites another user's record

[Advisory](https://spring.io/security/cve-2026-47849)

- **Issue:** JSON Patch requests could change `@Id` properties. Changing a record's ID to another
  record's ID means the save overwrites that other record.
- **In this app:** `Ticket` has a public `id` setter and is exposed through Spring Data REST.
- **Test:** Mallory patches her ticket's `id` to Alice's. Alice's ticket must keep its owner. On `main`
  it's replaced.
- **Note:** this needed a store without identifier checks. With JPA, Hibernate rejected the change with
  a 500 and nothing was overwritten, so this app uses Spring Data KeyValue instead.

### CVE-2026-47851: a crafted PDF overflows the stack

[Advisory](https://spring.io/security/cve-2026-47851)

- **Issue:** Spring AI's PDF reader walks a PDF's table of contents with no depth limit.
- **In this app:** `PdfIngestion` uses `ParagraphPdfDocumentReader`.
- **Test:** ingest a generated PDF with an outline 20,000 levels deep. It must not cause a
  `StackOverflowError`. On `main` it does.

## How the tests prove it

Each CVE has its own class in
[`vulnerable-app/src/test/.../cve`](vulnerable-app/src/test/kotlin/com/example/tickets/cve). The tests
run the actual attack and assert the **secure** behavior, so they fail while the issue is present.
They don't check version numbers.

Most classes also have a control test, which must pass on both branches: a logged-in user can still
read reports, public pages stay public, and JSON Patch can still change normal fields. These show the
fix didn't work by simply breaking the app.

A test only reports `VULNERABLE` when the attack actually succeeds, such as when the protected content
comes back. `./demo.sh prove` shows any other failure as `TEST BROKEN`, rather than as a vulnerability.
If you see that, check the report in `vulnerable-app/target/surefire-reports`.

## The recipes

Each CVE has its own recipe in [`vulnerable-app/recipes`](vulnerable-app/recipes). They use OpenRewrite's
Maven recipes from `rewrite-maven` (Apache 2.0), which edit `pom.xml` directly. The plugin already
includes them, so there's nothing else to download.

This table shows what each recipe fixes when applied alone to `main`:

| Recipe | Change to `pom.xml` | CVEs fixed |
|---|---|---|
| `CVE-2026-22732.yml` | `spring-security.version` = 7.0.4 | 22732 |
| `CVE-2026-22753.yml` | `spring-security.version` = 7.0.5 | 22732, 22753 |
| `CVE-2026-40976.yml` | Spring Boot parent = 4.0.6 | 22732, 22753, 40976 |
| `CVE-2026-41707.yml` | `spring-security.version` = 7.0.7 | 22732, 22753, 41707 |
| `CVE-2026-47849.yml` | `spring-data-bom.version` = 2025.1.7 | 47849 |
| `CVE-2026-47851.yml` | `spring-ai.version` = 2.0.1 | 47851 |

The overlaps are expected: each Spring Security fix includes the earlier ones, and Boot 4.0.6 brings
Security 7.0.5. To apply one yourself:

```sh
cd vulnerable-app
mvn org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
  -Drewrite.configLocation=recipes/CVE-2026-47851.yml \
  -Drewrite.activeRecipes=com.example.tickets.FixCve202647851
```

> [!WARNING]
> **Order matters.** `ChangePropertyValue` writes the version it's given, even if that's a downgrade.
> Applying `CVE-2026-41707.yml` and then `CVE-2026-22732.yml` sets Spring Security back to 7.0.4 and
> brings CVE-2026-41707 back. `./demo.sh fix` avoids this by applying them in ascending order.

### Why not one big upgrade?

`org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` (Moderne Source Available, from
`rewrite-spring`) moves Boot from 4.0.3 to 4.0.9 and fixes all of these except CVE-2026-47851. For a
real project, that's simpler and leaves no version overrides behind. It just can't show each CVE being
fixed separately. The more specific `UpgradeSpringSecurity_7_0` and `UpgradeSpringData_4_0` recipes
made no changes to this app.

None of the 18 Moderne Source Available recipe modules we searched has a recipe for Spring AI.

## Limits of this demo

- **Upgrades only:** all six fixes are version changes. The recipes don't change vulnerable code.
- **Version overrides:** the Security and Data recipes pin versions over Boot's defaults. Remove the
  overrides when you next upgrade Boot, or they'll hold those libraries back.
- **Not every issue is covered:** this repository only includes CVEs we could reproduce with a test.
  For example, it leaves out CVE-2026-53914, an unsafe deserialization issue in the Kotlin Gradle build
  cache, because this app builds with Maven.
- **Versions are pinned:** the plugin version in `demo.sh` comes from Maven Central, and OpenRewrite's
  docs say recipes are moving to the Code Genome Project, so newer versions may exist there.
