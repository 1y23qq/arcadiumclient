// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.client.features.NickHider;
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig;
import io.github.arcadiumclient.runtime.client.features.NickReplacementRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderUtils;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.internal.TextOptimizationOptions;
import org.polyfrost.oneconfig.internal.ui.api.ModuleSettingsPanels;

public final class UrgentNick072Qa {
    private UrgentNick072Qa() {}
    private static NickReplacementRule rule(String source,String target){return new NickReplacementRule(UUID.randomUUID().toString(),source,target,true,true);}
    public static Map<String,Object> verify(Minecraft mc)throws Exception {
        require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Nick QA requires the owned fake account");
        boolean enabled=NickHiderConfig.getEnabled(),cached=TextOptimizationOptions.nameMaskCache;
        String nickname=NickHiderConfig.getNickname(),rules=NickHiderConfig.getReplacementRulesJson(),account=mc.getSession().getUsername();
        Map<String,Object> report=new LinkedHashMap<>();
        try{
            require(ModuleSettingsPanels.INSTANCE.forModule("arcadium-nick-hider").stream().anyMatch(panel->panel.getTitle().equals("Replacements")),"Nick module card has no Replacements settings tab");
            NickHiderConfig.setNickname("Alias");NickHiderConfig.setEnabled(true);
            NickHiderConfig.INSTANCE.storeRules(List.of(rule("A","B"),rule("B","C")));
            require(NickHider.display("A").equals("B")&&NickHider.display(new String(new char[]{'B'})).equals("C"),"Literal single-pass or original B behavior failed");
            report.put("literalAtoBAndRawBtoC",true);
            NickHiderConfig.INSTANCE.storeRules(List.of(rule(account,"Player2")));
            require(NickHider.display(account).equals("Player2"),"Explicit own-name rule did not override fallback");
            NickHiderConfig.INSTANCE.storeRules(List.of());require(NickHider.display(account).equals("Alias"),"Existing own-name hiding changed");
            report.put("explicitOwnRuleAndExistingFallback",true);
            for(boolean useCache:new boolean[]{false,true}){
                TextOptimizationOptions.nameMaskCache=useCache;
                NickHiderConfig.INSTANCE.storeRules(List.of(rule("A","BB"),rule("BB","CCCC")));
                TextRenderer font=mc.textRenderer;
                int expected=bareWidth(font,"BB"),expectedRaw=bareWidth(font,"CCCC");
                require(font.getWidth("A")==expected&&font.getWidth("BB")==expectedRaw,"Native width cascaded or suppressed raw BB");
                int draw=font.drawWithShadow("A",4,4,0xFFFFFFFF),drawExpected=bareDraw(font,"BB");require(draw==drawExpected,"Nested native draw cascaded");
                var click=new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg AA original");
                var original=new LiteralText("A").setStyle(new Style().setBold(true).setClickEvent(click)).append(new LiteralText("A BB"));
                NickHiderConfig.INSTANCE.storeRules(List.of(rule("AA","BB"),rule("BB","CCCC")));
                List<net.minecraft.text.Text> lines=TextRenderUtils.wrapText(original,400,font,false,false);
                String rawWrapped=lines.stream().map(net.minecraft.text.Text::getString).reduce("",String::concat);
                String visibleWrapped=net.minecraft.text.Formatting.strip(rawWrapped);
                require("BB CCCC".equals(visibleWrapped),"Styled cross-run wrapping mismatch: raw="+rawWrapped+" visible="+visibleWrapped);
                report.put(useCache?"cachedWrappedText":"uncachedWrappedText",Map.of("raw",rawWrapped,"visible",visibleWrapped));
                int measurements=0;
                for(var line:lines){String formatted=line.getFormattedString();require(NickHider.display(formatted)==formatted,"Retained formatted line lost render provenance");require(font.getWidth(formatted)==bareWidth(font,formatted),"Retained line width was transformed twice");int actual=font.drawWithShadow(formatted,4,14,0xFFFFFFFF);require(actual==bareDraw(font,formatted),"Retained line draw was transformed twice");measurements++;}
                require(original.getString().equals("AA BB")&&original.getStyle().getClickEvent().getValue().equals("/msg AA original"),"Source text or outgoing click command changed");
                report.put(useCache?"cachedNativeDrawWidthWrap":"uncachedNativeDrawWidthWrap",Map.of("passed",true,"retainedLines",measurements,"nativeWidth",expected,"rawInputWidth",expectedRaw,"drawAdvance",draw));
            }
            boolean optimized=io.github.arcadiumclient.runtime.client.branding.NametagLayout.optimized;
            try{for(boolean cachedLayout:new boolean[]{false,true}){
                io.github.arcadiumclient.runtime.client.branding.NametagLayout.optimized=cachedLayout;
                for(int repeat=0;repeat<2;repeat++){
                    var previous=io.github.arcadiumclient.runtime.client.branding.NametagLayout.begin(null,"AA  ");
                    try{String label=io.github.arcadiumclient.runtime.client.branding.NametagLayout.display("AA  ");require(label.equals("BB")&&NickHider.display(label)==label,"Trimmed/cached nametag lost replacement provenance");require(mc.textRenderer.getWidth(label)==bareWidth(mc.textRenderer,"BB"),"Trimmed tag width cascaded");}
                    finally{io.github.arcadiumclient.runtime.client.branding.NametagLayout.end(previous);}
                }
            }}finally{io.github.arcadiumclient.runtime.client.branding.NametagLayout.optimized=optimized;}
            report.put("trimmedNametagFreshAndPreparedCacheHit",true);
            var epoch=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags").getDeclaredField("optionsEpoch");epoch.setAccessible(true);int beforeEpoch=epoch.getInt(null);
            NickHiderConfig.INSTANCE.storeRules(List.of(rule("A","Immediate")));require(NickHider.display("A").equals("Immediate"),"Rule edits were not immediate");
            mc.tick();
            require(epoch.getInt(null)>beforeEpoch,"Nick edits did not invalidate retained GPU names on the next client tick");int stableEpoch=epoch.getInt(null);mc.textRenderer.getWidth("A");mc.textRenderer.getWidth("A");require(epoch.getInt(null)==stableEpoch,"Nick invalidated retained geometry for every font draw");report.put("retainedNameInvalidatedOnlyOnPolicyChange",true);report.put("retainedNameInvalidationDispatch","next client tick");
            NickHiderConfig.INSTANCE.save();
            var manager=ConfigManager.active();var saved=manager.getNoRegister(manager.getFolder().resolve(NickHiderConfig.INSTANCE.id));
            require(saved!=null&&NickHiderConfig.getReplacementRulesJson().equals(saved.getProp("replacementRulesJson").get()),"Saved replacement rules did not reload from disk");
            require(account.equals(mc.getSession().getUsername()),"Render rules changed account identity");
            report.put("saveAndIndependentDiskReload",true);report.put("originalTextClickCommandAndAccountUnchanged",true);report.put("moduleSettingsEntry",true);report.put("passed",true);
            return report;
        }finally{
            TextOptimizationOptions.nameMaskCache=cached;NickHiderConfig.setReplacementRulesJson(rules);NickHiderConfig.setNickname(nickname);NickHiderConfig.setEnabled(enabled);NickHiderConfig.INSTANCE.save();
        }
    }
    private static int bareWidth(TextRenderer font,String text){NickHider.beginRender();try{return font.getWidth(text);}finally{NickHider.endRender();}}
    private static int bareDraw(TextRenderer font,String text){NickHider.beginRender();try{return font.drawWithShadow(text,4,4,0xFFFFFFFF);}finally{NickHider.endRender();}}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
