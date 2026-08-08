# Release Checklist

## Local build

- [x] Use Java 25 for server/ and client/.
- [x] Use Java 21 for client-legacy/ and protocol/.
- [x] Run server clean shadowJar -Ptarget=26.x.
- [x] Run client clean build -Ptarget=26.x.
- [x] Run legacy client clean build.
- [x] Run protocolTest and handshakeTest.
- [x] Inspect all three jars and record SHA-256 hashes.
- [x] Run git diff --check.

## Artifact checks

- [x] Server jar contains plugin.yml, bridge-target.properties, and the plugin entrypoint.
- [x] Both client jars contain expanded fabric.mod.json and the expected client,
  JEI, and Jade entrypoints.
- [x] Both client jars exclude Minecraft, Fabric Loader/API, JEI, and Jade
  runtime classes.
- [x] Artifact names match the target family and version.

## Deployment gate

Deployment is a separate action and requires explicit authorization for the
named servers: home1, a1, survival, resource, and login.

When authorized:

- back up under /home/samsung/backup, never inside a CraftEngine resource directory;
- replace only named plugin files;
- add new configuration keys incrementally;
- never overwrite the existing config MySQL settings;
- do not stop or restart an MC server;
- leave reload, restart, and in-game verification to the operator.
