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
package org.mycore.imagetiler;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * The <code>MCRImage</code> class describes an image with different zoom levels that can be accessed by its tiles.
 * 
 * The main purpose of this class is to provide a method to {@link #tile()} a image {@link File} of a MyCoRe derivate.
 * 
 * The resulting file of the tile process is a standard ZIP file with the suffix <code>.iview2</code>. 
 * Use {@link #setTileDir(Path)} to specify a common directory where all images of all derivates are tiled. The resulting IView2 file
 * will be stored under this base directory after the following schema (see {@link #getInstance(Path, String, String)}):
 * <dl>
 *  <dt>derivateID</dt>
 *  <dd>mycore_derivate_01234567</dd>
 *  <dt>imagePath</dt>
 *  <dd>directory/image.tiff</dd>
 * </dl>
 * results in: <code>tileDir/mycore/derivate/45/67/mycore_derivate_01234567/directory/image.iview2</code><br>
 * You can use {@link #getTiledFile(Path, String, String)} to get access to the IView2 file.
 * 
 * @author Thomas Scheffler (yagee)
 *
 */
public class MCRImage {

    private static final JAXBContext ctx;

    /**
     * this is the JPEG compression rate.
     * @see JPEGImageWriteParam#setCompressionQuality(float)
     */
    private static final float JPEG_COMPRESSION_RATE = 0.75f;

    /**
     * width and height of tiles in pixel.
     */
    protected static final int TILE_SIZE = 256;

    /**
     * Pixel size of a color JPEG image.
     */
    private static final int JPEG_CM_PIXEL_SIZE = 24;

    private static final int DIRECTORY_PART_LEN = 2;

    private static final JPEGImageWriteParam imageWriteParam;

    private static final double LOG_2 = Math.log(2);

    private static final Logger LOGGER = LogManager.getLogger();

    private static final int MIN_FILENAME_SUFFIX_LEN = 3;

    private static final short TILE_SIZE_FACTOR = (short) (Math.log(TILE_SIZE) / LOG_2);

    private static final double ZOOM_FACTOR = 0.5;

    /**
     * derivate ID (for output directory calculation).
     */
    private String derivate;

    /**
     * the whole image that gets tiled as a <code>Path</code>.
     */
    private Path imageFile;

    /**
     * path to image relative to derivate root. (for output directory calculation)
     */
    private String imagePath;

    /**
     * a counter for generated tiles.
     */
    private final AtomicInteger imageTilesCount = new AtomicInteger();

    /**
     * root dir for generated directories and tiles.
     */
    private Path tileBaseDir;

    /**
     * currently unused: a image used to watermark every tile.
     */
    private BufferedImage waterMarkImage;

    private int imageHeight;

    private int imageWidth;

    private final ImageWriter imageWriter;

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
        try {
            ctx = JAXBContext.newInstance(MCRDerivateTiledPictureProps.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * for internal use only: uses required properties to instantiate.
     * @param file the image file
     * @param derivateID the derivate ID the image belongs to
     * @param relImagePath the relative path from the derivate root to the image
     */
    protected MCRImage(final Path file, final String derivateID, final String relImagePath) {
        imageFile = file;
        derivate = derivateID;
        imagePath = relImagePath;
        imageWriter = ImageIO.getImageWritersBySuffix("jpeg").next();
    }

    /**
     * returns instance of MCRImage (or subclass).
     * @param file the image file
     * @param derivateID the derivate ID the image belongs to
     * @param imagePath the relative path from the derivate root to the image
     * @return new instance of MCRImage representing <code>file</code>
     */
    public static MCRImage getInstance(final Path file, final String derivateID, final String imagePath) {
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
    public static Path getTiledFile(final Path tileDir, final String derivateID, final String imagePath) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("tileDir: " + tileDir + ", derivate: " + derivateID + ", imagePath: " + imagePath);
        }
        Path tileFile = getTiledFileBaseDir(tileDir, derivateID);
        if (imagePath == null) {
            return tileFile;
        }
        final int pos = imagePath.lastIndexOf('.');
        final String relPath = imagePath.substring(imagePath.charAt(0) == '/' ? 1 : 0,
            pos > 0 ? pos : imagePath.length())
            + ".iview2";
        return tileFile.resolve(relPath);
    }

    private static Path getTiledFileBaseDir(Path tileDir, String derivateID) {
        if (derivateID == null) {
            LOGGER.info("No derivate ID given. Using " + tileDir + " as base directory.");
            return tileDir;
        }
        final String[] idParts = derivateID.split("_");
        for (int i = 0; i < idParts.length - 1; i++) {
            tileDir = tileDir.resolve(idParts[i]);
        }
        final String lastPart = idParts[idParts.length - 1];
        if (lastPart.length() > MIN_FILENAME_SUFFIX_LEN) {
            tileDir = tileDir.resolve(
                lastPart.substring(lastPart.length() - DIRECTORY_PART_LEN * 2, lastPart.length() - DIRECTORY_PART_LEN));
            tileDir = tileDir.resolve(lastPart.substring(lastPart.length() - DIRECTORY_PART_LEN, lastPart.length()));
        } else {
            tileDir = tileDir.resolve(lastPart);
        }
        tileDir = tileDir.resolve(derivateID);
        return tileDir;
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
        return (short) Math.ceil(Math.log(maxDim) / LOG_2 - TILE_SIZE_FACTOR);
    }

    private static ImageReader createImageReader(final Path imageFile) throws IOException {
        final ImageInputStream imageInputStream = ImageIO.createImageInputStream(Files.newByteChannel(imageFile,
            StandardOpenOption.READ));
        final Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
        if (!readers.hasNext()) {
            imageInputStream.close();
            return null;
        }
        final ImageReader reader = readers.next();
        reader.setInput(imageInputStream, false);
        return reader;
    }

    /**
     * Reads a rectangular area of the current image.
     * @param reader image reader with current image at pos 0
     * @param x upper left x-coordinate
     * @param y upper left y-coordinate
     * @param width width of the area of interest
     * @param height height of the area of interest
     * @return area of interest
     * @throws IOException if source file could not be read
     */
    protected static BufferedImage getTileOfFile(final ImageReader reader, final int x, final int y, final int width,
        final int height) throws IOException {
        final ImageReadParam param = reader.getDefaultReadParam();
        final Rectangle srcRegion = new Rectangle(x, y, width, height);
        param.setSourceRegion(srcRegion);
        BufferedImage tile = reader.read(0, param);
        return convertIfNeeded(tile);
    }

    private static BufferedImage convertIfNeeded(BufferedImage tile) {
        ColorModel colorModel = tile.getColorModel();
        boolean convertToGray = isFakeGrayScale(colorModel);
        int pixelSize = colorModel.getPixelSize();
        int targetType = tile.getType();
        if (convertToGray) {
            LOGGER.info("Image is gray scale but uses color map. Converting to gray scale");
            targetType = BufferedImage.TYPE_BYTE_GRAY;
        } else if (pixelSize > JPEG_CM_PIXEL_SIZE) {
            LOGGER.info("Converting image to 24 bit color depth: Color depth " + pixelSize);
            targetType = BufferedImage.TYPE_INT_RGB;
        } else if (tile.getType() == BufferedImage.TYPE_CUSTOM) {
            LOGGER.info("Converting image to 24 bit color depth: Image type is 'CUSTOM'");
            targetType = BufferedImage.TYPE_INT_RGB;
        }
        if (targetType == tile.getType()) {
            //no need for conversion
            return tile;
        }
        final BufferedImage newTile = new BufferedImage(tile.getWidth(), tile.getHeight(),
            convertToGray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2d = newTile.createGraphics();
        try {
            graphics2d.drawImage(tile, 0, 0, tile.getWidth(), tile.getHeight(), null);
        } finally {
            graphics2d.dispose();
        }
        return newTile;
    }

    /**
     * @return true, if gray scale image uses color map where every entry uses the same value for each color component
     */
    private static boolean isFakeGrayScale(ColorModel colorModel) {
        if (colorModel instanceof IndexColorModel) {
            IndexColorModel icm = (IndexColorModel) colorModel;
            int mapSize = icm.getMapSize();
            byte[] reds = new byte[mapSize], greens = new byte[mapSize], blues = new byte[mapSize];
            icm.getReds(reds);
            icm.getGreens(greens);
            icm.getBlues(blues);
            boolean isNotGray = IntStream.range(0, mapSize)
                .anyMatch(i -> reds[i] != greens[i] || greens[i] != blues[i]);
            return !isNotGray;
        }
        return false;
    }

    /**
     * shrinks the image to 50%.
     * @param image source image
     * @return shrinked image
     */
    protected static BufferedImage scaleBufferedImage(final BufferedImage image) {
        LOGGER.debug("Scaling image...");
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int newWidth = (int) Math.ceil(width / 2d);
        final int newHeight = (int) Math.ceil(height / 2d);
        final BufferedImage bicubic = new BufferedImage(newWidth, newHeight, image.getType());
        final Graphics2D bg = bicubic.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        bg.scale(ZOOM_FACTOR, ZOOM_FACTOR);
        bg.drawImage(image, 0, 0, null);
        bg.dispose();
        LOGGER.debug("Scaling done: " + width + "x" + height);
        return bicubic;
    }

    /**
     * Returns a {@link BufferedImage#getType()} response, where BufferedImage.TYPE_CUSTOM is translated to compatible image type.
     * @param imageReader with an image on index 0
     * @throws IOException while reading image
     */
    public static int getImageType(final ImageReader imageReader) throws IOException {
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
                ColorModel colorModel = imageTypeSpec.getColorModel();
                imageType = getImageType(colorModel);
            }
        }
        return imageType;
    }

    private static int getImageType(ColorModel colorModel) {
        int pixelSize = colorModel.getPixelSize();
        if (pixelSize > 8) {
            LOGGER.debug("Quite sure we should use TYPE_INT_RGB for a pixel size of " + pixelSize);
            return BufferedImage.TYPE_INT_RGB;
        } else if (pixelSize == 8) {
            if (isFakeGrayScale(colorModel)) {
                LOGGER.debug("Quite sure we should use TYPE_BYTE_GRAY as the color palette has only gray values");
                return BufferedImage.TYPE_BYTE_GRAY;
            }
            if (colorModel.getNumColorComponents() > 1) {
                LOGGER.debug("Quite sure we should use TYPE_INT_RGB for a pixel size of " + pixelSize);
                return BufferedImage.TYPE_INT_RGB;
            }
            LOGGER.debug("Quite sure we should use TYPE_BYTE_GRAY as there is only one color component present");
            return BufferedImage.TYPE_BYTE_GRAY;
        } else if (pixelSize == 1) {
            LOGGER.debug("Quite sure we should use TYPE_BYTE_GRAY as this image is binary.");
            return BufferedImage.TYPE_BYTE_GRAY;
        } else {
            LOGGER.warn("Do not know how to handle a pixel size of " + pixelSize);
        }
        //default value
        return BufferedImage.TYPE_INT_RGB;
    }

    /**
     * Returns a {@link BufferedImage#getType()} response, where BufferedImage.TYPE_CUSTOM is translated to compatible image type.
     * @param image image to get the image type from
     */
    public static int getImageType(BufferedImage image) {
        int imageType;
        if (image.getType() != BufferedImage.TYPE_CUSTOM) {
            //best fit
            LOGGER.debug("Pretty sure we should use " + image.getType());
            imageType = image.getType();
        } else {
            imageType = getImageType(image.getColorModel());
        }
        return imageType;
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
    public void setTileDir(final Path tileDir) {
        tileBaseDir = tileDir;
    }

    /**
     * starts the tile process.
     * 
     * Same as calling {@link #tile(MCRTileEventHandler)} with <code>null</code> as argument.
     * 
     * @return properties of image and generated tiles  
     * @throws IOException that occurs during tile process
     */
    public MCRTiledPictureProps tile() throws IOException {
        return tile(null);
    }

    /**
     * starts the tile process.
     * 
     * @param eventHandler
     *          eventHandler to control resources, may be null
     * @return properties of image and generated tiles  
     * @throws IOException that occurs during tile process
     */
    public MCRTiledPictureProps tile(MCRTileEventHandler eventHandler) throws IOException {
        long start = System.nanoTime();
        LOGGER.info(MessageFormat.format("Start tiling of {0}:{1}", derivate, imagePath));
        //waterMarkFile = ImageIO.read(new File(MCRIview2Props.getProperty("Watermark")));	
        //initialize some basic variables
        if (eventHandler != null) {
            eventHandler.preImageReaderCreated();
        }
        final ImageReader imageReader;
        try {
            imageReader = MCRImage.createImageReader(imageFile);
        } finally {
            if (eventHandler != null) {
                eventHandler.postImageReaderCreated();
            }
        }
        if (imageReader == null) {
            throw new IOException("No ImageReader available for file: " + imageFile);
        }
        LOGGER.debug("ImageReader: " + imageReader.getClass());
        try (final ZipOutputStream zout = getZipOutputStream()) {
            setImageSize(imageReader);
            doTile(imageReader, zout);
            writeMetaData(zout);
        } finally {
            imageReader.dispose();
        }
        long end = System.nanoTime();
        final MCRTiledPictureProps imageProperties = getImageProperties();
        long pixel = imageProperties.getWidth() * imageProperties.getHeight();
        LOGGER.info(MessageFormat.format("Finished tiling of {0}:{1} in {2} ms ({3} MPixel/s). ", derivate, imagePath,
            (end - start) / 1e6, 1000 * pixel / (end - start)));
        return imageProperties;
    }

    protected void doTile(final ImageReader imageReader, final ZipOutputStream zout) throws IOException {
        BufferedImage image = getTileOfFile(imageReader, 0, 0, getImageWidth(), getImageHeight());
        final int zoomLevels = getZoomLevels(getImageWidth(), getImageHeight());
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
    }

    /**
     * @return {@link MCRTiledPictureProps} instance for the current image
     */
    private MCRTiledPictureProps getImageProperties() {
        final MCRTiledPictureProps picProps = new MCRTiledPictureProps();
        picProps.width = getImageWidth();
        picProps.height = getImageHeight();
        picProps.zoomlevel = getImageZoomLevels();
        picProps.tilesCount = imageTilesCount.get();
        return picProps;
    }

    protected void handleSizeChanged() {
        setImageZoomLevels(getZoomLevels(getImageWidth(), getImageHeight()));
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
    private void writeMetaData(final ZipOutputStream zout) throws IOException {
        final ZipEntry ze = new ZipEntry(MCRTiledPictureProps.IMAGEINFO_XML);
        zout.putNextEntry(ze);
        try {
            MCRDerivateTiledPictureProps imageProps = new MCRDerivateTiledPictureProps(derivate, imagePath,
                imageTilesCount.get(), getImageZoomLevels(), getImageHeight(), getImageWidth());
            try {
                ctx.createMarshaller().marshal(imageProps, zout);
            } catch (JAXBException e) {
                throw new IOException(e);
            }
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
    protected void writeTile(final ZipOutputStream zout, final BufferedImage tile, final int x, final int y,
        final int z)
        throws IOException {
        if (tile != null) {
            try {
                String tileName = Integer.toString(z) + '/' + y + '/' + x + ".jpg";
                final ZipEntry ze = new ZipEntry(tileName);
                zout.putNextEntry(ze);
                writeImageIoTile(zout, tile);
                imageTilesCount.incrementAndGet();
            } finally {
                zout.closeEntry();
            }
        }
    }

    /**
     * currently unused: adds a watermark image to every generated tile.
     * @param image the image tile
     * @return the image tile with a randomly positioned watermark
     */
    @SuppressWarnings("unused")
    private BufferedImage addWatermark(final BufferedImage image) {
        if (image.getWidth() >= waterMarkImage.getWidth() && image.getHeight() >= waterMarkImage.getHeight()) {
            final int randx = (int) (Math.random() * (image.getWidth() - waterMarkImage.getWidth()));
            final int randy = (int) (Math.random() * (image.getHeight() - waterMarkImage.getHeight()));
            image.createGraphics().drawImage(waterMarkImage, randx, randy, waterMarkImage.getWidth(),
                waterMarkImage.getHeight(), null);
        }
        return image;
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

    /**
     * creates a {@link ZipOutputStream} to write image tiles and metadata to.
     * @return write ready ZIP output stream
     * @throws IOException while creating parent directories of tile file
     * @throws FileNotFoundException if tile directory or image file does not exist and cannot be created
     */
    private ZipOutputStream getZipOutputStream() throws IOException {
        final Path iviewFile = getTiledFile(tileBaseDir, derivate, imagePath);
        LOGGER.info("Saving tiles in " + iviewFile);
        Path parentDir = iviewFile.getParent();
        assert parentDir != null;
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        return new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(iviewFile)));
    }

    /**
     * sets the image height.
     * @param imgHeight height of the image
     */
    private void setImageHeight(final int imgHeight) {
        imageHeight = imgHeight;
    }

    /**
     * Set the image size.
     * Maybe the hole image will be read into RAM for getWidth() and getHeight().
     * 
     * @param imgReader image reader with image position '0' to get the image dimensions
     * @throws IOException while reading the source image
     */
    private void setImageSize(final ImageReader imgReader) throws IOException {
        setImageHeight(imgReader.getHeight(0));
        setImageWidth(imgReader.getWidth(0));
        handleSizeChanged();
    }

    /**
     * sets the image width.
     * @param imgWidth width of the image
     */
    private void setImageWidth(final int imgWidth) {
        imageWidth = imgWidth;
    }

    /**
     * sets the image zoom levels.
     * @param imgZoomLevels amount of zoom levels of the image
     */
    private void setImageZoomLevels(final int imgZoomLevels) {
        imageZoomLevels = imgZoomLevels;
    }

    private void writeImageIoTile(final ZipOutputStream zout, final BufferedImage tile) throws IOException {
        final ImageWriter imgWriter = imageWriter;
        if (tile.getType() == BufferedImage.TYPE_CUSTOM) {
            throw new IOException("Do not know how to handle image type 'CUSTOM'");
        }
        try (final ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(zout)) {
            imgWriter.setOutput(imageOutputStream);
            //tile = addWatermark(scaleBufferedImage(tile));        
            final IIOImage iioImage = new IIOImage(tile, null, null);
            imgWriter.write(null, iioImage, imageWriteParam);
        } finally {
            imgWriter.reset();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Please specify image to tile.");
            System.exit(1);
        }
        Path imageFile = Paths.get(args[0]);
        //imagePath 
        String imagePath;
        if (imageFile.isAbsolute()) {
            Path fileName = imageFile.getFileName();
            assert fileName != null;
            imagePath = fileName.toString();
        } else {
            imagePath = imageFile.toString();
        }
        Path tileDir = imageFile.isAbsolute() ? imageFile.getParent() : Paths.get(".");
        assert tileDir != null;
        String derivateId = args.length == 2 ? args[1] : null;
        MCRImage image = getInstance(imageFile, derivateId, imagePath);
        Path absolutePath = tileDir.toAbsolutePath();
        System.out.println("Tile to directory: " + absolutePath);
        image.setTileDir(tileDir);
        MCRTiledPictureProps props = image.tile(null);
        System.out.println("Tiling complete: " + props);
    }

    @SuppressWarnings("unused")
    @XmlRootElement(name = "imageinfo")
    @XmlAccessorType(XmlAccessType.FIELD)
    private static class MCRDerivateTiledPictureProps extends MCRTiledPictureProps {
        public MCRDerivateTiledPictureProps() {
            super();
        }

        public MCRDerivateTiledPictureProps(String derivate, String path, int tilesCount, int zoomLevel, int height,
            int width) {
            super();
            this.derivate = derivate;
            this.path = path;
            super.tilesCount = tilesCount;
            super.height = height;
            super.width = width;
            super.zoomlevel = zoomLevel;
        }

        @XmlAttribute
        private String derivate;

        @XmlAttribute
        private String path;

    }
}
