// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.openstreetmap.josm.gui.util.GuiSizesHelper;

import com.kitfox.svg.SVGDiagram;

/**
 * Holds data for one particular image.
 * It can be backed by a svg or raster image.
 *
 * In the first case, <code>svg</code> is not <code>null</code> and in the latter case,
 * <code>baseImage</code> is not <code>null</code>.
 * @since 4271
 */
public class ImageResource {

    /**
     * Caches the image data for resized versions of the same image.
     */
    private final Map<Dimension, Image> imgCache = new HashMap<>();
    /**
     * SVG diagram information in case of SVG vector image.
     */
    private SVGDiagram svg;
    /**
     * Use this dimension to request original file dimension.
     */
    public static final Dimension DEFAULT_DIMENSION = new Dimension(-1, -1);
    /**
     * ordered list of overlay images
     */
    protected List<ImageOverlay> overlayInfo;
    /**
     * <code>true</code> if icon must be grayed out
     */
    protected boolean isDisabled;
    /**
     * The base raster image for the final output
     */
    private Image baseImage;

    /**
     * Constructs a new {@code ImageResource} from an image.
     * @param img the image
     */
    public ImageResource(Image img) {
        CheckParameterUtil.ensureParameterNotNull(img);
        baseImage = scaleBaseImageIfNeeded(img);
    }

    /** Scale image according to screen DPI if needed.
     *
     * @param img an image loaded from file (it's width and height are virtual pixels)
     * @return original img if virtual size is the same as real size or new image resized to real pixels
     */
    private static Image scaleBaseImageIfNeeded(Image img) {
        int imgWidth = img.getWidth(null);
        int imgHeight = img.getHeight(null);
        int realWidth = GuiSizesHelper.getSizeDpiAdjusted(imgWidth);
        int realHeight = GuiSizesHelper.getSizeDpiAdjusted(imgHeight);
        if (realWidth != -1 && realHeight != -1 && imgWidth != realWidth && imgHeight != realHeight) {
            Image realImage = img.getScaledInstance(realWidth, realHeight, Image.SCALE_SMOOTH);
            BufferedImage bimg = new BufferedImage(realWidth, realHeight, BufferedImage.TYPE_INT_ARGB);
            bimg.getGraphics().drawImage(realImage, 0, 0, null);
            return bimg;
        }
        return img;
    }

    /**
     * Constructs a new {@code ImageResource} from SVG data.
     * @param svg SVG data
     */
    public ImageResource(SVGDiagram svg) {
        CheckParameterUtil.ensureParameterNotNull(svg);
        this.svg = svg;
    }

    /**
     * Constructs a new {@code ImageResource} from another one and sets overlays.
     * @param res the existing resource
     * @param overlayInfo the overlay to apply
     * @since 8095
     */
    public ImageResource(ImageResource res, List<ImageOverlay> overlayInfo) {
        this.svg = res.svg;
        this.baseImage = res.baseImage;
        this.overlayInfo = overlayInfo;
    }

    /**
     * Set, if image must be filtered to grayscale so it will look like disabled icon.
     *
     * @param disabled true, if image must be grayed out for disabled state
     * @return the current object, for convenience
     * @since 10428
     */
    public ImageResource setDisabled(boolean disabled) {
        this.isDisabled = disabled;
        return this;
    }

    /**
     * Set both icons of an Action
     * @param a The action for the icons
     * @since 10369
     */
    public void attachImageIcon(AbstractAction a) {
        Dimension iconDimension = ImageProvider.ImageSizes.SMALLICON.getImageDimension();
        ImageIcon icon = getImageIconBounded(iconDimension);
        a.putValue(Action.SMALL_ICON, icon);

        iconDimension = ImageProvider.ImageSizes.LARGEICON.getImageDimension();
        icon = getImageIconBounded(iconDimension);
        a.putValue(Action.LARGE_ICON_KEY, icon);
    }

    /**
     * Set both icons of an Action
     * @param a The action for the icons
     * @param addresource Adds an resource named "ImageResource" if <code>true</code>
     * @since 10369
     */
    public void attachImageIcon(AbstractAction a, boolean addresource) {
        attachImageIcon(a);
        if (addresource) {
            a.putValue("ImageResource", this);
        }
    }

    /**
     * Returns the image icon at default dimension.
     * @return the image icon at default dimension
     */
    public ImageIcon getImageIcon() {
        return getImageIcon(DEFAULT_DIMENSION);
    }

