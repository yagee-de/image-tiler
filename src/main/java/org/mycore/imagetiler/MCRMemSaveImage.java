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


import javax.imageio.ImageReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

/**
 * Uses a special fast and memory saving algorithm to tile images.
 * Upper memory usage for 4GP images is about 280 MB (was 28GB),
 * 68GP would take up to 1.1 GB (was 476 GB) and and 1TP images 4.4 GB (was 7 TB).
 *
 * @author Thomas Scheffler (yagee)
 * @author Matthias Eichner
 */
class MCRMemSaveImage extends MCRImage {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final short MIN_STEP = 3;

    private int megaTileSize;

    /**
     * for internal use only: uses required properties to instantiate.
     * @param file the image file
     * @param derivateID the derivate ID the image belongs to
     * @param relImagePath the relative path from the derivate root to the image
     */
    MCRMemSaveImage(final Path file, final String derivateID, final String relImagePath) {
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
        if (width * height > 1e9) {
            LOGGER.info("GigaPIXEL!!!!");
        }
        return (short) Math.max(MIN_STEP, (int) Math.ceil(zoomLevels / 2d));
    }

    private static void stichTiles(final BufferedImage stitchImage, final BufferedImage tileImage,
        final int x, final int y) {
        final Graphics graphics = stitchImage.getGraphics();
        graphics.drawImage(tileImage, x, y, null);
    }

    @Override
    protected void doTile(final ImageReader imageReader, final ZipOutputStream zout) throws IOException {
        final int redWidth = (int) Math.ceil(getImageWidth() / ((double) megaTileSize / TILE_SIZE));
        final int redHeight = (int) Math.ceil(getImageHeight() / ((double) megaTileSize / TILE_SIZE));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(() -> "reduced size: " + redWidth + "x" + redHeight);
        }
        final int stopOnZoomLevel = getZoomLevels(redWidth, redHeight);
        BufferedImage lastPhaseImage = null;
        final boolean lastPhaseNeeded = Math.max(redWidth, redHeight) > TILE_SIZE;

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
                    //prepare empty image for the last phase of tiling process
                    if (lastPhaseImage == null) {
                        lastPhaseImage = new BufferedImage(redWidth, redHeight, tile.getType());
                    }
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
        LOGGER.debug(() -> "Using mega tile size of " + megaTileSize + "px for image sized " + getImageWidth() + "x"
            + getImageHeight());
    }

    private void setZoomLevelPerStep(final short zoomLevel) {
        megaTileSize = MCRImage.TILE_SIZE * (int) Math.pow(2, zoomLevel); //4096x4096 if 4
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
