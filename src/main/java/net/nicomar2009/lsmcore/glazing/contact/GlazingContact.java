package net.nicomar2009.lsmcore.glazing.contact;

import net.minecraft.core.Direction;
import net.nicomar2009.lsmcore.glazing.profile.*;
import net.nicomar2009.lsmcore.glazing.view.GlazingSample;

/** Pure geometric contact, independent of frame visibility or whole-face occlusion. */
public final class GlazingContact {
    private GlazingContact() {}
    public static boolean compatible(GlazingProfile a,GlazingProfile b) {
        return a.facing()==b.facing() && a.mode()==b.mode() && a.family().equals(b.family());
    }
    public static boolean horizontal(GlazingProfile a,GlazingProfile b,Direction side) {
        if (!compatible(a,b) || !a.vertical().equals(b.vertical())) return false;
        Direction right=a.facing().getClockWise();
        if (side!=right && side!=right.getOpposite()) return false;
        GlazingProfile left=side==right?a:b,rightPane=side==right?b:a;
        if (left.horizontal().max()!=16 || rightPane.horizontal().min()!=0) return false;
        if (a.mode()==GlazingMode.NORMAL) return a.normalRow() && b.normalRow();
        var edge=left.rightEdge();
        return !edge.empty() && edge.equals(rightPane.leftEdge());
    }
    public static boolean vertical(GlazingProfile a,GlazingProfile b,Direction side) {
        if (!compatible(a,b) || side!=Direction.UP && side!=Direction.DOWN
                || !a.footprint().equals(b.footprint()) || !a.horizontal().equals(b.horizontal())) return false;
        return side==Direction.UP?a.vertical().max()==16 && b.reachesBottom()
                :a.reachesBottom() && b.vertical().max()==16;
    }
    public static boolean connected(GlazingSample a,GlazingSample b,Direction side) {
        return (horizontal(a.profile(),b.profile(),side) || vertical(a.profile(),b.profile(),side))
                && a.adapter().allowsConnection(a.profile(),b.profile(),side)
                && b.adapter().allowsConnection(b.profile(),a.profile(),side.getOpposite());
    }
    /** Proves non-contact from this profile alone, even if the adjacent chunk is unknown. */
    public static boolean reaches(GlazingProfile profile,Direction side) {
        if (side==Direction.UP) return profile.vertical().max()==16;
        if (side==Direction.DOWN) return profile.reachesBottom();
        if (profile.mode()==GlazingMode.NORMAL && !profile.normalRow()) return false;
        if (side==profile.facing().getClockWise()) return !profile.rightEdge().empty();
        if (side==profile.facing().getCounterClockWise()) return !profile.leftEdge().empty();
        return false;
    }
}
