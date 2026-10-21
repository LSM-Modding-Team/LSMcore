package net.nicomar2009.lsmcore.glazing.contact;

import net.minecraft.core.Direction;
import net.nicomar2009.lsmcore.glazing.profile.*;
import net.nicomar2009.lsmcore.glazing.view.GlazingSample;

/** SchoolGlazing.canCullHorizontalFace's shared rule, NOT the owner's entire skipRendering. */
public final class GlazingCulling {
    private GlazingCulling() {}
    public record Face(boolean contact,boolean matchingGlassCap,boolean sharedHorizontalRule,boolean wholeFaceAllowed) {
        public static final Face SEPARATE=new Face(false,false,false,false);
    }
    public static Face resolve(GlazingSample a,GlazingSample b,Direction side) {
        if (!GlazingContact.connected(a,b,side)) return Face.SEPARATE;
        boolean horizontal=side.getAxis()!=Direction.Axis.Y;
        var first=a.profile(); var second=b.profile();
        boolean panels=first.shape()==GlazingShape.PANEL && second.shape()==GlazingShape.PANEL;
        boolean sameStair=first.shape().stair() && first.shape()==second.shape() && first.vertical().equals(second.vertical());
        boolean shared=horizontal && (panels || sameStair);
        boolean owner=(shared || !horizontal) && a.adapter().allowsWholeFaceCulling(a.state(),first,b.state(),second,side);
        return new Face(true,true,shared,owner);
    }
}
