package cinnamon

import cinnamon.global.Constants
import cinnamon.global.ConfThreadLocal
import cinnamon.image.ImageMeta

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import cinnamon.utils.ParamParser
import java.awt.Image
import javax.imageio.stream.ImageOutputStream
import java.awt.image.RenderedImage
import java.awt.Graphics
import org.dom4j.Element

class ImageService {

    OsdService osdServiceBean

    /**
     * Create or retrieve a base64 encoded thumbnail version of an image,
     * which can be embedded in a HTML page. 
     * If requested, the thumbnail data will be stored in the object's custom metadata
     * in a metaset of type thumbnail.
     * If the image is already small enough, the image itself will be returned (and stored in the metadata,
     * if requested).
     * @param osd the OSD which contains the source image.
     * @param repositoryName the repository where the OSD object is stored (used to determine file system path)
     * @param longestSide the image will be scaled to have no side larger than longestSide 
     * @param storeInMetaset if true, store the generated thumbnail in a metaset of type thumbnail.
     * @return a base64 encoded String with the scaled image (or the source image, if longestSize is larger
     * than the actual dimension)
     */
    def fetchThumbnail(ObjectSystemData osd, String repositoryName, Integer longestSide, Boolean storeInMetaset) {
        // load thumbnail metaset
        def metaset = osd.fetchMetaset(Constants.METASET_THUMBNAIL)
        if (metaset) {
            log.debug("Found thumbnail metaset.")
            def xml = ParamParser.parseXmlToDocument(metaset.content)
            def thumbnailNode = xml.selectSingleNode("//thumbnail[@longestSide='${longestSide}']")
            if (thumbnailNode == null) {
                log.debug("thumbnail metaset did not contain a thumbnail node.")
                def image = loadImage(repositoryName, osd.contentPath)
                def baseImage
                if (GraphicsUtilities.needsThumbnail(image, longestSide)){
                     baseImage = imageToBase64(GraphicsUtilities.createThumbnail(image, longestSide))
                }
                else{
                    baseImage = imageToBase64(image)
                }                
                if (storeInMetaset) {
                    addToMetaset(osd, baseImage, longestSide)
                }
                return baseImage
            }
            else {
                return thumbnailNode.text
            }
        }
        else {
            // create thumbnail
            log.debug("Thumbnail for ${osd.id} does not exist yet.")
            def image = loadImage(repositoryName, osd.contentPath)

            if (GraphicsUtilities.needsThumbnail(image, longestSide)) {
                def baseImage = imageToBase64(GraphicsUtilities.createThumbnail(image, longestSide))
                if (storeInMetaset) {
                    addToMetaset(osd, baseImage, longestSide)
                }
                return baseImage
            }
            else {
                // image is already small enough: return image
                return imageToBase64(image)
            }
        }
    }

