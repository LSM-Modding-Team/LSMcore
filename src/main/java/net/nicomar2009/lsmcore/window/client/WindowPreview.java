package net.nicomar2009.lsmcore.window.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.nicomar2009.lsmcore.window.geometry.WindowGeometry;
import net.nicomar2009.lsmcore.window.model.Rect;

/** Schematic closed geometry, not a model/texture generator or a consumer block renderer. */
final class WindowPreview {
    private WindowPreview() {}
    static void draw(GuiGraphicsExtractor graphics,WindowGeometry.Geometry geometry,Rect target,double offsetX,double offsetY,int x,int y,int width,int height) {
        graphics.fill(x,y,x+width,y+height,0xFF141E28);
        if ((geometry==null && target==null) || width<12 || height<12) return;
        double targetWidth=target==null?geometry.layout().width():target.width();
        double targetHeight=target==null?geometry.layout().height():target.height();
        double viewWidth=Math.max(targetWidth,geometry==null?0:geometry.layout().width());
        double viewHeight=Math.max(targetHeight,geometry==null?0:geometry.layout().height());
        double scale=Math.min((width-12)/viewWidth,(height-12)/viewHeight);
        int left=x+(int)((width-viewWidth*scale)/2), bottom=y+(int)((height+viewHeight*scale)/2);
        if (geometry!=null) for (var leaf:geometry.leaves()) for (var surface:leaf.surfaces()) {
            Rect rect=surface.bounds();
            int x0=left+(int)Math.round(rect.minX()*scale),x1=left+(int)Math.round(rect.maxX()*scale);
            int y0=bottom-(int)Math.round(rect.maxY()*scale),y1=bottom-(int)Math.round(rect.minY()*scale);
            graphics.fill(x0,y0,Math.max(x0+1,x1),Math.max(y0+1,y1),surface.material()==WindowGeometry.Material.FRAME?0xFFB7C3CB:0xFF367894);
        }
        int right=left+(int)Math.round(targetWidth*scale),top=bottom-(int)Math.round(targetHeight*scale);
        // Dashed Minecraft cell boundaries differ from the solid per-leaf rails.
        for (int col=1;col<Math.ceil(offsetX+targetWidth-Rect.EPSILON);col++) {
            int gx=left+(int)Math.round((col-offsetX)*scale);
            for (int gy=top;gy<bottom;gy+=4) graphics.fill(gx,gy,gx+1,Math.min(bottom,gy+2),0x99FFFFFF);
        }
        for (int row=1;row<Math.ceil(offsetY+targetHeight-Rect.EPSILON);row++) {
            int gy=bottom-(int)Math.round((row-offsetY)*scale);
            for (int gx=left;gx<right;gx+=4) graphics.fill(gx,gy,Math.min(right,gx+2),gy+1,0x99FFFFFF);
        }
        graphics.fill(left-1,top-1,right+1,top,0xFFFFC85A); graphics.fill(left-1,bottom,right+1,bottom+1,0xFFFFC85A);
        graphics.fill(left-1,top,left,bottom,0xFFFFC85A); graphics.fill(right,top,right+1,bottom,0xFFFFC85A);
    }
}
