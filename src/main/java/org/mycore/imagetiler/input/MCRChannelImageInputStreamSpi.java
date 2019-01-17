/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mycore.imagetiler.input;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.mycore.imagetiler.input.impl.MCRFileChannelInputStream;

public class MCRChannelImageInputStreamSpi extends ImageInputStreamSpi {

    public MCRChannelImageInputStreamSpi() {
        super("MyCoRe Community (http://www.mycore.org)",
            Optional.ofNullable(
                MCRChannelImageInputStreamSpi.class.getPackage().getImplementationVersion()).orElse("1.0"),
            ReadableByteChannel.class);
    }

    @Override
    public String getDescription(Locale locale) {
        return "ReadableByteChannel ImageInputStream";
    }

    @Override
    public ImageInputStream createInputStreamInstance(Object input, boolean useCache, File cacheDir)
        throws IOException {
        if (input == null || !(input instanceof ReadableByteChannel)) {
            throw new IllegalArgumentException("invalid input");
        }
        if (input instanceof FileChannel) {
            return new MCRFileChannelInputStream((FileChannel) input);
        } else {
            InputStream is = Channels.newInputStream((ReadableByteChannel) input);

            if (useCache) {
                try {
                    return new FileCacheImageInputStream(is, cacheDir);
                } catch (IOException e) {
                    //no cache file
                    return new MemoryCacheImageInputStream(is);
                }
            }

            return new MemoryCacheImageInputStream(is);
        }
    }

}
