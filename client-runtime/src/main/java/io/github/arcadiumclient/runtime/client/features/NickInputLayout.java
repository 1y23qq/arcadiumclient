// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;

import java.util.Objects;

public final class NickInputLayout {
    public interface Metrics {
        int width(String text);
        String trim(String text,int width,boolean reverse);
        String formatting(String prefix);
    }
    private NickTextProjection projection,windowProjection;
    private String window="",prefix="",focusText="",suffix="",styledSuffix="",suffixStyle="",visibleSuffix,styledVisibleSuffix;
    private int first,focus=-1,caret=-1;
    public void update(NickTextProjection value,int width,int rawCaret,int rawSelection,int rawFirst,Metrics metrics){
        boolean changed=projection!=value;
        if(changed){projection=value;first=value.displayIndex(rawFirst);focus=caret=-1;}
        int nextFocus=value.displayIndex(rawSelection),nextCaret=value.displayIndex(rawCaret),budget=Math.max(0,width);
        boolean focusChanged=changed||focus!=nextFocus;
        if(focusChanged){focus=nextFocus;refreshWindow(metrics);}
        if(focus<first){first=focus;refreshWindow(metrics);}
        else if(focusChanged&&focus==first&&first>0&&budget>0){
            String before=value.getDisplay().substring(0,focus);first=value.ceilDisplayBoundary(Math.max(0,focus-metrics.trim(before,budget,true).length()));refreshWindow(metrics);
        }
        if(metrics.width(focusText)>budget){
            String before=value.getDisplay().substring(0,focus),trimmed=metrics.trim(before,budget,true);
            first=value.ceilDisplayBoundary(Math.max(0,focus-trimmed.length()));refreshWindow(metrics);
            while(first<focus&&metrics.width(focusText)>budget){first=value.ceilDisplayBoundary(first+1);refreshWindow(metrics);}
        }
        if(changed||caret!=nextCaret){caret=nextCaret;refreshSuffix(metrics);}
    }
    private void refreshWindow(Metrics metrics){
        first=projection.floorDisplayBoundary(first);prefix=metrics.formatting(projection.getDisplay().substring(0,first));
        window=prefix+projection.getDisplay().substring(first);windowProjection=NickTextProjection.identity(window);
        focusText=prefix+projection.getDisplay().substring(first,Math.max(first,focus));
        refreshSuffix(metrics);
    }
    private void refreshSuffix(Metrics metrics){
        int at=Math.max(0,Math.min(window.length(),windowIndexFromDisplay(caret)));
        suffix=window.substring(at);suffixStyle=metrics.formatting(window.substring(0,at));styledSuffix=suffixStyle+suffix;visibleSuffix=styledVisibleSuffix=null;
    }
    public String window(){return window;}
    public int windowIndex(int raw){return windowIndexFromDisplay(projection.displayIndex(raw));}
    private int windowIndexFromDisplay(int display){return prefix.length()+display-first;}
    public int sourceIndex(int windowIndex){return projection.sourceIndex(Math.max(0,windowIndex-prefix.length()+first),false);}
    public int sourceFirst(){return projection.sourceIndex(first,false);}
    public int displayFirst(){return first;}
    public String safePrefix(String source,String trimmed){
        if(!window.startsWith(source)||!source.startsWith(trimmed))return trimmed;
        int end=windowProjection.floorDisplayBoundary(trimmed.length());return source.substring(0,end);
    }
    public String styledSuffix(String value){
        if(Objects.equals(value,suffix))return styledSuffix;
        if(!suffix.startsWith(value))return value;
        if(!Objects.equals(value,visibleSuffix)){visibleSuffix=value;styledVisibleSuffix=suffixStyle+value;}
        return styledVisibleSuffix;
    }
    public void clear(){projection=windowProjection=null;window=prefix=focusText=suffix=styledSuffix=suffixStyle="";visibleSuffix=styledVisibleSuffix=null;first=0;focus=caret=-1;}
}
