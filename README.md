# CE-JEI-Bridge

CE-JEI-Bridge is a Paper plugin plus Fabric client mod pair for showing
CraftEngine custom items, blocks, and recipes correctly in JEI and Jade.

## Release shape

The project intentionally produces two Fabric client jars and one Paper
server jar. It does not build one jar for every Minecraft patch release.

| Artifact | Build baseline | Declared range | Build task |
| --- | --- | --- | --- |
| client/build/libs/ceclientmod-26.x-1.0.0.jar | Minecraft 26.2 | >=26.2 <27 | Fabric Loom build |
| client-legacy/build/libs/ceclientmod-1.21.x-1.0.0.jar | Minecraft 1.21.6 | >=1.21.6 <1.22 | Fabric Loom build |
| server/build/libs/CraftEngineClientBridge-1.1.0-26.x.jar | Paper/ASPaper 26.2 | 26.x | Paperweight shadowJar |

The 1.21.x client jar is compiled and remapped against 1.21.6. Its
fabric.mod.json declares the 1.21.x range so one legacy jar is distributed.
Patch-by-patch runtime testing is separate evidence and is not implied by the
manifest range.

## Dependencies

The 26.x client build uses Java 25, Fabric Loader 0.19.3, Fabric API
0.155.0+26.2, JEI 30.16.0.131, and Jade 26.2.10.

The 1.21.x client build uses Java 21, Fabric Loader 0.17.2, Fabric API
0.127.1+1.21.6, JEI 19.39.0.368, and Jade 19.0.3.

Fabric, Minecraft, JEI, and Jade classes remain external. They are not
bundled into either client jar.

## Build

The repository's client wrapper can run all three Gradle projects. Use
--offline after the required dependencies are cached.

~~~powershell
$env:JAVA_HOME = 'C:\Users\32394\.jdks\ms-21.0.11'

Push-Location server
.\gradlew.bat clean shadowJar -Ptarget=26.x --offline --no-daemon
Pop-Location

Push-Location client
.\gradlew.bat clean build -Ptarget=26.x --offline --no-daemon
.\gradlew.bat -p ..\client-legacy clean build --offline --no-daemon
.\gradlew.bat -p ..\protocol protocolTest handshakeTest --offline --no-daemon
Pop-Location
~~~

The server build must use shadowJar. The client build outputs are the two
Fabric jars listed above.

## GitHub Actions

`.github/workflows/build-publish.yml` runs the protocol tests and builds all
three release jars on pushes to `main`, `v*` tags, and manual dispatches. Every
run uploads the jars as an Actions artifact. A `v*` tag also publishes a
GitHub Release containing the jars and `SHA256SUMS.txt`.

The Paper build needs the non-redistributable CraftEngine compile jar. Configure
these repository secrets before enabling a release build:

- `CRAFTENGINE_JAR_URL`: HTTPS URL for the exact
  `craft-engine-paper-plugin-26.7-SNAPSHOT.jar`.
- `CRAFTENGINE_JAR_SHA256`: SHA-256 of that exact file.
- `CRAFTENGINE_JAR_TOKEN`: optional bearer token for a private URL.

The workflow downloads the three public 26.2 client compile dependencies and
verifies their SHA-512 hashes. It fails early if the CraftEngine URL or hash is
missing; no old local artifact is substituted.

## Runtime protocol

The server and client keep the existing ceclientbridge:* channels and use a
versioned handshake before sending generation-tagged payload chunks. A client
with a different Minecraft target or protocol version is rejected instead of
receiving undecodable data.

## Deployment boundary

This repository documentation does not authorize remote deployment. When
deployment is explicitly authorized, replace only named plugin files, back up
to /home/samsung/backup, apply configuration additions incrementally, and
preserve existing MySQL settings. Do not stop or restart an MC server; server
activation remains a manual operator action.
