// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class EntityProgramScopes {
    interface Adapter {
        int begin(int program);
        void end(int token);
    }

    static final class Stack {
        private final Adapter body, modern;
        private final int[] programs = new int[16], bodyTokens = new int[16], modernTokens = new int[16];
        private final long[] tokens = new long[16];
        private int depth;
        private long sequence;
        private boolean changing;

        Stack(Adapter body, Adapter modern) { this.body = body; this.modern = modern; }

        long begin(int program) {
            if (changing) throw new IllegalStateException("Reentrant entity program scope change");
            if (depth == tokens.length || sequence == Long.MAX_VALUE) throw new IllegalStateException("Entity program scope budget exceeded");
            changing = true;
            try {
                int bodyToken = body.begin(program), modernToken;
                try { modernToken = modern.begin(program); }
                catch (RuntimeException | Error failure) {
                    try { body.end(bodyToken); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                    throw failure;
                }
                if (bodyToken == 0 && modernToken == 0) return 0;
                programs[depth] = program; bodyTokens[depth] = bodyToken; modernTokens[depth] = modernToken;
                long token = tokens[depth] = ++sequence;
                depth++;
                return token;
            } finally { changing = false; }
        }

        void end(long token, int program) {
            if (token == 0) return;
            if (changing) throw new IllegalStateException("Reentrant entity program scope change");
            if (depth == 0 || tokens[depth - 1] != token || programs[depth - 1] != program)
                throw new IllegalStateException("Unbalanced entity program scope");
            int at = depth - 1;
            changing = true;
            try {
                try { modern.end(modernTokens[at]); }
                catch (RuntimeException | Error failure) {
                    try { body.end(bodyTokens[at]); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                    throw failure;
                }
                body.end(bodyTokens[at]);
            } finally {
                tokens[at] = 0; programs[at] = bodyTokens[at] = modernTokens[at] = 0; depth--; changing = false;
            }
        }
    }

    private static final Stack SCOPES = new Stack(new Adapter() {
        public int begin(int program) { return GpuResidentBodyShaderState.beginProgram(program); }
        public void end(int token) { GpuResidentBodyShaderState.end(token); }
    }, new Adapter() {
        public int begin(int program) { return ModernArmorGlintPipeline.beginProgram(program); }
        public void end(int token) { ModernArmorGlintPipeline.endDraw(token); }
    });

    private EntityProgramScopes() {}

    public static long begin(int program) {
        return GpuResidentBodyShaders.enabled() || ModernArmorGlintPipeline.shaderAdapterEnabled() ? SCOPES.begin(program) : 0;
    }

    public static void end(long token, int program) { SCOPES.end(token, program); }
}
