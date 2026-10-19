package io.github.arcadiumclient.forgecompat;

import java.io.Serializable;
import java.nio.charset.Charset;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.RandomAccessFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.RegexReplacement;
import org.apache.logging.log4j.core.util.Booleans;
import org.apache.logging.log4j.status.StatusLogger;

public final class LegacyLog4j {
    private LegacyLog4j() { }

    public static PatternLayout createLayout(String pattern, Configuration configuration, RegexReplacement replacement,
                                             String charsetName, String alwaysWriteExceptions) {
        Charset charset = Charset.defaultCharset();
        if (charsetName != null) {
            if (Charset.isSupported(charsetName)) charset = Charset.forName(charsetName);
            else StatusLogger.getLogger().error("Charset " + charsetName + " is not supported for layout, using " + charset.displayName());
        }
        return PatternLayout.newBuilder().withPattern(pattern == null ? PatternLayout.DEFAULT_CONVERSION_PATTERN : pattern)
            .withConfiguration(configuration).withRegexReplacement(replacement).withCharset(charset)
            .withAlwaysWriteExceptions(Booleans.parseBoolean(alwaysWriteExceptions, true)).build();
    }

    @SuppressWarnings("deprecation")
    public static RandomAccessFileAppender createAppender(String fileName, String append, String name, String immediateFlush,
                                                         String ignore, Layout<? extends Serializable> layout, Filter filter,
                                                         String advertise, String advertiseURI, Configuration configuration) {
        return RandomAccessFileAppender.createAppender(fileName, append, name, immediateFlush, null, ignore,
            layout, filter, advertise, advertiseURI, configuration);
    }
}
