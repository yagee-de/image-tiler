/**
 * 
 */
package org.mycore.imagetiler;

/**
 * Eventhandler to free resources after ImageReader is created
 * @author Thomas Scheffler
 *
 */
public interface MCRTileEventHandler {

    /**
     * Use this method to acquire resources needed for ImageReader.
     */
    void preImageReaderCreated();

    /**
     * Use this method to free resources after creation of ImageReader.
     */
    void postImageReaderCreated();
}
