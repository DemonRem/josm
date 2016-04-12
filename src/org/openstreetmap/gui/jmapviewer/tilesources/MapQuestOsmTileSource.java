// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer.tilesources;

import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;

public class MapQuestOsmTileSource extends AbstractMapQuestTileSource {

    private static final String PATTERN = "http://otile%d.mqcdn.com/tiles/1.0.0/osm";

    public MapQuestOsmTileSource() {
        super("MapQuest-OSM", PATTERN, "mapquest-osm");
    }

    @Override
    public String getAttributionText(int zoom, ICoordinate topLeft,
            ICoordinate botRight) {
        return super.getAttributionText(zoom, topLeft, botRight)+" - "+MAPQUEST_ATTRIBUTION;
    }
}