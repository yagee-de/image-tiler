/*
 * $Revision: 15646 $ $Date: 2009-07-28 11:32:04 +0200 (Di, 28 Jul 2009) $
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
    private final HashMap<String, String> pics = new HashMap<String, String>();

    private File tileDir;

    private static boolean deleteDirectory(final File path) {
        if (path.exists()) {
            final File[] files = path.listFiles();
            for (final File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return path.delete();
    }

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

    @Test
    public void testTiling() throws Exception {
        for (final Map.Entry<String, String> entry : pics.entrySet()) {
            final File file = new File(entry.getValue());
            final String derivateID = "derivateID";
            final String imagePath = "imagePath/" + FilenameUtils.getName(entry.getValue());
            final MCRImage image = new MCRMemSaveImage(file, derivateID, imagePath);
            image.setTileDir(tileDir);
            image.tile();
            assertTrue("Tile directory is not created.", tileDir.exists());
            final File iviewFile = MCRImage.getTiledFile(tileDir, derivateID, imagePath);
            assertTrue("IView File is not created:" + iviewFile.getAbsolutePath(), iviewFile.exists());
            final MCRTiledPictureProps props = MCRTiledPictureProps.getInstance(iviewFile);
            final ZipFile iviewImage = new ZipFile(iviewFile);
            final int tilesCount = iviewImage.size() - 1;
            assertEquals(entry.getKey() + ": Metadata tile count does not match stored tile count.", props.getTilesCount(), tilesCount);
            final int x = props.width;
            final int y = props.height;
            assertEquals(entry.getKey() + ": Calculated tile count does not match stored tile count.", MCRImage.getTileCount(x, y),
                    tilesCount);
        }
    }
}
