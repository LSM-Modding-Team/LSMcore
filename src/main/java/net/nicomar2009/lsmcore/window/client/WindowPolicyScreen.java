package net.nicomar2009.lsmcore.window.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.nicomar2009.lsmcore.window.config.WindowConfig;
import net.nicomar2009.lsmcore.window.model.LayoutLimits;
import net.nicomar2009.lsmcore.window.network.*;

/** No local policy copy is persisted: successful saves update the world's sole policy source. */
final class WindowPolicyScreen extends Screen implements WindowResponseScreen {
    private static final String[] KEYS={"policy_width","policy_height","policy_columns","policy_rows","policy_leaves","policy_cells","policy_custom"};
    private final WindowEditorScreen parent;
    private final EditorChannel channel;
    private final String[] values=new String[7];
    private Button save;
    private final EditBox[] fields=new EditBox[7];
    private int left,span;
    WindowPolicyScreen(WindowEditorScreen parent,String dimension,WindowSnapshot snapshot) {
        super(WindowEditorScreen.text("policy")); this.parent=parent; channel=new EditorChannel(dimension,snapshot);
        var limits=snapshot.limits();
        String[] initial={Double.toString(limits.maxWidth()),Double.toString(limits.maxHeight()),Integer.toString(limits.maxColumns()),
                Integer.toString(limits.maxRows()),Integer.toString(limits.maxLeaves()),Integer.toString(limits.maxCells()),Integer.toString(limits.maxCustomLayouts())};
        System.arraycopy(initial,0,values,0,7);
    }
    @Override protected void init() {
        span=Math.min(360,width-24); left=(width-span)/2;
        for (int i=0;i<7;i++) {
            final int index=i; var field=new EditBox(font,left+span/2,30+20*i,span/2,18,WindowEditorScreen.text(KEYS[i]));
            field.setMaxLength(20); field.setValue(values[i]); field.setEditable(channel.snapshot.administrator() && channel.snapshot.writable()); field.setResponder(value->values[index]=value); fields[i]=addRenderableWidget(field);
        }
        save=addRenderableWidget(Button.builder(WindowEditorScreen.text("save"),b->save()).bounds(left,height-44,span/2-2,20).build());
        save.active=channel.snapshot.administrator() && channel.snapshot.writable();
        addRenderableWidget(Button.builder(WindowEditorScreen.text("close"),b->onClose()).bounds(left+span/2+2,height-44,span/2-2,20).build());
    }
    private void save() {
        try {
            var next=new LayoutLimits(Double.parseDouble(values[0]),Double.parseDouble(values[1]),Integer.parseInt(values[2]),Integer.parseInt(values[3]),
                    Integer.parseInt(values[4]),Integer.parseInt(values[5]),Integer.parseInt(values[6]));
            channel.send(WindowRequest.Action.POLICY,false,BlockPos.ZERO,false,BlockPos.ZERO,BlockPos.ZERO,WindowConfig.encode(next).toString());
        } catch (IllegalArgumentException invalid) { channel.status="policy_bounds"; }
    }
    @Override public void accept(WindowReply reply) {
        if (!channel.accept(reply)) return;
        if (channel.snapshot!=null) {
            parent.refreshOnReturn();
            if (channel.status.equals("ok") || channel.status.equals("stale_catalog")) {
                var limits=channel.snapshot.limits();
                String[] current={Double.toString(limits.maxWidth()),Double.toString(limits.maxHeight()),Integer.toString(limits.maxColumns()),
                        Integer.toString(limits.maxRows()),Integer.toString(limits.maxLeaves()),Integer.toString(limits.maxCells()),Integer.toString(limits.maxCustomLayouts())};
                for (int i=0;i<7;i++) { values[i]=current[i]; fields[i].setValue(current[i]); }
            }
            for (EditBox field:fields) field.setEditable(channel.snapshot.administrator() && channel.snapshot.writable());
        }
    }
    @Override public void tick() {
        super.tick(); channel.tick(); save.active=channel.pending==null && channel.snapshot!=null && channel.snapshot.administrator() && channel.snapshot.writable();
        if (minecraft.level==null || !minecraft.level.dimension().identifier().toString().equals(channel.dimension)) minecraft.setScreenAndShow(null);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick) {
        g.fill(0,0,width,height,0xFF18212C); g.centeredText(font,title,width/2,10,0xFFFFFFFF);
        for (int i=0;i<7;i++) g.text(font,WindowEditorScreen.text(KEYS[i]),left,35+20*i,0xFFFFFFFF);
        g.textWithWordWrap(font,WindowEditorScreen.text("absolute_limits"),left,height-66,span,0xFFBBBBBB);
        if (!channel.status.isEmpty()) g.text(font,WindowEditorScreen.error(channel.status),left,height-20,0xFFFFCC66);
        super.extractRenderState(g,x,y,tick);
    }
    @Override public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
