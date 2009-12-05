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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;

/**
 * @author Thomas Scheffler (yagee)
 *
 */
public class MCRMemSaveImage extends MCRImage {
    private static final double LOG_2 = Math.log(2);

    private static final short TILE_SIZE_FACTOR = (short) (Math.log(TILE_SIZE) / LOG_2);

    private static int ZOOM_LEVEL_AT_A_TIME = 4;

    protected static int MEGA_TILE_SIZE = MCRImage.TILE_SIZE * (int) Math.pow(2, ZOOM_LEVEL_AT_A_TIME); //4096x4096
    
    public MCRMemSaveImage(File file, String derivateID, String imagePath) {
        super(file, derivateID, imagePath);
    }

    @Override
    public MCRTiledPictureProps tile() throws IOException {
        ImageReader imageReader = getReader();
        ImageWriter imageWriter = getImageWriter();
        try {
            ZipOutputStream zout = getZipOutputStream();
            this.imageHeight = imageReader.getHeight(0);
            this.imageWidth = imageReader.getWidth(0);
            this.imageZoomLevels = getZoomLevels(this.imageHeight, this.imageWidth);

            // Matthias
            for(int x = 0; x < imageWidth; x += MEGA_TILE_SIZE) {
                for(int y = 0; y < imageHeight; y += MEGA_TILE_SIZE) {
                    int width = Math.min(MEGA_TILE_SIZE, this.imageWidth - x);
                    int height = Math.min(MEGA_TILE_SIZE, this.imageHeight - y);
                    BufferedImage megaTile = getTileOfFile(imageReader, x, y, width, height);
                    // stitch this
                    // TODO: change 12 with dynamic value
                    BufferedImage tile = writeTiles(zout, imageWriter, megaTile, x >> 12, y >> 12, this.imageZoomLevels);
                }
            }
            zout.close();
            // Thomas
//            int xcount = (int) Math.ceil(this.imageWidth / MEGA_TILE_SIZE);
//            int ycount = (int) Math.ceil(this.imageHeight / MEGA_TILE_SIZE);
//            for (int x = 0; x < xcount; x++)
//                for (int y = 0; y < ycount; y++) {
//                    int xpos = x * MEGA_TILE_SIZE;
//                    int width = Math.min(MEGA_TILE_SIZE, this.imageWidth - xpos);
//                    int ypos = y * MEGA_TILE_SIZE;
//                    int height = Math.min(MEGA_TILE_SIZE, this.imageWidth - xpos);
//                    BufferedImage megaTile = getTileOfFile(imageReader, xpos, ypos, width, height);
//                    
//                    // stitch
//                    BufferedImage tile = writeTiles(zout, imageWriter, megaTile, x, y, this.imageZoomLevels);
//                }
        } finally {
            imageReader.dispose();
            imageWriter.dispose();
            
        }
        return getImageProperties();
    }

//    public BufferedImage stichTiles(BufferedImage buffImg_0_0, BufferedImage buffImg_1_0, BufferedImage buffImg_0_1, BufferedImage buffImg_1_1) {
//        int width = buffImg_0_0.getWidth() + buffImg_1_0.getWidth();
//        int height = buffImg_0_0.getHeight() + buffImg_0_1.getHeight();
//        BufferedImage stich = new BufferedImage(width, height , BufferedImage.TYPE_INT_RGB);
//        Graphics graphics = stich.getGraphics();
//        graphics.drawImage(buffImg_0_0, 0, 0, null);
//        graphics.drawImage(buffImg_1_0, buffImg_0_0.getWidth(), 0, null);
//        graphics.drawImage(buffImg_0_1, 0, buffImg_0_0.getHeight(), null);
//        graphics.drawImage(buffImg_1_1, buffImg_1_1.getWidth(), buffImg_1_0.getHeight(), null);
//        return stich;
//    }

    private BufferedImage writeTiles(ZipOutputStream zout, ImageWriter imageWriter, BufferedImage megaTile, int x, int y,
            int imageZoomLevels) throws IOException {
        int tWidth = megaTile.getWidth();
        int tHeight = megaTile.getHeight();
        BufferedImage tile = null;
        int txCount = (int) Math.ceil(tWidth / TILE_SIZE);
        int tyCount = (int) Math.ceil(tHeight / TILE_SIZE);
        int zoomFactor = (int) Math.pow(2, imageZoomLevels);
        for (int tx = 0; tx < txCount; tx++)
            for (int ty = 0; ty < tyCount; ty++) {
                tile = getTileOfImage(megaTile, tx, ty);
                int realX = zoomFactor * x + tx;
                int realY = zoomFactor * y + ty;
                writeTile(zout, imageWriter, tile, realX, realY, imageZoomLevels);
            }
        if (Math.max(tWidth, tHeight) > TILE_SIZE) {
            tile = scaleBufferedImage(megaTile);
            return writeTiles(zout, imageWriter, tile, txCount, tyCount, imageZoomLevels - 1);
        }
        return tile;
    }

    private BufferedImage getTileOfImage(BufferedImage megaTile, int x, int y) {
        int tileWidth = Math.min(megaTile.getWidth() - TILE_SIZE * x, TILE_SIZE);
        int tileHeight = Math.min(megaTile.getHeight() - TILE_SIZE * y, TILE_SIZE);
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

    protected ImageReader getReader() throws IOException {
        FileInputStream f = new FileInputStream(this.imageFile);
        ImageInputStream iis = ImageIO.createImageInputStream(f);
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        ImageReader reader = readers.next();
        reader.setInput(iis, false);
        return reader;
    }

    protected BufferedImage getTileOfFile(ImageReader reader, int x, int y, int width, int height) throws IOException {
        ImageReadParam param = reader.getDefaultReadParam();
//        int xDim = Math.min(width, this.imageWidth - x);
//        int yDim = Math.min(height, this.imageHeight - y);
//        Rectangle srcRegion = new Rectangle(x, y, xDim, yDim);
        
        Rectangle srcRegion = new Rectangle(x, y, width, height);
        param.setSourceRegion(srcRegion);
        return reader.read(0, param);
    }

}
