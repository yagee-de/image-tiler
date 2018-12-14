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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        pics.put("wide", "src/test/resources/labirynth_panorama_010.jpg");
        pics.put("1 pixel mega tile rest", "src/test/resources/BE_0681_0397.jpg");
        pics.put("extra small", "src/test/resources/5x5.jpg");

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
            final MCRImage image = new MCRMemSaveImage(file.toPath(), derivateID, imagePath);
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
