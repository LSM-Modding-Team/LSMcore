package net.nicomar2009.lsmcore.window.client;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.nicomar2009.lsmcore.window.catalog.*;
import net.nicomar2009.lsmcore.window.geometry.*;
import net.nicomar2009.lsmcore.window.model.*;
import net.nicomar2009.lsmcore.window.network.*;

/** Catalog selection is distinct from the currently resolved/placed geometry. */
public final class WindowEditorScreen extends Screen implements WindowResponseScreen {
    private enum Source { BASE,CUSTOM,SAVED,DRAFT }
    private record Option(WindowLayout layout,Source source) {}
    private final EditorChannel channel;
    private boolean hasTarget,manual,queryOnInit=true;
    private BlockPos clicked,first,last;
    private final LayoutForm form=new LayoutForm();
    private final List<Option> options=new ArrayList<>();
    private final EditBox[] fields=new EditBox[4];
    private Source source=Source.BASE;
    private int selected,left,right,panel;
    private Button save,delete,apply,reset,previous,next,create,refresh,measure,corners,policy;
    private String fitError="";
    public WindowEditorScreen(boolean hasTarget,BlockPos target,String dimension) {
        super(text("title")); this.hasTarget=hasTarget; clicked=first=last=target.immutable();
        channel=new EditorChannel(dimension,null); rebuildOptions(); form.load(BaseLayouts.defaultLayout());
    }
    static Component text(String key,Object... args) { return Component.translatable("screen.lsmcore.windows."+key,args); }
    static Component error(String key) { return Component.translatable("window.lsmcore.error."+key); }
    private LayoutLimits limits() { return channel.snapshot==null?LayoutLimits.HARD:channel.snapshot.limits(); }
    private EffectiveWindow target() { return channel.snapshot==null?null:channel.snapshot.target(); }
    @Override protected void init() {
        left=8; panel=(width-24)/2; right=left+panel+8;
        button("close",width-24,5,18,this::onClose);
        previous=button("previous",left,45,24,()->select(selected-1)); next=button("next",left+28,45,24,()->select(selected+1));
        create=button("new",left+56,45,panel-56,this::draft);
        measure=button("measure",left,70,(panel-4)/2,this::measure);
        refresh=button("refresh",left+(panel+4)/2,70,(panel-4)/2,()->send(WindowRequest.Action.QUERY));
        corners=button("rectangle",right,45,(panel-4)/2,()->minecraft.setScreen(new RectangleScreen(this,first,last)));
        policy=button("policy",right+(panel+4)/2,45,(panel-4)/2,()->minecraft.setScreen(new WindowPolicyScreen(this,channel.dimension,channel.snapshot)));
        String[] labels={"width","height","columns","rows"};
        for (int i=0;i<4;i++) {
            final int index=i; int x=left+(i%2)*(panel/2+2),y=108+(i/2)*32;
            var field=new EditBox(font,x,y,panel/2-3,18,text(labels[i])); field.setMaxLength(24); field.setValue(form.values[i]);
            field.setTooltip(Tooltip.create(text(labels[i]+"_help")));
            field.setResponder(value->{form.values[index]=value; recalculate();}); fields[i]=addRenderableWidget(field);
        }
        int bottom=height-49,half=(panel-4)/2;
        save=button("save",left,bottom,half,()->send(WindowRequest.Action.SAVE)); delete=button("delete",left+half+4,bottom,half,()->send(WindowRequest.Action.DELETE));
        apply=button("apply",right,bottom,half,()->send(WindowRequest.Action.APPLY)); reset=button("reset",right+half+4,bottom,half,()->send(WindowRequest.Action.RESET));
        reset.setTooltip(Tooltip.create(text("reset_help"))); recalculate();
        if (queryOnInit) { queryOnInit=false; send(WindowRequest.Action.QUERY); }
    }
    private Button button(String key,int x,int y,int span,Runnable action) {
        return addRenderableWidget(Button.builder(text(key),b->action.run()).bounds(x,y,span,20).build());
    }
    private void rebuildOptions() {
        options.clear(); BaseLayouts.entries().values().forEach(layout->options.add(new Option(layout,Source.BASE)));
        if (channel.snapshot!=null) {
            channel.snapshot.custom().forEach(layout->options.add(new Option(layout,Source.CUSTOM)));
            if (target().explicit()!=null && options.stream().noneMatch(option->option.layout().equals(target().explicit().selection().layout())))
                options.add(new Option(target().explicit().selection().layout(),Source.SAVED));
        }
    }
    private void select(int index) {
        selected=Math.floorMod(index,options.size()); var option=options.get(selected); source=option.source(); form.load(option.layout()); updateFields();
    }
    private void draft() { form.id="custom:"+UUID.randomUUID(); source=Source.DRAFT; recalculate(); }
    private void measure() {
        var rectangle=target().detection().assembly(); draft();
        form.load(new WindowLayout(form.id,rectangle.available().width(),rectangle.available().height(),
                Math.max(1,(int)Math.ceil(rectangle.available().width()/3)),Math.max(1,(int)Math.ceil(rectangle.available().height()/3)))); updateFields();
    }
    void rectangle(BlockPos first,BlockPos last) {
        this.first=first.immutable(); this.last=last.immutable(); clicked=this.first; hasTarget=true; manual=true; queryOnInit=true;
    }
    void refreshOnReturn() { queryOnInit=true; }
    private void updateFields() { for (int i=0;i<4;i++) if (fields[i]!=null) fields[i].setValue(form.values[i]); recalculate(); }
    private void recalculate() { form.calculate(limits()); updateButtons(); }
    private void updateButtons() {
        if (save==null) return;
        boolean idle=channel.pending==null,editable=source==Source.DRAFT || source==Source.CUSTOM;
        for (EditBox field:fields) if (field!=null) field.setEditable(idle && editable);
        boolean writable=channel.snapshot!=null && channel.snapshot.writable(),admin=writable && channel.snapshot.administrator();
        save.active=idle && admin && editable && form.valid(); delete.active=idle && admin && source==Source.CUSTOM;
        var target=target(); fitError="";
        if (target==null) fitError="waiting";
        else if (!target.detection().valid()) fitError=target.detection().status();
        else if (form.valid()) {
            var bounds=target.detection().assembly().available();
            var validation=LayoutValidator.placement(form.layout,bounds.minX(),bounds.minY(),limits());
            if (!validation.valid()) fitError=validation.errors().getFirst();
            else if (!LayoutValidator.fits(form.layout,bounds)) fitError="exact_fit";
        }
        boolean stored=form.valid() && options.stream().anyMatch(option->option.source()!=Source.SAVED && option.layout().equals(form.layout));
        apply.active=idle && writable && stored && source!=Source.SAVED && fitError.isEmpty();
        apply.setTooltip(Tooltip.create(!fitError.isEmpty()?error(fitError):!stored?text("unchanged"):text("apply_help")));
        reset.active=idle && writable && target!=null && target.explicit()!=null;
        measure.active=idle && target!=null && target.detection().valid(); policy.active=idle && channel.snapshot!=null;
        previous.active=next.active=create.active=refresh.active=corners.active=idle;
        create.setTooltip(Tooltip.create(text(source==Source.BASE?"base":"draft_help")));
        refresh.setTooltip(Tooltip.create(text("limits",limits().maxWidth(),limits().maxHeight(),limits().maxLeaves(),limits().maxCells())));
        corners.setTooltip(Tooltip.create(Component.literal(first.toShortString()+" -> "+last.toShortString())));
    }
    private void send(WindowRequest.Action action) {
        var json=new JsonObject(); json.addProperty("id",form.id);
        if (action==WindowRequest.Action.SAVE && form.layout!=null) json=LayoutJson.write(form.layout);
        channel.send(action,hasTarget,clicked,manual,first,last,json.toString()); updateButtons();
    }
    @Override public void accept(WindowReply reply) {
        boolean initial=channel.snapshot==null;
        if (!channel.accept(reply)) return;
        rebuildOptions();
        if (channel.snapshot!=null && channel.status.equals("ok")) {
            if ((initial || channel.completed==WindowRequest.Action.RESET) && target().layout()!=null) {
                for (int i=0;i<options.size();i++) if (options.get(i).layout().equals(target().layout())) { select(i); break; }
            } else if (channel.completed==WindowRequest.Action.SAVE || channel.completed==WindowRequest.Action.DELETE) {
                int index=0; for (int i=0;i<options.size();i++) if (options.get(i).layout().id().equals(form.id)) {index=i;break;} select(index);
            }
        }
        recalculate();
    }
    @Override public void tick() {
        super.tick(); channel.tick(); updateButtons();
        if (minecraft.level==null || !minecraft.level.dimension().identifier().toString().equals(channel.dimension)) onClose();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,0xFF18212C); graphics.centeredText(font,title,width/2,9,0xFFFFFFFF);
        graphics.text(font,font.plainSubstrByWidth(form.id,panel),left,30,0xFFDDEEFF);
        graphics.text(font,text(target()==null?"waiting":"mode_"+target().mode().name().toLowerCase(Locale.ROOT)),right,30,0xFFFFCC66);
        graphics.text(font,text("width"),left,98,0xFFFFFFFF); graphics.text(font,text("height"),left+panel/2+2,98,0xFFFFFFFF);
        graphics.text(font,text("columns"),left,130,0xFFFFFFFF); graphics.text(font,text("rows"),left+panel/2+2,130,0xFFFFFFFF);
        var rectangle=target()==null?null:target().detection().assembly(); var bounds=rectangle==null?null:rectangle.available();
        double ox=bounds==null?0:bounds.minX(),oy=bounds==null?0:bounds.minY();
        graphics.text(font,text("preview_selected"),right,69,0xFFBBBBBB);
        WindowPreview.draw(graphics,form.geometry,bounds,ox,oy,right,80,panel,Math.max(16,height-207));
        int metrics=height-121;
        if (form.geometry!=null) {
            graphics.text(font,text("total",n(form.layout.width()),n(form.layout.height())),right,metrics,0xFFFFFFFF);
            graphics.text(font,text("leaves",form.layout.columns(),form.layout.rows()),right,metrics+11,0xFFFFFFFF);
            graphics.text(font,text("leaf_size",n(form.layout.leafWidth()),n(form.layout.leafHeight())),right,metrics+22,0xFFFFFFFF);
            try {
                var occupied=WindowGeometry.occupancy(form.layout,ox,oy);
                graphics.text(font,text("cells",occupied.columns(),occupied.rows()),right,metrics+33,0xFFCCCCCC);
            } catch (IllegalArgumentException tooMany) { graphics.text(font,error("cell_limit"),right,metrics+33,0xFFFF9999); }
        }
        if (bounds!=null) {
            graphics.text(font,text("opening",n(bounds.width()),n(bounds.height())),left,height-77,0xFFBBBBBB);
            graphics.text(font,text("target_cells",(int)Math.ceil(bounds.maxX()-Rect.EPSILON),(int)Math.ceil(bounds.maxY()-Rect.EPSILON)),right,metrics+44,0xFFBBBBBB);
        }
        graphics.text(font,font.plainSubstrByWidth(text("legend").getString(),width-16),left,height-64,0xFFBBBBBB);
        boolean rejected=!channel.status.isEmpty() && !channel.status.equals("ok") && !channel.status.equals("waiting");
        String code=rejected?channel.status:!form.errors.isEmpty()?form.errors.getFirst():!fitError.isEmpty()?fitError:channel.status.isEmpty()?"ok":channel.status;
        Component feedback=error(code); int line=0;
        for (var split:font.split(feedback,width-16)) { if (line==2) break; graphics.text(font,split,left,height-25+10*line++,0xFFFFCC66); }
        super.extractRenderState(graphics,mouseX,mouseY,partialTick);
    }
    private static String n(double value) { return String.format(Locale.ROOT,"%.3f",value).replaceAll("0+$","").replaceAll("\\.$",""); }
    @Override public boolean isPauseScreen() { return false; }
}
