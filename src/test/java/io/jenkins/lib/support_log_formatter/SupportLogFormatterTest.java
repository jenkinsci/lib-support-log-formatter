/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import static io.jenkins.lib.support_log_formatter.SupportLogFormatter.transformMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToCompressingWhiteSpace;
import static org.hamcrest.Matchers.is;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Test;

public class SupportLogFormatterTest {

    @Test
    public void smokes() {
        assertFormatting("1970-01-01 00:00:00.000+0000 [id=999]\tINFO\tsome.pkg.Catcher#robust: some message\n",
                Level.INFO, "some message", null);
        assertFormatting("1970-01-01 00:00:00.000+0000 [id=999]\tWARNING\tsome.pkg.Catcher#robust: failed to do stuff\n" +
                        "PhonyException: oops\n" +
                        "\tat some.other.pkg.Thrower.buggy(Thrower.java:123)\n" +
                        "\tat some.pkg.Catcher.robust(Catcher.java:456)\n",
                Level.WARNING, "failed to do stuff", new PhonyException("oops", null));
        assertFormatting("1970-01-01 00:00:00.000+0000 [id=999]\tWARNING\tsome.pkg.Catcher#robust\n" +
                        "PhonyException: oops\n" +
                        "\tat some.other.pkg.Thrower.buggy(Thrower.java:123)\n" +
                        "\tat some.pkg.Catcher.robust(Catcher.java:456)\n",
                Level.WARNING, null, new PhonyException("oops", null));
        assertFormatting("1970-01-01 00:00:00.000+0000 [id=999]\tWARNING\tsome.pkg.Catcher#robust: failed to do stuff\n" +
                        "PhonyException2: lol\n" +
                        "\tat elsewhere.Classname.deeper(Classname.java:321)\n" +
                        "Caused: PhonyException: oops\n" +
                        "\tat some.other.pkg.Thrower.buggy(Thrower.java:123)\n" +
                        "\tat some.pkg.Catcher.robust(Catcher.java:456)\n",
                Level.WARNING, "failed to do stuff", new PhonyException("oops", new PhonyException2("lol", null)));
        assertFormatting("1970-01-01 00:00:00.000+0000 [id=999]\tWARNING\tsome.pkg.Catcher#robust: failed to do stuff\n" +
                        "I\n" + currentPlatformLineSeparatorIndicator() + "> am\n" + currentPlatformLineSeparatorIndicator() + "> fancy\n" +
                        "Caused: PhonyException: oops\n" +
                        "\tat some.other.pkg.Thrower.buggy(Thrower.java:123)\n" +
                        "\tat some.pkg.Catcher.robust(Catcher.java:456)\n",
                Level.WARNING, "failed to do stuff", new PhonyException("oops", new FancyException()));
        assertFormatting("1970-01-01 00:00:00.000+0000 [id=999]\tWARNING\tsome.pkg.Catcher#robust: failed to do stuff\n" +
                        "Also:   I\n\t" + currentPlatformLineSeparatorIndicator() + "> am\n\t" + currentPlatformLineSeparatorIndicator() + "> fancy\n" +
                        "PhonyException: oops\n" +
                        "\tat some.other.pkg.Thrower.buggy(Thrower.java:123)\n" +
                        "\tat some.pkg.Catcher.robust(Catcher.java:456)\n",
                Level.WARNING, "failed to do stuff", new PhonyException("oops", null, new FancyException()));
    }

    private static String currentPlatformLineSeparatorIndicator() {
        return System.lineSeparator().equals("\n") ? "[LF]" : "[CRLF]";
    }

    @Test
    public void testTransformBasics() {
        assertThat(transformMessage("foo", ""), is("foo"));
        assertThat(transformMessage("foo", "    "), is("foo"));
        assertThat(transformMessage("foo\nbar", ""), is("foo" + System.lineSeparator() + "[LF]> bar"));
        assertThat(transformMessage("foo\nbar", "    "), is("foo" + System.lineSeparator() + "    [LF]> bar"));
        assertThat(transformMessage("foo\rbar", ""), is("foo" + System.lineSeparator() + "[CR]> bar"));
        assertThat(transformMessage("foo\rbar", "    "), is("foo" + System.lineSeparator() + "    [CR]> bar"));
        assertThat(transformMessage("foo\r\nbar", ""), is("foo" + System.lineSeparator() + "[CRLF]> bar"));
        assertThat(transformMessage("foo\r\nbar", "    "), is("foo" + System.lineSeparator() + "    [CRLF]> bar"));
        assertThat(transformMessage("foo\n\rbar", ""), is("foo" + System.lineSeparator() + "[LF]> " + System.lineSeparator() + "[CR]> bar"));
        assertThat(transformMessage("foo\n\rbar", "    "), is("foo" + System.lineSeparator() + "    [LF]> " + System.lineSeparator() + "    [CR]> bar"));
    }

