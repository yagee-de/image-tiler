package org.mycore.services.iview2;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;

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
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        MCRMemSaveImage saveImage = new MCRMemSaveImage(file, "", "");
        BufferedImage tileOfFile = saveImage.getTileOfFile(3000, 500, MCRMemSaveImage.MEGA_TILE_SIZE, MCRMemSaveImage.MEGA_TILE_SIZE);
        stopWatch.stop();
        
        assertNotNull(tileOfFile);
        long time = stopWatch.getTime();
        System.out.println("(" + saveImage.getImageWidth() +"x"+saveImage.getImageHeight()+") read region time: " + time);
        assertTrue(time < 5000);
    }
}
