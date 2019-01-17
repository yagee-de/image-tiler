import javax.imageio.spi.ImageInputStreamSpi;

import org.mycore.imagetiler.input.MCRChannelImageInputStreamSpi;

module org.mycore.imagetiler {
    requires java.xml;
    requires java.desktop;
    requires java.logging;//no log4j: https://bugs.openjdk.java.net/browse/JDK-8208269
    requires java.xml.bind;
    requires jdk.zipfs;
    exports org.mycore.imagetiler;
    opens org.mycore.imagetiler to java.xml.bind;
    uses org.mycore.imagetiler.MCRTileEventHandler;
    provides ImageInputStreamSpi with MCRChannelImageInputStreamSpi;
}
