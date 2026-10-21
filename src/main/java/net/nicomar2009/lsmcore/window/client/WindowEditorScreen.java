package net.nicomar2009.lsmcore.window.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.nicomar2009.lsmcore.window.catalog.BaseLayouts;
import net.nicomar2009.lsmcore.window.geometry.WindowGeometry;
import net.nicomar2009.lsmcore.window.model.*;
import net.nicomar2009.lsmcore.window.network.*;

/** Local draft/calculation for everyone; only acknowledged server data can be applied/saved. */
public final class WindowEditorScreen extends Screen {
    private enum Source { BASE, CUSTOM, SAVED, DRAFT }
    private record Option(WindowLayout layout,Source source) {}
    private final UUID session=UUID.randomUUID();
    private final boolean hasTarget;
    private final BlockPos target;
    private final String dimension;
    private final LayoutForm form=new LayoutForm();
    private final List<Option> options=new ArrayList<>();
    private final EditBox[] fields=new EditBox[4];
    private WindowSnapshot snapshot;
    private Source source=Source.BASE;
    private int selected,left,right,panel;
    private Button save,delete,apply,reset,previous,next,create,refresh;
    private Component status=Component.empty();
    private WindowRequest.Action pending;
    private long sentAt;
    private long lastTransmission=System.nanoTime()-300_000_000L;
    private WindowRequest outgoing;
    private boolean requested;