    /**
     * Get an ImageIcon object for the image of this resource
     * @param  dim The requested dimensions. Use (-1,-1) for the original size and (width, -1)
     *         to set the width, but otherwise scale the image proportionally.
     * @return ImageIcon object for the image of this resource, scaled according to dim
     */
    public ImageIcon getImageIcon(Dimension dim) {
        if (dim.width < -1 || dim.width == 0 || dim.height < -1 || dim.height == 0)
            throw new IllegalArgumentException(dim+" is invalid");
        Image img = imgCache.get(dim);
        if (img != null) {
            return new ImageIcon(img);
        }
        BufferedImage bimg;
        if (svg != null) {
            Dimension realDim = GuiSizesHelper.getDimensionDpiAdjusted(dim);
            bimg = ImageProvider.createImageFromSvg(svg, realDim);
            if (bimg == null) {
                return null;
            }
        } else {
            if (baseImage == null) throw new AssertionError();

            int realWidth = GuiSizesHelper.getSizeDpiAdjusted(dim.width);
            int realHeight = GuiSizesHelper.getSizeDpiAdjusted(dim.height);
            ImageIcon icon = new ImageIcon(baseImage);
            if (realWidth == -1 && realHeight == -1) {
                realWidth = GuiSizesHelper.getSizeDpiAdjusted(icon.getIconWidth());
                realHeight = GuiSizesHelper.getSizeDpiAdjusted(icon.getIconHeight());
            } else if (realWidth == -1) {
                realWidth = Math.max(1, icon.getIconWidth() * realHeight / icon.getIconHeight());
            } else if (realHeight == -1) {
                realHeight = Math.max(1, icon.getIconHeight() * realWidth / icon.getIconWidth());
            }
            Image i = icon.getImage().getScaledInstance(realWidth, realHeight, Image.SCALE_SMOOTH);
            bimg = new BufferedImage(realWidth, realHeight, BufferedImage.TYPE_INT_ARGB);
            bimg.getGraphics().drawImage(i, 0, 0, null);
        }
        if (overlayInfo != null) {
            for (ImageOverlay o : overlayInfo) {
                o.process(bimg);
            }
        }
        if (isDisabled) {
            //Use default Swing functionality to make icon look disabled by applying grayscaling filter.
            Icon disabledIcon = UIManager.getLookAndFeel().getDisabledIcon(null, new ImageIcon(bimg));
            if (disabledIcon == null) {
                return null;
            }

            //Convert Icon to ImageIcon with BufferedImage inside
            bimg = new BufferedImage(bimg.getWidth(), bimg.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
            disabledIcon.paintIcon(new JPanel(), bimg.getGraphics(), 0, 0);
        }
        imgCache.put(dim, bimg);
        return new ImageIcon(bimg);
    }

    /**
     * Get image icon with a certain maximum size. The image is scaled down
     * to fit maximum dimensions. (Keeps aspect ratio)
     *
     * @param maxSize The maximum size. One of the dimensions (width or height) can be -1,
     * which means it is not bounded.
     * @return ImageIcon object for the image of this resource, scaled down if needed, according to maxSize
     */
    public ImageIcon getImageIconBounded(Dimension maxSize) {
        if (maxSize.width < -1 || maxSize.width == 0 || maxSize.height < -1 || maxSize.height == 0)
            throw new IllegalArgumentException(maxSize+" is invalid");
        float sourceWidth;
        float sourceHeight;
        int maxWidth = maxSize.width;
        int maxHeight = maxSize.height;
        if (svg != null) {
            sourceWidth = svg.getWidth();
            sourceHeight = svg.getHeight();
        } else {
            if (baseImage == null) throw new AssertionError();
            ImageIcon icon = new ImageIcon(baseImage);
            sourceWidth = icon.getIconWidth();
            sourceHeight = icon.getIconHeight();
            if (sourceWidth <= maxWidth) {
                maxWidth = -1;
            }
            if (sourceHeight <= maxHeight) {
                maxHeight = -1;
            }
        }

        if (maxWidth == -1 && maxHeight == -1)
            return getImageIcon(DEFAULT_DIMENSION);
        else if (maxWidth == -1)
            return getImageIcon(new Dimension(-1, maxHeight));
        else if (maxHeight == -1)
            return getImageIcon(new Dimension(maxWidth, -1));
        else if (sourceWidth / maxWidth > sourceHeight / maxHeight)
            return getImageIcon(new Dimension(maxWidth, -1));
        else
            return getImageIcon(new Dimension(-1, maxHeight));
   }
}
