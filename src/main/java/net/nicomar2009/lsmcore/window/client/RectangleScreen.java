package net.nicomar2009.lsmcore.window.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Selects whole cell surfaces; the server derives their fractional outer edges and rejects holes. */
final class RectangleScreen extends Screen {
    private final WindowEditorScreen parent;
    private final String[] values=new String[6];
    private String error="";
    private int left,span;
    RectangleScreen(WindowEditorScreen parent,BlockPos first,BlockPos last) {
        super(WindowEditorScreen.text("rectangle")); this.parent=parent;
        int[] coordinates={first.getX(),first.getY(),first.getZ(),last.getX(),last.getY(),last.getZ()};
        for (int i=0;i<6;i++) values[i]=Integer.toString(coordinates[i]);
    }
    @Override protected void init() {
        span=Math.min(360,width-24); left=(width-span)/2;
        for (int i=0;i<6;i++) {
            final int index=i; var box=new EditBox(font,left+(i%3)*(span/3),60+(i/3)*48,span/3-4,20,Component.literal("XYZ"));
            box.setMaxLength(10); box.setValue(values[i]); box.setResponder(value->values[index]=value); addRenderableWidget(box);
        }
        addRenderableWidget(Button.builder(WindowEditorScreen.text("select"),b->select()).bounds(left,height-44,span/2-2,20).build());
        addRenderableWidget(Button.builder(WindowEditorScreen.text("close"),b->onClose()).bounds(left+span/2+2,height-44,span/2-2,20).build());
    }
    private void select() {
        try {
            int[] v=new int[6]; for (int i=0;i<6;i++) { v[i]=Integer.parseInt(values[i].trim()); if (Math.abs((long)v[i])>30000000) throw new NumberFormatException(); }
            var first=new BlockPos(v[0],v[1],v[2]); var last=new BlockPos(v[3],v[4],v[5]);
            if (v[1]<-2048 || v[1]>2047 || v[4]<-2048 || v[4]>2047 || minecraft.level==null
                    || minecraft.level.isOutsideBuildHeight(first) || minecraft.level.isOutsideBuildHeight(last)) throw new NumberFormatException();
            parent.rectangle(first,last); onClose();
        } catch (NumberFormatException invalid) { error="invalid_numbers"; }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick) {
        g.fill(0,0,width,height,0xFF18212C); g.centeredText(font,title,width/2,12,0xFFFFFFFF);
        g.text(font,WindowEditorScreen.text("first_corner"),left,45,0xFFFFFFFF); g.text(font,WindowEditorScreen.text("last_corner"),left,93,0xFFFFFFFF);
        g.textWithWordWrap(font,WindowEditorScreen.text("corner_help"),left,140,span,0xFFBBBBBB);
        if (!error.isEmpty()) g.text(font,WindowEditorScreen.error(error),left,height-65,0xFFFF9999);
        super.extractRenderState(g,x,y,tick);
    }
    @Override public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
