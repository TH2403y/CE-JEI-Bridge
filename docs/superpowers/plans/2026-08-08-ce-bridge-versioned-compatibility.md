# CE-JEI Bridge Versioned Compatibility Implementation Plan

> For agentic workers: use the verification workflow before claiming completion. Steps use checkbox syntax for tracking.

Goal: produce exactly two Fabric client jars, one for 26.x and one for 1.21.x,
plus one 26.2 Paper bridge jar.

Architecture: keep protocol/ dependency-free, keep server/ on the single
Paperweight 26.2 target, and use independent Loom builds for client/ and
client-legacy/.

Tech stack: Java, Gradle Kotlin DSL, Paperweight, Shadow, Fabric Loom, Fabric
API, JEI, Jade, and JUnit 5.

## Global Constraints

- Build the server plugin with shadowJar.
- Do not build separate artifacts for individual 1.21 or 26.x patch releases.
- Do not deploy or activate remote servers in this task.
- When deployment is later authorized, preserve MySQL configuration and use additive config edits.

---

### Task 1: Protocol and handshake

Files:

- protocol/src/main/java/com/ceclientbridge/protocol/
- protocol/src/test/java/com/ceclientbridge/protocol/
- server/src/main/java/com/ceclientbridge/net/
- client/src/main/java/com/ceclientmod/net/

- [x] Add versioned frame encoding, checksum and size validation.
- [x] Add handshake negotiation for protocol, Minecraft target and capabilities.
- [x] Add atomic generation assembly and reject incomplete or mismatched streams.
- [x] Run the protocol and handshake checks.

### Task 2: Two Fabric client targets

Files:

- client/build.gradle.kts
- client/src/main/resources/fabric.mod.json
- client-legacy/build.gradle.kts
- client-legacy/src/main/resources/fabric.mod.json

- [x] Keep client/ as the single 26.2 build and publish it as 26.x.
- [x] Keep client-legacy/ as the single 1.21.6 baseline and publish it as 1.21.x.
- [x] Keep Fabric, Minecraft, JEI and Jade classes external to both jars.
- [x] Inspect both jars for metadata, entrypoints and forbidden bundled packages.

### Task 3: One Paper 26.2 shadow build

Files:

- server/build.gradle.kts
- server/src/main/resources/bridge-target.properties
- server/src/main/java/com/ceclientbridge/version/BridgeServerTarget.java

- [x] Keep only the 26.x server profile.
- [x] Use the Paperweight 26.2 dev bundle and Java 25.
- [x] Build the authoritative server artifact with clean shadowJar.
- [x] Do not substitute an older artifact when a dependency is unavailable.

### Task 4: Documentation and final verification

Files:

- README.md
- docs/compatibility-matrix.md
- docs/release-checklist.md

- [x] Record exact target metadata, artifact paths and hashes.
- [x] Run fresh protocol tests and both Fabric builds.
- [x] Check git diff --check and final worktree scope.
- [x] Report build evidence separately from runtime evidence.
- [x] Keep deployment as a separate, explicitly authorized step.

### Task 5: GitHub Actions build and release

Files:

- .github/workflows/build-publish.yml
- README.md

- [x] Build protocol tests, both Fabric targets, and the Paper `shadowJar` target.
- [x] Download and hash-check public 26.2 client compile dependencies.
- [x] Require a URL and SHA-256 for the non-redistributable CraftEngine compile jar.
- [x] Upload three jars for every workflow run and publish `v*` tags as releases.