    @Test
    public void testNewlinesForSecurity3424() {
        assertFormatting("1970-01-01 00:00:00.000+0000 [id=999]\tWARNING\tsome.pkg.Catcher#robust: oh no\n" +
                        "[LF]> 1970-01-01 00:00:00.000+0000 [id=999]\tSEVERE\tfake.Class#foo: injected\n" +
                        "Also:   SuppressedException: I\n\t[LF]> am\n\t[LF]> suppressed\n" +
                        "\t\tat suppressions.Suppressor.suppress(Suppressor.java:314)\n" +
                        "PhonyException2: foo\n[LF]> \n[LF]> bar\n" +
                        "\tat elsewhere.Classname.deeper(Classname.java:321)\n" +
                        "Caused: PhonyException: Where is this?\n[LF]> \tat fake.Class.method(Class.java:111)\n" +
                        "\tat some.other.pkg.Thrower.buggy(Thrower.java:123)\n" +
                        "\tat some.pkg.Catcher.robust(Catcher.java:456)\n",
                Level.WARNING, "oh no\n1970-01-01 00:00:00.000+0000 [id=999]\tSEVERE\tfake.Class#foo: injected",
                new PhonyException("Where is this?\n\tat fake.Class.method(Class.java:111)",
                        new PhonyException2("foo\n\nbar", null,
                                new SuppressedException("I\nam\nsuppressed", null))));
    }

    // TODO test abbreviateClassName

    private static void assertFormatting(@NonNull String expected, @NonNull Level level, @CheckForNull String message, @CheckForNull Throwable throwable) {
        LogRecord lr = new LogRecord(level, message);
        if (throwable != null) {
            lr.setThrown(throwable);
        }
        // unused: lr.setLoggerName("some.pkg.Catcher");
        lr.setThreadID(999);
        lr.setSourceClassName("some.pkg.Catcher");
        lr.setSourceMethodName("robust");
        lr.setInstant(Instant.ofEpochMilli(0));
        assertThat(new SupportLogFormatter().format(lr), equalToCompressingWhiteSpace(expected));
    }

    private static class PhonyException extends Throwable {
        @SuppressWarnings("OverridableMethodCallInConstructor")
        PhonyException(String message, Throwable cause, Throwable... suppressed) {
            super(message, cause);
            setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("some.other.pkg.Thrower", "buggy", "Thrower.java", 123),
                    new StackTraceElement("some.pkg.Catcher", "robust", "Catcher.java", 456),
            });
            for (Throwable throwable : suppressed) {
                addSuppressed(throwable);
            }
        }
        @Override
        public String toString() {
            // simplify a bit
            return super.toString().replace(getClass().getName(), getClass().getSimpleName());
        }
    }

    private static class PhonyException2 extends Throwable {
        @SuppressWarnings("OverridableMethodCallInConstructor")
        PhonyException2(String message, Throwable cause, Throwable... suppressed) {
            super(message, cause);
            setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("elsewhere.Classname", "deeper", "Classname.java", 321),
                    new StackTraceElement("some.other.pkg.Thrower", "buggy", "Thrower.java", 123),
                    new StackTraceElement("some.pkg.Catcher", "robust", "Catcher.java", 456),
            });
            for (Throwable throwable : suppressed) {
                addSuppressed(throwable);
            }
        }
        @Override
        public String toString() {
            // simplify a bit
            return super.toString().replace(getClass().getName(), getClass().getSimpleName());
        }
    }

    private static class SuppressedException extends Throwable {
        @SuppressWarnings("OverridableMethodCallInConstructor")
        SuppressedException(String message, Throwable cause, Throwable... suppressed) {
            super(message, cause);
            setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("suppressions.Suppressor", "suppress", "Suppressor.java", 314),
                    new StackTraceElement("some.other.pkg.Thrower", "buggy", "Thrower.java", 123),
                    new StackTraceElement("some.pkg.Catcher", "robust", "Catcher.java", 456),
            });
            for (Throwable throwable : suppressed) {
                addSuppressed(throwable);
            }
        }
        @Override
        public String toString() {
            // simplify a bit
            return super.toString().replace(getClass().getName(), getClass().getSimpleName());
        }
    }

    private static class FancyException extends Throwable {
        FancyException() {
            setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("suppressions.Suppressor", "suppress", "Suppressor.java", 314),
                    new StackTraceElement("some.other.pkg.Thrower", "buggy", "Thrower.java", 123),
                    new StackTraceElement("some.pkg.Catcher", "robust", "Catcher.java", 456),
            });
        }
        @Override
        public void printStackTrace(PrintStream s) {
            s.println("I");
            s.println("am");
            s.println("fancy");
        }

        @Override
        public void printStackTrace(PrintWriter s) {
            s.println("I");
            s.println("am");
            s.println("fancy");
        }
    }
}
