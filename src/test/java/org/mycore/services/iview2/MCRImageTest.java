package org.mycore.services.iview2;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;

import javax.imageio.ImageReader;

import junit.framework.TestCase;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.time.StopWatch;

public class MCRImageTest extends TestCase {
    private HashMap<String, String> pics = new HashMap<String, String>();

    @Override
    protected void setUp() throws Exception {
        pics.put("small", "src/test/resources/Bay_of_Noboto.jpg");
        super.setUp();
    }

    public void testTiling() throws Exception {
        String filePath = pics.get("small");
        File file = new File(filePath);
        MCRImage image = new MCRMemSaveImage(file, "derivateID", "imagePath/" + FilenameUtils.getName(filePath));
        File tiledir = new File("target/tileDir");
        image.setTileDir(tiledir);
        image.tile();
        assertTrue(tiledir.exists());
    }

    public void testReadRegion() throws Exception {
        // Test this with the GB image
        File file = new File(pics.get("small"));
        MCRMemSaveImage saveImage = new MCRMemSaveImage(file, "", "");
        ImageReader reader = saveImage.getReader();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        BufferedImage tileOfFile = saveImage.getTileOfFile(reader, 3000, 500, MCRMemSaveImage.MEGA_TILE_SIZE, MCRMemSaveImage.MEGA_TILE_SIZE);
        stopWatch.stop();
        
        assertNotNull(tileOfFile);
        assertTrue(stopWatch.getTime() < 5000);
    }
}
