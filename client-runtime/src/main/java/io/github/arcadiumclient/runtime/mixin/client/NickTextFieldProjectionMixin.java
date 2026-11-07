// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.features.*;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=TextFieldWidget.class,priority=1700)
public abstract class NickTextFieldProjectionMixin {
    @Shadow private String text;
    @Shadow @Final private TextRenderer textRenderer;
    @Shadow private int selectionStart,selectionEnd,firstCharacterIndex,maxLength;
    @Unique private final NickTextFieldProjection arcadium$projection=new NickTextFieldProjection();
    @Unique private boolean arcadium$viewReads,arcadium$wasProjected;
    @Unique private void arcadium$prepare(){
        arcadium$projection.prepare(this,text,((TextFieldWidget)(Object)this).getInnerWidth(),selectionStart,selectionEnd,firstCharacterIndex,textRenderer);
        firstCharacterIndex=arcadium$projection.layout().sourceFirst();arcadium$wasProjected=true;
    }
    @Unique private void arcadium$restoreNative(){
        if(!arcadium$wasProjected)return;arcadium$wasProjected=false;arcadium$projection.clear();((TextFieldWidget)(Object)this).setSelectionEnd(selectionEnd);
    }
    @WrapMethod(method="render")
    private void arcadium$drawProjection(Operation<Void> original){
        if(!NickHiderConfig.getEnabled()){arcadium$restoreNative();original.call();return;}
        if(textRenderer==null||!((TextFieldWidget)(Object)this).isVisible()){original.call();return;}
        Object previous=NickTextFieldProjection.enter(this);NickHider.beginRender();boolean reads=arcadium$viewReads;
        try{arcadium$prepare();arcadium$viewReads=true;original.call();}
        finally{arcadium$viewReads=reads;NickHider.endRender();NickTextFieldProjection.exit(previous);}
    }
    @WrapMethod(method="mouseClicked")
    private void arcadium$clickProjection(int x,int y,int button,Operation<Void> original){
        if(!NickHiderConfig.getEnabled()){arcadium$restoreNative();original.call(x,y,button);return;}
        if(textRenderer==null){original.call(x,y,button);return;}
        Object previous=NickTextFieldProjection.enter(this);NickHider.beginRender();boolean reads=arcadium$viewReads;
        try{arcadium$prepare();arcadium$viewReads=true;original.call(x,y,button);}
        finally{arcadium$viewReads=reads;NickHider.endRender();NickTextFieldProjection.exit(previous);}
    }
    @WrapMethod(method="setSelectionEnd")
    private void arcadium$scrollProjection(int index,Operation<Void> original){
        if(!NickHiderConfig.getEnabled()||textRenderer==null){original.call(index);return;}
        Object previous=NickTextFieldProjection.enter(null);NickHider.beginRender();
        try{original.call(index);arcadium$prepare();}
        finally{NickHider.endRender();NickTextFieldProjection.exit(previous);}
    }
    @ModifyExpressionValue(method={"render","mouseClicked"},at=@At(value="FIELD",target="Lnet/minecraft/client/gui/widget/TextFieldWidget;text:Ljava/lang/String;"))
    private String arcadium$viewText(String value){return arcadium$viewReads?arcadium$projection.layout().window():value;}
    @ModifyExpressionValue(method={"render","mouseClicked"},at=@At(value="FIELD",target="Lnet/minecraft/client/gui/widget/TextFieldWidget;firstCharacterIndex:I"))
    private int arcadium$viewStart(int value){return arcadium$viewReads?0:value;}
    @ModifyExpressionValue(method="render",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/widget/TextFieldWidget;selectionStart:I"))
    private int arcadium$viewCaret(int value){return arcadium$viewReads?arcadium$projection.layout().windowIndex(value):value;}
    @ModifyExpressionValue(method="render",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/widget/TextFieldWidget;selectionEnd:I"))
    private int arcadium$viewSelection(int value){return arcadium$viewReads?arcadium$projection.layout().windowIndex(value):value;}
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/widget/TextFieldWidget;getMaxLength()I"))
    private int arcadium$rawCursorShape(TextFieldWidget field,Operation<Integer> original){
        if(!arcadium$viewReads)return original.call(field);
        int length=arcadium$projection.layout().window().length();return selectionStart<text.length()||text.length()>=maxLength?length:length+1;
    }
    @WrapOperation(method="mouseClicked",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/widget/TextFieldWidget;setCursor(I)V"))
    private void arcadium$sourceCursor(TextFieldWidget field,int position,Operation<Void> original){original.call(field,arcadium$viewReads?arcadium$projection.layout().sourceIndex(position):position);}
    @WrapOperation(method={"render","mouseClicked"},at=@At(value="INVOKE",target="Lnet/minecraft/client/render/TextRenderer;trim(Ljava/lang/String;I)Ljava/lang/String;"))
    private String arcadium$safeVisible(TextRenderer font,String value,int width,Operation<String> original){
        String trimmed=original.call(font,value,width);return arcadium$viewReads?arcadium$projection.layout().safePrefix(value,trimmed):trimmed;
    }
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I",ordinal=1))
    private int arcadium$inheritedStyle(TextRenderer font,String value,float x,float y,int color,Operation<Integer> original){return original.call(font,arcadium$viewReads?arcadium$projection.layout().styledSuffix(value):value,x,y,color);}
}
