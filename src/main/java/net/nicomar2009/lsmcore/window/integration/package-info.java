/**
 * Projecting-window integration contract, format uniform_framed_v1.
 *
 * <p>Consumer mods keep their own block/item/entity registrations and opening/paint states.
 * During common setup, call WindowIntegration.register(consumerBlock, adapter). Do not register
 * blocks in Core. An adapter declares the explicit assembly UUID, family, horizontal facing,
 * absolute plane and glass-only usable rectangle per cell. Both sides must explicitly consent
 * to a connection, including ordinary/paintable combinations. Solid painted parts must be
 * excluded from the available rectangle. Disjoint/nonrectangular holes cannot be represented
 * as a filled rectangle; report no usable cell/assembly instead of spanning those holes.
 *
 * <p>describe returns a bounded server-owned opening starting at an anchor cell with optional
 * fractional offsets (e.g. 0.5 for half walls/slabs/stairs). It must not search without a limit
 * or load chunks. Persist stable assembly IDs and anchor ownership in the consumer. Geometry
 * and dimensions use blocks; 16 model pixels equal one block. Total width/height, leaf counts,
 * per-leaf dimensions and ceil(offset + size) occupied cells are different quantities.
 *
 * <p>writeSelection stores SavedSelection.toJson() with the owner's assembly data and marks it
 * dirty. Load with SavedSelection.fromJson(); preserve unknown/invalid data for diagnosis,
 * never silently replace it. readSelection returns empty only when nothing was saved: the
 * initial effective layout is BaseLayouts.defaultLayout(), exactly 1 x 1 block and 1 x 1 leaf.
 * Do not resolve a stored ID against the catalog at render time: the snapshot is authoritative
 * for existing geometry even after catalog edits/deletion or policy tightening. A changed
 * default affects only genuinely unconfigured assemblies; it cannot rewrite saved snapshots.
 *
 * <p>On a changed selection call WindowGeometry.restore(snapshot) once for the affected assembly;
 * use WindowGeometry.fragments with its grid offsets, cell coordinates and available surface.
 * Implement refreshRendering to synchronize the snapshot and invalidate only the affected
 * cells' model data/meshes. The owner supplies a dynamic renderer, materials, collision shapes
 * and any opening transforms. Never generate JSON models during gameplay. Core geometry is
 * closed and has no opening state. LegacyGrid exposes opt-in 24px lattice phase math only;
 * Core does not modify existing blockstates, Minecraft IDs, worlds or schematic files.
 *
 * <p>WindowApplication.apply(player, clicked, layout) revalidates server policy, line of sight,
 * loaded cells, available area, plane/orientation, adjacency and building/owner permissions.
 * Use WindowApplyEvent for external protection vetoes. mayConfigure must honor the owner's
 * locks/claim/paint-face policy; never make it a client-side permission decision. The editor
 * sends catalog IDs for application and never accepts client parameters as selected geometry.
 *
 * <p>The client entry point WindowClient.openEditor() and /lsmcore_windows open the editor;
 * reference WindowClient only from client code. The editor also works with no adapters, for
 * calculations and operator catalog editing. No consumer block is integrated automatically.
 */
package net.nicomar2009.lsmcore.window.integration;
