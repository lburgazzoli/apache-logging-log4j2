/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.filter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 * Filters events that fall within a specified time period in each day.
 */
@Plugin(name = "TimeFilter", category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE, printObject = true)
public final class TimeFilter extends AbstractFilter {

    /**
     * Length of hour in milliseconds.
     */
    private static final long HOUR_MS = 3600000;

    /**
     * Length of minute in milliseconds.
     */
    private static final long MINUTE_MS = 60000;

    /**
     * Length of second in milliseconds.
     */
    private static final long SECOND_MS = 1000;

    /**
     * Starting offset from midnight in milliseconds.
     */
    private final long start;
    /**
     * Ending offset from midnight in milliseconds.
     */
    private final long end;
    /**
     * Timezone.
     */
    private final TimeZone timezone;


    private TimeFilter(final long start, final long end, final TimeZone tz, final Result onMatch,
                       final Result onMismatch) {
        super(onMatch, onMismatch);
        this.start = start;
        this.end = end;
        timezone = tz;
    }

    @Override
    public Result filter(final LogEvent event) {
        final Calendar calendar = Calendar.getInstance(timezone);
        calendar.setTimeInMillis(event.getTimeMillis());
        //
        //   get apparent number of milliseconds since midnight
        //      (ignores extra or missing hour on daylight time changes).
        //
        final long apparentOffset = calendar.get(Calendar.HOUR_OF_DAY) * HOUR_MS +
            calendar.get(Calendar.MINUTE) * MINUTE_MS +
            calendar.get(Calendar.SECOND) * SECOND_MS +
            calendar.get(Calendar.MILLISECOND);
        return apparentOffset >= start && apparentOffset < end ? onMatch : onMismatch;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("start=").append(start);
        sb.append(", end=").append(end);
        sb.append(", timezone=").append(timezone.toString());
        return sb.toString();
    }

    /**
     * Create a TimeFilter.
     * @param start The start time.
     * @param end The end time.
     * @param tz timezone.
     * @param match Action to perform if the time matches.
     * @param mismatch Action to perform if the action does not match.
     * @return A TimeFilter.
     */
    @PluginFactory
    public static TimeFilter createFilter(
            @PluginAttribute("start") final String start,
            @PluginAttribute("end") final String end,
            @PluginAttribute("timezone") final String tz,
            @PluginAttribute("onMatch") final Result match,
            @PluginAttribute("onMismatch") final Result mismatch) {
        final long s = parseTimestamp(start, 0);
        final long e = parseTimestamp(end, Long.MAX_VALUE);
        final TimeZone timezone = tz == null ? TimeZone.getDefault() : TimeZone.getTimeZone(tz);
        final Result onMatch = match == null ? Result.NEUTRAL : match;
        final Result onMismatch = mismatch == null ? Result.DENY : mismatch;
        return new TimeFilter(s, e, timezone, onMatch, onMismatch);
    }

    private static long parseTimestamp(final String timestamp, final long defaultValue) {
        if (timestamp == null) {
            return defaultValue;
        }
        final SimpleDateFormat stf = new SimpleDateFormat("HH:mm:ss");
        stf.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return stf.parse(timestamp).getTime();
        } catch (ParseException e) {
            LOGGER.warn("Error parsing TimeFilter timestamp value {}", timestamp, e);
            return defaultValue;
        }
    }

}
