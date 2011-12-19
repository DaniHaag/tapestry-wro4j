// Copyright 2011 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.github.lltyk.wro4j.services;

import org.apache.tapestry5.internal.IOOperation;
import org.apache.tapestry5.internal.TapestryInternalUtils;
import org.apache.tapestry5.internal.services.assets.BytestreamCache;
import org.apache.tapestry5.internal.services.assets.StreamableResourceImpl;
import org.apache.tapestry5.ioc.OperationTracker;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.services.assets.CompressionStatus;
import org.apache.tapestry5.services.assets.ResourceMinimizer;
import org.apache.tapestry5.services.assets.StreamableResource;
import org.slf4j.Logger;

import javax.management.RuntimeErrorException;
import java.io.*;

/**
 * Base class for resource minimizers.
 *
 * @since 5.3
 */
public abstract class AbstractMinimizer implements ResourceMinimizer
{
    private static final double NANOS_TO_MILLIS = 1.0d / 1000000.0d;

    private final Logger logger;

    private final OperationTracker tracker;

    private final String resourceType;

    public AbstractMinimizer(Logger logger, OperationTracker tracker, String resourceType)
    {
        this.logger = logger;
        this.tracker = tracker;
        this.resourceType = resourceType;
    }

    public StreamableResource minimize(final StreamableResource input) throws IOException
    {
        long startNanos = System.nanoTime();

        ByteArrayOutputStream bos = new ByteArrayOutputStream(1000);

        final Writer writer = new OutputStreamWriter(bos);

        TapestryInternalUtils.performIO(tracker, "Minimizing " + resourceType, new IOOperation()
        {
            public void perform() throws IOException
            {
                try
                {
                    doMinimize(input, writer);
                } catch (RuntimeErrorException ex)
                {
                    throw new RuntimeException(String.format("Unable to minimize %s: %s", resourceType,
                            InternalUtils.toMessage(ex)), ex);
                }

            }
        });

        writer.close();

        // The content is minimized, but can still be (GZip) compressed.

        StreamableResource output = new StreamableResourceImpl("minimized " + input.getDescription(),
                input.getContentType(), CompressionStatus.COMPRESSABLE,
                input.getLastModified(), new BytestreamCache(bos));

        long elapsedNanos = System.nanoTime() - startNanos;

        if (logger.isDebugEnabled())
        {
            double elapsedMillis = ((double) elapsedNanos) * NANOS_TO_MILLIS;

            logger.debug(String.format("Minimized %s (%,d input bytes of %s to %,d output bytes in %.2f ms)",
                    input.getDescription(), input.getSize(), resourceType, output.getSize(), elapsedMillis));
        }

        return output;
    }

    protected Reader toReader(StreamableResource input) throws IOException
    {
        InputStream is = input.openStream();

        return new InputStreamReader(is, "UTF-8");
    }

    /**
     * Implemented in subclasses to do the actual work.
     *
     * @param resource content to minimize
     * @param output   writer for minimized version of input
     */
    protected abstract void doMinimize(StreamableResource resource, Writer output) throws IOException;
}
