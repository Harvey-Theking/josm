// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.ViewportData;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.visitor.paint.PaintColors;
import org.openstreetmap.josm.data.osm.visitor.paint.Rendering;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.MultipolygonCache;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.layer.geoimage.GeoImageLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.MarkerLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.PlayHeadMarker;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.AudioPlayer;
import org.openstreetmap.josm.tools.BugReportExceptionHandler;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 * This is a component used in the {@link MapFrame} for browsing the map. It use is to
 * provide the MapMode's enough capabilities to operate.<br><br>
 *
 * {@code MapView} holds meta-data about the data set currently displayed, as scale level,
 * center point viewed, what scrolling mode or editing mode is selected or with
 * what projection the map is viewed etc..<br><br>
 *
 * {@code MapView} is able to administrate several layers.
 *
 * @author imi
 */
public class MapView extends NavigatableComponent
implements PropertyChangeListener, PreferenceChangedListener, OsmDataLayer.LayerStateChangeListener {

    /**
     * Interface to notify listeners of a layer change.
     * @author imi
     */
    public interface LayerChangeListener {

        /**
         * Notifies this listener that the active layer has changed.
         * @param oldLayer The previous active layer
         * @param newLayer The new activer layer
         */
        void activeLayerChange(Layer oldLayer, Layer newLayer);

        /**
         * Notifies this listener that a layer has been added.
         * @param newLayer The new added layer
         */
        void layerAdded(Layer newLayer);

        /**
         * Notifies this listener that a layer has been removed.
         * @param oldLayer The old removed layer
         */
        void layerRemoved(Layer oldLayer);
    }

    /**
     * An interface that needs to be implemented in order to listen for changes to the active edit layer.
     */
    public interface EditLayerChangeListener {

        /**
         * Called after the active edit layer was changed.
         * @param oldLayer The old edit layer
         * @param newLayer The current (new) edit layer
         */
        void editLayerChanged(OsmDataLayer oldLayer, OsmDataLayer newLayer);
    }

    public boolean viewportFollowing = false;

    /**
     * the layer listeners
     */
    private static final CopyOnWriteArrayList<LayerChangeListener> layerChangeListeners = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EditLayerChangeListener> editLayerChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * Removes a layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    public static void removeLayerChangeListener(LayerChangeListener listener) {
        layerChangeListeners.remove(listener);
    }

    public static void removeEditLayerChangeListener(EditLayerChangeListener listener) {
        editLayerChangeListeners.remove(listener);
    }

    /**
     * Adds a layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    public static void addLayerChangeListener(LayerChangeListener listener) {
        if (listener != null) {
            layerChangeListeners.addIfAbsent(listener);
        }
    }

    /**
     * Adds a layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     * @param initialFire fire an active-layer-changed-event right after adding
     * the listener in case there is a layer present (should be)
     */
    public static void addLayerChangeListener(LayerChangeListener listener, boolean initialFire) {
        addLayerChangeListener(listener);
        if (initialFire && Main.isDisplayingMapView()) {
            listener.activeLayerChange(null, Main.map.mapView.getActiveLayer());
        }
    }

    /**
     * Adds an edit layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     * @param initialFire fire an edit-layer-changed-event right after adding
     * the listener in case there is an edit layer present
     */
    public static void addEditLayerChangeListener(EditLayerChangeListener listener, boolean initialFire) {
        addEditLayerChangeListener(listener);
        if (initialFire && Main.isDisplayingMapView() && Main.map.mapView.getEditLayer() != null) {
            listener.editLayerChanged(null, Main.map.mapView.getEditLayer());
        }
    }

    /**
     * Adds an edit layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    public static void addEditLayerChangeListener(EditLayerChangeListener listener) {
        if (listener != null) {
            editLayerChangeListeners.addIfAbsent(listener);
        }
    }

    /**
     * Calls the {@link LayerChangeListener#activeLayerChange(Layer, Layer)} method of all listeners.
     *
     * @param oldLayer The old layer
     * @param newLayer The new active layer.
     */
    protected void fireActiveLayerChanged(Layer oldLayer, Layer newLayer) {
        checkLayerLockNotHeld();
        for (LayerChangeListener l : layerChangeListeners) {
            l.activeLayerChange(oldLayer, newLayer);
        }
    }

    protected void fireLayerAdded(Layer newLayer) {
        checkLayerLockNotHeld();
        for (MapView.LayerChangeListener l : MapView.layerChangeListeners) {
            l.layerAdded(newLayer);
        }
    }

    protected void fireLayerRemoved(Layer layer) {
        checkLayerLockNotHeld();
        for (MapView.LayerChangeListener l : MapView.layerChangeListeners) {
            l.layerRemoved(layer);
        }
    }

    protected void fireEditLayerChanged(OsmDataLayer oldLayer, OsmDataLayer newLayer) {
        checkLayerLockNotHeld();
        for (EditLayerChangeListener l : editLayerChangeListeners) {
            l.editLayerChanged(oldLayer, newLayer);
        }
    }

    /**
     * This is a simple invariant check that tests if the {@link #layerLock} is not write locked.
     * This should be the case whenever a layer listener is invoked.
     */
    private void checkLayerLockNotHeld() {
        if (layerLock.isWriteLockedByCurrentThread()) {
            Main.warn("layerLock is write-held while a listener was called.");
        }
    }

    /**
     * A list of all layers currently loaded. Locked by {@link #layerLock}.
     */
    private final transient List<Layer> layers = new ArrayList<>();

    /**
     * This lock manages concurrent access to {@link #layers},
     * {@link #editLayer} and {@link #activeLayer}.
     * <p>
     * The read lock is always held while those fields are read or while layer change listeners are fired.
     */
    private final ReentrantReadWriteLock layerLock = new ReentrantReadWriteLock();

    /**
     * The play head marker: there is only one of these so it isn't in any specific layer
     */
    public transient PlayHeadMarker playHeadMarker = null;

    /**
     * The layer from the layers list that is currently active. Locked by {@link #layerLock}.
     */
    private transient Layer activeLayer;

    /**
     * The edit layer is the current active data layer. Locked by {@link #layerLock}.
     */
    private transient OsmDataLayer editLayer;

    /**
     * The last event performed by mouse.
     */
    public MouseEvent lastMEvent = new MouseEvent(this, 0, 0, 0, 0, 0, 0, false); // In case somebody reads it before first mouse move

    /**
     * Temporary layers (selection rectangle, etc.) that are never cached and
     * drawn on top of regular layers.
     * Access must be synchronized.
     */
    private final transient Set<MapViewPaintable> temporaryLayers = new LinkedHashSet<>();

    private transient BufferedImage nonChangedLayersBuffer;
    private transient BufferedImage offscreenBuffer;
    // Layers that wasn't changed since last paint
    private final transient List<Layer> nonChangedLayers = new ArrayList<>();
    private transient Layer changedLayer;
    private int lastViewID;
    private boolean paintPreferencesChanged = true;
    private Rectangle lastClipBounds = new Rectangle();
    private transient MapMover mapMover;

    /**
     * Constructs a new {@code MapView}.
     * @param contentPane The content pane used to register shortcuts in its
     * {@link InputMap} and {@link ActionMap}
     * @param viewportData the initial viewport of the map. Can be null, then
     * the viewport is derived from the layer data.
     */
    public MapView(final JPanel contentPane, final ViewportData viewportData) {
        initialViewport = viewportData;
        Main.pref.addPreferenceChangeListener(this);
        final boolean unregisterTab = Shortcut.findShortcut(KeyEvent.VK_TAB, 0) != null;

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                removeComponentListener(this);

                for (JComponent c : getMapNavigationComponents(MapView.this)) {
                    MapView.this.add(c);
                }

                mapMover = new MapMover(MapView.this, contentPane);
            }
        });

        // listend to selection changes to redraw the map
        DataSet.addSelectionListener(repaintSelectionChangedListener);

        //store the last mouse action
        this.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                lastMEvent = e;
            }
        });
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent me) {
                // focus the MapView component when mouse is pressed inside it
                requestFocus();
            }
        });

        if (Shortcut.findShortcut(KeyEvent.VK_TAB, 0) != null) {
            setFocusTraversalKeysEnabled(false);
        }
    }

    /**
     * Adds the map navigation components to a
     * @param forMapView The map view to get the components for.
     * @return A list containing the correctly positioned map navigation components.
     */
    public static List<? extends JComponent> getMapNavigationComponents(MapView forMapView) {
        MapSlider zoomSlider = new MapSlider(forMapView);
        zoomSlider.setBounds(3, 0, 114, 30);
        zoomSlider.setFocusTraversalKeysEnabled(Shortcut.findShortcut(KeyEvent.VK_TAB, 0) == null);

        MapScaler scaler = new MapScaler(forMapView);
        scaler.setLocation(10, 30);

        return Arrays.asList(zoomSlider, scaler);
    }

    // remebered geometry of the component
    private Dimension oldSize = null;
    private Point oldLoc = null;

    /*
     * Call this method to keep map position on screen during next repaint
     */
    public void rememberLastPositionOnScreen() {
        oldSize = getSize();
        oldLoc  = getLocationOnScreen();
    }

    /**
     * Adds a GPX layer. A GPX layer is added below the lowest data layer.
     * <p>
     * Does not call {@link #fireLayerAdded(Layer)}.
     *
     * @param layer the GPX layer
     */
    protected void addGpxLayer(GpxLayer layer) {
        layerLock.writeLock().lock();
        try {
            if (layers.isEmpty()) {
                layers.add(layer);
                return;
            }
            for (int i = layers.size()-1; i >= 0; i--) {
                if (layers.get(i) instanceof OsmDataLayer) {
                    if (i == layers.size()-1) {
                        layers.add(layer);
                    } else {
                        layers.add(i+1, layer);
                    }
                    return;
                }
            }
            layers.add(0, layer);
        } finally {
            layerLock.writeLock().unlock();
        }
    }

    /**
     * Add a layer to the current MapView. The layer will be added at topmost
     * position.
     * @param layer The layer to add
     */
    public void addLayer(Layer layer) {
        boolean isOsmDataLayer = layer instanceof OsmDataLayer;
        layerLock.writeLock().lock();
        layerLock.readLock().lock();
        EnumSet<LayerListenerType> listenersToFire = EnumSet.noneOf(LayerListenerType.class);
        Layer oldActiveLayer = activeLayer;
        OsmDataLayer oldEditLayer = editLayer;
        try {
            try {
                if (layer instanceof MarkerLayer && playHeadMarker == null) {
                    playHeadMarker = PlayHeadMarker.create();
                }

                if (layer instanceof GpxLayer) {
                    addGpxLayer((GpxLayer) layer);
                } else if (layers.isEmpty()) {
                    layers.add(layer);
                } else if (layer.isBackgroundLayer()) {
                    int i = 0;
                    for (; i < layers.size(); i++) {
                        if (layers.get(i).isBackgroundLayer()) {
                            break;
                        }
                    }
                    layers.add(i, layer);
                } else {
                    layers.add(0, layer);
                }

                if (isOsmDataLayer || oldActiveLayer == null) {
                    // autoselect the new layer
                    listenersToFire.addAll(setActiveLayer(layer, true));
                }
            } finally {
                layerLock.writeLock().unlock();
            }

            fireLayerAdded(layer);
            if (isOsmDataLayer) {
                ((OsmDataLayer) layer).addLayerStateChangeListener(this);
            }
            onActiveEditLayerChanged(oldActiveLayer, oldEditLayer, listenersToFire);
            layer.addPropertyChangeListener(this);
            Main.addProjectionChangeListener(layer);
            AudioPlayer.reset();
        } finally {
            layerLock.readLock().unlock();
        }
        if (!listenersToFire.isEmpty()) {
            repaint();
        }
    }

    @Override
    protected DataSet getCurrentDataSet() {
        layerLock.readLock().lock();
        try {
            if (editLayer != null)
                return editLayer.data;
            else
                return null;
        } finally {
            layerLock.readLock().unlock();
        }
    }

    /**
     * Replies true if the active data layer (edit layer) is drawable.
     *
     * @return true if the active data layer (edit layer) is drawable, false otherwise
     */
    public boolean isActiveLayerDrawable() {
        layerLock.readLock().lock();
        try {
            return editLayer != null;
        } finally {
            layerLock.readLock().unlock();
        }
    }

    /**
     * Replies true if the active data layer (edit layer) is visible.
     *
     * @return true if the active data layer (edit layer) is visible, false otherwise
     */
    public boolean isActiveLayerVisible() {
        layerLock.readLock().lock();
        try {
            return isActiveLayerDrawable() && editLayer.isVisible();
        } finally {
            layerLock.readLock().unlock();
        }
    }

    /**
     * Determines the next active data layer according to the following
     * rules:
     * <ul>
     *   <li>if there is at least one {@link OsmDataLayer} the first one
     *     becomes active</li>
     *   <li>otherwise, the top most layer of any type becomes active</li>
     * </ul>
     *
     * @return the next active data layer
     */
    protected Layer determineNextActiveLayer(List<Layer> layersList) {
        // First look for data layer
        for (Layer layer:layersList) {
            if (layer instanceof OsmDataLayer)
                return layer;
        }

        // Then any layer
        if (!layersList.isEmpty())
            return layersList.get(0);

        // and then give up
        return null;

    }

    /**
     * Remove the layer from the mapview. If the layer was in the list before,
     * an LayerChange event is fired.
     * @param layer The layer to remove
     */
    public void removeLayer(Layer layer) {
        layerLock.writeLock().lock();
        layerLock.readLock().lock();

        EnumSet<LayerListenerType> listenersToFire = EnumSet.noneOf(LayerListenerType.class);
        Layer oldActiveLayer = activeLayer;
        OsmDataLayer oldEditLayer = editLayer;
        try {
            try {
                List<Layer> layersList = new ArrayList<>(layers);

                if (!layersList.remove(layer))
                    return;

                listenersToFire = setEditLayer(layersList);

                if (layer == activeLayer) {
                    listenersToFire.addAll(setActiveLayer(determineNextActiveLayer(layersList), false));
                }

                if (layer instanceof OsmDataLayer) {
                    ((OsmDataLayer) layer).removeLayerPropertyChangeListener(this);
                }

                layers.remove(layer);
                Main.removeProjectionChangeListener(layer);

            } finally {
                layerLock.writeLock().unlock();
            }
            onActiveEditLayerChanged(oldActiveLayer, oldEditLayer, listenersToFire);
            fireLayerRemoved(layer);
            layer.removePropertyChangeListener(this);
            layer.destroy();
            AudioPlayer.reset();
        } finally {
            layerLock.readLock().unlock();
        }
        repaint();
    }

    private void onEditLayerChanged(OsmDataLayer oldEditLayer) {
        fireEditLayerChanged(oldEditLayer, editLayer);
        refreshTitle();
    }

    private boolean virtualNodesEnabled = false;

    public void setVirtualNodesEnabled(boolean enabled) {
        if (virtualNodesEnabled != enabled) {
            virtualNodesEnabled = enabled;
            repaint();
        }
    }

    /**
     * Checks if virtual nodes should be drawn. Default is <code>false</code>
     * @return The virtual nodes property.
     * @see Rendering#render(DataSet, boolean, Bounds)
     */
    public boolean isVirtualNodesEnabled() {
        return virtualNodesEnabled;
    }

    /**
     * Moves the layer to the given new position. No event is fired, but repaints
     * according to the new Z-Order of the layers.
     *
     * @param layer     The layer to move
     * @param pos       The new position of the layer
     */
    public void moveLayer(Layer layer, int pos) {
        layerLock.writeLock().lock();
        layerLock.readLock().lock();
        EnumSet<LayerListenerType> listenersToFire;
        Layer oldActiveLayer = activeLayer;
        OsmDataLayer oldEditLayer = editLayer;
        try {
            try {
                int curLayerPos = layers.indexOf(layer);
                if (curLayerPos == -1)
                    throw new IllegalArgumentException(tr("Layer not in list."));
                if (pos == curLayerPos)
                    return; // already in place.
                layers.remove(curLayerPos);
                if (pos >= layers.size()) {
                    layers.add(layer);
                } else {
                    layers.add(pos, layer);
                }
                listenersToFire = setEditLayer(layers);
            } finally {
                layerLock.writeLock().unlock();
            }
            onActiveEditLayerChanged(oldActiveLayer, oldEditLayer, listenersToFire);
            AudioPlayer.reset();
        } finally {
            layerLock.readLock().unlock();
        }
        repaint();
    }

    /**
     * Gets the index of the layer in the layer list.
     * @param layer The layer to search for.
     * @return The index in the list.
     * @throws IllegalArgumentException if that layer does not belong to this view.
     */
    public int getLayerPos(Layer layer) {
        int curLayerPos;
        layerLock.readLock().lock();
        try {
            curLayerPos = layers.indexOf(layer);
        } finally {
            layerLock.readLock().unlock();
        }
        if (curLayerPos == -1)
            throw new IllegalArgumentException(tr("Layer not in list."));
        return curLayerPos;
    }

    /**
     * Creates a list of the visible layers in Z-Order, the layer with the lowest Z-Order
     * first, layer with the highest Z-Order last.
     *
     * @return a list of the visible in Z-Order, the layer with the lowest Z-Order
     * first, layer with the highest Z-Order last.
     */
    public List<Layer> getVisibleLayersInZOrder() {
        layerLock.readLock().lock();
        try {
            List<Layer> ret = new ArrayList<>();
            for (Layer l: layers) {
                if (l.isVisible()) {
                    ret.add(l);
                }
            }
            // sort according to position in the list of layers, with one exception:
            // an active data layer always becomes a higher Z-Order than all other data layers
            Collections.sort(
                    ret,
                    new Comparator<Layer>() {
                        @Override
                        public int compare(Layer l1, Layer l2) {
                            if (l1 instanceof OsmDataLayer && l2 instanceof OsmDataLayer) {
                                if (l1 == getActiveLayer()) return -1;
                                if (l2 == getActiveLayer()) return 1;
                                return Integer.compare(layers.indexOf(l1), layers.indexOf(l2));
                            } else
                                return Integer.compare(layers.indexOf(l1), layers.indexOf(l2));
                        }
                    }
            );
            Collections.reverse(ret);
            return ret;
        } finally {
            layerLock.readLock().unlock();
        }
    }

    private void paintLayer(Layer layer, Graphics2D g, Bounds box) {
        if (layer.getOpacity() < 1) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) layer.getOpacity()));
        }
        layer.paint(g, this, box);
        g.setPaintMode();
    }

    /**
     * Draw the component.
     */
    @Override
    public void paint(Graphics g) {
        if (!prepareToDraw()) {
            return;
        }

        List<Layer> visibleLayers = getVisibleLayersInZOrder();

        int nonChangedLayersCount = 0;
        for (Layer l: visibleLayers) {
            if (l.isChanged() || l == changedLayer) {
                break;
            } else {
                nonChangedLayersCount++;
            }
        }

        boolean canUseBuffer;

        synchronized (this) {
            canUseBuffer = !paintPreferencesChanged;
            paintPreferencesChanged = false;
        }
        canUseBuffer = canUseBuffer && nonChangedLayers.size() <= nonChangedLayersCount &&
        lastViewID == getViewID() && lastClipBounds.contains(g.getClipBounds());
        if (canUseBuffer) {
            for (int i = 0; i < nonChangedLayers.size(); i++) {
                if (visibleLayers.get(i) != nonChangedLayers.get(i)) {
                    canUseBuffer = false;
                    break;
                }
            }
        }

        if (null == offscreenBuffer || offscreenBuffer.getWidth() != getWidth() || offscreenBuffer.getHeight() != getHeight()) {
            offscreenBuffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        }

        Graphics2D tempG = offscreenBuffer.createGraphics();
        tempG.setClip(g.getClip());
        Bounds box = getLatLonBounds(g.getClipBounds());

        if (!canUseBuffer || nonChangedLayersBuffer == null) {
            if (null == nonChangedLayersBuffer
                    || nonChangedLayersBuffer.getWidth() != getWidth() || nonChangedLayersBuffer.getHeight() != getHeight()) {
                nonChangedLayersBuffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            }
            Graphics2D g2 = nonChangedLayersBuffer.createGraphics();
            g2.setClip(g.getClip());
            g2.setColor(PaintColors.getBackgroundColor());
            g2.fillRect(0, 0, getWidth(), getHeight());

            for (int i = 0; i < nonChangedLayersCount; i++) {
                paintLayer(visibleLayers.get(i), g2, box);
            }
        } else {
            // Maybe there were more unchanged layers then last time - draw them to buffer
            if (nonChangedLayers.size() != nonChangedLayersCount) {
                Graphics2D g2 = nonChangedLayersBuffer.createGraphics();
                g2.setClip(g.getClip());
                for (int i = nonChangedLayers.size(); i < nonChangedLayersCount; i++) {
                    paintLayer(visibleLayers.get(i), g2, box);
                }
            }
        }

        nonChangedLayers.clear();
        changedLayer = null;
        for (int i = 0; i < nonChangedLayersCount; i++) {
            nonChangedLayers.add(visibleLayers.get(i));
        }
        lastViewID = getViewID();
        lastClipBounds = g.getClipBounds();

        tempG.drawImage(nonChangedLayersBuffer, 0, 0, null);

        for (int i = nonChangedLayersCount; i < visibleLayers.size(); i++) {
            paintLayer(visibleLayers.get(i), tempG, box);
        }

        synchronized (temporaryLayers) {
            for (MapViewPaintable mvp : temporaryLayers) {
                mvp.paint(tempG, this, box);
            }
        }

        // draw world borders
        tempG.setColor(Color.WHITE);
        Bounds b = getProjection().getWorldBoundsLatLon();
        double lat = b.getMinLat();
        double lon = b.getMinLon();

        Point p = getPoint(b.getMin());

        GeneralPath path = new GeneralPath();

        path.moveTo(p.x, p.y);
        double max = b.getMax().lat();
        for (; lat <= max; lat += 1.0) {
            p = getPoint(new LatLon(lat >= max ? max : lat, lon));
            path.lineTo(p.x, p.y);
        }
        lat = max; max = b.getMax().lon();
        for (; lon <= max; lon += 1.0) {
            p = getPoint(new LatLon(lat, lon >= max ? max : lon));
            path.lineTo(p.x, p.y);
        }
        lon = max; max = b.getMinLat();
        for (; lat >= max; lat -= 1.0) {
            p = getPoint(new LatLon(lat <= max ? max : lat, lon));
            path.lineTo(p.x, p.y);
        }
        lat = max; max = b.getMinLon();
        for (; lon >= max; lon -= 1.0) {
            p = getPoint(new LatLon(lat, lon <= max ? max : lon));
            path.lineTo(p.x, p.y);
        }

        int w = getWidth();
        int h = getHeight();

        // Work around OpenJDK having problems when drawing out of bounds
        final Area border = new Area(path);
        // Make the viewport 1px larger in every direction to prevent an
        // additional 1px border when zooming in
        final Area viewport = new Area(new Rectangle(-1, -1, w + 2, h + 2));
        border.intersect(viewport);
        tempG.draw(border);

        if (Main.isDisplayingMapView() && Main.map.filterDialog != null) {
            Main.map.filterDialog.drawOSDText(tempG);
        }

        if (playHeadMarker != null) {
            playHeadMarker.paint(tempG, this);
        }

        g.drawImage(offscreenBuffer, 0, 0, null);
        super.paint(g);
    }

    /**
     * Sets up the viewport to prepare for drawing the view.
     * @return <code>true</code> if the view can be drawn, <code>false</code> otherwise.
     */
    public boolean prepareToDraw() {
        if (initialViewport != null) {
            zoomTo(initialViewport);
            initialViewport = null;
        }
        if (BugReportExceptionHandler.exceptionHandlingInProgress())
            return false;

        if (getCenter() == null)
            return false; // no data loaded yet.

        // if the position was remembered, we need to adjust center once before repainting
        if (oldLoc != null && oldSize != null) {
            Point l1  = getLocationOnScreen();
            final EastNorth newCenter = new EastNorth(
                    getCenter().getX()+ (l1.x-oldLoc.x - (oldSize.width-getWidth())/2.0)*getScale(),
                    getCenter().getY()+ (oldLoc.y-l1.y + (oldSize.height-getHeight())/2.0)*getScale()
                    );
            oldLoc = null; oldSize = null;
            zoomTo(newCenter);
        }

        return true;
    }

    /**
     * @return An unmodifiable collection of all layers
     */
    public Collection<Layer> getAllLayers() {
        layerLock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new ArrayList<>(layers));
        } finally {
            layerLock.readLock().unlock();
        }
    }

    /**
     * @return An unmodifiable ordered list of all layers
     */
    public List<Layer> getAllLayersAsList() {
        layerLock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(layers));
        } finally {
            layerLock.readLock().unlock();
        }
    }

    /**
     * Replies an unmodifiable list of layers of a certain type.
     *
     * Example:
     * <pre>
     *     List&lt;WMSLayer&gt; wmsLayers = getLayersOfType(WMSLayer.class);
     * </pre>
     *
     * @param ofType The layer type.
     * @return an unmodifiable list of layers of a certain type.
     */
    public <T extends Layer> List<T> getLayersOfType(Class<T> ofType) {
        return new ArrayList<>(Utils.filteredCollection(getAllLayers(), ofType));
    }

    /**
     * Replies the number of layers managed by this map view
     *
     * @return the number of layers managed by this map view
     */
    public int getNumLayers() {
        layerLock.readLock().lock();
        try {
            return layers.size();
        } finally {
            layerLock.readLock().unlock();
        }
    }

    /**
     * Replies true if there is at least one layer in this map view
     *
     * @return true if there is at least one layer in this map view
     */
    public boolean hasLayers() {
        return getNumLayers() > 0;
    }

    /**
     * Sets the active edit layer.
     * <p>
     * You must own a write {@link #layerLock} when calling this method.
     * @param layersList A list to select that layer from.
     * @return A list of change listeners that should be fired using {@link #onActiveEditLayerChanged(Layer, OsmDataLayer, EnumSet)}
     */
    private EnumSet<LayerListenerType> setEditLayer(List<Layer> layersList) {
        final OsmDataLayer newEditLayer = findNewEditLayer(layersList);

        // Set new edit layer
        if (newEditLayer != editLayer) {
            if (newEditLayer == null) {
                // Note: Unsafe to call while layer write lock is held.
                getCurrentDataSet().setSelected();
            }

            editLayer = newEditLayer;
            return EnumSet.of(LayerListenerType.EDIT_LAYER_CHANGE);
        } else {
            return EnumSet.noneOf(LayerListenerType.class);
        }

    }

    private OsmDataLayer findNewEditLayer(List<Layer> layersList) {
        OsmDataLayer newEditLayer = layersList.contains(editLayer) ? editLayer : null;
        // Find new edit layer
        if (activeLayer != editLayer || !layersList.contains(editLayer)) {
            if (activeLayer instanceof OsmDataLayer && layersList.contains(activeLayer)) {
                newEditLayer = (OsmDataLayer) activeLayer;
            } else {
                for (Layer layer:layersList) {
                    if (layer instanceof OsmDataLayer) {
                        newEditLayer = (OsmDataLayer) layer;
                        break;
                    }
                }
            }
        }
        return newEditLayer;
    }

    /**
     * Sets the active layer to <code>layer</code>. If <code>layer</code> is an instance
     * of {@link OsmDataLayer} also sets {@link #editLayer} to <code>layer</code>.
     *
     * @param layer the layer to be activate; must be one of the layers in the list of layers
     * @throws IllegalArgumentException if layer is not in the lis of layers
     */
    public void setActiveLayer(Layer layer) {
        layerLock.writeLock().lock();
        layerLock.readLock().lock();
        EnumSet<LayerListenerType> listenersToFire;
        Layer oldActiveLayer = activeLayer;
        OsmDataLayer oldEditLayer = editLayer;
        try {
            try {
                listenersToFire = setActiveLayer(layer, true);
            } finally {
                layerLock.writeLock().unlock();
            }
            onActiveEditLayerChanged(oldActiveLayer, oldEditLayer, listenersToFire);
        } finally {
            layerLock.readLock().unlock();
        }
        repaint();
    }

    /**
     * Sets the active layer. Propagates this change to all map buttons.
     * @param layer The layer to be active.
     * @param setEditLayer if this is <code>true</code>, the edit layer is also set.
     * @return A list of change listeners that should be fired using {@link #onActiveEditLayerChanged(Layer, OsmDataLayer, EnumSet)}
     */
    private EnumSet<LayerListenerType> setActiveLayer(final Layer layer, boolean setEditLayer) {
        if (layer != null && !layers.contains(layer))
            throw new IllegalArgumentException(tr("Layer ''{0}'' must be in list of layers", layer.toString()));

        if (layer == activeLayer)
            return EnumSet.noneOf(LayerListenerType.class);

        activeLayer = layer;
        EnumSet<LayerListenerType> listenersToFire = EnumSet.of(LayerListenerType.ACTIVE_LAYER_CHANGE);
        if (setEditLayer) {
            listenersToFire.addAll(setEditLayer(layers));
        }

        return listenersToFire;
    }

    /**
     * Replies the currently active layer
     *
     * @return the currently active layer (may be null)
     */
    public Layer getActiveLayer() {
        layerLock.readLock().lock();
        try {
            return activeLayer;
        } finally {
            layerLock.readLock().unlock();
        }
    }

    private enum LayerListenerType {
        ACTIVE_LAYER_CHANGE,
        EDIT_LAYER_CHANGE
    }

    /**
     * This is called whenever one of active layer/edit layer or both may have been changed,
     * @param oldActive The old active layer
     * @param oldEdit The old edit layer.
     * @param listenersToFire A mask of listeners to fire using {@link LayerListenerType}s
     */
    private void onActiveEditLayerChanged(final Layer oldActive, final OsmDataLayer oldEdit, EnumSet<LayerListenerType> listenersToFire) {
        if (listenersToFire.contains(LayerListenerType.EDIT_LAYER_CHANGE)) {
            onEditLayerChanged(oldEdit);
        }
        if (listenersToFire.contains(LayerListenerType.ACTIVE_LAYER_CHANGE)) {
            onActiveLayerChanged(oldActive);
        }
    }

    private void onActiveLayerChanged(final Layer old) {
        fireActiveLayerChanged(old, activeLayer);

        /* This only makes the buttons look disabled. Disabling the actions as well requires
         * the user to re-select the tool after i.e. moving a layer. While testing I found
         * that I switch layers and actions at the same time and it was annoying to mind the
         * order. This way it works as visual clue for new users */
        for (final AbstractButton b: Main.map.allMapModeButtons) {
            MapMode mode = (MapMode) b.getAction();
            final boolean activeLayerSupported = mode.layerIsSupported(activeLayer);
            if (activeLayerSupported) {
                Main.registerActionShortcut(mode, mode.getShortcut()); //fix #6876
            } else {
                Main.unregisterShortcut(mode.getShortcut());
            }
            GuiHelper.runInEDTAndWait(new Runnable() {
                @Override public void run() {
                    b.setEnabled(activeLayerSupported);
                }
            });
        }
        AudioPlayer.reset();
        repaint();
    }

    /**
     * Replies the current edit layer, if any
     *
     * @return the current edit layer. May be null.
     */
    public OsmDataLayer getEditLayer() {
        layerLock.readLock().lock();
        try {
            return editLayer;
        } finally {
            layerLock.readLock().unlock();
        }
    }

    /**
     * replies true if the list of layers managed by this map view contain layer
     *
     * @param layer the layer
     * @return true if the list of layers managed by this map view contain layer
     */
    public boolean hasLayer(Layer layer) {
        layerLock.readLock().lock();
        try {
            return layers.contains(layer);
        } finally {
            layerLock.readLock().unlock();
        }
    }

    /**
     * Adds a new temporary layer.
     * <p>
     * A temporary layer is a layer that is painted above all normal layers. Layers are painted in the order they are added.
     *
     * @param mvp The layer to paint.
     * @return <code>true</code> if the layer was added.
     */
    public boolean addTemporaryLayer(MapViewPaintable mvp) {
        synchronized (temporaryLayers) {
            return temporaryLayers.add(mvp);
        }
    }

    /**
     * Removes a layer previously added as temporary layer.
     * @param mvp The layer to remove.
     * @return <code>true</code> if that layer was removed.
     */
    public boolean removeTemporaryLayer(MapViewPaintable mvp) {
        synchronized (temporaryLayers) {
            return temporaryLayers.remove(mvp);
        }
    }

    /**
     * Gets a list of temporary layers.
     * @return The layers in the order they are added.
     */
    public List<MapViewPaintable> getTemporaryLayers() {
        synchronized (temporaryLayers) {
            return Collections.unmodifiableList(new ArrayList<>(temporaryLayers));
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(Layer.VISIBLE_PROP)) {
            repaint();
        } else if (evt.getPropertyName().equals(Layer.OPACITY_PROP)) {
            Layer l = (Layer) evt.getSource();
            if (l.isVisible()) {
                changedLayer = l;
                repaint();
            }
        } else if (evt.getPropertyName().equals(OsmDataLayer.REQUIRES_SAVE_TO_DISK_PROP)
                || evt.getPropertyName().equals(OsmDataLayer.REQUIRES_UPLOAD_TO_SERVER_PROP)) {
            OsmDataLayer layer = (OsmDataLayer) evt.getSource();
            if (layer == getEditLayer()) {
                refreshTitle();
            }
        }
    }

    /**
     * Sets the title of the JOSM main window, adding a star if there are dirty layers.
     * @see Main#parent
     */
    protected void refreshTitle() {
        if (Main.parent != null) {
            layerLock.readLock().lock();
            try {
                boolean dirty = editLayer != null &&
                        (editLayer.requiresSaveToFile() || (editLayer.requiresUploadToServer() && !editLayer.isUploadDiscouraged()));
                ((JFrame) Main.parent).setTitle((dirty ? "* " : "") + tr("Java OpenStreetMap Editor"));
                ((JFrame) Main.parent).getRootPane().putClientProperty("Window.documentModified", dirty);
            } finally {
                layerLock.readLock().unlock();
            }
        }
    }

    @Override
    public void preferenceChanged(PreferenceChangeEvent e) {
        synchronized (this) {
            paintPreferencesChanged = true;
        }
    }

    private transient SelectionChangedListener repaintSelectionChangedListener = new SelectionChangedListener() {
        @Override
        public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
            repaint();
        }
    };

    public void destroy() {
        Main.pref.removePreferenceChangeListener(this);
        DataSet.removeSelectionListener(repaintSelectionChangedListener);
        MultipolygonCache.getInstance().clear(this);
        if (mapMover != null) {
            mapMover.destroy();
        }
        layerLock.writeLock().lock();
        try {
            activeLayer = null;
            changedLayer = null;
            editLayer = null;
            layers.clear();
        } finally {
            layerLock.writeLock().unlock();
        }
        nonChangedLayers.clear();
        synchronized (temporaryLayers) {
            temporaryLayers.clear();
        }
    }

    @Override
    public void uploadDiscouragedChanged(OsmDataLayer layer, boolean newValue) {
        if (layer == getEditLayer()) {
            refreshTitle();
        }
    }

    /**
     * Get a string representation of all layers suitable for the {@code source} changeset tag.
     * @return A String of sources separated by ';'
     */
    public String getLayerInformationForSourceTag() {
        final Collection<String> layerInfo = new ArrayList<>();
        if (!getLayersOfType(GpxLayer.class).isEmpty()) {
            // no i18n for international values
            layerInfo.add("survey");
        }
        for (final GeoImageLayer i : getLayersOfType(GeoImageLayer.class)) {
            layerInfo.add(i.getName());
        }
        for (final ImageryLayer i : getLayersOfType(ImageryLayer.class)) {
            layerInfo.add(ImageryInfo.ImageryType.BING.equals(i.getInfo().getImageryType()) ? "Bing" : i.getName());
        }
        return Utils.join("; ", layerInfo);
    }

    /**
     * This is a listener that gets informed whenever repaint is called for this MapView.
     * <p>
     * This is the only safe method to find changes to the map view, since many components call MapView.repaint() directly.
     * @author Michael Zangl
     */
    public interface RepaintListener {
        /**
         * Called when any repaint method is called (using default arguments if required).
         * @param tm see {@link JComponent#repaint(long, int, int, int, int)}
         * @param x see {@link JComponent#repaint(long, int, int, int, int)}
         * @param y see {@link JComponent#repaint(long, int, int, int, int)}
         * @param width see {@link JComponent#repaint(long, int, int, int, int)}
         * @param height see {@link JComponent#repaint(long, int, int, int, int)}
         */
        void repaint(long tm, int x, int y, int width, int height);
    }

    private final CopyOnWriteArrayList<RepaintListener> repaintListeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a listener that gets informed whenever repaint() is called for this class.
     * @param l The listener.
     */
    public void addRepaintListener(RepaintListener l) {
        repaintListeners.add(l);
    }

    /**
     * Removes a registered repaint listener.
     * @param l The listener.
     */
    public void removeRepaintListener(RepaintListener l) {
        repaintListeners.remove(l);
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
        // This is the main repaint method, all other methods are convenience methods and simply call this method.
        // This is just an observation, not a must, but seems to be true for all implementations I found so far.
        if (repaintListeners != null) {
            // Might get called early in super constructor
            for (RepaintListener l : repaintListeners) {
                l.repaint(tm, x, y, width, height);
            }
        }
        super.repaint(tm, x, y, width, height);
    }
}
