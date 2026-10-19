// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public final class ArcadiumSlf4jProvider implements SLF4JServiceProvider {
    private final ConcurrentHashMap<String, org.slf4j.Logger> loggers = new ConcurrentHashMap<>();
    private final ILoggerFactory factory = name -> loggers.computeIfAbsent(name, Adapter::new);
    private final IMarkerFactory markers = new BasicMarkerFactory();
    private final MDCAdapter context = new Context();

    @Override public ILoggerFactory getLoggerFactory() { return factory; }
    @Override public IMarkerFactory getMarkerFactory() { return markers; }
    @Override public MDCAdapter getMDCAdapter() { return context; }
    @Override public String getRequestedApiVersion() { return "2.0.99"; }
    @Override public void initialize() { }

    private static final class Adapter extends LegacyAbstractLogger {
        private static final long serialVersionUID = 1;
        private final transient org.apache.logging.log4j.Logger delegate;

        Adapter(String loggerName) { name = loggerName; delegate = LogManager.getLogger(loggerName); }

        @Override public boolean isTraceEnabled() { return delegate.isTraceEnabled(); }
        @Override public boolean isDebugEnabled() { return delegate.isDebugEnabled(); }
        @Override public boolean isInfoEnabled() { return delegate.isInfoEnabled(); }
        @Override public boolean isWarnEnabled() { return delegate.isWarnEnabled(); }
        @Override public boolean isErrorEnabled() { return delegate.isErrorEnabled(); }
        @Override protected String getFullyQualifiedCallerName() { return Adapter.class.getName(); }

        @Override protected void handleNormalizedLoggingCall(Level level, Marker marker, String message, Object[] arguments, Throwable error) {
            FormattingTuple rendered = MessageFormatter.arrayFormat(message, arguments, error);
            delegate.log(org.apache.logging.log4j.Level.valueOf(level.name()), rendered.getMessage(), rendered.getThrowable());
        }
    }

    private static final class Context extends BasicMDCAdapter {
        @Override public void put(String key, String value) { ThreadContext.put(key, value); }
        @Override public String get(String key) { return ThreadContext.get(key); }
        @Override public void remove(String key) { ThreadContext.remove(key); }
        @Override public void clear() {
            for (String key : ThreadContext.getContext().keySet()) ThreadContext.remove(key);
        }
        @Override public Map<String, String> getCopyOfContextMap() { return new HashMap<>(ThreadContext.getContext()); }
        @Override public void setContextMap(Map<String, String> values) {
            clear();
            if (values != null) values.forEach(ThreadContext::put);
        }
    }
}
