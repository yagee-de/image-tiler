package org.mycore.services.iview2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
public class MCRImageTest {
    private HashMap<String, String> pics = new HashMap<String, String>();

    File tileDir;

    @Before
    public void setUp() throws Exception {
        pics.put("small", "src/test/resources/Bay_of_Noboto.jpg");
        pics.put("wide", "src/test/resources/labirynth_panorama_010.jpg");
        pics.put("1 pixel mega tile rest", "src/test/resources/BE_0681_0397.jpg");
        pics.put("extra small", "src/test/resources/5x5.jpg");

        tileDir = new File("target/tileDir");
        System.setProperty("java.awt.headless", "true"); 
    }

    @After
    public void tearDown() throws Exception {
        deleteDirectory(tileDir);
    }

    private static boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }

    @Test
    public void testTiling() throws Exception {
        for (Map.Entry<String, String> entry : pics.entrySet()) {
            File file = new File(entry.getValue());
            String derivateID = "derivateID";
            String imagePath = "imagePath/" + FilenameUtils.getName(entry.getValue());
            MCRImage image = new MCRMemSaveImage(file, derivateID, imagePath);
            image.setTileDir(tileDir);
            image.tile();
            assertTrue("Tile directory is not created.", tileDir.exists());
            File iviewFile = MCRImage.getTiledFile(tileDir, derivateID, imagePath);
            assertTrue("IView File is not created:" + iviewFile.getAbsolutePath(), iviewFile.exists());
            MCRTiledPictureProps props = MCRTiledPictureProps.getInstance(iviewFile);
            ZipFile iviewImage = new ZipFile(iviewFile);
            int tilesCount = iviewImage.size() - 1;
            assertEquals(entry.getKey() + ": Metadata tile count does not match stored tile count.", props.countTiles, tilesCount);
            int x = props.width;
            int y = props.height;
            assertEquals(entry.getKey() + ": Calculated tile count does not match stored tile count.", MCRImage.getTileCount(x, y), tilesCount);
        }
    }
}