    public WindowEditorScreen(boolean hasTarget,BlockPos target,String dimension) {
        super(text("title")); this.hasTarget=hasTarget; this.target=target.immutable(); this.dimension=dimension;
        rebuildOptions(); form.load(BaseLayouts.defaultLayout());
    }
    private static Component text(String key,Object... args) { return Component.translatable("screen.lsmcore.windows."+key,args); }
    private LayoutLimits limits() { return snapshot==null?LayoutLimits.HARD:snapshot.limits(); }
    @Override protected void init() {
        left=8; panel=(width-24)/2; right=left+panel+8;
        addRenderableWidget(Button.builder(Component.literal("x"),b->onClose()).bounds(width-24,5,18,18).build());
        previous=button("previous",left,45,24,()->select(selected-1));
        next=button("next",left+28,45,24,()->select(selected+1));
        create=button("new",left+56,45,panel-56,this::createDraft);
        refresh=button("refresh",left,70,panel,()->send(WindowRequest.Action.SYNC));
        String[] labels={"width","height","columns","rows"};
        for (int i=0;i<4;i++) {
            final int index=i; int x=left+(i%2)*(panel/2+2),y=108+(i/2)*32;
            var field=new EditBox(font,x,y,panel/2-3,18,text(labels[i]));
            field.setMaxLength(24); field.setValue(form.values[i]);
            field.setTooltip(Tooltip.create(text(labels[i]+"_help")));
            field.setResponder(value->{ form.values[index]=value; recalculate(); });
            fields[i]=addRenderableWidget(field);
        }
        int bottom=height-49,half=(panel-4)/2;
        save=button("save",left,bottom,half,()->send(WindowRequest.Action.SAVE));
        delete=button("delete",left+half+4,bottom,half,()->send(WindowRequest.Action.DELETE));
        apply=button("apply",right,bottom,half,()->send(WindowRequest.Action.APPLY));
        reset=button("reset",right+half+4,bottom,half,()->send(WindowRequest.Action.RESET));
        reset.setTooltip(Tooltip.create(text("reset_help")));
        refresh.setTooltip(Tooltip.create(text("limits",limits().maxWidth(),limits().maxHeight(),limits().maxLeaves(),limits().maxCells())));
        recalculate();
        if (!requested) { requested=true; send(WindowRequest.Action.SYNC); }
    }
    private Button button(String key,int x,int y,int span,Runnable action) {
        return addRenderableWidget(Button.builder(text(key),b->action.run()).bounds(x,y,span,20).build());
    }
    private void rebuildOptions() {
        options.clear();
        BaseLayouts.entries().values().forEach(layout->options.add(new Option(layout,Source.BASE)));
        if (snapshot!=null) {
            snapshot.custom().forEach(layout->options.add(new Option(layout,Source.CUSTOM)));
            if (snapshot.current()!=null && options.stream().noneMatch(option->option.layout().equals(snapshot.current().layout())))
                options.add(new Option(snapshot.current().layout(),Source.SAVED));
        }
    }
    private void select(int index) {
        selected=Math.floorMod(index,options.size());
        Option option=options.get(selected); source=option.source(); form.load(option.layout());
        updateFields(); status=Component.empty();
    }
    private void createDraft() {
        form.id="custom:"+UUID.randomUUID().toString(); source=Source.DRAFT; recalculate();
        status=text("draft_help");
    }
    private void updateFields() {
        for (int i=0;i<4;i++) if (fields[i]!=null) fields[i].setValue(form.values[i]);
        recalculate();
    }
    private void recalculate() {
        form.calculate(limits()); updateButtons();
    }
    private void updateButtons() {
        if (save==null) return;
        refresh.setTooltip(Tooltip.create(text("limits",limits().maxWidth(),limits().maxHeight(),limits().maxLeaves(),limits().maxCells())));
        boolean idle=pending==null,editable=source==Source.DRAFT || source==Source.CUSTOM;
        for (EditBox field:fields) if (field!=null) field.setEditable(editable && idle);
        boolean admin=snapshot!=null && snapshot.administrator() && snapshot.writable();
        save.active=idle && admin && editable && form.valid();
        delete.active=idle && admin && source==Source.CUSTOM;
        boolean stored=form.valid() && options.stream().anyMatch(option->option.source()!=Source.SAVED && option.layout().equals(form.layout));
        apply.active=idle && snapshot!=null && snapshot.targetAvailable() && stored && source!=Source.SAVED;
        apply.setTooltip(Tooltip.create(snapshot==null?text("waiting")
                :!snapshot.targetAvailable()?Component.translatable("window.lsmcore.error."+(hasTarget?"no_integration":"no_target"))
                :!stored?text("unchanged"):text("apply_help")));
        reset.active=idle && snapshot!=null && snapshot.targetAvailable();
        previous.active=next.active=create.active=refresh.active=idle;
    }
    private void send(WindowRequest.Action action) {
        if (pending!=null || minecraft.getConnection()==null) return;
        if (minecraft.level==null || !minecraft.level.dimension().identifier().toString().equals(dimension)) { onClose(); return; }
        pending=action; sentAt=System.nanoTime(); status=text("waiting"); updateButtons();
        double w=form.layout==null?0:form.layout.width(),h=form.layout==null?0:form.layout.height();
        int c=form.layout==null?0:form.layout.columns(),r=form.layout==null?0:form.layout.rows();
        outgoing=new WindowRequest(session,dimension,action,hasTarget,target,form.id,w,h,c,r,snapshot==null?0:snapshot.revision());
    }
    public void accept(WindowReply reply) {
        if (!session.equals(reply.session())) return;
        try {
            boolean first=snapshot==null; WindowRequest.Action completed=pending;
            snapshot=WindowSnapshot.decode(reply.json()); pending=null; outgoing=null; rebuildOptions();
            if ((first || completed==WindowRequest.Action.RESET) && snapshot.current()!=null) {
                WindowLayout current=snapshot.current().layout();
                for (int i=0;i<options.size();i++) if (options.get(i).layout().equals(current)) { select(i); break; }
            } else if ("success".equals(snapshot.status()) && (completed==WindowRequest.Action.SAVE || completed==WindowRequest.Action.DELETE)) {
                int match=-1;
                for (int i=0;i<options.size();i++) if (options.get(i).layout().id().equals(form.id)) { match=i; break; }
                select(match<0?0:match);
            }
            status=snapshot.status().isEmpty()?text(snapshot.administrator()?"admin_help":"viewer_help")
                    :Component.translatable("window.lsmcore.error."+snapshot.status());
            recalculate();
        } catch (RuntimeException invalid) {
            pending=null; outgoing=null; snapshot=null; status=text("bad_snapshot"); updateButtons();
        }
    }
    @Override public void tick() {
        super.tick();
        if (outgoing!=null && System.nanoTime()-lastTransmission>=300_000_000L) {
            ClientPacketDistributor.sendToServer(outgoing); outgoing=null;
            lastTransmission=sentAt=System.nanoTime();
        }
        if (pending!=null && System.nanoTime()-sentAt>5_000_000_000L) {
            pending=null; outgoing=null; status=text("timeout"); updateButtons();
        }
        if (minecraft.level==null || !minecraft.level.dimension().identifier().toString().equals(dimension)) onClose();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,0xFF18212C);
        graphics.centeredText(font,title,width/2,9,0xFFFFFFFF);
        graphics.text(font,font.plainSubstrByWidth(form.id,panel),left,30,0xFFDDEEFF);
        graphics.text(font,text(source.name().toLowerCase(Locale.ROOT)),right,30,0xFFFFCC66);
        graphics.text(font,text("width"),left,98,0xFFFFFFFF); graphics.text(font,text("height"),left+panel/2+2,98,0xFFFFFFFF);
        graphics.text(font,text("columns"),left,130,0xFFFFFFFF); graphics.text(font,text("rows"),left+panel/2+2,130,0xFFFFFFFF);
        double offsetX=snapshot!=null && snapshot.available()!=null?snapshot.available().minX():0;
        double offsetY=snapshot!=null && snapshot.available()!=null?snapshot.available().minY():0;
        WindowPreview.draw(graphics,form.geometry,offsetX,offsetY,right,45,panel,Math.max(16,height-172));
        int metrics=height-121;
        if (form.geometry!=null) {
            var layout=form.layout;
            graphics.text(font,text("total",n(layout.width()),n(layout.height())),right,metrics,0xFFFFFFFF);
            graphics.text(font,text("leaves",layout.columns(),layout.rows()),right,metrics+11,0xFFFFFFFF);
            graphics.text(font,text("leaf_size",n(layout.leafWidth()),n(layout.leafHeight())),right,metrics+22,0xFFFFFFFF);
            var occupied=WindowGeometry.occupancy(layout,offsetX,offsetY);
            graphics.text(font,text(snapshot!=null && snapshot.available()!=null?"target_cells":"cells",occupied.columns(),occupied.rows()),right,metrics+33,0xFFCCCCCC);
            var half=WindowGeometry.occupancy(layout,.5,.5);
            graphics.text(font,text("half_cells",half.columns(),half.rows()),right,metrics+44,0xFFCCCCCC);
        }
        Component info=!form.errors.isEmpty()?Component.translatable("window.lsmcore.error."+form.errors.getFirst())
                :text("legend");
        graphics.text(font,font.plainSubstrByWidth(info.getString(),width-16),left,height-64,form.valid()?0xFFBBBBBB:0xFFFF9999);
        int line=0;
        Component feedback=form.errors.isEmpty()?status:Component.translatable("window.lsmcore.error."+form.errors.getFirst());
        for (var split:font.split(feedback,width-16)) {
            if (line>=2) break;
            graphics.text(font,split,left,height-25+line++*10,0xFFFFCC66);
        }
        super.extractRenderState(graphics,mouseX,mouseY,partialTick);
    }
    private static String n(double value) { return String.format(Locale.ROOT,"%.3f",value).replaceAll("0+$","").replaceAll("\\.$",""); }
    @Override public boolean isPauseScreen() { return false; }
}
