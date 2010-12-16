/*
 * $Revision: 5697 $ $Date: 04.12.2009 $
 *
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, in a file called gpl.txt or license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 */

package org.mycore.imagetiler;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;

import org.apache.log4j.Logger;

/**
 * Uses a special fast and memory saving algorithm to tile images.
 * Upper memory usage for 4GP images is about 280 MB (was 28GB),
 * 68GP would take up to 1.1 GB (was 476 GB) and and 1TP images 4.4 GB (was 7 TB).
 * 
 * @author Thomas Scheffler (yagee) & Matthias Eichner!!!
 */
class MCRMemSaveImage extends MCRImage {
    private static final Logger LOGGER = Logger.getLogger(MCRMemSaveImage.class);

    private static final short MIN_STEP = 3;

    private short zoomLevelPerStep;

    private int megaTileSize;

    private ImageReader imageReader;

    private static ImageInputStream imageInputStream;

    private static RandomAccessFile randomAccessFile;

    /**
     * for internal use only: uses required properties to instantiate.
     * @param file the image file
     * @param derivateID the derivate ID the image belongs to
     * @param relImagePath the relative path from the derivate root to the image
     * @throws IOException if {@link ImageReader} could not be instantiated
     */
    MCRMemSaveImage(final File file, final String derivateID, final String relImagePath) throws IOException {
        super(file, derivateID, relImagePath);
        final ImageReader imgReader = createImageReader(imageFile);
        setImageReader(imgReader);
        setImageSize(imgReader);
        final short zoomLevelAtATime = getZoomLevelPerStep(getImageWidth(), getImageHeight());
        setZoomLevelPerStep(zoomLevelAtATime);
        LOGGER.info("Using mega tile size of " + megaTileSize + "px for image sized " + getImageWidth() + "x" + getImageHeight());
    }

    private static ImageReader createImageReader(final File imageFile) throws IOException {
        randomAccessFile = new RandomAccessFile(imageFile, "r");
        imageInputStream = ImageIO.createImageInputStream(randomAccessFile);
        final Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
        final ImageReader reader = readers.next();
        reader.setInput(imageInputStream, false);
        return reader;
    }

    private static BufferedImage getTileOfImage(final BufferedImage megaTile, final int x, final int y) {
        final int tileWidth = Math.min(megaTile.getWidth() - TILE_SIZE * x, TILE_SIZE);
        final int tileHeight = Math.min(megaTile.getHeight() - TILE_SIZE * y, TILE_SIZE);
        if (tileWidth != 0 && tileHeight != 0) {
            return megaTile.getSubimage(x * TILE_SIZE, y * TILE_SIZE, tileWidth, tileHeight);
        }
        return null;
    }

    private static short getZoomLevelPerStep(final int width, final int height) {
        final int zoomLevels = getZoomLevels(width, height);
        return (short) Math.max(MIN_STEP, (int) Math.ceil(zoomLevels / 2d));
    }

