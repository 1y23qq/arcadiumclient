// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.shared.debug;

import java.util.Locale;

public final class DebugGraphs {
    private final double[] frames=new double[160],pings=new double[160];
    private int position,count;
    private long previous;
    public void reset(){previous=0;position=count=0;}
    public void draw(DebugView.Surface surface,int width,int height,boolean fps,boolean ping,int latency,double serverMillis,long now){
        if(!fps && !ping){reset();return;}
        if(previous!=0){frames[position]=(now-previous)/1_000_000d;pings[position]=latency;position=(position+1)%frames.length;count=Math.min(frames.length,count+1);}previous=now;
        surface.begin(1);
        try{
            if(fps){chart(surface,2,height-68,frames,66.7);if(serverMillis>=0)surface.text(String.format(Locale.ROOT,"Server tick: %.1f ms",serverMillis),4,height-90,0xFFFFFFFF,true);surface.text("Frame time (ms)",4,height-80,0xFFFFFFFF,true);}
            if(ping){chart(surface,Math.max(2,width-162),height-68,pings,300);surface.text(latency<0?"Ping: --":latency+" ms ping",Math.max(4,width-160),height-80,0xFFFFFFFF,true);}
        }finally{surface.end();}
    }
    private void chart(DebugView.Surface surface,int x,int y,double[] values,double limit){
        surface.rect(x,y,x+160,y+62,0xB0202020);
        for(int i=0;i<count;i++){double value=values[(position-count+i+values.length)%values.length];if(value<0)continue;float size=(float)Math.max(1,Math.min(60,value*60/limit));surface.rect(x+i,y+61-size,x+i+1,y+61,value>limit*.75?0xFFFF5555:value>limit*.4?0xFFFFFF55:0xFF55FF55);}
    }
}
