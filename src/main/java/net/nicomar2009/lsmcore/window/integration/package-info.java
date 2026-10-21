/**
 * Shared projecting-window API. No blocks, school assets or consumer dependencies are registered here.
 *
 * <p>LSMMod registers ordinary windows. PaintingMod will conditionally register its compatibility
 * blocks ONLY when LSMMod and LSMcore are also present. That detection/registration belongs to
 * PaintingMod, not Core. Both register WindowAdapter implementations during common setup using
 * WindowIntegration.register. PROJECTING_FAMILY is a shared semantic ID, so PaintingMod need not
 * import LSMMod classes. The two adapters explicitly consent to each connection.
 *
 * <p>cell describes geometry ONLY: facing, absolute plane and one available rectangular surface
 * per Minecraft cell, with solid painted wall/slab/stair parts excluded. Full, half and quarter
 * surfaces may form ONE continuous rectangle, with fractional starts/ends. Core discovers it
 * before assigning membership. UUIDs are never geometric compatibility. Disjoint/irregular
 * regions are not filled or arbitrarily cropped; the editor can select whole pieces by two
 * corner cells, deriving fractional outer bounds from their surfaces. Explicit boundaries are
 * reserved by Core and may not be crossed by discovery or by another set's selection.
 *
 * <p>WindowQueries.effective is the server entry point; ClientWindowViews.query is client-only.
 * Query on the logical main thread, call WindowResolver.prepare once and publish the immutable
 * Prepared geometry to render workers. Cache it at mesh/block-entity rebuild, not per frame/tick.
 * WindowResolver.fragments then uses that geometry with the consumer's read-only BlockGetter snapshot.
 * Observe WindowGeometryChangedEvent to invalidate only relevant cached meshes in that chunk.
 * Return UNAVAILABLE/incomplete while authoritative chunk membership is still being received.
 * Keep the last known mesh on UNAVAILABLE; fragments throws instead of returning an empty replacement.
 * Acknowledged records are durable per world/dimension. Chunk unload is never deletion.
 *
 * <p>Resolution is universal: valid explicit snapshot first; otherwise detect a rectangle and
 * match ONLY immutable base dimensions; otherwise each piece uses its individual closed geometry.
 * individual defaults to a framed unit sash clipped to the original partial available surface;
 * override for a consumer's original partial geometry, at most 32 cell-local surfaces. There is
 * no special old-construction path. Unknown/invalid foreign configuration must be reported with
 * hasUnmanagedConfiguration; never interpret it as absent or erase it. Unknown Core files fail
 * closed and are retained verbatim. No world/schematic migration is performed.
 *
 * <p>EffectiveWindow distinguishes AUTOMATIC, INDIVIDUAL, EXPLICIT and UNAVAILABLE and exposes
 * detection status and the separate explicit record. No aggregate snapshot represents individual
 * fallback. Explicit 1 x 1 means an exact 1 x 1 target, not absence. An explicit selection must
 * match the complete target size; no repeating, centering, stretching or clipping to fill a hole.
 * Catalog edits/deletions and tighter new-application limits never rewrite placed snapshots.
 *
 * <p>Core alone persists IDs, original membership and versioned complete snapshots. Owners must
 * NOT keep another authoritative selection copy. They retain paint, solid occupation, hinges,
 * opening states and other mod-specific data. Geometry is closed; consumers supply materials,
 * dynamic rendering/collision shapes and opening transforms. No runtime JSON models are generated.
 * The uniform profile preserves LSM's one-pixel rails per sash, two-pixel depth and adjacent
 * double rails; occupied cells use ceil(offset + total size), not the leaf count.
 *
 * <p>After placement/removal/orientation/available-surface edits call WindowLifecycle.changed
 * AFTER the block state changed. Do not call it for paint/open changes. Automatic regions may
 * split/join; explicit sets keep their original membership/snapshot and become recoverably
 * invalid. Never redistribute that selection into new sets. Replacing pieces can recover it;
 * authorized reset deletes the explicit record and restores automatic/individual rules. An
 * explicit reconfiguration may replace one set; other sets cannot be consumed implicitly.
 * Reset also works for missing pieces by selecting a reserved corner coordinate within reach.
 * All affected original cells must be loaded and authorized. WindowApplyEvent permits claim
 * vetoes; its null selection denotes reset. geometryChanged only invalidates owner render data.
 *
 * <p>WindowApplication revalidates line of sight, range, every affected cell, exact fit, current
 * policy and real offset occupancy. Policies live ONLY in world/lsmcore/window-policy.json;
 * the operator editor atomically edits that source. Catalogs and placed records are separate.
 * Absolute bounds: 64 blocks per axis, 256 leaves, 4096 occupied cells per set, 32768 discovery
 * work units per operation; each leaf is at most 3 x 3 blocks. World/dimension storage has separate
 * record/membership/file bounds. No unbounded searches or periodic discovery are installed.
 *
 * <p>These contracts prepare integration only. LSMMod/PaintingMod adapters, conditional blocks,
 * mutation hooks, materials and dynamic renderers must be implemented in those owner mods later.
 */
package net.nicomar2009.lsmcore.window.integration;
