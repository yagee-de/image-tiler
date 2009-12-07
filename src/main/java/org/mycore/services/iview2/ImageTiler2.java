package org.mycore.services.iview2;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;

import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageEncoder;
import com.sun.media.jai.codec.JPEGEncodeParam;

public class ImageTiler2 extends MCRImage{
    public ImageTiler2(File file, String derivateID, String imagePath) {
        super(file, derivateID, imagePath);
    }

    public ImageTiler2() {
        super(null, null, null);
    }

    private int imageWidth;
    private int imageHeight;
    private ImageReader imageReader;
    private int ZOOM_LEVEL_AT_A_TIME = 4;

    private int MEGA_TILE_SIZE = 256 * (int) Math.pow(2, ZOOM_LEVEL_AT_A_TIME); //4096x4096

    public BufferedImage loadImgAsBufferedImage(File imageFile) {
        RenderedOp renderedOp = JAI.create("fileload", imageFile.getAbsolutePath());
        BufferedImage bufferedImage = renderedOp.getAsBufferedImage();
        setImageWidth(bufferedImage.getWidth());
        setImageHeight(bufferedImage.getHeight());
        return bufferedImage;
    }

    public HashMap<String, BufferedImage> makeTiles(String imageFile) {
        HashMap<String, BufferedImage> tiles = new HashMap<String, BufferedImage>();
        setImageReader(createImageReader(imageFile));
        try {
            setImageWidth(getImageReader().getWidth(0));
            setImageHeight(getImageReader().getHeight(0));
            for (int posX = 0; posX < getImageWidth(); posX += getMEGA_TILE_SIZE()) {
                for (int posY = 0; posY < getImageHeight(); posY += getMEGA_TILE_SIZE()) {
                    int width = Math.min(getMEGA_TILE_SIZE(), getImageWidth()-posX);
                    int height = Math.min(getMEGA_TILE_SIZE(), getImageHeight()-posY);
                    BufferedImage tileOfFile = getTileOfFile(posX, posY, width, height);
                    tiles.putAll(makeTiles(tileOfFile, posX, posY));
                }
            }
            return tiles;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            getImageReader().dispose();
        }
        
        return null;
        
    }
    
    public HashMap<String, BufferedImage> makeTiles(BufferedImage bufferedImage) {
        int indexX = 0;
        int indexY = 0;
        HashMap<String, BufferedImage> tiles = makeTiles(bufferedImage, indexX, indexY);
        return tiles;
    }

    private HashMap<String, BufferedImage> makeTiles(BufferedImage bufferedImage, int indexX, int indexY) {
        HashMap<String, BufferedImage> tiles = new HashMap<String, BufferedImage>();
        
        for (int posX = 0; posX < bufferedImage.getWidth(); posX += getTileSize()) {
            for (int posY = 0 ; posY < bufferedImage.getHeight(); posY += getTileSize()) {
                int tileWidth = Math.min(getTileSize(), bufferedImage.getWidth() - posX);
                int tileHeight = Math.min(getTileSize(), bufferedImage.getHeight() - posY);
                BufferedImage tile = bufferedImage.getSubimage(posX, posY, tileWidth, tileHeight);
                tiles.put((posX + indexX)+ "_" + (posY + indexY), tile);
            }
        }
        return tiles;
    }

    private int getTileSize() {
        return 256;
    }

    public BufferedImage stichTiles(BufferedImage buffImg_0_0, BufferedImage buffImg_1_0, BufferedImage buffImg_0_1,
            BufferedImage buffImg_1_1) {
        if (buffImg_0_1 == null && buffImg_1_0 == null && buffImg_1_1 == null) {
            return buffImg_0_0;
        }
        
        int width = (buffImg_1_0 == null)? buffImg_0_0.getWidth() : buffImg_0_0.getWidth() + buffImg_1_0.getWidth();
        int height = (buffImg_0_1 == null)? buffImg_0_0.getHeight() : buffImg_0_0.getHeight() + buffImg_0_1.getHeight();
        BufferedImage stich = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = stich.getGraphics();
        graphics.drawImage(buffImg_0_0, 0, 0, null);
        
        if(buffImg_1_0 != null) {
            graphics.drawImage(buffImg_1_0, buffImg_0_0.getWidth(), 0, null);
        }
        
        if(buffImg_0_1 != null) {
            graphics.drawImage(buffImg_0_1, 0, buffImg_0_0.getHeight(), null);
        }
        
        if(buffImg_1_0 != null && buffImg_0_1 != null) {
            graphics.drawImage(buffImg_1_1, buffImg_0_0.getWidth(), buffImg_0_0.getHeight(), null);
        }
        return stich;
    }

