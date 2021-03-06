/*
 * Copyright 2014-2020 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.agent;

import org.agrona.SystemUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static io.aeron.agent.DriverEventCode.*;

/**
 * Common configuration elements between event loggers and event reader side
 */
public class EventConfiguration
{
    /**
     * Event Buffer length system property name
     */
    public static final String BUFFER_LENGTH_PROP_NAME = "aeron.event.buffer.length";

    /**
     * Driver Event tags system property name. This is either:
     * <ul>
     * <li>A comma separated list of {@link DriverEventCode}s to enable</li>
     * <li>"all" which enables all the codes</li>
     * <li>"admin" which enables the codes specified by {@link #ADMIN_ONLY_EVENT_CODES} which is the admin commands</li>
     * </ul>
     */
    public static final String ENABLED_EVENT_CODES_PROP_NAME = "aeron.event.log";

    /**
     * Cluster Event tags system property name. This is either:
     * <ul>
     * <li>A comma separated list of {@link ClusterEventCode}s to enable</li>
     * <li>"all" which enables all the codes</li>
     * </ul>
     */
    public static final String ENABLED_CLUSTER_EVENT_CODES_PROP_NAME = "aeron.event.cluster.log";

    /**
     * Archive Event tags system property name. This is either:
     * <ul>
     * <li>A comma separated list of {@link ArchiveEventCode}s to enable</li>
     * <li>"all" which enables all the codes</li>
     * </ul>
     */
    public static final String ENABLED_ARCHIVE_EVENT_CODES_PROP_NAME = "aeron.event.archive.log";

    public static final Set<DriverEventCode> ADMIN_ONLY_EVENT_CODES = EnumSet.of(
        CMD_IN_ADD_PUBLICATION,
        CMD_IN_ADD_SUBSCRIPTION,
        CMD_IN_KEEPALIVE_CLIENT,
        CMD_IN_REMOVE_PUBLICATION,
        CMD_IN_REMOVE_SUBSCRIPTION,
        CMD_IN_ADD_COUNTER,
        CMD_IN_REMOVE_COUNTER,
        CMD_IN_CLIENT_CLOSE,
        CMD_IN_ADD_RCV_DESTINATION,
        CMD_IN_REMOVE_RCV_DESTINATION,
        REMOVE_IMAGE_CLEANUP,
        REMOVE_PUBLICATION_CLEANUP,
        REMOVE_SUBSCRIPTION_CLEANUP,
        CMD_OUT_PUBLICATION_READY,
        CMD_OUT_AVAILABLE_IMAGE,
        CMD_OUT_ON_UNAVAILABLE_IMAGE,
        CMD_OUT_ON_OPERATION_SUCCESS,
        CMD_OUT_ERROR,
        CMD_OUT_SUBSCRIPTION_READY,
        CMD_OUT_COUNTER_READY,
        CMD_OUT_ON_UNAVAILABLE_COUNTER,
        CMD_OUT_ON_CLIENT_TIMEOUT,
        CMD_IN_TERMINATE_DRIVER,
        SEND_CHANNEL_CREATION,
        RECEIVE_CHANNEL_CREATION,
        SEND_CHANNEL_CLOSE,
        RECEIVE_CHANNEL_CLOSE);

    public static final Set<DriverEventCode> ALL_LOGGER_EVENT_CODES = EnumSet.allOf(DriverEventCode.class);

    /**
     * Event Buffer default length (in bytes)
     */
    public static final int BUFFER_LENGTH_DEFAULT = 2 * 1024 * 1024;

    /**
     * Maximum length of an event in bytes
     */
    public static final int MAX_EVENT_LENGTH = 4096 + System.lineSeparator().length();

    /**
     * Limit for event reader loop
     */
    public static final int EVENT_READER_FRAME_LIMIT = 8;

    /**
     * Ring Buffer to use for logging that will be read by {@link EventLogAgent#READER_CLASSNAME_PROP_NAME}.
     */
    public static final ManyToOneRingBuffer EVENT_RING_BUFFER;

