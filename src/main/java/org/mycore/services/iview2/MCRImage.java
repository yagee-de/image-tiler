/*
 * $Revision: 1.0 $ $Date: 09.10.2008 08:37:05 $
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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import com.sun.media.jai.codec.MemoryCacheSeekableStream;

/**
 * The <code>MCRImage</code> class describes an image with different zoom levels that can be accessed by its tiles.
 * 
 * The main purpose of this class is to provide a method to {@link #tile()} a image {@link File} of a MyCoRe derivate.
 * 
 * The resulting file of the tile process is a standard ZIP file with the suffix <code>.iview2</code>. 
 * Use {@link #setTileDir(File)} to specify a common directory where all images of all derivates are tiled. The resulting IView2 file
 * will be stored under this base directory after the following schema (see {@link #getInstance(File, String, String)}):
 * <dl>
 *  <dt>derivateID</dt>
 *  <dd>mycore_derivate_01234567</dd>
 *  <dt>imagePath</dt>
 *  <dd>directory/image.tiff</dd>
 * </dl>
 * results in: <code>tileDir/mycore/derivate/45/67/mycore_derivate_01234567/directory/image.iview2</code><br/>
 * You can use {@link #getTiledFile(File, String, String)} to get access to the IView2 file. 
 * 
 * @author Thomas Scheffler (yagee)
 *
 */
public class MCRImage {

    /**
     * this is the JPEG compression rate.
     * @see JPEGImageWriteParam#setCompressionQuality(float)
     */
    protected static final float JPEG_COMPRESSION_RATE = 0.75f;

    /**
     * width and height of tiles in pixel.
     */
    protected static final int TILE_SIZE = 256;

    /**
     * Pixel size of a color JPEG image.
     */
    protected static final int JPEG_CM_PIXEL_SIZE = 24;

    private static final int DIRECTORY_PART_LEN = 2;

    private static JPEGImageWriteParam imageWriteParam;

    private static final double LOG_2 = Math.log(2);

    private static final Logger LOGGER = Logger.getLogger(MCRImage.class);

    private static final int MIN_FILENAME_SUFFIX_LEN = 3;

    private static final short TILE_SIZE_FACTOR = (short) (Math.log(TILE_SIZE) / LOG_2);

    private static final double ZOOM_FACTOR = 0.5;

    /**
     * derivate ID (for output directory calculation).
     */
    protected String derivate;

    /**
     * the whole image that gets tiled as a <code>File</code>.
     */
    protected File imageFile;

    /**
     * path to image relative to derivate root. (for output directory calculation)
     */
    protected String imagePath;

    /**
     * a counter for generated tiles.
     */
    protected AtomicInteger imageTilesCount = new AtomicInteger();

    /**
     * root dir for generated directories and tiles.
     */
    protected File tileBaseDir;

    /**
     * currently unused: a image used to watermark every tile.
     */
    protected BufferedImage waterMarkImage;

    private int imageHeight;

    private int imageWidth;

    private ImageWriter imageWriter;

    private int imageZoomLevels;

    static {
        imageWriteParam = new JPEGImageWriteParam(Locale.getDefault());
        try {
            imageWriteParam.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        } catch (final UnsupportedOperationException e) {
            LOGGER.warn("Your JPEG encoder does not support progressive JPEGs.");
        }
        imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        imageWriteParam.setCompressionQuality(JPEG_COMPRESSION_RATE);
    }

    /**
     * for internal use only: uses required properties to instantiate.
     * @param file the image file
     * @param derivateID the derivate ID the image belongs to
     * @param relImagePath the relative path from the derivate root to the image
     */
    protected MCRImage(final File file, final String derivateID, final String relImagePath) {
        imageFile = file;
        derivate = derivateID;
        this.imagePath = relImagePath;
        setImageWriter(createJPEGImageWriter());
        LOGGER.info("MCRImage initialized");
    }

    /**
     * returns instance of MCRImage (or subclass).
     * @param file the image file
     * @param derivateID the derivate ID the image belongs to
     * @param imagePath the relative path from the derivate root to the image
     * @return new instance of MCRImage representing <code>file</code>
     * @throws IOException if access to image file is not possible
     */
    public static MCRImage getInstance(final File file, final String derivateID, final String imagePath) throws IOException {
        return new MCRMemSaveImage(file, derivateID, imagePath);
    }

