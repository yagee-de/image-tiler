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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;

import org.apache.log4j.Logger;

/**
 * @author Thomas Scheffler (yagee) & ich!!!
 *
 */
public class MCRMemSaveImage extends MCRImage {
    private static Logger LOGGER = Logger.getLogger(MCRMemSaveImage.class);

    private static final double LOG_2 = Math.log(2);

    private static final short TILE_SIZE_FACTOR = (short) (Math.log(TILE_SIZE) / LOG_2);

    private static int ZOOM_LEVEL_AT_A_TIME = 4;

    protected static int MEGA_TILE_SIZE = MCRImage.TILE_SIZE * (int) Math.pow(2, ZOOM_LEVEL_AT_A_TIME); //4096x4096

    private ImageReader imageReader;

    public MCRMemSaveImage(File file, String derivateID, String imagePath) {
        super(file, derivateID, imagePath);
        setImageReader(createImageReader());
        setImageSize(getImageReader());
    }

    @Override
    public MCRTiledPictureProps tile() throws IOException {
        try {
            ZipOutputStream zout = getZipOutputStream();
            setImageZoomLevels(getZoomLevels(getImageHeight(), getImageWidth()));
            int redWidth = getImageWidth() / (MEGA_TILE_SIZE / TILE_SIZE);
            int redHeight = getImageHeight() / (MEGA_TILE_SIZE / TILE_SIZE);
            ImageTypeSpecifier imageType = imageReader.getImageTypes(0).next();
            int bufferedImageType = imageType.getBufferedImageType();
            if (bufferedImageType == BufferedImage.TYPE_CUSTOM)
                bufferedImageType = BufferedImage.TYPE_INT_RGB;
            BufferedImage lastPhaseImage = new BufferedImage(redWidth, redHeight, bufferedImageType);
            int xcount = (int) Math.ceil((float) getImageWidth() / (float) MEGA_TILE_SIZE);
            int ycount = (int) Math.ceil((float) getImageHeight() / (float) MEGA_TILE_SIZE);
            int imageZoomLevels = getImageZoomLevels();
            int zoomFactor = MEGA_TILE_SIZE / TILE_SIZE;

            for (int x = 0; x < xcount; x++)
                for (int y = 0; y < ycount; y++) {
                    LOGGER.info("create new mega tile (" + x + "," + y + ")");
                    int xpos = x * MEGA_TILE_SIZE;
                    int width = Math.min(MEGA_TILE_SIZE, getImageWidth() - xpos);
                    int ypos = y * MEGA_TILE_SIZE;
                    int height = Math.min(MEGA_TILE_SIZE, getImageHeight() - ypos);
                    BufferedImage megaTile = getTileOfFile(xpos, ypos, width, height);
                    LOGGER.info("megaTile create - start tiling");
                    // stitch
                    BufferedImage tile = writeTiles(zout, megaTile, x, y, imageZoomLevels, zoomFactor);
                    stichTiles(lastPhaseImage, tile, x * TILE_SIZE, y * TILE_SIZE);
                }
            if (Math.max(lastPhaseImage.getHeight(), lastPhaseImage.getWidth()) > TILE_SIZE) {
                lastPhaseImage = scaleBufferedImage(lastPhaseImage);
                int lastPhaseZoomLevels = getZoomLevels(lastPhaseImage.getWidth(), lastPhaseImage.getHeight());
                writeTiles(zout, lastPhaseImage, 0, 0, lastPhaseZoomLevels, 0);
            }
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

    public BufferedImage stichTiles(BufferedImage stitchImage, BufferedImage tileImage, int x, int y) {
        Graphics graphics = stitchImage.getGraphics();
        graphics.drawImage(tileImage, x, y, null);
        return stitchImage;
    }

    private BufferedImage writeTiles(ZipOutputStream zout, BufferedImage megaTile, int x, int y, int imageZoomLevels, int zoomFactor)
            throws IOException {
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
        if (Math.max(tWidth, tHeight) > TILE_SIZE) {
            tile = scaleBufferedImage(megaTile);
            return writeTiles(zout, tile, x, y, imageZoomLevels - 1, zoomFactor / 2);
        }
        return tile;
    }

    private BufferedImage getTileOfImage(BufferedImage megaTile, int x, int y) {
        int tileWidth = Math.min(megaTile.getWidth() - (TILE_SIZE * x), TILE_SIZE);
        int tileHeight = Math.min(megaTile.getHeight() - (TILE_SIZE * y), TILE_SIZE);
        if (tileWidth != 0 && tileHeight != 0) {
            return megaTile.getSubimage(x * TILE_SIZE, y * TILE_SIZE, tileWidth, tileHeight);
        }
        return null;
    }

    private static short getZoomLevels(int imageHeight, int imageWidth) {
        int maxDim = Math.max(imageHeight, imageWidth);
        short maxZoom = (short) Math.ceil(Math.log(maxDim) / LOG_2 - TILE_SIZE_FACTOR);
        return maxZoom;
    }

    private ImageReader getImageReader() {
        return imageReader;
    }

    private ImageReader createImageReader() {
        try {
            FileInputStream f = new FileInputStream(this.imageFile);
            ImageInputStream iis = ImageIO.createImageInputStream(f);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            ImageReader reader = readers.next();
            reader.setInput(iis, false);
            return reader;
        } catch (FileNotFoundException e) {
            LOGGER.error(e);
        } catch (IOException e) {
            LOGGER.error(e);
        }
        return null;
    }

    protected BufferedImage getTileOfFile(int x, int y, int width, int height) throws IOException {
        ImageReadParam param = getImageReader().getDefaultReadParam();
        //        int xDim = Math.min(width, this.imageWidth - x);
        //        int yDim = Math.min(height, this.imageHeight - y);
        //        Rectangle srcRegion = new Rectangle(x, y, xDim, yDim);

        Rectangle srcRegion = new Rectangle(x, y, width, height);
        param.setSourceRegion(srcRegion);
        return getImageReader().read(0, param);
    }

    private void setImageReader(ImageReader reader) {
        this.imageReader = reader;
    }

}
