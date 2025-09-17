/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.lib.support_log_formatter;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings; // Acceptable because RetentionPolicy.CLASS
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Format log files in a nicer format that is easier to read and search.
 *
 * @author Stephen Connolly
 */
public class SupportLogFormatter extends Formatter {

    private final static ThreadLocal<SimpleDateFormat> threadLocalDateFormat = ThreadLocal.withInitial(() -> {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    });

    protected String formatTime(LogRecord record) {
        return threadLocalDateFormat.get().format(new Date(record.getMillis()));
    }
    private static final char[] CRLF = new char[] { '\r', '\n' };

    /**
     * Transforms a log message string for use on the CLI.
     * @param message the original log message
     * @param indent how far to indent subsequent lines
     * @return the transformed log message string
     */
    static String transformMessage(@NonNull String message, @NonNull String indent) {
        if (DO_NOT_FORMAT_FOR_CLI) {
            return message;
        }
        StringBuilder sb = new StringBuilder();
        final char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (i < chars.length - 1 && Arrays.equals(CRLF, 0, 2, chars, i, i + 2)) {
                // explicit line break indicator unless trailing newline
                if (i < chars.length - CRLF.length) {
                    sb.append(LINE_SEPARATOR).append(indent).append("[CRLF]").append(NEWLINE_INDICATOR);
                } else {
                    sb.append(LINE_SEPARATOR);
                }
                i += 1;
                continue;
            }
            if (c == '\n' || c == '\r') {
                // explicit line break indicator unless trailing newline
                // TODO Should we even consider \r to be a line break on its own?
                if (i < chars.length - 1) {
                    sb.append(LINE_SEPARATOR).append(indent).append(c == '\n' ? "[LF]" : "[CR]").append(NEWLINE_INDICATOR);
                } else {
                    sb.append(c);
                }
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static /* quasi-final */ boolean DO_NOT_FORMAT_FOR_CLI = Boolean.getBoolean(SupportLogFormatter.class.getName() + ".DO_NOT_FORMAT_FOR_CLI");
    static /* quasi-final */ String NEWLINE_INDICATOR = System.getProperty(SupportLogFormatter.class.getName() + ".NEWLINE_INDICATOR", "> ");

    private static final String LINE_SEPARATOR = System.lineSeparator();

    @Override
    @SuppressFBWarnings(
            value = {"DE_MIGHT_IGNORE"},
            justification = "The exception wasn't thrown on our stack frame"
    )
    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append(formatTime(record));
        builder.append(" [id=").append(record.getLongThreadID()).append("]");

        builder.append("\t").append(record.getLevel().getName()).append("\t");

        if (record.getSourceMethodName() != null) {
            String sourceClass;
            if (record.getSourceClassName() == null) {
                sourceClass = record.getLoggerName();
            } else {
                sourceClass = record.getSourceClassName();
            }

            builder.append(abbreviateClassName(sourceClass, 32)).append("#").append(record.getSourceMethodName());
        } else {
            String sourceClass;
            if (record.getSourceClassName() == null) {
                sourceClass = record.getLoggerName();
            } else {
                sourceClass = record.getSourceClassName();
            }
            builder.append(abbreviateClassName(sourceClass, 40));
        }

        String message = formatMessage(record);
        if (message != null) {
            builder.append(": ").append(transformMessage(message, ""));
        }

        builder.append("\n");

        if (record.getThrown() != null) {
            try {
                builder.append(printThrowable(record.getThrown()));
            } catch (Exception e) {
                // ignore
            }
        }

        return builder.toString();
    }

    public String abbreviateClassName(String fqcn, int targetLength) {
        if (fqcn == null) {
            return "-";
        }
        int fqcnLength = fqcn.length();
        if (fqcnLength < targetLength) {
            return fqcn;
        }
        int[] indexes = new int[16];
        int[] lengths = new int[17];
        int count = 0;
        for (int i = fqcn.indexOf('.'); i != -1 && count < indexes.length; i = fqcn.indexOf('.', i + 1)) {
            indexes[count++] = i;
        }
        if (count == 0) {
            return fqcn;
        }
        StringBuilder buf = new StringBuilder(targetLength);
        int requiredSavings = fqcnLength - targetLength;
        for (int i = 0; i < count; i++) {
            int previous = i > 0 ? indexes[i - 1] : -1;
            int available = indexes[i] - previous - 1;
            int length = requiredSavings > 0 ? Math.min(available, 1) : available;
            requiredSavings -= available - length;
            lengths[i] = length + 1;
        }
        lengths[count] = fqcnLength - indexes[count - 1];
        for (int i = 0; i <= count; i++) {
            if (i == 0) {
                buf.append(fqcn, 0, lengths[i] - 1);
            } else {
                buf.append(fqcn, indexes[i - 1], indexes[i - 1] + lengths[i]);
            }
        }
        return buf.toString();
    }

    // Copied from hudson.Functions, but with external references removed:
    public static String printThrowable(Throwable t) {
        if (t == null) {
            return "No Exception details";
        }
        StringBuilder s = new StringBuilder();
        doPrintStackTrace(s, t, null, "", new HashSet<>());
        return s.toString();
    }
    @SuppressFBWarnings(value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE", justification = "TODO needs triage")
    private static void doPrintStackTrace(StringBuilder s, Throwable t, Throwable higher, String prefix, Set<Throwable> encountered) {
        if (!encountered.add(t)) {
            s.append("<cycle to ").append(transformMessage(t.toString(), prefix)).append(">\n");
            return;
        }
        try {
            if (!t.getClass().getMethod("printStackTrace", PrintWriter.class).equals(Throwable.class.getMethod("printStackTrace", PrintWriter.class))) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                s.append(transformMessage(sw.toString(), prefix));
                return;
            }
        } catch (NoSuchMethodException x) {
            x.printStackTrace(); // err on the conservative side here
        }
        Throwable lower = t.getCause();
        if (lower != null) {
            doPrintStackTrace(s, lower, t, prefix, encountered);
        }
        for (Throwable suppressed : t.getSuppressed()) {
            s.append(prefix).append("Also:   ");
            doPrintStackTrace(s, suppressed, t, prefix + "\t", encountered);
        }
        if (lower != null) {
            s.append(prefix).append("Caused: ");
        }
        String summary = transformMessage(t.toString(), "");
        if (lower != null) {
            String suffix = ": " + transformMessage(lower.toString(), prefix);
            if (summary.endsWith(suffix)) {
                summary = summary.substring(0, summary.length() - suffix.length());
            }
        }
        s.append(summary).append(LINE_SEPARATOR);
        StackTraceElement[] trace = t.getStackTrace();
        int end = trace.length;
        if (higher != null) {
            StackTraceElement[] higherTrace = higher.getStackTrace();
            while (end > 0) {
                int higherEnd = end + higherTrace.length - trace.length;
                if (higherEnd <= 0 || !higherTrace[higherEnd - 1].equals(trace[end - 1])) {
                    break;
                }
                end--;
            }
        }
        for (int i = 0; i < end; i++) {
            s.append(prefix).append("\tat ").append(trace[i]).append(LINE_SEPARATOR);
        }
    }
    public static void printStackTrace(Throwable t, PrintWriter pw) {
        pw.println(printThrowable(t).trim());
    }
    public static void printStackTrace(Throwable t, PrintStream ps) {
        ps.println(printThrowable(t).trim());
    }

}
