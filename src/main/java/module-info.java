import javax.imageio.spi.ImageInputStreamSpi;

import org.mycore.imagetiler.input.MCRChannelImageInputStreamSpi;

module org.mycore.imagetiler {
    requires java.xml;
    requires java.desktop;
    requires jakarta.xml.bind;
    requires org.apache.logging.log4j;
    requires com.github.spotbugs.annotations;
    exports org.mycore.imagetiler;
    opens org.mycore.imagetiler to jakarta.xml.bind;
    uses org.mycore.imagetiler.MCRTileEventHandler;
    provides ImageInputStreamSpi with MCRChannelImageInputStreamSpi;
}