    @Override
    public MCRTiledPictureProps tile() throws IOException {
        try {
            //initialize some basic variables
            final ZipOutputStream zout = getZipOutputStream();
            setImageZoomLevels(getZoomLevels(getImageWidth(), getImageHeight()));
            final int redWidth = (int) Math.ceil(getImageWidth() / ((double) megaTileSize / TILE_SIZE));
            final int redHeight = (int) Math.ceil(getImageHeight() / ((double) megaTileSize / TILE_SIZE));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("reduced size: " + redWidth + "x" + redHeight);
            }
            final int stopOnZoomLevel = getZoomLevels(redWidth, redHeight);
            BufferedImage lastPhaseImage = null;
            final boolean lastPhaseNeeded = Math.max(redWidth, redHeight) > TILE_SIZE;
            if (lastPhaseNeeded) {
                //prepare empty image for the last phase of tiling process 
                final ImageTypeSpecifier imageType = imageReader.getImageTypes(0).next();
                int bufferedImageType = imageType.getBufferedImageType();
                if (bufferedImageType == BufferedImage.TYPE_CUSTOM) {
                    bufferedImageType = BufferedImage.TYPE_INT_RGB;
                }
                lastPhaseImage = new BufferedImage(redWidth, redHeight, bufferedImageType);
            }
            final int xcount = (int) Math.ceil((float) getImageWidth() / (float) megaTileSize);
            final int ycount = (int) Math.ceil((float) getImageHeight() / (float) megaTileSize);
            final int imageZoomLevels = getImageZoomLevels();
            final int zoomFactor = megaTileSize / TILE_SIZE;

            for (int x = 0; x < xcount; x++) {
                for (int y = 0; y < ycount; y++) {
                    LOGGER.info("create new mega tile (" + x + "," + y + ")");
                    final int xpos = x * megaTileSize;
                    final int width = Math.min(megaTileSize, getImageWidth() - xpos);
                    final int ypos = y * megaTileSize;
                    final int height = Math.min(megaTileSize, getImageHeight() - ypos);
                    final BufferedImage megaTile = getTileOfFile(xpos, ypos, width, height);
                    LOGGER.info("megaTile create - start tiling");
                    // stitch
                    final BufferedImage tile = writeTiles(zout, megaTile, x, y, imageZoomLevels, zoomFactor, stopOnZoomLevel);
                    if (lastPhaseNeeded) {
                        stichTiles(lastPhaseImage, tile, x * TILE_SIZE, y * TILE_SIZE);
                    }
                }
            }
            if (lastPhaseNeeded) {
                lastPhaseImage = scaleBufferedImage(lastPhaseImage);
                final int lastPhaseZoomLevels = getZoomLevels(lastPhaseImage.getHeight(), lastPhaseImage.getWidth());
                writeTiles(zout, lastPhaseImage, 0, 0, lastPhaseZoomLevels, 0, 0);
            }
            writeMetaData(zout);
            zout.close();
        } finally {
            // do we need to set the reader and writer to null?? like setImageReader(null) explicitly
            getImageReader().dispose();
            randomAccessFile.close();
        }
        return getImageProperties();
    }

    private ImageReader getImageReader() {
        return imageReader;
    }

    private void setImageReader(final ImageReader reader) {
        imageReader = reader;
    }

    /**
     * Set the image size.
     * Maybe the hole image will be read into RAM for getWidth() and getHeight().
     * 
     * @param imgReader
     * @throws IOException
     */
    private void setImageSize(final ImageReader imgReader) {
        try {
            setImageHeight(imgReader.getHeight(0));
            setImageWidth(imgReader.getWidth(0));
        } catch (final IOException e) {
            LOGGER.error(e);
        }
    }

    private void setZoomLevelPerStep(final short zoomLevel) {
        zoomLevelPerStep = zoomLevel;
        megaTileSize = MCRImage.TILE_SIZE * (int) Math.pow(2, zoomLevelPerStep); //4096x4096 if 4
    }

    private BufferedImage stichTiles(final BufferedImage stitchImage, final BufferedImage tileImage, final int x, final int y) {
        final Graphics graphics = stitchImage.getGraphics();
        graphics.drawImage(tileImage, x, y, null);
        return stitchImage;
    }

    private BufferedImage writeTiles(final ZipOutputStream zout, final BufferedImage megaTile, final int x, final int y,
            final int imageZoomLevels, final int zoomFactor, final int stopOnZoomLevel) throws IOException {
        final int tWidth = megaTile.getWidth();
        final int tHeight = megaTile.getHeight();
        BufferedImage tile = null;
        final int txCount = (int) Math.ceil((float) tWidth / (float) TILE_SIZE);
        final int tyCount = (int) Math.ceil((float) tHeight / (float) TILE_SIZE);
        for (int tx = 0; tx < txCount; tx++) {
            for (int ty = 0; ty < tyCount; ty++) {
                tile = getTileOfImage(megaTile, tx, ty);
                final int realX = zoomFactor * x + tx;
                final int realY = zoomFactor * y + ty;
                writeTile(zout, tile, realX, realY, imageZoomLevels);
            }
        }
        if (imageZoomLevels > stopOnZoomLevel) {
            tile = scaleBufferedImage(megaTile);
            return writeTiles(zout, tile, x, y, imageZoomLevels - 1, zoomFactor / 2, stopOnZoomLevel);
        }
        return tile;
    }

    /**
     * Reads a rectangular area of the current image.
     * @param x upper left x-coordinate
     * @param y upper left y-coordinate
     * @param width width of the area of interest
     * @param height height of the area of interest
     * @return area of interest
     * @throws IOException if source file could not be read
     */
    protected BufferedImage getTileOfFile(final int x, final int y, final int width, final int height) throws IOException {
        final ImageReadParam param = getImageReader().getDefaultReadParam();
        final Rectangle srcRegion = new Rectangle(x, y, width, height);
        param.setSourceRegion(srcRegion);

        //BugFix for bug reported at: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4705399
        ImageTypeSpecifier typeToUse = null;
        for (final Iterator<ImageTypeSpecifier> i = getImageReader().getImageTypes(0); i.hasNext();) {
            final ImageTypeSpecifier type = i.next();
            if (type.getColorModel().getColorSpace().isCS_sRGB()) {
                typeToUse = type;
                break;
            }
        }
        if (typeToUse != null) {
            param.setDestinationType(typeToUse);
            //End Of BugFix
        }

        BufferedImage tile = getImageReader().read(0, param);
        if (tile.getColorModel().getPixelSize() > JPEG_CM_PIXEL_SIZE) {
            // convert to 24 bit
            LOGGER.info("Converting image to 24 bit color depth");
            final BufferedImage newTile = new BufferedImage(tile.getWidth(), tile.getHeight(), BufferedImage.TYPE_INT_RGB);
            newTile.createGraphics().drawImage(tile, 0, 0, tile.getWidth(), tile.getHeight(), null);
            tile = newTile;
        }

        return tile;
    }

}
