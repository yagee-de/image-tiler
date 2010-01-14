/*
 * $Id$
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

package org.mycore.services.iview2;

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
    private static Logger LOGGER = Logger.getLogger(MCRMemSaveImage.class);

    private static final short MIN_STEP = 3;

    private short zoomLevelPerStep;

    private int megaTileSize;

    private ImageReader imageReader;

    public MCRMemSaveImage(File file, String derivateID, String imagePath) throws IOException {
        super(file, derivateID, imagePath);
        ImageReader imageReader = createImageReader(this.imageFile);
        setImageReader(imageReader);
        setImageSize(imageReader);
        short zoomLevelAtATime = getZoomLevelPerStep(getImageWidth(), getImageHeight());
        setZoomLevelPerStep(zoomLevelAtATime);
        LOGGER.info("Using mega tile size of " + megaTileSize + "px");
    }

    @Override
    public MCRTiledPictureProps tile() throws IOException {
        try {
            //initialize some basic variables
            ZipOutputStream zout = getZipOutputStream();
            setImageZoomLevels(getZoomLevels(getImageWidth(), getImageHeight()));
            int redWidth = getImageWidth() / (megaTileSize / TILE_SIZE);
            int redHeight = getImageHeight() / (megaTileSize / TILE_SIZE);
            int stopOnZoomLevel=getZoomLevels(redWidth, redHeight);
            BufferedImage lastPhaseImage = null;
            boolean lastPhaseNeeded = Math.max(redWidth, redHeight) > TILE_SIZE;
            if (lastPhaseNeeded) {
                //prepare empty image for the last phase of tiling process 
                ImageTypeSpecifier imageType = imageReader.getImageTypes(0).next();
                int bufferedImageType = imageType.getBufferedImageType();
                if (bufferedImageType == BufferedImage.TYPE_CUSTOM)
                    bufferedImageType = BufferedImage.TYPE_INT_RGB;
                lastPhaseImage = new BufferedImage(redWidth, redHeight, bufferedImageType);
            }
            int xcount = (int) Math.ceil((float) getImageWidth() / (float) megaTileSize);
            int ycount = (int) Math.ceil((float) getImageHeight() / (float) megaTileSize);
            int imageZoomLevels = getImageZoomLevels();
            int zoomFactor = megaTileSize / TILE_SIZE;

            for (int x = 0; x < xcount; x++)
                for (int y = 0; y < ycount; y++) {
                    LOGGER.info("create new mega tile (" + x + "," + y + ")");
                    int xpos = x * megaTileSize;
                    int width = Math.min(megaTileSize, getImageWidth() - xpos);
                    int ypos = y * megaTileSize;
                    int height = Math.min(megaTileSize, getImageHeight() - ypos);
                    BufferedImage megaTile = getTileOfFile(xpos, ypos, width, height);
                    LOGGER.info("megaTile create - start tiling");
                    // stitch
                    BufferedImage tile = writeTiles(zout, megaTile, x, y, imageZoomLevels, zoomFactor, stopOnZoomLevel);
                    if (lastPhaseNeeded)
                        stichTiles(lastPhaseImage, tile, x * TILE_SIZE, y * TILE_SIZE);
                }
            if (lastPhaseNeeded) {
                lastPhaseImage = scaleBufferedImage(lastPhaseImage);
                int lastPhaseZoomLevels = getZoomLevels(lastPhaseImage.getHeight(), lastPhaseImage.getWidth());
                writeTiles(zout, lastPhaseImage, 0, 0, lastPhaseZoomLevels, 0, 0);
            }
            writeMetaData(zout);
            zout.close();
        } finally {
            // do we need to set the reader and writer to null?? like setImageReader(null) explicitly
            getImageReader().dispose();
        }
        return getImageProperties();
    }

    /**
     * Set the image size.
     * Maybe the hole image will be read into RAM for getWidth() and getHeight().
     * 
     * @param imageReader
     * @throws IOException
     */
    private void setImageSize(ImageReader imageReader) {
        try {
            setImageHeight(imageReader.getHeight(0));
            setImageWidth(imageReader.getWidth(0));
        } catch (IOException e) {
            LOGGER.error(e);
        }
    }

    private BufferedImage stichTiles(BufferedImage stitchImage, BufferedImage tileImage, int x, int y) {
        Graphics graphics = stitchImage.getGraphics();
        graphics.drawImage(tileImage, x, y, null);
        return stitchImage;
    }

    private BufferedImage writeTiles(ZipOutputStream zout, BufferedImage megaTile, int x, int y, int imageZoomLevels, int zoomFactor,
            int stopOnZoomLevel) throws IOException {
        int tWidth = megaTile.getWidth();
        int tHeight = megaTile.getHeight();
        BufferedImage tile = null;
        int txCount = (int) Math.ceil((float) tWidth / (float) TILE_SIZE);
        int tyCount = (int) Math.ceil((float) tHeight / (float) TILE_SIZE);
        for (int tx = 0; tx < txCount; tx++)
            for (int ty = 0; ty < tyCount; ty++) {
                tile = getTileOfImage(megaTile, tx, ty);
                int realX = (zoomFactor * x) + tx;
                int realY = (zoomFactor * y) + ty;
                writeTile(zout, tile, realX, realY, imageZoomLevels);
            }
        if (imageZoomLevels > stopOnZoomLevel) {
            tile = scaleBufferedImage(megaTile);
            return writeTiles(zout, tile, x, y, imageZoomLevels - 1, zoomFactor / 2, stopOnZoomLevel);
        }
        return tile;
    }

    private void setZoomLevelPerStep(short zoomLevel) {
        this.zoomLevelPerStep = zoomLevel;
        megaTileSize = MCRImage.TILE_SIZE * (int) Math.pow(2, zoomLevelPerStep); //4096x4096 if 4
    }

    private ImageReader getImageReader() {
        return imageReader;
    }

    protected BufferedImage getTileOfFile(int x, int y, int width, int height) throws IOException {
        ImageReadParam param = getImageReader().getDefaultReadParam();
        Rectangle srcRegion = new Rectangle(x, y, width, height);
        param.setSourceRegion(srcRegion);

        //BugFix for bug reported at: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4705399
        ImageTypeSpecifier typeToUse = null;
        for (Iterator<ImageTypeSpecifier> i = getImageReader().getImageTypes(0); i.hasNext();) {
            ImageTypeSpecifier type = i.next();
            if (type.getColorModel().getColorSpace().isCS_sRGB()) {
                typeToUse = type;
                break;
            }
        }
        if (typeToUse != null)
            param.setDestinationType(typeToUse);
        //End Of BugFix

        BufferedImage tile = getImageReader().read(0, param);
        if (tile.getColorModel().getPixelSize() > 24) {
            // convert to 24 bit
            LOGGER.info("Converting image to 24 bit color depth");
            BufferedImage newTile = new BufferedImage(tile.getWidth(), tile.getHeight(), BufferedImage.TYPE_INT_RGB);
            newTile.createGraphics().drawImage(tile, 0, 0, tile.getWidth(), tile.getHeight(), null);
            tile = newTile;
        }

        return tile;
    }

    private void setImageReader(ImageReader reader) {
        this.imageReader = reader;
    }

    private static BufferedImage getTileOfImage(BufferedImage megaTile, int x, int y) {
        int tileWidth = Math.min(megaTile.getWidth() - (TILE_SIZE * x), TILE_SIZE);
        int tileHeight = Math.min(megaTile.getHeight() - (TILE_SIZE * y), TILE_SIZE);
        if (tileWidth != 0 && tileHeight != 0) {
            return megaTile.getSubimage(x * TILE_SIZE, y * TILE_SIZE, tileWidth, tileHeight);
        }
        return null;
    }

    private static short getZoomLevelPerStep(int width, int height) {
        int zoomLevels = getZoomLevels(width, height);
        return (short) Math.max(MIN_STEP, (int) Math.ceil(zoomLevels / 2d));
    }

    private static ImageReader createImageReader(File imageFile) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(imageFile, "r");
        ImageInputStream iis = ImageIO.createImageInputStream(raf);
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        ImageReader reader = readers.next();
        reader.setInput(iis, false);
        return reader;
    }

}
