// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;

import java.text.BreakIterator;
import java.util.*;

public final class NickTextProjection {
    public record Edit(int start,int end,String value) { }
    private record Span(int start,int end,int displayStart,int displayEnd) { }
    private final String source,display;
    private final int[] mapping,sourceBoundaries,displayBoundaries;
    private final List<Span> spans;

    private NickTextProjection(String source,String display,int[] mapping,List<Span> spans){
        this.source=source;this.display=display;this.mapping=mapping;this.spans=spans;
        sourceBoundaries=boundaries(source);displayBoundaries=source.equals(display)?sourceBoundaries:boundaries(display);
    }
    public static NickTextProjection identity(String text){return new NickTextProjection(text,text,null,List.of());}
    public static NickTextProjection replace(String source,List<Edit> edits){
        if(edits.isEmpty())return identity(source);
        StringBuilder output=new StringBuilder(source.length());int[] map=new int[source.length()+1];List<Span> spans=new ArrayList<>();int position=0;
        for(Edit edit:edits){
            if(edit.start<position||edit.start>=edit.end||edit.end>source.length())throw new IllegalArgumentException("Overlapping or empty projection edits");
            for(int i=position;i<=edit.start;i++)map[i]=output.length()+i-position;
            output.append(source,position,edit.start);int displayStart=output.length();output.append(edit.value);
            int[] raw=boundaries(source.substring(edit.start,edit.end)),target=boundaries(edit.value);
            int rawSteps=raw.length-1,targetSteps=target.length-1;
            for(int step=0;step<raw.length;step++){
                int from=edit.start+raw[step],to=step+1<raw.length?edit.start+raw[step+1]:from;
                int projected=displayStart+target[rawSteps==0?targetSteps:(int)((long)step*targetSteps/rawSteps)];
                Arrays.fill(map,from,to,projected);map[from]=projected;
            }
            map[edit.end]=output.length();spans.add(new Span(edit.start,edit.end,displayStart,output.length()));position=edit.end;
        }
        for(int i=position;i<=source.length();i++)map[i]=output.length()+i-position;
        output.append(source,position,source.length());return new NickTextProjection(source,output.toString(),map,List.copyOf(spans));
    }
    public String getSource(){return source;}
    public String getDisplay(){return display;}
    public boolean changed(){return !source.equals(display);}
    public int displayIndex(int raw){int index=floor(sourceBoundaries,Math.max(0,Math.min(raw,source.length())));return floor(displayBoundaries,mapping==null?index:mapping[index]);}
    public int sourceIndex(int projected,boolean trailing){
        if(projected>=display.length())return source.length();
        int value=floor(displayBoundaries,Math.max(0,Math.min(projected,display.length()))),low=0,high=sourceBoundaries.length;
        if(trailing){
            while(low<high){int middle=(low+high)>>>1;if(displayIndex(sourceBoundaries[middle])<=value)low=middle+1;else high=middle;}
            return sourceBoundaries[Math.max(0,low-1)];
        }
        while(low<high){int middle=(low+high)>>>1;if(displayIndex(sourceBoundaries[middle])<value)low=middle+1;else high=middle;}
        if(low<sourceBoundaries.length&&displayIndex(sourceBoundaries[low])==value)return sourceBoundaries[low];
        return sourceBoundaries[Math.max(0,low-1)];
    }
    public int floorDisplayBoundary(int index){return floor(displayBoundaries,Math.max(0,Math.min(index,display.length())));}
    public int ceilDisplayBoundary(int index){int value=Math.max(0,Math.min(index,display.length())),at=Arrays.binarySearch(displayBoundaries,value);return at>=0?value:displayBoundaries[Math.min(displayBoundaries.length-1,-at-1)];}
    public String sourceTrim(String trimmed,boolean reverse){
        if(mapping==null)return trimmed;
        if(display.isEmpty())return source;
        int cutoff=reverse?ceilDisplayBoundary(display.length()-trimmed.length()):floorDisplayBoundary(trimmed.length());
        int raw=sourceIndex(cutoff,!reverse);
        for(Span span:spans)if(cutoff>span.displayStart&&cutoff<span.displayEnd||raw>span.start&&raw<span.end)raw=reverse?span.end:span.start;
        raw=floor(sourceBoundaries,raw);return reverse?source.substring(raw):source.substring(0,raw);
    }
    public NickTextProjection then(NickTextProjection next){
        if(!display.equals(next.source))throw new IllegalArgumentException("Projection input differs");
        if(!next.changed())return this;
        int[] map=new int[source.length()+1];for(int i=0;i<map.length;i++)map[i]=next.displayIndex(displayIndex(i));
        return new NickTextProjection(source,next.display,map,List.of());
    }
    public NickTextProjection withSource(String raw){
        if(raw.length()!=source.length())throw new IllegalArgumentException("Input length differs");
        return raw.equals(source)?this:new NickTextProjection(raw,display,mapping,List.of());
    }
    public static boolean boundary(String text,int index){return Arrays.binarySearch(boundaries(text),index)>=0;}
    public static boolean codePointBoundary(String text,int index){return index<=0||index>=text.length()||!Character.isHighSurrogate(text.charAt(index-1))||!Character.isLowSurrogate(text.charAt(index));}
    private static int floor(int[] boundaries,int index){int at=Arrays.binarySearch(boundaries,index);return at>=0?index:boundaries[Math.max(0,-at-2)];}
    private static int[] boundaries(String text){
        boolean[] safe=boundaryMask(text);int count=0;for(boolean value:safe)if(value)count++;
        int[] result=new int[count];int at=0;for(int i=0;i<safe.length;i++)if(safe[i])result[at++]=i;return result;
    }
    public static boolean[] boundaryMask(String text){
        BreakIterator iterator=BreakIterator.getCharacterInstance(Locale.ROOT);iterator.setText(text);boolean[] safe=new boolean[text.length()+1];
        for(int at=iterator.first();at!=BreakIterator.DONE;at=iterator.next())safe[at]=true;
        for(int i=0;i+1<text.length();i++)if(text.charAt(i)=='\u00a7'){safe[i+1]=false;i++;}
        safe[0]=safe[text.length()]=true;return safe;
    }
}
