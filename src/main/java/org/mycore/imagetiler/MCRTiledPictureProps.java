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
package org.mycore.imagetiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 * The <code>MCRTiledPictureProps</code> gives access to a bunch of properties referring to a {@link MCRImage} instance.
 * @author Thomas Scheffler (yagee)
 *
 */
public class MCRTiledPictureProps {

    /**
     * file name of property file inside .iview2 file
     */
    public static final String IMAGEINFO_XML = "imageinfo.xml";

    static final String PROP_DERIVATE = "derivate";

    static final String PROP_HEIGHT = "height";

    static final String PROP_PATH = "path";

    static final String PROP_TILES = "tiles";

    static final String PROP_WIDTH = "width";

    static final String PROP_ZOOM_LEVEL = "zoomLevel";

    private static final ThreadLocal<SAXBuilder> DOC_BUILDER = new ThreadLocal<SAXBuilder>() {
        @Override
        protected SAXBuilder initialValue() {
            return new SAXBuilder(false);
        }

    };

    private static final Logger LOGGER = Logger.getLogger(MCRTiledPictureProps.class);

    int tilesCount;

    int height;

    int width;

    int zoomlevel;

    /**
     * gets properties of the given <code>.iview2</code> file.
     * Use {@link MCRImage#getTiledFile(File, String, String)} to get the {@link File} instance of the <code>.iview2</code> file.
     * @param iviewFile the IView2 file
     * @return instance of the class referring <code>iviewFile</code> 
     * @throws IOException Exceptions occurs while accessing <code>iviewFile</code>.
     * @throws JDOMException Exceptions while parsing metadata (file: <code>imageinfo.xml</code>)
     */
    public static MCRTiledPictureProps getInstance(final File iviewFile) throws IOException, JDOMException {
        final ZipFile zipFile = new ZipFile(iviewFile);
        final ZipEntry ze = zipFile.getEntry(IMAGEINFO_XML);
        if (ze != null) {
            //size of a tile or imageinfo.xml file is always smaller than Integer.MAX_VALUE
            LOGGER.debug("Extracting " + ze.getName() + " size " + ze.getSize());
            final InputStream zin = zipFile.getInputStream(ze);
            try {
                final Document imageInfo = DOC_BUILDER.get().build(zin);
                final Element root = imageInfo.getRootElement();
                final MCRTiledPictureProps props = new MCRTiledPictureProps();
                props.tilesCount = Integer.parseInt(root.getAttributeValue(PROP_TILES));
                props.height = Integer.parseInt(root.getAttributeValue(PROP_HEIGHT));
                props.width = Integer.parseInt(root.getAttributeValue(PROP_WIDTH));
                props.zoomlevel = Integer.parseInt(root.getAttributeValue(PROP_ZOOM_LEVEL));
                return props;
            } finally {
                zin.close();
                zipFile.close();
            }
        } else {
            LOGGER.warn("Did not find " + IMAGEINFO_XML + " in " + iviewFile.getAbsolutePath());
            return null;
        }
    }

    /**
     * @return the tiles count
     */
    public int getTilesCount() {
        return tilesCount;
    }

    /**
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return the zoomlevel
     */
    public int getZoomlevel() {
        return zoomlevel;
    }
}
