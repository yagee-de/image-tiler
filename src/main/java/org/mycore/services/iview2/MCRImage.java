/**
 * $RCSfile: MCRImage.java,v $
 * $Revision: 1.0 $ $Date: 09.10.2008 08:37:05 $
 *
 * This file is part of ** M y C o R e **
 * Visit our homepage at http://www.mycore.de/ for details.
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
 * along with this program, normally in the file license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 *
 **/
package org.mycore.services.iview2;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageEncoder;
import com.sun.media.jai.codec.JPEGEncodeParam;
import com.sun.media.jai.codec.MemoryCacheSeekableStream;

public class MCRImage {

    protected File imageFile;

    protected BufferedImage waterMarkFile;

    private static Logger LOGGER = Logger.getLogger(MCRImage.class);

    protected BufferedImage image;

    protected static final int TILE_SIZE = 256;

    protected AtomicInteger imageTilesCount = new AtomicInteger();

    private int imageWidth;

    private int imageHeight;

    private int imageZoomLevels;

    protected String derivate;

    protected String imagePath;

    protected File tileDir;

    private ImageWriter imageWriter;

    private static JPEGImageWriteParam imageWriteParam;
    static {
        imageWriteParam = new JPEGImageWriteParam(Locale.getDefault());
        try {
            imageWriteParam.setProgressiveMode(JPEGImageWriteParam.MODE_DEFAULT);
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("Your JPEG encoder does not support progressive JPEGs.");
        }
        imageWriteParam.setCompressionMode(JPEGImageWriteParam.MODE_EXPLICIT);
        imageWriteParam.setCompressionQuality(0.75f);
    }

    protected MCRImage(File file, String derivateID, String imagePath) {
        this.imageFile = file;
        this.derivate = derivateID;
        this.imagePath = imagePath;
        setImageWriter(createImageWriter());
        LOGGER.info("MCRImage initialized");
    }

    public static MCRImage getInstance(File file, String derivateID, String imagePath) throws IOException {
        return new MCRMemSaveImage(file, derivateID, imagePath);
    }

    /**
     * set directory for generated .iview2 file
     * @param tileDir
     */
    public void setTileDir(File tileDir) {
        this.tileDir = tileDir;
    }

    public MCRTiledPictureProps tile() throws IOException {
        //the absolute Path is the docportal-directory, therefore the path "../mycore/..."
        //waterMarkFile = ImageIO.read(new File(MCRIview2Props.getProperty("Watermark")));	
        //create JPEG ImageWriter
        ImageWriter imageWriter = getImageWriter();
        //ImageWriter created
        BufferedImage image = loadImage();
        ZipOutputStream zout = getZipOutputStream();
        try {
            int zoomLevels = getZoomLevels(image);
            LOGGER.info("Will generate " + zoomLevels + " zoom levels.");
            for (int z = zoomLevels; z >= 0; z--) {
                LOGGER.info("Generating zoom level " + z);
                //image = reformatImage(scale(image));
                LOGGER.info("Writing out tiles..");

                int getMaxTileY = (int) Math.ceil(image.getHeight() / TILE_SIZE);
                int getMaxTileX = (int) Math.ceil(image.getWidth() / TILE_SIZE);
                for (int y = 0; y <= getMaxTileY; y++) {
                    for (int x = 0; x <= getMaxTileX; x++) {
                        BufferedImage tile = getTile(image, x, y, z);
                        writeTile(zout, tile, x, y, z);
                    }
                }
                if (z > 0)
                    image = scaleBufferedImage(image);
            }
            imageWriter.dispose();
            //close imageOutputStream after disposing imageWriter or else application will hang
            writeMetaData(zout);
            return getImageProperties();
        } finally {
            zout.close();
        }
    }

    protected MCRTiledPictureProps getImageProperties() {
        MCRTiledPictureProps picProps = new MCRTiledPictureProps();
        picProps.width = getImageWidth();
        picProps.height = getImageHeight();
        picProps.zoomlevel = getImageZoomLevels();
        picProps.countTiles = imageTilesCount.get();
        return picProps;
    }

    protected ImageWriter getImageWriter() {
        return imageWriter;
    }

