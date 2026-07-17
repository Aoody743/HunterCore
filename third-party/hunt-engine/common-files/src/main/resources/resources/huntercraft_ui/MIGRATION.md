# HunterCraft UI migration asset

This directory is the source-form HuntEngine overlay for the HunterCraft UI.
It was imported from:

`huntercore-plugins/hunter-assets/src/main/resources/default-packs/HunterCore-default-ui.zip`

Source archive SHA-256:

`643d84459580dabb4afee08d45b61a6c704dd2547c9e8beb413d17615947db59`

The old archive remains in place during the v2.9.16 migration. This directory
is not a standalone HunterAssets pack: HuntEngine's normal resource-pack
builder copies it into the managed `resources/huntercraft_ui` content pack,
where it is validated and merged with the active resource pack.

The `huntercore` namespace and existing CustomModelData assignments are
preserved intentionally so HunterAuth and the shared GUI can retain their
resource-pack presentation while vanilla clients continue to use their normal
fallback GUI.
