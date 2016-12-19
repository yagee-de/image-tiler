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
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.transform.stream.StreamSource;

/**
 * The <code>MCRTiledPictureProps</code> gives access to a bunch of properties referring to a {@link MCRImage} instance.
 * @author Thomas Scheffler (yagee)
 *
 */
@XmlRootElement(name = "imageinfo")
@XmlAccessorType(XmlAccessType.FIELD)
public class MCRTiledPictureProps {

    private static JAXBContext jaxbContext;

    static {
        try {
            jaxbContext = JAXBContext.newInstance(MCRTiledPictureProps.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * file name of property file inside .iview2 file
     */
    public static final String IMAGEINFO_XML = "imageinfo.xml";

    @XmlAttribute(name = "tiles")
    protected int tilesCount;

    @XmlAttribute
    protected int height;

    @XmlAttribute
    protected int width;

    @XmlAttribute
    protected int zoomlevel;

    /**
     * gets properties of the given <code>.iview2</code> file.
     * Use {@link MCRImage#getTiledFile(Path, String, String)} to get the {@link Path} instance of the <code>.iview2</code> file.
     * @param iviewFile the IView2 file
     * @return instance of the class referring <code>iviewFile</code>
     * @throws IOException Exceptions occurs while accessing <code>iviewFile</code>.
     */
    public static MCRTiledPictureProps getInstance(final File iviewFile) throws IOException {
        return iviewFile.isDirectory() ? getInstanceFromDirectory(iviewFile.toPath()) : getInstanceFromFile(iviewFile
            .toPath());
    }

    /**
     * gets properties of the given <code>.iview2</code> file.
     * Use {@link MCRImage#getTiledFile(Path, String, String)} to get the {@link Path} instance of the <code>.iview2</code> file.
     * @param iviewFile the IView2 file
     * @return instance of the class referring <code>iviewFile</code>
     * @throws IOException Exceptions occurs while accessing <code>iviewFile</code>.
     */
    public static MCRTiledPictureProps getInstanceFromFile(Path iviewFile) throws IOException {
        URI uri = URI.create("jar:" + iviewFile.toUri().toString());
        try (FileSystem zipFileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap(),
            MCRTiledPictureProps.class.getClassLoader())) {
            Path iviewFileRoot = zipFileSystem.getRootDirectories().iterator().next();
            return getInstanceFromDirectory(iviewFileRoot);
        }
    }

    /**
     * gets properties of the given <code>.iview2</code> file.
     * Use {@link MCRImage#getTiledFile(Path, String, String)} to get the {@link Path} instance of the <code>.iview2</code> file.
     * @param iviewFileRoot the root of the iviewFile
     * @return instance of the class referring <code>iviewFile</code>
     * @throws IOException Exceptions occurs while accessing <code>iviewFile</code>.
     */
    public static MCRTiledPictureProps getInstanceFromDirectory(Path iviewFileRoot) throws IOException {
        Path imageInfoPath = iviewFileRoot.resolve(IMAGEINFO_XML);
        try (final InputStream zin = Files.newInputStream(imageInfoPath)) {
            try {
                StreamSource is = new StreamSource(zin);
                return jaxbContext.createUnmarshaller().unmarshal(is, MCRTiledPictureProps.class).getValue();
            } catch (JAXBException e) {
                throw new IOException(e);
            }
        }
    }

    public static JAXBContext getJaxbContext() {
        return jaxbContext;
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

    @Override
    public String toString() {
        return "MCRTiledPictureProps [tilesCount=" + tilesCount + ", height=" + height + ", width=" + width
            + ", zoomlevel=" + zoomlevel + "]";
    }
}