    protected ImageWriter createImageWriter() {
        ImageWriter imageWriter = ImageIO.getImageWritersBySuffix("jpeg").next();
        return imageWriter;
    }

    protected ZipOutputStream getZipOutputStream() throws FileNotFoundException {
        File iviewFile = getTiledFile(this.tileDir, derivate, imagePath);
        LOGGER.info("Saving tiles in " + iviewFile.getAbsolutePath());
        iviewFile.getParentFile().mkdirs();
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(iviewFile));
        return zout;
    }

    protected void writeTile(ZipOutputStream zout, BufferedImage tile, int x, int y, int z) throws IOException {
        if (tile != null) {
            try {
                ZipEntry ze = new ZipEntry(new StringBuilder(Integer.toString(z)).append('/').append(y).append('/').append(x)
                        .append(".jpg").toString());
                zout.putNextEntry(ze);
                writeImageIoTile(zout, tile, x, y, z);
                imageTilesCount.incrementAndGet();
            } finally {
                zout.closeEntry();
            }
        }
    }

    protected void writeImageIoTile(ZipOutputStream zout, BufferedImage tile, int x, int y, int z) throws IOException {
        ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(zout);
        ImageWriter imageWriter = getImageWriter();
        try {
            imageWriter.setOutput(imageOutputStream);
            //tile = addWatermark(scaleBufferedImage(tile));        
            IIOImage iioImage = new IIOImage(tile, null, null);
            imageWriter.write(null, iioImage, imageWriteParam);
        } finally {
            imageWriter.reset();
            imageOutputStream.close();
        }
    }

    protected void writeJAITile(ZipOutputStream zout, BufferedImage tile, int x, int y, int z) throws IOException {
        JPEGEncodeParam jpegParam = new JPEGEncodeParam();
        jpegParam.setQuality(0.75f);
        ImageEncoder jpegEncoder = ImageCodec.createImageEncoder("JPEG", zout, jpegParam);
        jpegEncoder.encode(tile);
    }

    protected BufferedImage scaleBufferedImage(BufferedImage image) {
        LOGGER.info("Scaling image...");
        BufferedImage scaled;
        int width = image.getWidth() / 2;
        int height = image.getHeight() / 2;
        if (image.getType() == 0) {
            if (image.getColorModel().getPixelSize() > 8) {
                scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            } else {
                scaled = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            }
        } else {
            scaled = new BufferedImage(width, height, image.getType());
        }
        scaled.createGraphics().drawImage(image, 0, 0, width, height, null);
        LOGGER.info("Scaling done: " + width + "x" + height);
        return scaled;
    }

    private BufferedImage loadImage() throws IOException {
        BufferedImage image;
        RenderedOp render = getImage(imageFile);
        LOGGER.info("Converting to BufferedImage");
        // handle images with 32 and more bits
        if (render.getColorModel().getPixelSize() > 24) {
            // convert to 24 bit
            LOGGER.info("Converting image to 24 bit color depth");
            image = new BufferedImage(render.getWidth(), render.getHeight(), BufferedImage.TYPE_INT_RGB);
            image.createGraphics().drawImage(render.getAsBufferedImage(), 0, 0, render.getWidth(), render.getHeight(), null);
        } else {
            image = render.getAsBufferedImage();
        }
        LOGGER.info("Done loading image: " + image);
        return image;
    }

    @SuppressWarnings("unused")
    private BufferedImage getMemImage(File imageFile) throws FileNotFoundException {
        MemoryCacheSeekableStream stream = new MemoryCacheSeekableStream(new BufferedInputStream(new FileInputStream(imageFile)));
        final RenderedOp create = JAI.create("stream", stream);
        return create.getAsBufferedImage();
    }

    @SuppressWarnings("unused")
    private BufferedImage readAnImage(File imageFile2) throws FileNotFoundException, IOException {
        FileImageInputStream is = new FileImageInputStream(imageFile2);
        for (Iterator<ImageReader> it = ImageIO.getImageReaders(is); it.hasNext();) {
            ImageReader ir = it.next();
            ir.setInput(is, true);
            return ir.read(0);
        }
        return null;
    }

    private RenderedOp getImage(File imageFile) {
        LOGGER.info("Reading image: " + imageFile);
        RenderedOp render;
        render = JAI.create("fileload", imageFile.getAbsolutePath());
        return render;
    }

    private int getZoomLevels(RenderedImage image) {
        int maxDim = image.getHeight() > image.getWidth() ? image.getHeight() : image.getWidth();
        LOGGER.info("maximum dimension: " + maxDim);
        int zoomLevel = 0;
        while (maxDim > TILE_SIZE) {
            zoomLevel++;
            maxDim = maxDim / 2;
        }
        setImageHeight(image.getHeight());
        setImageWidth(image.getWidth());
        setImageZoomLevels(zoomLevel);
        return zoomLevel;
    }

    private BufferedImage getTile(BufferedImage image, int x, int y, int zoom) {
        int tileWidth = image.getWidth() - TILE_SIZE * x;
        int tileHeight = image.getHeight() - TILE_SIZE * y;
        if (tileWidth > TILE_SIZE)
            tileWidth = TILE_SIZE;
        if (tileHeight > TILE_SIZE)
            tileHeight = TILE_SIZE;
        if (tileWidth != 0 && tileHeight != 0) {
            return image.getSubimage(x * TILE_SIZE, y * TILE_SIZE, tileWidth, tileHeight);
        }
        return null;

    }

    private void writeMetaData(ZipOutputStream zout) throws IOException {
        ZipEntry ze = new ZipEntry("imageinfo.xml");
        zout.putNextEntry(ze);
        try {
            Element rootElement = new Element("imageinfo");
            Document imageInfo = new Document(rootElement);
            rootElement.setAttribute("derivate", derivate);
            rootElement.setAttribute("path", imagePath);
            rootElement.setAttribute("tiles", imageTilesCount.toString());
            rootElement.setAttribute("width", Integer.toString(getImageWidth()));
            rootElement.setAttribute("height", Integer.toString(getImageHeight()));
            rootElement.setAttribute("zoomLevel", Integer.toString(getImageZoomLevels()));
            XMLOutputter xout = new XMLOutputter(Format.getCompactFormat());
            xout.output(imageInfo, zout);
        } finally {
            zout.closeEntry();
        }
    }

    public BufferedImage addWatermark(BufferedImage image) {
        if (image.getWidth() >= waterMarkFile.getWidth() && image.getHeight() >= waterMarkFile.getHeight()) {
            int randx = (int) (Math.random() * (image.getWidth() - waterMarkFile.getWidth()));
            int randy = (int) (Math.random() * (image.getHeight() - waterMarkFile.getHeight()));
            image.createGraphics().drawImage(waterMarkFile, randx, randy, waterMarkFile.getWidth(), waterMarkFile.getHeight(), null);
        }
        return image;
    }

    /**
     * returns a {@link File} object of the .iview2 file or the derivate folder.
     * @param derivate derivateID
     * @param imagePath absolute image path or <code>null</code>
     * @return tile directory of derivate if <code>imagePath</code> is null or the tile file (.iview2)
     */
    public static File getTiledFile(File tileDir, String derivate, String imagePath) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("tileDir: " + tileDir + ", derivate: " + derivate + ", imagePath: " + imagePath);
        String[] idParts = derivate.split("_");
        for (int i = 0; i < idParts.length - 1; i++) {
            tileDir = new File(tileDir, idParts[i]);
        }
        String lastPart = idParts[idParts.length - 1];
        if (lastPart.length() > 3) {
            tileDir = new File(tileDir, lastPart.substring(lastPart.length() - 4, lastPart.length() - 2));
            tileDir = new File(tileDir, lastPart.substring(lastPart.length() - 2, lastPart.length()));
        } else {
            tileDir = new File(tileDir, lastPart);
        }
        tileDir = new File(tileDir, derivate);
        if (imagePath == null)
            return tileDir;
        String relPath = imagePath.substring(0, imagePath.lastIndexOf('.')) + ".iview2";
        return new File(tileDir.getAbsolutePath() + "/" + relPath);
    }

    protected void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    protected void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    protected void setImageZoomLevels(int imageZoomLevels) {
        this.imageZoomLevels = imageZoomLevels;
    }

    public int getImageZoomLevels() {
        return imageZoomLevels;
    }

    private void setImageWriter(ImageWriter imageWriter) {
        this.imageWriter = imageWriter;
    }
}
