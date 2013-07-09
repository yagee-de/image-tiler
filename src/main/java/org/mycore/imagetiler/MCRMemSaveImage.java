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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;

import org.apache.log4j.Logger;

/**
 * Uses a special fast and memory saving algorithm to tile images.
 * Upper memory usage for 4GP images is about 280 MB (was 28GB),
 * 68GP would take up to 1.1 GB (was 476 GB) and and 1TP images 4.4 GB (was 7 TB).
 * 
 * @author Thomas Scheffler (yagee)
 * @author Matthias Eichner
 */
class MCRMemSaveImage extends MCRImage {
    private static final Logger LOGGER = Logger.getLogger(MCRMemSaveImage.class);

    private static final short MIN_STEP = 3;

    private short zoomLevelPerStep;

    int megaTileSize;

    /**
     * for internal use only: uses required properties to instantiate.
     * @param file the image file
     * @param derivateID the derivate ID the image belongs to
     * @param relImagePath the relative path from the derivate root to the image
     * @throws IOException if {@link ImageReader} could not be instantiated
     */
    MCRMemSaveImage(final File file, final String derivateID, final String relImagePath) throws IOException {
        super(file, derivateID, relImagePath);
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

    private static BufferedImage stichTiles(final BufferedImage stitchImage, final BufferedImage tileImage,
        final int x, final int y) {
        final Graphics graphics = stitchImage.getGraphics();
        graphics.drawImage(tileImage, x, y, null);
        return stitchImage;
    }

    private static int getImageType(final ImageReader imageReader) throws IOException {
        Iterator<ImageTypeSpecifier> imageTypes = imageReader.getImageTypes(0);
        int imageType = BufferedImage.TYPE_INT_RGB;
        while (imageTypes.hasNext()) {
            final ImageTypeSpecifier imageTypeSpec = imageTypes.next();
            if (imageTypeSpec.getBufferedImageType() != BufferedImage.TYPE_CUSTOM) {
                //best fit
                LOGGER.debug("Pretty sure we should use " + imageTypeSpec.getBufferedImageType());
                imageType = imageTypeSpec.getBufferedImageType();
                break;
            } else {
                int pixelSize = imageTypeSpec.getColorModel().getPixelSize();
                if (pixelSize >= 8) {
                    LOGGER.debug("Quite sure we should use TYPE_INT_RGB for a pixel size of " + pixelSize);
                    imageType = BufferedImage.TYPE_INT_RGB;
                } else if (pixelSize == 8) {
                    if (imageTypeSpec.getColorModel().getNumColorComponents() > 1) {
                        LOGGER.debug("Quite sure we should use TYPE_INT_RGB for a pixel size of " + pixelSize);
                        imageType = BufferedImage.TYPE_INT_RGB;
                    } else {
                        LOGGER
                            .debug("Quite sure we should use TYPE_BYTE_GRAY as there is only one color component present");
                        imageType = BufferedImage.TYPE_BYTE_GRAY;
                    }
                } else if (pixelSize == 1) {
                    LOGGER.debug("Quite sure we should use TYPE_BYTE_GRAY as this image is binary.");
                    imageType = BufferedImage.TYPE_BYTE_GRAY;
                } else {
                    LOGGER.warn("Do not know how to handle a pixel size of " + pixelSize);
                }
            }
        }
        return imageType;
    }

    @Override
    protected void doTile(final ImageReader imageReader, final ZipOutputStream zout) throws IOException {
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
            int imageType = getImageType(imageReader);
            lastPhaseImage = new BufferedImage(redWidth, redHeight, imageType);
        }
        final int xcount = (int) Math.ceil((float) getImageWidth() / (float) megaTileSize);
        final int ycount = (int) Math.ceil((float) getImageHeight() / (float) megaTileSize);
        final int imageZoomLevels = getImageZoomLevels();
        final int zoomFactor = megaTileSize / TILE_SIZE;

        for (int x = 0; x < xcount; x++) {
            for (int y = 0; y < ycount; y++) {
                LOGGER.debug("create new mega tile (" + x + "," + y + ")");
                final int xpos = x * megaTileSize;
                final int width = Math.min(megaTileSize, getImageWidth() - xpos);
                final int ypos = y * megaTileSize;
                final int height = Math.min(megaTileSize, getImageHeight() - ypos);
                final BufferedImage megaTile = MCRImage.getTileOfFile(imageReader, xpos, ypos, width, height);
                LOGGER.debug("megaTile create - start tiling");
                // stitch
                final BufferedImage tile = writeTiles(zout, megaTile, x, y, imageZoomLevels, zoomFactor,
                    stopOnZoomLevel);
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
    }

    @Override
    protected void handleSizeChanged() {
        super.handleSizeChanged();
        final short zoomLevelAtATime = getZoomLevelPerStep(getImageWidth(), getImageHeight());
        setZoomLevelPerStep(zoomLevelAtATime);
        LOGGER.debug("Using mega tile size of " + megaTileSize + "px for image sized " + getImageWidth() + "x"
            + getImageHeight());
    }

    private void setZoomLevelPerStep(final short zoomLevel) {
        zoomLevelPerStep = zoomLevel;
        megaTileSize = MCRImage.TILE_SIZE * (int) Math.pow(2, zoomLevelPerStep); //4096x4096 if 4
    }

    private BufferedImage writeTiles(final ZipOutputStream zout, final BufferedImage megaTile, final int x,
        final int y, final int imageZoomLevels, final int zoomFactor, final int stopOnZoomLevel) throws IOException {
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

}
