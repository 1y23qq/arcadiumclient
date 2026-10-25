// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

final class QaRuntimeErrors {
    private static final List<Map<String, Object>> ERRORS = new ArrayList<>();
    private static AbstractAppender appender;
    private static Throwable firstCause;
    private QaRuntimeErrors() {}

    static void install() {
        var context = (LoggerContext) LogManager.getContext(false);
        appender = new AbstractAppender("ArcadiumGuiQaStartup", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                if (!event.getLevel().isMoreSpecificThan(Level.ERROR)) return;
                String message = event.getMessage().getFormattedMessage();
                var causes = new ArrayList<String>();
                boolean critical = false;
                Throwable current = event.getThrown();
                for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
                    causes.add(current.getClass().getName() + ": " + current.getMessage());
                    critical |= current instanceof LinkageError;
                }
                String detail = (message + " " + causes).toLowerCase(Locale.ROOT);
                critical |= detail.contains("mixin") || detail.contains("critical injection") || detail.contains("shader") &&
                        (detail.contains("compil") || detail.contains("link") || detail.contains("failed"));
                if (!critical) return;
                synchronized (ERRORS) {
                    if (ERRORS.size() >= 20) return;
                    var error = new LinkedHashMap<String, Object>();
                    error.put("logger", event.getLoggerName());
                    error.put("message", message.substring(0, Math.min(message.length(), 4000)));
                    error.put("causes", causes);
                    if (event.getThrown() != null) error.put("stack", java.util.Arrays.stream(event.getThrown().getStackTrace()).limit(12).map(Object::toString).toList());
                    ERRORS.add(error);
                    if (firstCause == null) firstCause = event.getThrown();
                }
            }
        };
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, Level.ERROR, null);
        context.updateLoggers();
    }

    static void check() {
        synchronized (ERRORS) {
            if (!ERRORS.isEmpty()) throw new IllegalStateException("Native runtime component failed: " + ERRORS.getFirst(), firstCause);
        }
    }

    static List<Map<String, Object>> snapshot() { synchronized (ERRORS) { return List.copyOf(ERRORS); } }

    static void stop() {
        if (appender == null) return;
        var context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getRootLogger().removeAppender(appender.getName());
        context.updateLoggers();
        appender.stop();
    }
}
