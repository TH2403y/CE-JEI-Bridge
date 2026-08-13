# Compatibility Matrix

This is the intentionally small release matrix. A target is a build profile,
not a promise that every patch in its manifest range has been tested in-game.

| Component | Target | Baseline | Java | Important dependencies | Artifact |
| --- | --- | --- | --- | --- | --- |
| Paper bridge | 26.x | 26.2 | 25 | Paperweight dev bundle 26.2.build.65-beta; local CraftEngine API jar | server/build/libs/CraftEngineClientBridge-1.0.2-26.x.jar |
| Paper bridge | 1.21.11 | 1.21.11 | 21 | Paperweight dev bundle 1.21.11-R0.1-SNAPSHOT; local CraftEngine API jar | server/build/libs/CraftEngineClientBridge-1.0.2-1.21.11.jar |
| Fabric client | 26.x | 26.2 | 25 | Loader 0.19.3; Fabric API 0.155.0+26.2; JEI 30.16.0.131; Jade 26.2.10 | client/build/libs/ceclientmod-26.x-1.0.2.jar |
| Fabric client | 1.21.11 | 1.21.11 | 21 | Loader 0.19.3; Fabric API 0.141.6+1.21.11; JEI 27.22.0.66; Jade 19.0.3 | client-legacy/build/libs/ceclientmod-1.21.11-1.0.2.jar |

## Metadata contracts

- 26.x client: Minecraft >=26.2 <27, Java >=25, Loader >=0.19.3.
- 1.21.11 client: Minecraft >=1.21.11 <1.22, Java >=21, Loader >=0.19.3.
- The client jars do not bundle net/minecraft, net/fabricmc, or mezz/jei
  runtime classes.
- The server jar is built for two Paperweight targets: 26.2 (26.x) and 1.21.11.
  Each target carries its own version-specific recipe-sync NMS adapter.
- The bridge protocol accepts either Fabric client target (`26.x` or `1.21.11`)
  when paired with a server target of the same Minecraft family, provided protocol version and
  advertised capability bits match.

## Evidence boundary

Build success proves compilation, remapping, packaging, and metadata checks.
It does not prove a live server reload or an in-game JEI/Jade session. Those
require a separately authorized server operation and manual runtime testing.