    public void writeImageToFile(RenderedImage tile, OutputStream outputStream) {
        ImageEncoder jpegEncoder = getJpegEncoder(outputStream);

        try {
            jpegEncoder.encode(tile);
            outputStream.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private OutputStream getOutputStream(String tileFileName) {
        try {
            OutputStream output = new FileOutputStream(tileFileName);
            return output;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    private ImageEncoder getJpegEncoder(OutputStream output) {
        JPEGEncodeParam jpegParam = new JPEGEncodeParam();
        jpegParam.setQuality(0.75f);
        ImageEncoder jpegEncoder = ImageCodec.createImageEncoder("JPEG", output, jpegParam);
        return jpegEncoder;
    }

    public BufferedImage scale(BufferedImage image, double scale) {
        int width = (int) (image.getWidth() * scale);
        int height = (int) (image.getHeight() * scale);
        BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = scaledImage.createGraphics();
        AffineTransform xform = AffineTransform.getScaleInstance(scale, scale);
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics2D.drawImage(image, xform, null);
        graphics2D.dispose();
        return scaledImage;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    /* # # #
     * # # #
     * # # #
     * 
     */
    public HashMap<String, BufferedImage> scaleTiles(HashMap<String, BufferedImage> tiles) {
        int tileSize = getTileSize();
        int tileSizeLarge = tileSize * 2;
        float scaleFactor = 0.5f;
        for (int posX = 0; posX < getImageWidth(); posX += tileSizeLarge) {
            for (int posY = 0; posY < getImageHeight(); posY += tileSizeLarge) {
                BufferedImage tile_0_0 = tiles.remove(posX + "_" + posY);
                BufferedImage tile_0_1 = tiles.remove(posX + "_" + (posY + tileSize));
                BufferedImage tile_1_0 = tiles.remove((posX + tileSize) + "_" + posY);
                BufferedImage tile_1_1 = tiles.remove((posX + tileSize) + "_" + (posY + tileSize));
                BufferedImage stichTiles = stichTiles(tile_0_0, tile_1_0, tile_0_1, tile_1_1);
                BufferedImage scaledStich = scale(stichTiles, scaleFactor);
                tiles.put((int)(posX * 0.5f)+ "_" + (int)(posY * 0.5f), scaledStich);
            }
        }
        setImageWidth((int) (getImageWidth() * scaleFactor));
        setImageHeight((int) (getImageHeight() * scaleFactor));
        return tiles;
    }

    public void createTilesForAllZoomLevel(String imageFile, File ouputFile) {
        if(!ouputFile.exists()) {
            ouputFile.mkdirs();
        }
        
        HashMap<String,BufferedImage> tiles = makeTiles(imageFile);
        scale(ouputFile, tiles);
    }
    
    public void createTilesForAllZoomLevel(File imageFile, File ouputFile) {
        if(!ouputFile.exists()) {
            ouputFile.mkdirs();
        }
        
        
        BufferedImage bufferedImage = loadImgAsBufferedImage(imageFile);
        HashMap<String,BufferedImage> tiles = makeTiles(bufferedImage);
        bufferedImage = null;
        scale(ouputFile, tiles);
        
    }

    private void scale(File ouputFile, HashMap<String, BufferedImage> tiles) {
        int zoomlevel = createZoomlevel();
        String newFolderName = String.valueOf(zoomlevel);
        File subFolder = makeSubFolder(ouputFile, newFolderName);
        saveTiles(tiles, subFolder);
        
        
        for (int level = zoomlevel-1; level >= 0; level--) {
            HashMap<String,BufferedImage> scaleTiles = scaleTiles(tiles);
            newFolderName = String.valueOf(level);
            subFolder = makeSubFolder(ouputFile, newFolderName);
            saveTiles(tiles, subFolder);
        }
    }

    private int createZoomlevel() {
        double logTileSize = Math.log(getTileSize());
        double log_2 = Math.log(2);
        int zoomLevelWidth = (int) Math.ceil(((Math.log(getImageWidth()) - logTileSize)/log_2));
        int zoomLevelHeight = (int) Math.ceil(((Math.log(getImageHeight()) - logTileSize)/log_2));
        int zoomlevel = Math.max(zoomLevelWidth, zoomLevelHeight);
        return zoomlevel;
    }

    private void saveTiles(HashMap<String, BufferedImage> tiles, File subFolder) {
        if(!subFolder.exists()) {
            subFolder.mkdirs();
        }
        
        for (String tileName : tiles.keySet()) {
            String fileName = subFolder.getAbsolutePath() + File.separator + tileName;
            OutputStream outputStream = getOutputStream(fileName + ".jpeg");
            writeImageToFile(tiles.get(tileName), outputStream);
        }
    }

    private File makeSubFolder(File parentFile, String newFolderName) {
        if(parentFile.isDirectory()){
            File newFolder = new File(parentFile.getAbsolutePath() + File.separator + newFolderName);
            newFolder.mkdirs();
            return newFolder;
        }
        return null;
    }
    
    private ImageReader createImageReader(String imageFile) {
        try {
            FileInputStream f = new FileInputStream(imageFile);
            ImageInputStream iis = ImageIO.createImageInputStream(f);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            ImageReader reader = readers.next();
            reader.setInput(iis, false);
            return reader;
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    protected BufferedImage getTileOfFile(int x, int y, int width, int height) throws IOException {
        ImageReadParam param = getImageReader().getDefaultReadParam();
        Rectangle srcRegion = new Rectangle(x, y, width, height);
        param.setSourceRegion(srcRegion);
        
        return getImageReader().read(0, param);
    }

    public void setImageReader(ImageReader imageReader) {
        this.imageReader = imageReader;
    }

    public ImageReader getImageReader() {
        return imageReader;
    }

    public void setMEGA_TILE_SIZE(int mEGA_TILE_SIZE) {
        MEGA_TILE_SIZE = mEGA_TILE_SIZE;
    }

    public int getMEGA_TILE_SIZE() {
        return MEGA_TILE_SIZE;
    }

    public void writeImageToFile(BufferedImage image, String fileName) {
        OutputStream outputStream = getOutputStream(fileName + ".jpeg");
        writeImageToFile(image, outputStream);
    }
}