    static
    {
        final int bufferLength = SystemUtil.getSizeAsInt(
            EventConfiguration.BUFFER_LENGTH_PROP_NAME, EventConfiguration.BUFFER_LENGTH_DEFAULT) +
            RingBufferDescriptor.TRAILER_LENGTH;

        EVENT_RING_BUFFER = new ManyToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));
    }

    public static long getEnabledDriverEventCodes()
    {
        return makeTagBitSet(getEnabledDriverEventCodes(System.getProperty(ENABLED_EVENT_CODES_PROP_NAME)));
    }

    public static long getEnabledClusterEventCodes()
    {
        return makeTagBitSet(getEnabledClusterEventCodes(System.getProperty(ENABLED_CLUSTER_EVENT_CODES_PROP_NAME)));
    }

    public static long getEnabledArchiveEventCodes()
    {
        return makeTagBitSet(getEnabledArchiveEventCodes(System.getProperty(ENABLED_ARCHIVE_EVENT_CODES_PROP_NAME)));
    }

    static long makeTagBitSet(final Set<? extends EventCode> eventCodes)
    {
        long result = 0;

        for (final EventCode eventCode : eventCodes)
        {
            result |= eventCode.tagBit();
        }

        return result;
    }

    /**
     * Get the {@link Set} of {@link ClusterEventCode}s that are enabled for the logger.
     *
     * @param enabledClusterEventCodes that can be "all" or a comma separated list of Event Code ids or names.
     * @return the {@link Set} of {@link ClusterEventCode}s that are enabled for the logger.
     */
    static Set<ClusterEventCode> getEnabledClusterEventCodes(final String enabledClusterEventCodes)
    {
        if (null == enabledClusterEventCodes || "".equals(enabledClusterEventCodes))
        {
            return EnumSet.noneOf(ClusterEventCode.class);
        }

        final Function<Integer, ClusterEventCode> eventCodeById = ClusterEventCode::get;
        final Function<String, ClusterEventCode> eventCodeByName = ClusterEventCode::valueOf;
        final EnumSet<ClusterEventCode> allEventsSet = EnumSet.allOf(ClusterEventCode.class);

        return parseEventCodes(enabledClusterEventCodes, eventCodeById, eventCodeByName, allEventsSet);
    }

    /**
     * Get the {@link Set} of {@link ArchiveEventCode}s that are enabled for the logger.
     *
     * @param enabledArchiveEventCodes that can be "all" or a comma separated list of Event Code ids or names.
     * @return the {@link Set} of {@link ArchiveEventCode}s that are enabled for the logger.
     */
    static Set<ArchiveEventCode> getEnabledArchiveEventCodes(final String enabledArchiveEventCodes)
    {
        if (null == enabledArchiveEventCodes || "".equals(enabledArchiveEventCodes))
        {
            return EnumSet.noneOf(ArchiveEventCode.class);
        }

        final Function<Integer, ArchiveEventCode> eventCodeById = ArchiveEventCode::get;
        final Function<String, ArchiveEventCode> eventCodeByName = ArchiveEventCode::valueOf;
        final EnumSet<ArchiveEventCode> allEventsSet = EnumSet.allOf(ArchiveEventCode.class);

        return parseEventCodes(enabledArchiveEventCodes, eventCodeById, eventCodeByName, allEventsSet);
    }

    /**
     * Get the {@link Set} of {@link DriverEventCode}s that are enabled for the logger.
     *
     * @param enabledLoggerEventCodes that can be "all", "admin", or a comma separated list of Event Code ids or names.
     * @return the {@link Set} of {@link DriverEventCode}s that are enabled for the logger.
     */
    static Set<DriverEventCode> getEnabledDriverEventCodes(final String enabledLoggerEventCodes)
    {
        if (null == enabledLoggerEventCodes || "".equals(enabledLoggerEventCodes))
        {
            return EnumSet.noneOf(DriverEventCode.class);
        }

        final Set<DriverEventCode> eventCodeSet = new HashSet<>();
        final String[] codeIds = enabledLoggerEventCodes.split(",");

        for (final String codeId : codeIds)
        {
            switch (codeId)
            {
                case "all":
                    eventCodeSet.addAll(ALL_LOGGER_EVENT_CODES);
                    break;

                case "admin":
                    eventCodeSet.addAll(ADMIN_ONLY_EVENT_CODES);
                    break;

                default:
                {
                    DriverEventCode code = null;
                    try
                    {
                        code = DriverEventCode.valueOf(codeId);
                    }
                    catch (final IllegalArgumentException ignore)
                    {
                    }

                    if (null == code)
                    {
                        try
                        {
                            code = DriverEventCode.get(Integer.parseInt(codeId));
                        }
                        catch (final IllegalArgumentException ignore)
                        {
                        }
                    }

                    if (null != code)
                    {
                        eventCodeSet.add(code);
                    }
                    else
                    {
                        System.err.println("unknown event code: " + codeId);
                    }
                }
            }
        }

        return eventCodeSet;
    }

    private static <T extends Enum<T>> Set<T> parseEventCodes(
        final String enabledEventCodes,
        final Function<Integer, T> eventCodeById,
        final Function<String, T> eventCodeByName,
        final EnumSet<T> allEventsSet)
    {
        final Set<T> eventCodeSet = new HashSet<>();
        final String[] codeIds = enabledEventCodes.split(",");

        for (final String codeId : codeIds)
        {
            if ("all".equals(codeId))
            {
                eventCodeSet.addAll(allEventsSet);
            }
            else
            {
                T code = null;
                try
                {
                    code = eventCodeByName.apply(codeId);
                }
                catch (final IllegalArgumentException ignore)
                {
                }

                if (null == code)
                {
                    try
                    {
                        code = eventCodeById.apply(Integer.parseInt(codeId));
                    }
                    catch (final IllegalArgumentException ignore)
                    {
                    }
                }

                if (null != code)
                {
                    eventCodeSet.add(code);
                }
                else
                {
                    System.err.println("unknown event code: " + codeId);
                }
            }
        }

        return eventCodeSet;
    }
}
