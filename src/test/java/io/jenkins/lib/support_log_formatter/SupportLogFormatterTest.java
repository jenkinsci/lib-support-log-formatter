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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import static org.hamcrest.Matchers.equalToIgnoringWhiteSpace;
import static org.junit.Assert.*;
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
            Level.WARNING, "failed to do stuff", new PhonyException());
        assertFormatting("1970-01-01 00:00:00.000+0000 [id=999]\tWARNING\tsome.pkg.Catcher#robust\n" +
                         "PhonyException: oops\n" +
                         "\tat some.other.pkg.Thrower.buggy(Thrower.java:123)\n" +
                         "\tat some.pkg.Catcher.robust(Catcher.java:456)\n",
            Level.WARNING, null, new PhonyException());
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
        lr.setMillis(0);
        assertThat(new SupportLogFormatter().format(lr), equalToIgnoringWhiteSpace(expected));
    }

    private static class PhonyException extends Throwable {
        @SuppressWarnings("OverridableMethodCallInConstructor")
        PhonyException() {
            setStackTrace(new StackTraceElement[] {
                new StackTraceElement("some.other.pkg.Thrower", "buggy", "Thrower.java", 123),
                new StackTraceElement("some.pkg.Catcher", "robust", "Catcher.java", 456),
            });
        }
        @Override
        public String toString() {
            return "PhonyException: oops";
        }
    }

}
