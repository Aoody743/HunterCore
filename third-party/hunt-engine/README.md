# HuntEngine

HuntEngine is HunterCraft's GPLv3 server-only distribution of the CraftEngine
Community Edition. It is vendored at upstream commit
[`22fe37c023ba348bac6602302dbcc08bee6d4860`](UPSTREAM.md) and keeps the
upstream `net.momirealms.craftengine` Java packages so integrations that rely
on the Community Edition API remain binary-compatible.

The public product identity is **HuntEngine**:

- Paper plugin name: `HuntEngine`
- Primary commands: `/huntengine` and `/he`
- Primary permissions: `huntengine.use`, `huntengine.item.<id>`,
  `huntengine.items.give`, and `huntengine.admin`
- Deterministic Paper artifact: `target/HuntEngine.jar`

No CraftEngine Premium material, wiki submodule, or client-mod submodule is
vendored or distributed by this source tree. HuntEngine does not require a
client mod; optional upstream protocol compatibility remains disabled by
default.

## Build

HuntEngine is an independent Gradle build. It is not included through a Gradle
composite build and must be built with Java 25.

```sh
./gradlew :bukkit:paper-loader:shadowJar
```

The supported release task is also available as:

```sh
./gradlew assembleHuntEngine
```

Both commands write the Paper bootstrap plugin to `target/HuntEngine.jar`.
Build metadata is pinned in `gradle.properties`, archive ordering and
timestamps are normalized for reproducible output, and the Paper/legacy
loaders shade the runtime libraries previously fetched at startup. HunterCore
consumes only that verified jar; it does not compile this project as a
subproject.

The server never downloads HuntEngine dependencies on first startup. This is
separate from the source build: Gradle resolves the pinned build dependencies
before producing a release artifact, so a fresh source build is not expected
to work without its verified dependency cache. After building, validate the
vendored source and jar without network access from the HunterCore root:

```sh
bash scripts/verify-hunt-engine-vendor.sh third-party/hunt-engine/target/HuntEngine.jar
```

The verifier checks provenance, the fixed Paper coordinate, excluded upstream
material, plugin identity, legal notices, the nested proxy diagnostic jar, and
duplicate archive entries. A full cold-start smoke test still requires the
verified Paper server fixture and must be completed before publishing.

## Licensing and source

HuntEngine is a modified work of
[CraftEngine Community Edition](https://github.com/Xiao-MoMi/craft-engine),
licensed under the GNU General Public License, version 3. The complete GPLv3
text is in [LICENSE](LICENSE); bundled dependency notices remain in
[common-files/src/main/resources/THIRD_PARTY_LICENSES](common-files/src/main/resources/THIRD_PARTY_LICENSES).

- [UPSTREAM.md](UPSTREAM.md) records the source repository, fixed commit, and
  excluded upstream submodules.
- [CHANGES.md](CHANGES.md) records HunterCraft changes to this fork.
- [NOTICE](NOTICE) preserves upstream attribution and explains how recipients
  can obtain the corresponding source.

When distributing `HuntEngine.jar`, distribute or offer the corresponding
source for this exact tree under GPLv3 as well.
