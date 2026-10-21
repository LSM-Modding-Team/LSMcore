package net.nicomar2009.lsmcore.window.detection;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.nicomar2009.lsmcore.window.integration.*;
import net.nicomar2009.lsmcore.window.model.Rect;

/** Discovers geometry BEFORE membership. Never fills a hole or chooses a subrectangle of a ragged component. */
public final class WindowDetection {
    private WindowDetection() {}
    private static Direction[] edges(WindowCell cell) {
        return new Direction[]{Direction.DOWN,Direction.UP,cell.facing().getClockWise(),cell.facing().getCounterClockWise()};
    }
    public static DetectionResult discover(SurfaceAccess access,BlockPos start,DetectionWork work) {
        try {
            work.use(); if (!access.loaded(start)) return DetectionResult.error("incomplete");
            WindowCell first=access.cell(start).orElse(null);
            if (first==null) return DetectionResult.error("no_integration");
            UUID own=access.membership(start).orElse(null);
            Map<BlockPos,WindowCell> cells=new LinkedHashMap<>(); var queue=new ArrayDeque<BlockPos>();
            cells.put(start.immutable(),first); queue.add(start.immutable()); boolean incomplete=false;
            while (!queue.isEmpty()) {
                BlockPos pos=queue.removeFirst(); WindowCell cell=cells.get(pos);
                for (Direction edge:edges(cell)) {
                    if (!reaches(cell,edge)) continue;
                    BlockPos next=pos.relative(edge); work.use();
                    if (!access.loaded(next)) { incomplete=true; continue; }
                    if (!Objects.equals(own,access.membership(next).orElse(null))) continue;
                    var other=access.cell(next).orElse(null);
                    if (other==null || !WindowConnections.touches(pos,cell,next,other) || !access.permits(pos,cell,next,other)) continue;
                    if (!cells.containsKey(next)) {
                        if (cells.size()>=4096) return DetectionResult.error("detection_limit");
                        cells.put(next.immutable(),other); queue.add(next.immutable());
                    }
                }
            }
            if (incomplete) return new DetectionResult("incomplete",null,List.copyOf(cells.keySet()));
            return rectangle(access,start,first,cells,own,work);
        } catch (IllegalArgumentException limit) { return DetectionResult.error(limit.getMessage()); }
    }
    /** Cell corners select WHOLE available pieces, including fractional outer edges; no piece is cut to fit. */
    public static DetectionResult corners(SurfaceAccess access,BlockPos clicked,BlockPos first,BlockPos last,DetectionWork work) {
        try {
            work.use(); if (!access.loaded(clicked) || !access.loaded(first) || !access.loaded(last)) return DetectionResult.error("incomplete");
            var identity=access.cell(clicked).orElse(null);
            if (identity==null) return DetectionResult.error("no_integration");
            Direction right=identity.facing().getClockWise();
            int dx=last.getX()-first.getX(),dz=last.getZ()-first.getZ();
            if (dx*identity.facing().getStepX()+dz*identity.facing().getStepZ()!=0) return DetectionResult.error("incompatible");
            int u=dx*right.getStepX()+dz*right.getStepZ(),v=last.getY()-first.getY();
            if (Math.abs((long)u)>64 || Math.abs((long)v)>64 || (Math.abs((long)u)+1)*(Math.abs((long)v)+1)>4096)
                return DetectionResult.error("detection_limit");
            UUID own=access.membership(clicked).orElse(null);
            var cells=new LinkedHashMap<BlockPos,WindowCell>();
            for (int y=Math.min(0,v);y<=Math.max(0,v);y++) for (int x=Math.min(0,u);x<=Math.max(0,u);x++) {
                work.use(); BlockPos pos=first.relative(right,x).above(y);
                if (!access.loaded(pos)) return DetectionResult.error("incomplete");
                UUID member=access.membership(pos).orElse(null);
                if (member!=null && !member.equals(own)) return DetectionResult.error("explicit_boundary");
                var cell=access.cell(pos).orElse(null);
                if (cell==null || !WindowConnections.compatible(identity,cell)) return DetectionResult.error("surface_gap");
                cells.put(pos.immutable(),cell);
            }
            if (!cells.containsKey(clicked)) return DetectionResult.error("target_outside");
            return rectangle(access,clicked,identity,cells,own,work);
        } catch (IllegalArgumentException error) { return DetectionResult.error(error.getMessage()); }
    }
    private static DetectionResult rectangle(SurfaceAccess access,BlockPos origin,WindowCell identity,
                                             Map<BlockPos,WindowCell> cells,UUID own,DetectionWork work) {
        Direction right=identity.facing().getClockWise();
        double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
        for (var entry:cells.entrySet()) {
            BlockPos pos=entry.getKey(); Rect bounds=entry.getValue().available();
            int x=(pos.getX()-origin.getX())*right.getStepX()+(pos.getZ()-origin.getZ())*right.getStepZ(),y=pos.getY()-origin.getY();
            x0=Math.min(x0,x+bounds.minX()); y0=Math.min(y0,y+bounds.minY());
            x1=Math.max(x1,x+bounds.maxX()); y1=Math.max(y1,y+bounds.maxY());
        }
        if (x1-x0>64+Rect.EPSILON || y1-y0>64+Rect.EPSILON) return DetectionResult.error("detection_limit");
        int ax=(int)Math.floor(x0),ay=(int)Math.floor(y0);
        var assembly=new WindowAssembly(origin.relative(right,ax).above(ay),identity,new Rect(x0-ax,y0-ay,x1-ax,y1-ay));
        DetectionResult checked=validate(access,assembly,own,work,false);
        if (!checked.valid()) return new DetectionResult(checked.status(),checked.assembly(),List.copyOf(cells.keySet()));
        if (!new HashSet<>(checked.cells()).equals(cells.keySet())) return DetectionResult.error("irregular");
        return checked;
    }
    /** Validate a known rectangle without discovering or expanding its persistent boundary. */
    public static DetectionResult validate(SurfaceAccess access,WindowAssembly assembly,UUID own,DetectionWork work,boolean strictMembership) {
        try {
            Rect bounds=assembly.available(); int columns=(int)Math.ceil(bounds.maxX()-Rect.EPSILON),rows=(int)Math.ceil(bounds.maxY()-Rect.EPSILON);
            if ((long)columns*rows>4096) return DetectionResult.error("cell_limit");
            var result=new ArrayList<BlockPos>(); WindowCell[][] cells=new WindowCell[rows][columns];
            for (int y=0;y<rows;y++) for (int x=0;x<columns;x++) {
                work.use(); BlockPos pos=assembly.cell(x,y);
                if (!access.loaded(pos)) return new DetectionResult("incomplete",assembly,result);
                UUID member=access.membership(pos).orElse(null);
                if ((strictMembership && !Objects.equals(member,own)) || (member!=null && !member.equals(own)))
                    return new DetectionResult("explicit_boundary",assembly,result);
                var cell=access.cell(pos).orElse(null);
                if (cell==null || !WindowConnections.compatible(assembly.identity(),cell)) return new DetectionResult("surface_changed",assembly,result);
                Rect expected=bounds.intersect(new Rect(x,y,x+1,y+1)).orElseThrow().move(-x,-y);
                if (!same(expected,cell.available())) return new DetectionResult("irregular",assembly,result);
                cells[y][x]=cell; result.add(pos.immutable());
                if (x>0 && !edge(access,assembly.cell(x-1,y),cells[y][x-1],pos,cell)) return new DetectionResult("incompatible",assembly,result);
                if (y>0 && !edge(access,assembly.cell(x,y-1),cells[y-1][x],pos,cell)) return new DetectionResult("incompatible",assembly,result);
            }
            return new DetectionResult("ok",assembly,result);
        } catch (IllegalArgumentException error) { return DetectionResult.error(error.getMessage()); }
    }
    private static boolean same(Rect a,Rect b) {
        return Math.abs(a.minX()-b.minX())<=Rect.EPSILON && Math.abs(a.maxX()-b.maxX())<=Rect.EPSILON
                && Math.abs(a.minY()-b.minY())<=Rect.EPSILON && Math.abs(a.maxY()-b.maxY())<=Rect.EPSILON;
    }
    private static boolean edge(SurfaceAccess access,BlockPos a,WindowCell ac,BlockPos b,WindowCell bc) {
        return WindowConnections.touches(a,ac,b,bc) && access.permits(a,ac,b,bc);
    }
    private static boolean reaches(WindowCell cell,Direction direction) {
        Rect r=cell.available();
        if (direction==Direction.UP) return r.maxY()>=1-Rect.EPSILON;
        if (direction==Direction.DOWN) return r.minY()<=Rect.EPSILON;
        return direction==cell.facing().getClockWise()?r.maxX()>=1-Rect.EPSILON:r.minX()<=Rect.EPSILON;
    }
}
