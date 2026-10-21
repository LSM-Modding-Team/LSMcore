/**
 * Reusable multiblock doors. Consumers register blocks, items, materials and DoorAdapter instances.
 * Core alone owns immutable definition snapshots, UUID membership, leaf bits, power memory and retirement.
 *
 * <p>DoorPlacement.anchored transforms the full unit cube of an explicit local anchor, then subtracts its
 * minimum corner from BlockPlaceContext's resolved target. Mirror-X precedes rotation. For anchor (a,0,0),
 * the transformed X/Z minima at quarter-turns 0..3 are (a,0),(-1,a),(-a-1,-1),(0,-a-1); mirrored they are
 * (-a-1,0),(-1,-a-1),(a,-1),(0,a). This is independent of model width, includes negative cell extents and
 * guarantees the same destination cube for all eight placements. Existing origins are NEVER normalized.
 *
 * <p>MotionPolicy.RESTRICTIVE preserves the v1 conservative sweep reservations, stamped owner proxies and
 * canMove veto. MotionPolicy.VANILLA reserves ONLY the declared closed cells and changes directly by 90
 * degrees. It does not consult canMove, entities, arc clearance or foreign blocks in the target pose.
 * Core still verifies ownership, required loaded chunks, reach, occlusion, player permissions and the
 * cancellable DoorPermissionEvent. No foreign block is replaced and no entity is deliberately pushed.
 * Definitions and anchor choices are owner data; consumers must explicitly opt in to VANILLA.
 *
 * <p>VANILLA creates Core infrastructure DoorBody entities for moving selection/collision boxes. These
 * are not door content registrations. They save only owner UUID, surface and collision index. Their exact
 * axis-aligned final boxes and client meshes are derived from the SAME DoorRecord as block shapes; they
 * never save opening, a pivot, an independent controller or a competing collision authority. Entities let
 * Minecraft's spatial index find surfaces spanning multiple cells, without protruding block VoxelShapes.
 * Selection/collision fragments in DoorGeometry remain cell-local; required carrier blocks can return
 * those shapes. Entity boxes may overlap foreign blocks, as vanilla policy permits. Entity interaction
 * and attacks ray-test current Core surfaces again and use the ordinary authorization/retirement path.
 * Auxiliary entities have no inventory/drops, physics, push, damage reaction or steady-state tick work.
 * Consumers provide their entity type/renderer; fixed surfaces and painting remain consumer-owned.
 * DoorUpdate synchronizes discrete poses; auxiliary entities need no periodic movement feed.
 *
 * <p>DoorBodies reconciles only on committed pose/lifecycle changes, bounded chunk-load recovery and entity
 * load/unload events. Stable UUIDs and a loaded-owner index prevent duplicates. Late unloaded entities with
 * retired owners are rejected on join. Observation chunks include possible final poses, not the arc; they
 * are metadata for chunk-watch synchronization and never imply block occupation. No global world scans,
 * polling ticker, forced chunk loads or per-frame geometry compilation are installed.
 *
 * <p>Snapshot format v2 adds explicit motion policy/schema and a bounded retiredProxies journal; v1 stays
 * readable with RESTRICTIVE/schema 1 defaults. Storage container and state-delta versions remain v1.
 * Upgrade requires an adapter's explicit known-schema opt-in, compatible type and leaf IDs. Core preserves
 * UUID, origin, orientation, each leaf's open/powered bits and lifecycle. It durably installs the new
 * definition and old proxy positions BEFORE cleanup. Only loaded, verified UUID/proxy links may be removed;
 * foreign replacements stay intact, unavailable chunks stay journaled until a chunk-load recovery event.
 * Cleanup is permission-checked and reentrant callbacks are ignored. No drops are emitted for an upgrade.
 * Unknown/custom definitions, malformed files and other consumers are preserved rather than guessed.
 *
 * <p>Placement/removal remain journaled before world writes. Whole-structure drops are durably claimed
 * at most once, not atomically with external item spawning: a crash in that final gap can lose a drop.
 * Consumer paint data must be stored separately through its painting provider and preserved across upgrades.
 * No world-coordinate relocation or schematic migration is performed.
 */
package net.nicomar2009.lsmcore.door;
