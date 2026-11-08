package ink.tuanzi.neomacros.fabric.web.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Bridges the mod's own log output into a {@link LogRing}: a log4j2 appender
 * attached to the root logger configuration that forwards only events whose
 * logger name belongs to this mod. The vanilla backend is log4j2 on every
 * supported node, so this stays version-independent.
 */
public final class LogTap {

    private final RingAppender appender;

    private LogTap(RingAppender appender) {
        this.appender = appender;
    }

    /**
     * Installs the tap: events from loggers named {@code <prefix>} or
     * {@code <prefix>.…} flow into the ring. Returns the installed tap (call
     * {@link #remove()} to detach, e.g. at server stop).
     */
    public static LogTap install(String loggerPrefix, LogRing ring) {
        return install("neomacros-web-log-ring", loggerPrefix, ring);
    }

    /**
     * Installs a named tap (each consumer needs its own appender name so
     * removal cannot detach a sibling). A blank prefix accepts every
     * logger, which is how the full-game capture mode records vanilla lines.
     */
    public static LogTap install(String appenderName, String loggerPrefix, LogRing ring) {
        RingAppender appender = new RingAppender(appenderName, loggerPrefix, ring);
        appender.start();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = context.getConfiguration().getRootLogger();
        root.addAppender(appender, null, null);
        context.updateLoggers();
        return new LogTap(appender);
    }

    /** Detaches the appender from the logging configuration. */
    public void remove() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = context.getConfiguration().getRootLogger();
        root.removeAppender(appender.getName());
        context.updateLoggers();
        appender.stop();
    }

    private static final class RingAppender extends AbstractAppender {

        private final String prefix;
        private final LogRing ring;

        private RingAppender(String name, String prefix, LogRing ring) {
            super(name, null, null, true,
                    org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
            this.prefix = prefix;
            this.ring = ring;
        }

        @Override
        public void append(LogEvent event) {
            String logger = event.getLoggerName();
            if (logger == null) {
                return;
            }
            if (!prefix.isBlank()
                    && !(logger.equals(prefix) || logger.startsWith(prefix + "."))) {
                return;
            }
            Throwable thrown = event.getThrown();
            String line = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
            if (thrown != null) {
                String message = thrown.getMessage();
                line = line + (message == null || line.contains(message) ? "" : ": " + message);
            }
            line = line.replace('\n', ' ').strip();
            if (line.isEmpty()) {
                return;
            }
            ring.append(event.getTimeMillis(), levelName(event.getLevel()), logger, line);
        }

        private static String levelName(Level level) {
            return switch (level.getStandardLevel()) {
                case DEBUG -> "DEBUG";
                case TRACE -> "DEBUG";
                case WARN -> "WARN";
                case ERROR, FATAL -> "ERROR";
                default -> "INFO";
            };
        }
    }
}
