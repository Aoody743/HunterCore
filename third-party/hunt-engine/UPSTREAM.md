# Upstream provenance

HuntEngine is derived from the **CraftEngine Community Edition** source
repository:

- Upstream: <https://github.com/Xiao-MoMi/craft-engine>
- Fixed source commit: `22fe37c023ba348bac6602302dbcc08bee6d4860`
- Upstream project version: `26.7.3`
- Upstream license: GNU General Public License, version 3.0
- HuntEngine distribution version: `2.9.16`
- Supported Java/Minecraft baseline: Java 25 / Minecraft 26.2
- Pinned Paper API coordinate: `io.papermc.paper:paper-api:26.2.build.10-alpha`

This directory is a source snapshot, not a Gradle composite project. Its
upstream Git metadata is only a temporary verification aid while the vendor
import is prepared; release commits must contain the source files and these
provenance records, not a nested Git repository.

## Deliberately excluded upstream material

The following upstream Git submodules are not initialized or distributed:

- `wiki`
- `client-mod`
- `proxy` (the external submodule, not the in-tree `bukkit:proxy` build module)

No CraftEngine Premium source, artifact, credential, or documentation is
included. The in-tree `bukkit:proxy` module remains because it is Community
Edition code required to construct the Paper bootstrap jar.

## Corresponding source

The complete corresponding source for HunterCraft's changes is this
`third-party/hunt-engine` tree together with the exact upstream commit above.
See [CHANGES.md](CHANGES.md) for the modified files and [NOTICE](NOTICE) for
distribution obligations.
