import javax.imageio.spi.ImageInputStreamSpi;

import org.mycore.imagetiler.input.MCRChannelImageInputStreamSpi;

module org.mycore.imagetiler {
    requires java.xml;
    requires java.desktop;
    requires java.xml.bind;
    requires org.apache.logging.log4j;
    exports org.mycore.imagetiler;
    opens org.mycore.imagetiler to java.xml.bind;
    uses org.mycore.imagetiler.MCRTileEventHandler;
    provides ImageInputStreamSpi with MCRChannelImageInputStreamSpi;
}
