// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.imagery.DefaultLayer;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;
import org.openstreetmap.josm.data.imagery.WMTSTileSource;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.layer.AlignImageryPanel;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.preferences.imagery.WMSLayerTree;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.imagery.WMSImagery;
import org.openstreetmap.josm.io.imagery.WMSImagery.LayerDetails;
import org.openstreetmap.josm.io.imagery.WMSImagery.WMSGetCapabilitiesException;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Action displayed in imagery menu to add a new imagery layer.
 * @since 3715
 */
public class AddImageryLayerAction extends JosmAction implements AdaptableAction {
    private final transient ImageryInfo info;

    static class SelectWmsLayersDialog extends ExtendedDialog {
        SelectWmsLayersDialog(WMSLayerTree tree, JComboBox<String> formats) {
            super(Main.parent, tr("Select WMS layers"), tr("Add layers"), tr("Cancel"));
            final JScrollPane scrollPane = new JScrollPane(tree.getLayerTree());
            scrollPane.setPreferredSize(new Dimension(400, 400));
            final JPanel panel = new JPanel(new GridBagLayout());
            panel.add(scrollPane, GBC.eol().fill());
            panel.add(formats, GBC.eol().fill(GBC.HORIZONTAL));
            setContent(panel);
        }
    }

    /**
     * Constructs a new {@code AddImageryLayerAction} for the given {@code ImageryInfo}.
     * If an http:// icon is specified, it is fetched asynchronously.
     * @param info The imagery info
     */
    public AddImageryLayerAction(ImageryInfo info) {
        super(info.getMenuName(), /* ICON */"imagery_menu", tr("Add imagery layer {0}", info.getName()), null,
                true, ToolbarPreferences.IMAGERY_PREFIX + info.getToolbarName(), false);
        putValue("help", ht("/Preferences/Imagery"));
        setTooltip(info.getToolTipText().replaceAll("</?html>", ""));
        this.info = info;
        installAdapters();

        // change toolbar icon from if specified
        String icon = info.getIcon();
        if (icon != null) {
            new ImageProvider(icon).setOptional(true).getResourceAsync().thenAccept(result -> {
                if (result != null) {
                    GuiHelper.runInEDT(() -> result.attachImageIcon(this));
                }
            });
        }
    }

    /**
     * Converts general ImageryInfo to specific one, that does not need any user action to initialize
     * see: https://josm.openstreetmap.de/ticket/13868
     * @param info ImageryInfo that will be converted (or returned when no conversion needed)
     * @return ImageryInfo object that's ready to be used to create TileSource
     */
    private ImageryInfo convertImagery(ImageryInfo info) {
        try {
            switch(info.getImageryType()) {
            case WMS_ENDPOINT:
                // convert to WMS type
                return getWMSLayerInfo(info);
            case WMTS:
                // specify which layer to use
                DefaultLayer layerId = new WMTSTileSource(info).userSelectLayer();
                if (layerId != null) {
                    ImageryInfo copy = new ImageryInfo(info);
                    Collection<DefaultLayer> defaultLayers = new ArrayList<>(1);
                    defaultLayers.add(layerId);
                    copy.setDefaultLayers(defaultLayers);
                    return copy;
                }
                // layer not selected - refuse to add
                return null;
            default:
                return info;
            }
        } catch (MalformedURLException ex) {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(Main.parent, tr("Invalid service URL."),
                        tr("WMS Error"), JOptionPane.ERROR_MESSAGE);
            }
            Main.error(ex, false);
        } catch (IOException ex) {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(Main.parent, tr("Could not retrieve WMS layer list."),
                        tr("WMS Error"), JOptionPane.ERROR_MESSAGE);
            }
            Main.error(ex, false);
        } catch (WMSGetCapabilitiesException ex) {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(Main.parent, tr("Could not parse WMS layer list."),
                        tr("WMS Error"), JOptionPane.ERROR_MESSAGE);
            }
            Main.error(ex, "Could not parse WMS layer list. Incoming data:\n"+ex.getIncomingData());
        }
        return null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) return;
        try {
            final ImageryInfo infoToAdd = convertImagery(info);
            if (infoToAdd != null) {
                Main.getLayerManager().addLayer(ImageryLayer.create(infoToAdd));
                AlignImageryPanel.addNagPanelIfNeeded(infoToAdd);
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() == null || ex.getMessage().isEmpty() || GraphicsEnvironment.isHeadless()) {
                throw ex;
            } else {
                JOptionPane.showMessageDialog(Main.parent,
                        ex.getMessage(), tr("Error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Asks user to choose a WMS layer from a WMS endpoint.
     * @param info the WMS endpoint.
     * @return chosen WMS layer, or null
     * @throws IOException if any I/O error occurs while contacting the WMS endpoint
     * @throws WMSGetCapabilitiesException if the WMS getCapabilities request fails
     */
    protected static ImageryInfo getWMSLayerInfo(ImageryInfo info) throws IOException, WMSGetCapabilitiesException {
        CheckParameterUtil.ensureThat(ImageryType.WMS_ENDPOINT.equals(info.getImageryType()), "wms_endpoint imagery type expected");

        final WMSImagery wms = new WMSImagery();
        wms.attemptGetCapabilities(info.getUrl());

        final WMSLayerTree tree = new WMSLayerTree();
        tree.updateTree(wms);
        List<String> wmsFormats = wms.getFormats();
        final JComboBox<String> formats = new JComboBox<>(wmsFormats.toArray(new String[wmsFormats.size()]));
        formats.setSelectedItem(wms.getPreferredFormats());
        formats.setToolTipText(tr("Select image format for WMS layer"));

        if (!GraphicsEnvironment.isHeadless() && 1 != new SelectWmsLayersDialog(tree, formats).showDialog().getValue()) {
            return null;
        }

        final String url = wms.buildGetMapUrl(
                tree.getSelectedLayers(), (String) formats.getSelectedItem());
        Set<String> supportedCrs = new HashSet<>();
        boolean first = true;
        StringBuilder layersString = new StringBuilder();
        for (LayerDetails layer: tree.getSelectedLayers()) {
            if (first) {
                supportedCrs.addAll(layer.getProjections());
                first = false;
            }
            layersString.append(layer.name);
            layersString.append(", ");
            supportedCrs.retainAll(layer.getProjections());
        }

        // copy all information from WMS
        ImageryInfo ret = new ImageryInfo(info);
        // and update according to user choice
        ret.setUrl(url);
        ret.setImageryType(ImageryType.WMS);
        if (layersString.length() > 2) {
            ret.setName(ret.getName() + ' ' + layersString.substring(0, layersString.length() - 2));
        }
        ret.setServerProjections(supportedCrs);
        return ret;
    }

    @Override
    protected void updateEnabledState() {
        if (info.isBlacklisted()) {
            setEnabled(false);
        } else {
            setEnabled(true);
        }
    }

    @Override
    public String toString() {
        return "AddImageryLayerAction [info=" + info + ']';
    }
}
