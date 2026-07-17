# HuntEngine changes

## HunterCore v2.9.16

- Rebranded the Community Edition's Paper and legacy plugin descriptors as
  `HuntEngine`; retained the `net.momirealms.craftengine` Java packages and
  upstream runtime namespaces for binary and data compatibility.
- Pinned deterministic, standalone build metadata to CraftEngine commit
  `22fe37c023ba348bac6602302dbcc08bee6d4860`, normalized archive timestamps
  and order, and pinned the Paper API to `26.2.build.10-alpha`, the first
  published Paper 26.2 API artifact compatible with HunterCore's 26.2
  Mache build-1 baseline. The Paper output is `target/HuntEngine.jar`.
- Kept only the bootstrap proxy classes when expanding `proxy.jarinjar` into
  the main loader jars, preventing duplicated relocated runtime libraries and
  service resources. The original nested proxy jar remains for upstream
  development diagnostics.
- Packaged GPLv3, notice, provenance, change log, and complete third-party
  license collection under `META-INF/huntengine/` in both standalone loaders.
- Replaced public default command roots with `/huntengine` and `/he`; removed
  `/craftengine` and `/ce` aliases. Replaced the default public permission
  surface with `huntengine.use`, `huntengine.items.give`, and
  `huntengine.admin`.
- Rebranded default resource-pack text, generated content metadata, visible
  diagnostics, and bundled translations. Upstream documentation URLs remain
  as source-reference links.
- Disabled upstream update checks, upstream metrics, and client-mod asset
  generation by default. HuntEngine does not distribute a client mod.
- Replaced the upstream runtime dependency downloader with an offline bundled
  dependency manager; Paper and legacy loader builds now explicitly shade the
  runtime libraries needed before server startup and content imports,
  including jar-relocator. The Paper distribution also expands the bootstrap
  proxy classes into the main jar while retaining its diagnostic nested copy,
  so no runtime jar extraction is required.
- Changed the default resource-pack policy for HunterAuth: it does not send on
  join, does not kick a player who declines or has an unverified UUID, and does
  not auto-upload a newly built pack. Authorized publishing remains explicit.
- Added HunterCraft's UI resource-pack overlay as an input/migration asset
  under `common-files/src/main/resources/resources/huntercraft_ui`.
- Removed upstream submodule declarations from the vendored release source;
  no wiki, external proxy, client-mod, or Premium material is included.

All other source is the unmodified CraftEngine Community Edition snapshot at
the pinned upstream commit unless a file above or a later HunterCraft change
entry says otherwise.
