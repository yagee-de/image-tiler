package org.mycore.services.iview2;

import java.io.File;
import java.util.HashMap;

import org.apache.commons.io.FilenameUtils;

import junit.framework.TestCase;

public class MCRImageTest extends TestCase {
    private HashMap<String, String> pics = new HashMap<String, String>();
    @Override
    protected void setUp() throws Exception {
        pics.put("small", "src/test/resources/Bay_of_Noboto.jpg");
        super.setUp();
    }
    public void testTiling() throws Exception {
        String filePath = pics.get("small");
        File file =new File(filePath);
        MCRImage image = new MCRMemSaveImage(file , "derivateID", "imagePath/" + FilenameUtils.getName(filePath));
        File tiledir = new File("target/tileDir");
        image.setTileDir(tiledir);
        image.tile();
        
        assertTrue(tiledir.exists());
    }
}
