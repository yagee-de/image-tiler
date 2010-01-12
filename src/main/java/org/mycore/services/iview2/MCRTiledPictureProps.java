package org.mycore.services.iview2;

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

public class MCRTiledPictureProps {

    int countTiles;

    int width;

    int height;

    int zoomlevel;

    static final String PROP_ZOOM_LEVEL = "zoomLevel";

    static final String PROP_HEIGHT = "height";

    static final String PROP_WIDTH = "width";

    static final String PROP_TILES = "tiles";

    static final String PROP_PATH = "path";

    static final String PROP_DERIVATE = "derivate";

    static final String IMAGEINFO_XML = "imageinfo.xml";

    private static Logger LOGGER = Logger.getLogger(MCRTiledPictureProps.class);

    private static SAXBuilder DOC_BUILDER = new SAXBuilder(false);

    public static MCRTiledPictureProps getInstance(File iviewFile) throws IOException, JDOMException {
        ZipFile zipFile = new ZipFile(iviewFile);
        ZipEntry ze = zipFile.getEntry(IMAGEINFO_XML);
        if (ze != null) {
            //size of a tile or imageinfo.xml file is always smaller than Integer.MAX_VALUE
            LOGGER.debug("Extracting " + ze.getName() + " size " + ze.getSize());
            InputStream zin = zipFile.getInputStream(ze);
            try {
                Document imageInfo = DOC_BUILDER.build(zin);
                Element root = imageInfo.getRootElement();
                MCRTiledPictureProps props = new MCRTiledPictureProps();
                props.countTiles = Integer.parseInt(root.getAttributeValue(PROP_TILES));
                props.height = Integer.parseInt(root.getAttributeValue(PROP_HEIGHT));
                props.width = Integer.parseInt(root.getAttributeValue(PROP_WIDTH));
                props.zoomlevel = Integer.parseInt(root.getAttributeValue(PROP_ZOOM_LEVEL));
                return props;
            } finally {
                zin.close();
            }
        } else {
            LOGGER.warn("Did not find "+IMAGEINFO_XML+" in "+iviewFile.getAbsolutePath());
            return null;
        }
    }

    /**
     * @return the countTiles
     */
    public int getCountTiles() {
        return countTiles;
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return the zoomlevel
     */
    public int getZoomlevel() {
        return zoomlevel;
    }
}