/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.mycore.imagetiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

/**
 * Provides a good test case for {@link MCRImage}.
 * @author Thomas Scheffler (yagee)
 *
 */
public class MCRImageTest {
    private final HashMap<String, String> pics = new HashMap<>();

    private Path tileDir;

    private static boolean deleteDirectory(final Path path) {
        if(Files.exists(path)) {
            try {
                Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            } catch (IOException e) {
                //ignore
            }
        }
        return !Files.exists(path);
    }

    /**
     * Sets up test.
     * 
     * A list of images is initialized which provides various testcases for the tiler.
     */
    @Before
    public void setUp() {
        pics.put("small", "src/test/resources/Bay_of_Noboto.jpg");
        pics.put("stripes", "src/test/resources/stripes.png");
        pics.put("wide", "src/test/resources/labirynth_panorama_010.jpg");
        pics.put("1 pixel mega tile rest", "src/test/resources/BE_0681_0397.jpg");
        pics.put("extra small", "src/test/resources/5x5.jpg");
        pics.put("tiff 48 bit", "src/test/resources/tiff48.tif");

        tileDir = Paths.get("target/tileDir");
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * Tears down the testcase and removes temporary directories.
     */
    @After
    public void tearDown() {
        deleteDirectory(tileDir);
    }

    /**
     * Tests {@link MCRImage#tile()} with various images provided by {@link #setUp()}.
     * @throws Exception if tiling process fails
     */
    @Test
    public void testTiling() throws Exception {
        for (final Map.Entry<String, String> entry : pics.entrySet()) {
            final File file = new File(entry.getValue());
            final String derivateID = "derivateID";
            final String imagePath = "imagePath/" + FilenameUtils.getName(entry.getValue());
            final MCRImage image = MCRImage.getInstance(file.toPath(), derivateID, imagePath);
            image.setTileDir(tileDir);
            final BitSet events = new BitSet(2);//pre and post event
            image.tile(new MCRTileEventHandler() {

                @Override
                public void preImageReaderCreated() {
                    events.flip(0);
                }

                @Override
                public void postImageReaderCreated() {
                    events.flip(1);
                }
            });
            assertTrue("preImageReaderCreated() was not called", events.get(0));
            assertTrue("postImageReaderCreated() was not called", events.get(1));
            assertTrue("Tile directory is not created.", Files.exists(tileDir));
            final Path iviewFile = MCRImage.getTiledFile(tileDir, derivateID, imagePath);
            assertTrue("IView File is not created:" + iviewFile, Files.exists(iviewFile));
            final MCRTiledPictureProps props = MCRTiledPictureProps.getInstanceFromFile(iviewFile);
            final int tilesCount;
            try (final ZipFile iviewImage = new ZipFile(iviewFile.toFile())) {
                tilesCount = iviewImage.size() - 1;
                ZipEntry imageInfoXML = iviewImage.getEntry(MCRTiledPictureProps.IMAGEINFO_XML);
                DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document imageInfo = documentBuilder.parse(iviewImage.getInputStream(imageInfoXML));
                String hAttr = Objects.requireNonNull(imageInfo.getDocumentElement().getAttribute("height"));
                String wAttr = Objects.requireNonNull(imageInfo.getDocumentElement().getAttribute("width"));
                String zAttr = Objects.requireNonNull(imageInfo.getDocumentElement().getAttribute("zoomLevel"));
                String tAttr = Objects.requireNonNull(imageInfo.getDocumentElement().getAttribute("tiles"));
                assertTrue("height must be positive: " + hAttr, Integer.parseInt(hAttr) > 0);
                assertTrue("width must be positive: " + wAttr, Integer.parseInt(wAttr) > 0);
                assertTrue("zoomLevel must be zero or positive: " + zAttr, Integer.parseInt(zAttr) >= 0);
                int iTiles = Integer.parseInt(tAttr);
                assertEquals(tilesCount, iTiles);
                if ("stripes".equals(entry.getKey())) {
                    ZipEntry tileEntry = new ZipEntry("0/0/0.jpg");
                    try (InputStream is = iviewImage.getInputStream(tileEntry)) {
                        System.out.println("Reading tile " + tileEntry.getName());
                        final Path targetDir = tileDir.getParent();
                        Files.copy(iviewImage.getInputStream(tileEntry), targetDir.resolve("stripes-thumb.jpg"),
                            StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            assertEquals(entry.getKey() + ": Metadata tile count does not match stored tile count.",
                props.getTilesCount(), tilesCount);
            final int x = props.width;
            final int y = props.height;
            assertEquals(entry.getKey() + ": Calculated tile count does not match stored tile count.",
                MCRImage.getTileCount(x, y), tilesCount);
        }
    }

    @Test
    public void testStripes() throws IOException {
        BufferedImage stripes = new BufferedImage(3000, 3000, BufferedImage.TYPE_INT_RGB);
        Color top = new Color(134, 49, 68);
        Color middle = new Color(255, 255, 255);
        Color bottom = new Color(36, 52, 83);
        for (int y = 0; y < 2366; y++) {
            for (int x = 0; x < stripes.getWidth(); x++) {
                stripes.setRGB(x, y, top.getRGB());
            }
        }
        for (int y = 2366; y < 2376; y++) {
            for (int x = 0; x < stripes.getWidth(); x++) {
                stripes.setRGB(x, y, middle.getRGB());
            }
        }
        for (int y = 2376; y < stripes.getHeight(); y++) {
            for (int x = 0; x < stripes.getWidth(); x++) {
                stripes.setRGB(x, y, bottom.getRGB());
            }
        }
        final ImageWriter pngWriter = ImageIO.getImageWritersByMIMEType("image/png").next();
        final String stripesImagePath = "target/simple-stripes.png";
        try (FileOutputStream fout = new FileOutputStream(stripesImagePath);
            ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(fout)) {
            pngWriter.setOutput(imageOutputStream);
            final IIOImage iioImage = new IIOImage(stripes, null, null);
            ImageWriteParam imageWriteParam = pngWriter.getDefaultWriteParam();
            imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            imageWriteParam.setCompressionQuality(0f);
            pngWriter.write(null, iioImage, imageWriteParam);
        }
        final File file = new File(stripesImagePath);
        final String derivateID = "derivateID";
        final String imagePath = "imagePath/" + FilenameUtils.getName(stripesImagePath);
        final MCRImage image = MCRImage.getInstance(file.toPath(), derivateID, imagePath);
        image.setTileDir(tileDir);
        image.tile();
        assertTrue("Tile directory is not created.", Files.exists(tileDir));
        final Path iviewFile = MCRImage.getTiledFile(tileDir, derivateID, imagePath);
        try (final ZipFile iviewImage = new ZipFile(iviewFile.toFile())) {
            ZipEntry tileEntry = new ZipEntry("0/0/0.jpg");
            try (InputStream is = iviewImage.getInputStream(tileEntry)) {
                final Path targetDir = tileDir.getParent();
                final Path target = targetDir.resolve("simple-stripes-thumb.jpg");
                System.out.println("Copy tile " + tileEntry.getName() + " to " + target);
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Test
    public void testgetTiledFile() {
        String final1 = "00";
        String final2 = "01";
        String derivateID = "junit_derivate_0000" + final1 + final2;
        Path pExpected = tileDir.resolve("junit/derivate/" + final1 + "/"
            + final2 + '/' + derivateID + "/foo/bar.iview2");
        Path tiledFile = MCRImage.getTiledFile(tileDir, derivateID, "foo/bar.tif");
        assertEquals("Path to file is not es axpected.", pExpected, tiledFile);
        tiledFile = MCRImage.getTiledFile(tileDir, derivateID, "/foo/bar.tif");
        assertEquals("Path to file is not es axpected.", pExpected, tiledFile);
    }

}
