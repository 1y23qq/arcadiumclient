// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;

import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.render.TextRenderer;
import io.github.arcadiumclient.runtime.mixin.client.access.ChatScreenAccessor;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class NickTextFieldProjection {
    private static final ThreadLocal<Object[]> ACTIVE=ThreadLocal.withInitial(()->new Object[1]);
    private final NickInputLayout layout=new NickInputLayout();
    private NickTextProjection projection;
    private String raw,commands;
    private long revision=Long.MIN_VALUE;
    private boolean password,emoji;
    private TextRenderer metricFont;
    private final NickInputLayout.Metrics metrics=new NickInputLayout.Metrics(){
        public int width(String value){return metricFont.getWidth(value);}
        public String trim(String value,int budget,boolean reverse){return metricFont.trim(value,budget,reverse);}
        public String formatting(String value){return TextRenderer.isolateFormatting(value);}
    };
    public long builds;
    public static Object enter(Object field){Object[] holder=ACTIVE.get();Object previous=holder[0];holder[0]=field;return previous;}
    public static void exit(Object previous){ACTIVE.get()[0]=previous;}
    public static boolean active(Object field){return ACTIVE.get()[0]==field;}
    public void prepare(Object field,String text,int width,int caret,int selection,int rawFirst,TextRenderer font){
        boolean nextPassword=PasswordHider.getEnabled()&&Minecraft.getInstance().screen instanceof ChatScreen chat&&((ChatScreenAccessor)chat).arcadium$chatField()==field;
        String nextCommands=nextPassword?PasswordHider.getCommands():"";long nextRevision=NickHider.renderRevision();boolean nextEmoji=EmojiText.enabled();
        if(projection==null||!Objects.equals(raw,text)||revision!=nextRevision||password!=nextPassword||!Objects.equals(commands,nextCommands)||emoji!=nextEmoji){
            String privateText=nextPassword?PasswordHider.display(text):text;
            projection=NickHider.inputProjection(privateText);
            if(nextEmoji)projection=projection.then(emojiProjection(projection.getDisplay()));
            projection=projection.withSource(text);raw=text;revision=nextRevision;password=nextPassword;commands=nextCommands;emoji=nextEmoji;builds++;
        }
        metricFont=font;layout.update(projection,width,caret,selection,rawFirst,metrics);
    }
    public NickInputLayout layout(){return layout;}
    public void clear(){projection=null;raw=commands=null;revision=Long.MIN_VALUE;layout.clear();}
    static NickTextProjection emojiProjection(String source){
        String encoded=EmojiText.display(source);if(encoded.equals(source))return NickTextProjection.identity(source);
        ArrayList<NickTextProjection.Edit> edits=new ArrayList<>();int raw=0,out=0;
        while(raw<source.length()&&out<encoded.length()){
            if(source.charAt(raw)==encoded.charAt(out)){raw++;out++;continue;}
            int limit=Math.min(source.length(),raw+128);String tail=source.substring(raw,limit),display=EmojiText.display(tail);
            if(display.isEmpty()||display.charAt(0)!=encoded.charAt(out)){tail=source.substring(raw);display=EmojiText.display(tail);}
            if(display.isEmpty()||display.charAt(0)!=encoded.charAt(out))throw new IllegalStateException("Emoji projection differs");
            int consumed=EmojiText.sourceTrim(tail,display,display.substring(0,1),false).length();
            if(consumed<=0)throw new IllegalStateException("Empty emoji projection");
            edits.add(new NickTextProjection.Edit(raw,raw+consumed,encoded.substring(out,out+1)));raw+=consumed;out++;
        }
        NickTextProjection value=NickTextProjection.replace(source,edits);if(!value.getDisplay().equals(encoded))throw new IllegalStateException("Emoji projection changed text");return value;
    }
}
