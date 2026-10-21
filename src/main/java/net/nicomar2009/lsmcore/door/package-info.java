/**
 * MULTI-BLOCK DOORS: independent of projecting windows and fixed glazing. No owner block, item,
 * texture, material, paint state or concrete door registration lives here. Units are blocks.
 *
 * <p>Owner integration:
 * <ol>
 * <li>Register a common-side DoorAdapter with Doors.register(type, adapter). Supply a
 * DoorDefinition containing the closed envelope, named required DoorCells/controller,
 * DoorLeaves (local vertical hinge, +/-90 degree turn, manual/redstone policy), and named
 * DoorSurfaces with owner collision boxes. Null surface leaf means fixed; ALL geometry of a
 * moving leaf, including glass, outline and handle, shares that leaf ID and transform.
 * Stable surface/part IDs are future paint/interaction anchors; Core does not store paint.</li>
 * <li>DoorService.place validates every required AND swept-space cell before any block change:
 * world height/border, loaded chunks, membership, replaceability with the actual placement
 * context, player build/use permissions, owner checks and cancellable DoorPermissionEvent.
 * Owner placement must create stamped carrier/proxy blocks for EVERY DoorPlan.Slot, including
 * empty swept space, suppress vanilla drops and retain only UUID/slot links. Query Core for
 * leaf state; never persist a second authoritative controller/block-state copy.</li>
 * <li>Use DoorService.at on server and ClientDoors.at on client to obtain cached DoorView.
 * geometry.at(pos).outline()/solid() are strictly cell-local VoxelShapes. Return them from
 * owner selection/collision hooks for carriers AND proxies; drawing only a controller or
 * returning a shape beyond its cell cannot correctly cover an opened leaf. Render with
 * DoorTransform using the same placement/leaf/open bit, or consume the per-cell fragments.
 * Use one immutable view revision per reconstruction. Missing sync must use an explicit safe
 * owner fallback, never assume closed state is authoritative. ClientDoors.availability separates
 * READY from pending/unwatched/BLOCKED; blocked data retains its last valid view, not absence.
 * Check actual local UUID links before rendering: a reserved slot is not proof its owner block is currently present.</li>
 * <li>Manual interaction uses DoorService.manual from an authoritative vanilla use hook OR
 * ClientDoors.requestUse (not both). Core traces current surfaces, including opened leaves,
 * checks occlusion/chunk availability/reach and rejects fixed/ambiguous hits. Client packets
 * cannot select a leaf, inject geometry, power or force an open state. Ties between different
 * leaves/fixed surfaces are rejected; same-leaf ties remain clickable with null surface ID.</li>
 * <li>Call DoorService.power only on real changed owner redstone inputs, separately per leaf.
 * IGNORE/FOLLOW_POWER/TOGGLE_ON_RISING are per-leaf, with persisted power memory. Core does
 * not scan neighboring signals every tick. Pure canMove receives DoorMotion with actual
 * current/target collision and conservative continuous sweep; owners decide entity clearance,
 * inter-leaf contact, sounds and special physics. No entity pushing is performed by Core.</li>
 * <li>On owner break BEFORE vanilla removal call breakPiece; on acceptance suppress vanilla
 * removal/drops. A source cell's actual removal/replacement calls pieceRemoved; never call it
 * for block-state changes, notifications or chunk unload. Recursive owner callbacks during a
 * transaction are ignored by pieceRemoved or rejected as structure_busy by mutation APIs.
 * Owner removeWithoutDrops must be idempotent and verify UUID/slot before touching a cell.</li>
 * <li>changed is AFTER durable state change. Notify owner block/pathfinding/render caches and
 * play transition sounds there; do not mutate Core state recursively. Client subscribers use
 * DoorClientChangedEvent to rebuild only affected chunk meshes. DoorReplyEvent is correlated
 * with a current outstanding request and never overwrites newer synchronized state.</li>
 * </ol>
 *
 * <p>Geometry: DoorOrientation mirrors local X, then rotates around the placement origin.
 * Local pivots and turns stay unchanged; this transformation naturally reverses world turn
 * when reflected. rotated/reflected produce new placements for placement/structure templates.
 * Reorienting an ALREADY placed structure is an explicit remove/re-place operation, with
 * full permissions/space validation; no silent rewrite or in-place world migration.
 * Closed and open poses are exact rigid 90-degree transforms. Core stores discrete states,
 * not animation progress; owner-only visual interpolation must not claim a different physical
 * pose. The full quarter-arc AABB, including intermediate extrema, reserves sweep cells.
 * This deliberately reserves some empty corner space and may reject a tight installation.
 * It guarantees no foreign block can be intentionally placed into the sweep if proxy links
 * and owner placement/break hooks are honored; replacing a proxy is a structural removal.
 * It does NOT by itself prove entity or inter-leaf clearance: canMove is mandatory.
 *
 * <p>Persistence: lsmcore/doors has one version-1 file per dimension, containing complete
 * immutable geometry/placement, structure UUID, named parts/controller/leaves and leaf states.
 * The controller is not required to be loaded. Membership, chunks, plans and posed shapes are
 * derived indexes/caches, never competing saved authorities. Catalog/adapter edits do not
 * silently alter snapshots. Unreadable/unknown files are preserved and fail closed.
 * PLACING is journaled before block writes; failures/restart roll it back without drops.
 * REMOVING is journaled before cleanup; loaded owned pieces are removed, unavailable chunks
 * leave durable pending membership. Chunk-load events enqueue bounded next-tick recovery
 * (four structures per tick); the queue is empty at rest and never polls unavailable chunks.
 * ACTIVE state is checked only on affected chunk loads or explicit owner events. No geometry
 * search/compilation per tick/frame. A missing adapter or denied protection preserves the
 * record for explicit recover after the issue is fixed. Repairing an unreadable file requires
 * restart; it is not automatically reread/overwritten. Chunk unload never deletes a record.
 * Server state updates and files do not constitute an atomic Minecraft chunk-save transaction;
 * restart recovery verifies actual UUID links instead of trusting an interrupted placement.
 *
 * <p>Removal claims drops durably BEFORE its sole callback, then deletes the journal.
 * DoorRemovalPolicy defaults to PLAYER_ONLY; ANY_BREAK opts into confirmed integrity-loss
 * drops (including recovery), NO_DROPS disables them. Creative/failed placement never drop.
 * This is at-most-once:
 * a crash between claim and external item spawning can lose drops; file I/O cannot atomically
 * commit an external inventory/entity side effect. A callback must not invoke vanilla per-cell
 * drops. Cell-state changes and chunk unload must never enter the removal path.
 *
 * <p>Limits: at most 8 leaves, 128 required cells/surfaces, 384 geometry boxes in total,
 * 64 shape boxes per cell, 512 reserved cells per structure; local envelope/pivots within
 * +/-32 blocks. A dimension is capped at 256 structures/65536 memberships and 16MiB storage;
 * snapshots at 65536 characters. No 3x3 leaf/catalog rule is borrowed from windows.
 * Initial watch/placement sends a full snapshot; later leaf/lifecycle deltas omit geometry.
 * Power-memory/lifecycle-only changes reuse cached posed shapes. Results are immutable;
 * validation rejects with IllegalArgumentException(code), storage failures with IOException, integration defects with RuntimeException. DoorResult reports
 * ok/unchanged/pending_chunks; callers consume an item only after successful placement.
 * No migration of existing blocks or .schem files is attempted.
 *
 * <p>Representable examples (all height 3, no models/registrations supplied): one moving leaf
 * plus fixed side wall; the same with separate glass surface IDs; a width-2 moving leaf plus
 * width-1 fixed wood panel; two leaves with different IDs, pivots/turns and independent state.
 * Glass/wood are consumer materials, not Core variants. LSMMod/PaintingMod need only Core's
 * adapter/types and can share this contract without importing one another.
 */
package net.nicomar2009.lsmcore.door;
