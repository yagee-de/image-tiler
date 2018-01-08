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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mycore.imagetiler.MCRImage;
import org.mycore.imagetiler.MCRMemSaveImage;
import org.mycore.imagetiler.MCRTiledPictureProps;

/**
 * Provides a good test case for {@link MCRImage}.
 * @author Thomas Scheffler (yagee)
 *
 */
public class MCRImageTest {
    private final HashMap<String, String> pics = new HashMap<>();

    private File tileDir;

    private static boolean deleteDirectory(final File path) {
        if (path.exists()) {
            final File[] files = path.listFiles();
            assert files != null;
            for (final File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        return path.delete();
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

        tileDir = new File("target/tileDir").getAbsoluteFile();
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
            image.setTileDir(tileDir.toPath());
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
            assertTrue("Tile directory is not created.", tileDir.exists());
            final Path iviewFile = MCRImage.getTiledFile(tileDir.toPath(), derivateID, imagePath);
            assertTrue("IView File is not created:" + iviewFile, Files.exists(iviewFile));
            final MCRTiledPictureProps props = MCRTiledPictureProps.getInstanceFromFile(iviewFile);
            final int tilesCount;
            try (final ZipFile iviewImage = new ZipFile(iviewFile.toFile())) {
                tilesCount = iviewImage.size() - 1;
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
        Path tiledFile = MCRImage.getTiledFile(tileDir.toPath(), derivateID, "foo/bar.tif");
        assertEquals("Path to file is not es axpected.", tileDir + "/junit/derivate/" + final1 + "/"
            + final2 + '/' + derivateID + "/foo/bar.iview2", tiledFile.toString());
        tiledFile = MCRImage.getTiledFile(tileDir.toPath(), derivateID, "/foo/bar.tif");
        assertEquals("Path to file is not es axpected.", tileDir + "/junit/derivate/" + final1 + "/"
            + final2 + '/' + derivateID + "/foo/bar.iview2", tiledFile.toString());
    }

}
