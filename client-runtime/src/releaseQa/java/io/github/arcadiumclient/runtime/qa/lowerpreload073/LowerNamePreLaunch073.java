// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.lowerpreload073;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class LowerNamePreLaunch073 implements PreLaunchEntrypoint {
    private static final String CONTROL="io.github.arcadiumclient.runtime.client.optimizations.LowerNamePreparation";
    private static String quoted(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
    @Override public void onPreLaunch(){
        String output=System.getProperty("arcadium.lowerNames073.prelaunch.output");if(output==null)return;
        try{
            ClassLoader loader=Thread.currentThread().getContextClassLoader();if(loader==null)throw new IllegalStateException("No Fabric context loader");
            Class<?> control=Class.forName(CONTROL,false,loader);if(control.getClassLoader()!=loader)throw new IllegalStateException("Control did not load through the target context loader");
            List<String> handlers=new ArrayList<>();for(Method method:control.getDeclaredMethods())for(String suffix:List.of("lower073$active","lower073$noteHead","lower073$noteReturn"))if(method.getName().endsWith(suffix))handlers.add(suffix);
            if(new HashSet<>(handlers).size()!=3||handlers.size()!=3)throw new IllegalStateException("Reviewed lower GuardProbe handlers are missing or duplicated: "+handlers);
            String json="{\"schema\":\"arcadium.lower-prelaunch073.v1\",\"passed\":true,\"entrypoint\":\"Fabric preLaunch\",\"initializationRequested\":false,\"control\":"+quoted(CONTROL)+",\"loaderClass\":"+quoted(loader.getClass().getName())+",\"guardHandlers\":[\"lower073$active\",\"lower073$noteHead\",\"lower073$noteReturn\"],\"counterFieldsReadOrWritten\":false,\"rendererClassRequested\":false,\"performanceClaim\":false}";
            Path path=Path.of(output);Files.createDirectories(path.getParent());Files.writeString(path,json+"\n");
        }catch(Throwable failure){try{Path path=Path.of(output);Files.createDirectories(path.getParent());Files.writeString(path,"{\"schema\":\"arcadium.lower-prelaunch073.v1\",\"passed\":false,\"failure\":"+quoted(failure.toString())+"}\n");}catch(Exception write){failure.addSuppressed(write);}throw new IllegalStateException("QA-only lower preLaunch preload failed",failure);}
    }
}
