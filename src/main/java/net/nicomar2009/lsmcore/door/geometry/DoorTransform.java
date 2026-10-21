package net.nicomar2009.lsmcore.door.geometry;

import java.util.function.UnaryOperator;
import net.nicomar2009.lsmcore.door.model.*;

/** One transform for vertices, selection and collision. Fixed surfaces pass null leaf. */
public final class DoorTransform {
    private DoorTransform() {}
    public static DoorPoint point(DoorPlacement placement,DoorLeaf leaf,boolean open,DoorPoint p) {
        if (leaf!=null && open) {
            double x=p.x()-leaf.pivot().x(),z=p.z()-leaf.pivot().z();
            p=new DoorPoint(leaf.pivot().x()-leaf.turn()*z,p.y(),leaf.pivot().z()+leaf.turn()*x);
        }
        p=placement.orientation().apply(p);
        return new DoorPoint(p.x()+placement.origin().getX(),p.y()+placement.origin().getY(),p.z()+placement.origin().getZ());
    }
    public static DoorBox box(DoorBox b,UnaryOperator<DoorPoint> transform) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for (double x:new double[]{b.minX(),b.maxX()}) for (double y:new double[]{b.minY(),b.maxY()}) for (double z:new double[]{b.minZ(),b.maxZ()}) {
            DoorPoint p=transform.apply(new DoorPoint(x,y,z));
            minX=Math.min(minX,p.x()); minY=Math.min(minY,p.y()); minZ=Math.min(minZ,p.z());
            maxX=Math.max(maxX,p.x()); maxY=Math.max(maxY,p.y()); maxZ=Math.max(maxZ,p.z());
        }
        return new DoorBox(minX,minY,minZ,maxX,maxY,maxZ);
    }
    public static DoorBox box(DoorPlacement p,DoorLeaf leaf,boolean open,DoorBox box) {
        return box(box,v->point(p,leaf,open,v));
    }
    /** Exact AABB of the entire continuous 0..90 degree sweep, NOT just endpoint union.
     * It is a conservative reservation volume (corner voids are intentionally reserved).
     */
    public static DoorBox sweep(DoorBox b,DoorLeaf leaf) {
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for (double x:new double[]{b.minX(),b.maxX()}) for (double z:new double[]{b.minZ(),b.maxZ()}) {
            double dx=x-leaf.pivot().x(),dz=z-leaf.pivot().z();
            // x(t)=dx*cos(t)-turn*dz*sin(t), z(t)=dz*cos(t)+turn*dx*sin(t).
            double[] a={dx,dz},c={-leaf.turn()*dz,leaf.turn()*dx};
            double[] low=new double[2],high=new double[2];
            for (int axis=0;axis<2;axis++) {
                low[axis]=Math.min(a[axis],c[axis]); high[axis]=Math.max(a[axis],c[axis]);
                double critical=Math.atan2(c[axis],a[axis]);
                for (int k=-1;k<=1;k++) {
                    double t=critical+k*Math.PI;
                    if (t>0 && t<Math.PI/2) {
                        double value=a[axis]*Math.cos(t)+c[axis]*Math.sin(t);
                        low[axis]=Math.min(low[axis],value); high[axis]=Math.max(high[axis],value);
                    }
                }
            }
            minX=Math.min(minX,leaf.pivot().x()+low[0]); maxX=Math.max(maxX,leaf.pivot().x()+high[0]);
            minZ=Math.min(minZ,leaf.pivot().z()+low[1]); maxZ=Math.max(maxZ,leaf.pivot().z()+high[1]);
        }
        return new DoorBox(minX,b.minY(),minZ,maxX,b.maxY(),maxZ);
    }
}
