# CE-JEI Bridge Versioned Compatibility Design

## Goal

Ship a small compatibility matrix for the CraftEngine JEI bridge:

- one Fabric client jar for Minecraft 26.x, built against 26.2;
- one Fabric client jar for Minecraft 1.21.x, built against 1.21.6;
- one Paper bridge jar for the 26.2 server target.

The project must not create a separate artifact for every 1.21 patch or every
26.x patch.

## Scope

- Preserve CraftEngine item, block, brewing, crafting-display, and smithing-display sync.
- Keep the existing ceclientbridge:* channel names.
- Keep the versioned handshake and generation-tagged chunk validation.
- Keep target-specific Fabric, Minecraft, and JEI code in separate client builds.
- Build the server artifact with shadowJar.
- Do not deploy or activate anything remotely in this task.

## Architecture

protocol/ contains dependency-free framing and handshake logic. server/ is one
Paperweight 26.2 build and reads CraftEngine's public API. client/ is the 26.2
Fabric/Loom build. client-legacy/ is a separate 1.21.6 Fabric/Loom build using
Yarn mappings and the older JEI/Jade APIs. Both client builds share the
protocol source but produce separate jars.

The supported build targets are exactly:

| Target | Minecraft baseline | Java | Artifact |
| --- | --- | --- | --- |
| 26.x server | 26.2 | 25 | CraftEngineClientBridge-1.1.0-26.x.jar |
| 26.x client | 26.2 | 25 | ceclientmod-26.x-1.0.0.jar |
| 1.21.x client | 1.21.6 | 21 | ceclientmod-1.21.x-1.0.0.jar |

The legacy client declares >=1.21.6 <1.22 in metadata. That range is the
single-jar distribution contract; exact runtime testing on each patch remains
separate evidence.

## Data Flow

1. The plugin rebuilds one immutable snapshot after CraftEngine is available.
2. A client join sends a hello containing protocol, target, and capabilities.
3. A compatible client receives five generation-tagged chunk streams.
4. The client validates size, ordering, and checksum before replacing a cache generation.
5. JEI and Jade consume the complete generation.
6. CraftEngine reload rebuilds and resends the current generation.

## Failure Handling

- Unknown protocol versions are rejected without decoding.
- Oversized, incomplete, duplicate, or checksum-invalid streams are discarded.
- A target mismatch disables sync for that connection.
- A missing CraftEngine compile jar fails the server build; an old artifact is never substituted.
- No server stop, restart, reload, or gameplay verification is performed by the build task.

## Verification

- Run protocol and handshake tests.
- Run one server shadowJar build for 26.x.
- Run one Fabric build for 26.x and one for 1.21.x.
- Inspect each jar's metadata, entrypoints, Java level, and bundled packages.
- Report build and artifact evidence separately from runtime/deployment evidence.