    /**
     * Convert a BufferedImage to a base64 encoded String
     * @param image
     * @return the image in jpg format, encoded as unchunked base64 String. 
     */
    String imageToBase64(BufferedImage image) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ImageIO.write(image, 'jpeg', bos)
        return bos.toByteArray().encodeBase64()
    }

    byte[] imageToBytes(BufferedImage image) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ImageIO.write(image, 'jpeg', bos)
        return bos.toByteArray()
    }

    /**
     * Load and convert an image to a BufferedImage with RGB ColorModel 
     * (should now work with PNG to JPEG thumbnails). 
     * @param repositoryName
     * @param contentPath
     * @return BufferedImage with ColorModel TYPE_INT_RGB
     */
    BufferedImage loadImage(String repositoryName, String contentPath) {
        def file = new File(ConfThreadLocal.conf.getDataRoot() + File.separator + repositoryName, contentPath)
        log.debug("looking at image file: ${file.absolutePath}")
        def buffy
        if(file.exists()){
            buffy = ImageIO.read(file)
        }
        else{
            log.error("Could not find file ${file.absolutePath}")
            // TODO: report broken image file to user, perhaps by using a notification object.
            return new BufferedImage(1,1, BufferedImage.TYPE_INT_RGB)
        }
        return convert(buffy, BufferedImage.TYPE_INT_RGB)
    }

    public static BufferedImage convert(BufferedImage src, int bufImgType) {
        BufferedImage img= new BufferedImage(src.getWidth(), src.getHeight(), bufImgType);
        Graphics2D g2d= img.createGraphics();
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return img;
    }
    
    def addToMetaset(ObjectSystemData osd, String imageData, Integer longestSide) {
        def metaset = osd.fetchMetaset(Constants.METASET_THUMBNAIL)
        def metasetRoot
        def addMetasetToOsd = false
        if (metaset) {
            metasetRoot = ParamParser.parseXmlToDocument(metaset.content)
        }
        else {
            def imageMetasetType = MetasetType.findOrSaveByName(Constants.METASET_THUMBNAIL)
            metaset = new Metaset(null, imageMetasetType)
            metasetRoot = ParamParser.parseXmlToDocument("<metaset />")
            addMetasetToOsd = true
        }
        Element thumbnail = metasetRoot.rootElement.addElement('thumbnail')
        thumbnail.addAttribute('longestSide', longestSide.toString())
        thumbnail.addText(imageData)
        metaset.content = metasetRoot.asXML()
        metaset.save()
        if (addMetasetToOsd) {
            osd.addMetaset(metaset)
            osd.save()
        }
    }

    /**
     * Load image from OSD and scale it to the longest side parameter. If the image is smaller
     * or longestSide is 0, just return the image.
     * @param osd OSD which contains the image
     * @param repositoryName repository where the OSD is stored
     * @param longestSide maximum value of the image's longest side. If 0, the image will not be scaled.
     * @return image data as byte array
     */
    byte[] fetchScaledImage(ObjectSystemData osd, String repositoryName, Integer longestSide) {
        def image = loadImage(repositoryName, osd.contentPath)
        return imageToBytes(scaleImage(image, longestSide))
    }

    BufferedImage scaleImage(BufferedImage image, Integer longestSide) {
        if (longestSide == 0 || (image.height <= longestSide && image.width <= longestSide)) {
            // image is already small enough: return image
            return image
        }
        else {
            return GraphicsUtilities.createThumbnail(image, longestSide)
        }
    }

    /**
     * Fetch an image with 
     * @param osd
     * @param repositoryName
     * @param longestSide maximum value of the image's longest side. 
     *  If 0 or larger than the longest side of the image, the image will not be scaled.
     * @param storeAsThumbnail if true, store the base64 encoded image in the OSD's custom metadata field.
     * @return container object which holds the image data as byte[] and dimensions. 
     */
    ImageMeta fetchImageWithMeta(ObjectSystemData osd, String repositoryName, Integer longestSide, Boolean storeAsThumbnail) {
        def image = loadImage(repositoryName, osd.contentPath)
        def scaledImage = scaleImage(image, longestSide)
        def asBytes = imageToBytes(scaledImage)
        ImageMeta imageMeta = new ImageMeta(imageData: asBytes, width: image.width, height: image.height,
                scaledWidth: scaledImage.width, scaledHeight: scaledImage.height)
        if (storeAsThumbnail) {
            addToMetaset(osd, imageMeta.imageAsBase64(), longestSide)
        }
        return imageMeta
    }

    BufferedImage cropImage(BufferedImage buffy, Integer x, Integer y, Integer width, Integer height) {
        // check parameters:
        def bounds = [x, y, width, height]
        bounds.each {
            if (it == null) {
                throw new RuntimeException('error.missing.params')
            }
            if (it < 0) {
                throw new RuntimeException('error.image.negative.boundary')
            }
        }
        if (x + width > buffy.width) {
            log.debug("reducing width $width to maximum width")
            width = buffy.width - x
        }
        if (y + height > buffy.height) {
            log.debug("reducing height to maximum height")
            height = buffy.height - y
        }
        buffy.getSubimage(x, y, width, height)
    }

    ObjectSystemData rescaleImage(String idName, String repositoryName, params) {
        ObjectSystemData osd = ObjectSystemData.get(params."$idName")
        BufferedImage buffy = ImageIO.read(new File(osd.getFullContentPath()))
        Integer x = params.x?.toBigDecimal()?.intValue()
        Integer y = params.y?.toBigDecimal()?.intValue()
        Integer width = params.width?.toBigDecimal()?.intValue()
        Integer height = params.height?.toBigDecimal()?.intValue()
        BufferedImage subImage = cropImage(buffy, x, y, width, height)

        // add dimensions to name
        String name
        if (osd.name.matches(/^.+ \d+x\d+$/)) {
            name = osd.name.replaceAll('^(.+) \\d+x\\d+', '$1')
        }
        else {
            name = osd.name
        }
        params.name = "$name ${width}x${height}"

        byte[] imageData = imageToBytes(subImage)
        File tempImage = File.createTempFile('imageService', 'jpg')
        tempImage.withOutputStream { it.write(imageData) }
        params.objectType = osd.type.id.toString()
        params.format = osd.format.id.toString()
        ObjectSystemData newImage = osdServiceBean.createOsd(params, repositoryName, tempImage, osd.parent)
        newImage.save()
        params."$idName" = newImage.id
        return newImage
    }        
}
