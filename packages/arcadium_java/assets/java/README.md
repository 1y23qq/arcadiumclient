[`JavaInfo.java`](./JavaInfo.java) is compiled by Cargo's build script with a Java
8 bytecode target. Install JDK 25 and set `JAVA_HOME` before compiling the launcher.
No precompiled helper is stored in this source repository.

The probe reports the runtime's architecture, version, vendor, home, AWT support
and compiler availability without requiring a visible display. The original
OneLauncher helper was compiled by [LynithDev](https://github.com/LynithDev);
Arcadium builds its modified source during compilation.