    /**
     * calculates the amount of tiles produces by this image dimensions.
     * @param imageWidth width of the image
     * @param imageHeight height of the image
     * @return amount of tiles produced by {@link #tile()}
     */
    public static int getTileCount(final int imageWidth, final int imageHeight) {
        int tiles = 1;
        int w = imageWidth;
        int h = imageHeight;
        while (w >= MCRImage.TILE_SIZE || h >= MCRImage.TILE_SIZE) {
            tiles += Math.ceil(w / (double) MCRImage.TILE_SIZE) * Math.ceil(h / (double) MCRImage.TILE_SIZE);
            w = (int) Math.ceil(w / 2d);
            h = (int) Math.ceil(h / 2d);
        }
        return tiles;
    }

    /**
     * returns a {@link File} object of the .iview2 file or the derivate folder.
     * @param tileDir the base directory of all image tiles
     * @param derivateID the derivate ID the image belongs to
     * @param imagePath the relative path from the derivate root to the image
     * @return tile directory of derivate if <code>imagePath</code> is null or the tile file (.iview2)
     */
    public static File getTiledFile(final File tileDir, final String derivateID, final String imagePath) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("tileDir: " + tileDir + ", derivate: " + derivateID + ", imagePath: " + imagePath);
        }
        File tileFile = tileDir;
        final String[] idParts = derivateID.split("_");
        for (int i = 0; i < idParts.length - 1; i++) {
            tileFile = new File(tileFile, idParts[i]);
        }
        final String lastPart = idParts[idParts.length - 1];
        if (lastPart.length() > MIN_FILENAME_SUFFIX_LEN) {
            tileFile = new File(tileFile, lastPart.substring(lastPart.length() - DIRECTORY_PART_LEN * 2, lastPart.length() - DIRECTORY_PART_LEN));
            tileFile = new File(tileFile, lastPart.substring(lastPart.length() - DIRECTORY_PART_LEN, lastPart.length()));
        } else {
            tileFile = new File(tileFile, lastPart);
        }
        tileFile = new File(tileFile, derivateID);
        if (imagePath == null) {
            return tileFile;
        }
        final int pos = imagePath.lastIndexOf('.');
        final String relPath = imagePath.substring(0, pos > 0 ? pos : imagePath.length()) + ".iview2";
        return new File(tileFile.getAbsolutePath() + "/" + relPath);
    }

    /**
     * returns the tile size dimensions.
     * @return width and height of a full tile
     */
    public static int getTileSize() {
        return TILE_SIZE;
    }

    /**
     * returns amount of zoom levels generated by an image of this dimensions.
     * @param imageWidth width of image
     * @param imageHeight height of image
     * @return number of generated zoom levels by {@link #tile()}
     */
    public static short getZoomLevels(final int imageWidth, final int imageHeight) {
        int maxDim = Math.max(imageHeight, imageWidth);
        maxDim = Math.max(maxDim, TILE_SIZE);
        final short maxZoom = (short) Math.ceil(Math.log(maxDim) / LOG_2 - TILE_SIZE_FACTOR);
        return maxZoom;
    }

    /**
     * @return the height of the image
     */
    public int getImageHeight() {
        return imageHeight;
    }

    /**
     * @return the width of the image
     */
    public int getImageWidth() {
        return imageWidth;
    }

    /**
     * @return the amount of generated zoom levels
     */
    public int getImageZoomLevels() {
        return imageZoomLevels;
    }

    /**
     * set directory of the generated .iview2 file.
     * @param tileDir a base directory where all tiles of all derivates are stored
     */
    public void setTileDir(final File tileDir) {
        this.tileBaseDir = tileDir;
    }

    /**
     * starts the tile process.
     * 
     * @return properties of image and generated tiles  
     * @throws IOException that occurs during tile process
     */
    public MCRTiledPictureProps tile() throws IOException {
        //the absolute Path is the docportal-directory, therefore the path "../mycore/..."
        //waterMarkFile = ImageIO.read(new File(MCRIview2Props.getProperty("Watermark")));	
        //create JPEG ImageWriter
        final ImageWriter curImgWriter = getImageWriter();
        //ImageWriter created
        BufferedImage image = loadImage();
        final ZipOutputStream zout = getZipOutputStream();
        try {
            final int zoomLevels = getZoomLevels(image);
            LOGGER.info("Will generate " + zoomLevels + " zoom levels.");
            for (int z = zoomLevels; z >= 0; z--) {
                LOGGER.info("Generating zoom level " + z);
                //image = reformatImage(scale(image));
                LOGGER.info("Writing out tiles..");

                final int getMaxTileY = (int) Math.ceil((double) image.getHeight() / TILE_SIZE);
                final int getMaxTileX = (int) Math.ceil((double) image.getWidth() / TILE_SIZE);
                for (int y = 0; y <= getMaxTileY; y++) {
                    for (int x = 0; x <= getMaxTileX; x++) {
                        final BufferedImage tile = getTile(image, x, y);
                        writeTile(zout, tile, x, y, z);
                    }
                }
                if (z > 0) {
                    image = scaleBufferedImage(image);
                }
            }
            curImgWriter.dispose();
            //close imageOutputStream after disposing imageWriter or else application will hang
            writeMetaData(zout);
            return getImageProperties();
        } finally {
            zout.close();
        }
    }

    /**
     * currently unused: adds a watermark image to every generated tile.
     * @param image the image tile
     * @return the image tile with a randomly positioned watermark
     */
    protected BufferedImage addWatermark(final BufferedImage image) {
        if (image.getWidth() >= waterMarkImage.getWidth() && image.getHeight() >= waterMarkImage.getHeight()) {
            final int randx = (int) (Math.random() * (image.getWidth() - waterMarkImage.getWidth()));
            final int randy = (int) (Math.random() * (image.getHeight() - waterMarkImage.getHeight()));
            image.createGraphics().drawImage(waterMarkImage, randx, randy, waterMarkImage.getWidth(), waterMarkImage.getHeight(), null);
        }
        return image;
    }

    /**
     * @return a {@link ImageWriter} for JPEG image files
     */
    protected ImageWriter createJPEGImageWriter() {
        final ImageWriter jpegWriter = ImageIO.getImageWritersBySuffix("jpeg").next();
        return jpegWriter;
    }

    /**
     * @return {@link MCRTiledPictureProps} instance for the current image
     */
    protected MCRTiledPictureProps getImageProperties() {
        final MCRTiledPictureProps picProps = new MCRTiledPictureProps();
        picProps.width = getImageWidth();
        picProps.height = getImageHeight();
        picProps.zoomlevel = getImageZoomLevels();
        picProps.tilesCount = imageTilesCount.get();
        return picProps;
    }

    /**
     * @return the current {@link ImageWriter} used for the tiles
     */
    protected ImageWriter getImageWriter() {
        return imageWriter;
    }

    /**
     * creates a {@link ZipOutputStream} to write image tiles and metadata to.
     * @return write ready ZIP output stream
     * @throws FileNotFoundException if tile directory or image file does not exist and cannot be created
     */
    protected ZipOutputStream getZipOutputStream() throws FileNotFoundException {
        final File iviewFile = getTiledFile(tileBaseDir, derivate, imagePath);
        LOGGER.info("Saving tiles in " + iviewFile.getAbsolutePath());
        if (iviewFile.getParentFile().exists() || iviewFile.getParentFile().mkdirs()) {
            final ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(iviewFile));
            return zout;
        } else {
            throw new FileNotFoundException("Cannot create directory " + iviewFile.getParentFile());
        }
    }

    /**
     * shrinks the image to 50%.
     * @param image source image
     * @return shrinked image
     */
    protected BufferedImage scaleBufferedImage(final BufferedImage image) {
        LOGGER.info("Scaling image...");
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int newWidth = (int) Math.ceil(width / 2d);
        final int newHeight = (int) Math.ceil(height / 2d);
        int imageType = image.getType();
        if (imageType == BufferedImage.TYPE_CUSTOM) {
            imageType = BufferedImage.TYPE_INT_RGB;
        }
        final BufferedImage bicubic = new BufferedImage(newWidth, newHeight, imageType);
        final Graphics2D bg = bicubic.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        bg.scale(ZOOM_FACTOR, ZOOM_FACTOR);
        bg.drawImage(image, 0, 0, null);
        bg.dispose();
        LOGGER.info("Scaling done: " + width + "x" + height);
        return bicubic;
    }

    /**
     * sets the image height.
     * @param imgHeight height of the image
     */
    protected void setImageHeight(final int imgHeight) {
        this.imageHeight = imgHeight;
    }

    /**
     * sets the image width.
     * @param imgWidth width of the image
     */
    protected void setImageWidth(final int imgWidth) {
        this.imageWidth = imgWidth;
    }

    /**
     * sets the image zoom levels.
     * @param imgZoomLevels amount of zoom levels of the image
     */
    protected void setImageZoomLevels(final int imgZoomLevels) {
        this.imageZoomLevels = imgZoomLevels;
    }

    /**
     * writes metadata as <code>imageinfo.xml</code> to <code>.iview2</code> file.
     * Format of metadata file:
     * <pre>
     * &lt;imageinfo
     *   derivate=""
     *   path=""
     *   tiles=""
     *   width=""
     *   height=""
     *   zoomLevel=""
     * /&gt;
     * </pre>  
     * @param zout ZipOutputStream of <code>.iview2</code> file
     * @throws IOException Exception during ZIP output
     */
    protected void writeMetaData(final ZipOutputStream zout) throws IOException {
        final ZipEntry ze = new ZipEntry(MCRTiledPictureProps.IMAGEINFO_XML);
        zout.putNextEntry(ze);
        try {
            final Element rootElement = new Element("imageinfo");
            final Document imageInfo = new Document(rootElement);
            rootElement.setAttribute(MCRTiledPictureProps.PROP_DERIVATE, derivate);
            rootElement.setAttribute(MCRTiledPictureProps.PROP_PATH, imagePath);
            rootElement.setAttribute(MCRTiledPictureProps.PROP_TILES, imageTilesCount.toString());
            rootElement.setAttribute(MCRTiledPictureProps.PROP_WIDTH, Integer.toString(getImageWidth()));
            rootElement.setAttribute(MCRTiledPictureProps.PROP_HEIGHT, Integer.toString(getImageHeight()));
            rootElement.setAttribute(MCRTiledPictureProps.PROP_ZOOM_LEVEL, Integer.toString(getImageZoomLevels()));
            final XMLOutputter xout = new XMLOutputter(Format.getCompactFormat());
            xout.output(imageInfo, zout);
        } finally {
            zout.closeEntry();
        }
    }

    /**
     * writes image tile to <code>.iview2</code> file.
     * @param zout ZipOutputStream of <code>.iview2</code> file
     * @param tile image tile to be written
     * @param x x coordinate of tile in current zoom level (x * tile width = x-pixel)
     * @param y y coordinate of tile in current zoom level (y * tile width = y-pixel)
     * @param z zoom level
     * @throws IOException Exception during ZIP output
     */
    protected void writeTile(final ZipOutputStream zout, final BufferedImage tile, final int x, final int y, final int z) throws IOException {
        if (tile != null) {
            try {
                final StringBuilder tileName = new StringBuilder(Integer.toString(z)).append('/').append(y).append('/').append(x).append(".jpg");
                final ZipEntry ze = new ZipEntry(tileName.toString());
                zout.putNextEntry(ze);
                writeImageIoTile(zout, tile);
                imageTilesCount.incrementAndGet();
            } finally {
                zout.closeEntry();
            }
        }
    }

    private RenderedOp getImage(final File imgFile) {
        LOGGER.info("Reading image: " + imgFile);
        RenderedOp render;
        render = JAI.create("fileload", imgFile.getAbsolutePath());
        return render;
    }

    @SuppressWarnings("unused")
    private BufferedImage getMemImage(final File imgFile) throws FileNotFoundException {
        final MemoryCacheSeekableStream stream = new MemoryCacheSeekableStream(new BufferedInputStream(new FileInputStream(imgFile)));
        final RenderedOp create = JAI.create("stream", stream);
        return create.getAsBufferedImage();
    }

    private BufferedImage getTile(final BufferedImage image, final int x, final int y) {
        int tileWidth = image.getWidth() - TILE_SIZE * x;
        int tileHeight = image.getHeight() - TILE_SIZE * y;
        if (tileWidth > TILE_SIZE) {
            tileWidth = TILE_SIZE;
        }
        if (tileHeight > TILE_SIZE) {
            tileHeight = TILE_SIZE;
        }
        if (tileWidth != 0 && tileHeight != 0) {
            return image.getSubimage(x * TILE_SIZE, y * TILE_SIZE, tileWidth, tileHeight);
        }
        return null;

    }

    private int getZoomLevels(final RenderedImage image) {
        int maxDim = image.getHeight() > image.getWidth() ? image.getHeight() : image.getWidth();
        LOGGER.info("maximum dimension: " + maxDim);
        int zoomLevel = 0;
        while (maxDim > TILE_SIZE) {
            zoomLevel++;
            maxDim = maxDim / DIRECTORY_PART_LEN;
        }
        setImageHeight(image.getHeight());
        setImageWidth(image.getWidth());
        setImageZoomLevels(zoomLevel);
        return zoomLevel;
    }

    private BufferedImage loadImage() throws IOException {
        BufferedImage image;
        final RenderedOp render = getImage(imageFile);
        LOGGER.info("Converting to BufferedImage");
        // handle images with 32 and more bits
        if (render.getColorModel().getPixelSize() > JPEG_CM_PIXEL_SIZE) {
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

    private void setImageWriter(final ImageWriter imgWriter) {
        this.imageWriter = imgWriter;
    }

    private void writeImageIoTile(final ZipOutputStream zout, final BufferedImage tile) throws IOException {
        final ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(zout);
        final ImageWriter imgWriter = getImageWriter();
        try {
            imgWriter.setOutput(imageOutputStream);
            //tile = addWatermark(scaleBufferedImage(tile));        
            final IIOImage iioImage = new IIOImage(tile, null, null);
            imgWriter.write(null, iioImage, imageWriteParam);
        } finally {
            imgWriter.reset();
            imageOutputStream.close();
        }
    }
}
