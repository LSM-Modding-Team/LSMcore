/**
 * Reusable FIXED glazing, independent of lsmcore.window (projecting leaves). No catalog,
 * leaf-size restriction, opening state, rectangle requirement or owner block registration.
 *
 * <p>Reference: LSMMod26.2Neoforge SchoolGlazing, SchoolGlassBlock, SchoolGlassStairBlock,
 * SchoolContinuousGlassBlock and the glazing generators at 826c791022f49e6ac0ce95b4f7ab30ded50ab302.
 * The normal layout retains first-edge alignment, even-row 32px / odd-row 24px spacing,
 * exterior frames and optional cell-center posts (model band 7..9px). Continuous mode only
 * examines immediate neighbors and never creates a center post or scans a row.
 *
 * <p>Consumer setup: register each owned block with FixedGlazing.register during common setup.
 * Adapters describe only glass, not solid/carved occupancy, and both must authorize contact.
 * Use a shared family (FixedGlazing.FAMILY is available) for cross-mod connections; material
 * identifiers are policy input, not an implicit same-block or same-material restriction.
 * school_glass and school_connected_tinted_glass are NORMAL; school_continuous_glass is
 * CONTINUOUS. school_paintable_glass_stairs is NORMAL and
 * school_paintable_continuous_glass_stairs is CONTINUOUS. These are integration mappings,
 * not registrations. No dependency on either owner is present.
 *
 * <p>Map vanilla stair shape to GlazingShape and solid Half to GlazingProfile.SolidHalf.
 * Both ordinary and paintable stairs use the SAME profile factory: glass is opposite the
 * solid half; corner contact codes are straight=3, outer-left=1, outer-right=2,
 * inner-left=11, inner-right=7. These are original glass contact codes, never the solid
 * 8px carving mask. Carving/painting does not change the glass profile. Bottom-sill panels
 * retain their original connection envelope but declare reachesBottom=false.
 *
 * <p>Use GlazingViews with a no-load availability predicate and a stable source/revision.
 * Revisions cover geometry, compatibility policy and availability; mutate on the owning
 * thread or supply a frozen render snapshot. Call GlazingResolver at model reconstruction,
 * not from each rendering predicate/frame. Cache complete GlazingRow ranges and resolveInRow
 * for O(1) additional cells. RowScan is constant-memory and resumable with a per-call read
 * budget, not a maximum row length. BUDGET means continue; UNAVAILABLE means wait for data;
 * STALE means restart against the new revision. None invent an endpoint or partial pattern.
 * INCOMPLETE retains a supplied last valid snapshot; absent last data means defer the mesh.
 * Never apply stale whole-face culling to new geometry. No background scans or tick hook.
 *
 * <p>After placement, removal, orientation, sill, shape, half, mode or policy changes, advance
 * the revision and call FixedGlazing.changed with the BEFORE profile. Normal plans describe
 * affected complete row ranges, including split/merged components; continuous plans contain
 * only the changed cell and its immediate dependencies. Subscribe to GlazingChangedEvent,
 * invalidate cached ranges intersecting local dependencies immediately (including old rows),
 * and schedule bounded resumeChange work. Do not enumerate huge row ranges synchronously.
 * On chunk unload, retain last valid meshes but invalidate their dependencies; on availability
 * change advance revision and restart pending queries/plans. Owners index pending unavailable
 * positions and row dependencies, clear world caches on disconnect, and synchronize their own
 * block updates. Core stores no world references, pending scans or per-tick tasks.
 *
 * <p>Mask bits are visible-left=1, visible-right=2, center-post=4, connected-up=8,
 * connected-down=16. A visible periodic frame may coexist with horizontal continuity.
 * Curtains query snapshot left/right/up/down contact, not frame bits. GlazingReflection and
 * GlazingMask.reflected allow owners to swap curtain sides on reflection without sharing enums.
 *
 * <p>GlazingCulling separates matching transparent caps, the shared original horizontal rule,
 * and permission to hide a whole owner face. Whole-face removal is opt-in and directional;
 * adapters for painted/carved stairs should leave it false and trim only glass caps in their
 * generated/dynamic meshes. Owner skipRendering same-ID/transverse and superclass decisions
 * remain owner decisions. Never infer solid coverage from a glass connection.
 *
 * <p>LSMMod/PaintingMod must implement adapters, block-change/chunk hooks, caching and rendering.
 * PaintingMod detects both other mods before registering its compatibility blocks; it needs
 * only Core's contract, no LSMMod imports. Materials/models, painting, carving, curtain state,
 * drops, placement, collisions and physical shapes stay with owners. Core neither changes IDs
 * nor migrates worlds. Existing owner windows are not integrated by this API alone.
 */
package net.nicomar2009.lsmcore.glazing;
