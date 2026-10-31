// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.debug;
import io.github.arcadiumclient.runtime.mixin.client.access.SoundManagerDebugAccess;
import io.github.arcadiumclient.runtime.mixin.client.access.SoundEngineDebugAccess;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.client.Minecraft;
import paulscode.sound.SoundSystemConfig;

public final class LegacyDebugExtras {
    public static final LongAdder SENT=new LongAdder(),RECEIVED=new LongAdder();
    private static long sampled,sent,received;
    private static double sentRate,receivedRate;
    private LegacyDebugExtras() { }
    public static void collect(Minecraft mc,Map<String,String> values) {
        var engine=((SoundManagerDebugAccess)mc.getSoundManager()).arcadium$engine();
        var sounds=((SoundEngineDebugAccess)engine).arcadium$sources();
        long streams=sounds.values().stream().filter(sound->sound.isStream()).count();
        values.put("sounds",(sounds.size()-streams)+"/"+SoundSystemConfig.getNumberNormalChannels());
        values.put("ambient_sounds",streams+"/"+SoundSystemConfig.getNumberStreamingChannels());
        long now=System.nanoTime(),nextSent=SENT.sum(),nextReceived=RECEIVED.sum();
        if(sampled!=0 && now-sampled>=500_000_000L){double seconds=(now-sampled)/1_000_000_000d;sentRate=(nextSent-sent)/seconds;receivedRate=(nextReceived-received)/seconds;sent=nextSent;received=nextReceived;sampled=now;}
        if(sampled==0){sampled=now;sent=nextSent;received=nextReceived;}
        values.put("packets_sent",String.format(java.util.Locale.ROOT,"%.1f/s",sentRate));
        values.put("packets_received",String.format(java.util.Locale.ROOT,"%.1f/s",receivedRate));
    }
}
