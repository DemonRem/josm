// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.openstreetmap.gui.jmapviewer.FeatureAdapter;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.OpenFileAction;
import org.openstreetmap.josm.actions.OpenLocationAction;
import org.openstreetmap.josm.actions.downloadtasks.DownloadGpsTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadTask;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.actions.mapmode.DrawAction;
import org.openstreetmap.josm.actions.search.SearchAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.cache.JCSCacheManager;
import org.openstreetmap.josm.data.coor.CoordinateFormat;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.ProjectionChangeListener;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MainPanel;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapFrameListener;
import org.openstreetmap.josm.gui.ProgramArguments;
import org.openstreetmap.josm.gui.ProgramArguments.Option;
import org.openstreetmap.josm.gui.io.SaveLayersDialog;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer.CommandQueueListener;
import org.openstreetmap.josm.gui.layer.TMSLayer;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.preferences.display.LafPreference;
import org.openstreetmap.josm.gui.preferences.imagery.ImageryPreference;
import org.openstreetmap.josm.gui.preferences.map.MapPaintPreference;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitorExecutor;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.RedirectInputMap;
import org.openstreetmap.josm.io.FileWatcher;
import org.openstreetmap.josm.io.OnlineResource;
import org.openstreetmap.josm.io.OsmApi;
import org.openstreetmap.josm.io.OsmApiInitializationException;
import org.openstreetmap.josm.io.OsmTransferCanceledException;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.OpenBrowser;
import org.openstreetmap.josm.tools.OsmUrlToBounds;
import org.openstreetmap.josm.tools.OverpassTurboQueryWizard;
import org.openstreetmap.josm.tools.PlatformHook;
import org.openstreetmap.josm.tools.PlatformHookOsx;
import org.openstreetmap.josm.tools.PlatformHookUnixoid;
import org.openstreetmap.josm.tools.PlatformHookWindows;
import org.openstreetmap.josm.tools.RightAndLefthandTraffic;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Territories;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.bugreport.BugReport;

/**
 * Abstract class holding various static global variables and methods used in large parts of JOSM application.
 * @since 98
 */
public abstract class Main {

    /**
     * The JOSM website URL.
     * @since 6897 (was public from 6143 to 6896)
     */
    private static final String JOSM_WEBSITE = "https://josm.openstreetmap.de";

    /**
     * The OSM website URL.
     * @since 6897 (was public from 6453 to 6896)
     */
    private static final String OSM_WEBSITE = "https://www.openstreetmap.org";

    /**
     * Replies true if JOSM currently displays a map view. False, if it doesn't, i.e. if
     * it only shows the MOTD panel.
     * <p>
     * You do not need this when accessing the layer manager. The layer manager will be empty if no map view is shown.
     *
     * @return <code>true</code> if JOSM currently displays a map view
     */
    public static boolean isDisplayingMapView() {
        return map != null && map.mapView != null;
    }

    /**
     * Global parent component for all dialogs and message boxes
     */
    public static Component parent;

    /**
     * Global application.
     */
    public static volatile Main main;

    /**
     * The worker thread slave. This is for executing all long and intensive
     * calculations. The executed runnables are guaranteed to be executed separately
     * and sequential.
     */
    public static final ExecutorService worker = new ProgressMonitorExecutor("main-worker-%d", Thread.NORM_PRIORITY);

    /**
     * Global application preferences
     */
    public static final Preferences pref = new Preferences();

    /**
     * The MapFrame.
     * <p>
     * There should be no need to access this to access any map data. Use {@link #layerManager} instead.
     *
     * @see MainPanel
     */
    public static MapFrame map;

    /**
     * Provides access to the layers displayed in the main view.
     * @since 10271
     */
    private static final MainLayerManager layerManager = new MainLayerManager();

    /**
     * The toolbar preference control to register new actions.
     */
    public static volatile ToolbarPreferences toolbar;

    /**
     * The commands undo/redo handler.
     */
    public final UndoRedoHandler undoRedo = new UndoRedoHandler();

    /**
     * The progress monitor being currently displayed.
     */
    public static PleaseWaitProgressMonitor currentProgressMonitor;

    /**
     * The main menu bar at top of screen.
     */
    public MainMenu menu;

    /**
     * The main panel.
     * @since 12125
     */
    public MainPanel panel;

    /**
     * The same main panel, required to be static for {@code MapFrameListener} handling.
     */
    protected static MainPanel mainPanel;

    /**
     * The private content pane of {@code MainFrame}, required to be static for shortcut handling.
     */
    protected static JComponent contentPanePrivate;

    /**
     * The file watcher service.
     */
    public static final FileWatcher fileWatcher = new FileWatcher();

    private static final Map<String, Throwable> NETWORK_ERRORS = new HashMap<>();

    private static final Set<OnlineResource> OFFLINE_RESOURCES = EnumSet.noneOf(OnlineResource.class);

    /**
     * Logging level (5 = trace, 4 = debug, 3 = info, 2 = warn, 1 = error, 0 = none).
     * @since 6248
     * @deprecated Use {@link Logging} class.
     */
    @Deprecated
    public static int logLevel = 3;

    /**
     * Replies the first lines of last 5 error and warning messages, used for bug reports
     * @return the first lines of last 5 error and warning messages
     * @since 7420
     */
    public static final Collection<String> getLastErrorAndWarnings() {
        return Logging.getLastErrorAndWarnings();
    }

    /**
     * Clears the list of last error and warning messages.
     * @since 8959
     */
    public static void clearLastErrorAndWarnings() {
        Logging.clearLastErrorAndWarnings();
    }

    /**
     * Prints an error message if logging is on.
     * @param msg The message to print.
     * @since 6248
     */
    public static void error(String msg) {
        Logging.error(msg);
    }

    /**
     * Prints a warning message if logging is on.
     * @param msg The message to print.
     */
    public static void warn(String msg) {
        Logging.warn(msg);
    }

    /**
     * Prints an informational message if logging is on.
     * @param msg The message to print.
     */
    public static void info(String msg) {
        Logging.info(msg);
    }

    /**
     * Prints a debug message if logging is on.
     * @param msg The message to print.
     */
    public static void debug(String msg) {
        Logging.debug(msg);
    }

    /**
     * Prints a trace message if logging is on.
     * @param msg The message to print.
     */
    public static void trace(String msg) {
        Logging.trace(msg);
    }

    /**
     * Determines if debug log level is enabled.
     * Useful to avoid costly construction of debug messages when not enabled.
     * @return {@code true} if log level is at least debug, {@code false} otherwise
     * @since 6852
     */
    public static boolean isDebugEnabled() {
        return Logging.isLoggingEnabled(Logging.LEVEL_DEBUG);
    }

    /**
     * Determines if trace log level is enabled.
     * Useful to avoid costly construction of trace messages when not enabled.
     * @return {@code true} if log level is at least trace, {@code false} otherwise
     * @since 6852
     */
    public static boolean isTraceEnabled() {
        return Logging.isLoggingEnabled(Logging.LEVEL_TRACE);
    }

    /**
     * Prints a formatted error message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param msg The formatted message to print.
     * @param objects The objects to insert into format string.
     * @since 6248
     */
    public static void error(String msg, Object... objects) {
        Logging.error(msg, objects);
    }

    /**
     * Prints a formatted warning message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param msg The formatted message to print.
     * @param objects The objects to insert into format string.
     */
    public static void warn(String msg, Object... objects) {
        Logging.warn(msg, objects);
    }

    /**
     * Prints a formatted informational message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param msg The formatted message to print.
     * @param objects The objects to insert into format string.
     */
    public static void info(String msg, Object... objects) {
        Logging.info(msg, objects);
    }

    /**
     * Prints a formatted debug message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param msg The formatted message to print.
     * @param objects The objects to insert into format string.
     */
    public static void debug(String msg, Object... objects) {
        Logging.debug(msg, objects);
    }

    /**
     * Prints a formatted trace message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param msg The formatted message to print.
     * @param objects The objects to insert into format string.
     */
    public static void trace(String msg, Object... objects) {
        Logging.trace(msg, objects);
    }

    /**
     * Prints an error message for the given Throwable.
     * @param t The throwable object causing the error
     * @since 6248
     */
    public static void error(Throwable t) {
        Logging.logWithStackTrace(Logging.LEVEL_ERROR, t);
    }

    /**
     * Prints a warning message for the given Throwable.
     * @param t The throwable object causing the error
     * @since 6248
     */
    public static void warn(Throwable t) {
        Logging.logWithStackTrace(Logging.LEVEL_WARN, t);
    }

    /**
     * Prints a debug message for the given Throwable. Useful for exceptions usually ignored
     * @param t The throwable object causing the error
     * @since 10420
     */
    public static void debug(Throwable t) {
        Logging.log(Logging.LEVEL_DEBUG, t);
    }

    /**
     * Prints a trace message for the given Throwable. Useful for exceptions usually ignored
     * @param t The throwable object causing the error
     * @since 10420
     */
    public static void trace(Throwable t) {
        Logging.log(Logging.LEVEL_TRACE, t);
    }

    /**
     * Prints an error message for the given Throwable.
     * @param t The throwable object causing the error
     * @param stackTrace {@code true}, if the stacktrace should be displayed
     * @since 6642
     */
    public static void error(Throwable t, boolean stackTrace) {
        if (stackTrace) {
            Logging.log(Logging.LEVEL_ERROR, t);
        } else {
            Logging.logWithStackTrace(Logging.LEVEL_ERROR, t);
        }
    }

    /**
     * Prints an error message for the given Throwable.
     * @param t The throwable object causing the error
     * @param message additional error message
     * @since 10420
     */
    public static void error(Throwable t, String message) {
        Logging.log(Logging.LEVEL_ERROR, message, t);
    }

    /**
     * Prints a warning message for the given Throwable.
     * @param t The throwable object causing the error
     * @param stackTrace {@code true}, if the stacktrace should be displayed
     * @since 6642
     */
    public static void warn(Throwable t, boolean stackTrace) {
        if (stackTrace) {
            Logging.log(Logging.LEVEL_WARN, t);
        } else {
            Logging.logWithStackTrace(Logging.LEVEL_WARN, t);
        }
    }

    /**
     * Prints a warning message for the given Throwable.
     * @param t The throwable object causing the error
     * @param message additional error message
     * @since 10420
     */
    public static void warn(Throwable t, String message) {
        Logging.log(Logging.LEVEL_WARN, message, t);
    }

    /**
     * Returns a human-readable message of error, also usable for developers.
     * @param t The error
     * @return The human-readable error message
     * @since 6642
     */
    public static String getErrorMessage(Throwable t) {
        if (t == null) {
            return null;
        } else {
            return Logging.getErrorMessage(t);
        }
    }

    /**
     * Platform specific code goes in here.
     * Plugins may replace it, however, some hooks will be called before any plugins have been loeaded.
     * So if you need to hook into those early ones, split your class and send the one with the early hooks
     * to the JOSM team for inclusion.
     */
    public static volatile PlatformHook platform;

    private static volatile InitStatusListener initListener;

    /**
     * Initialization task listener.
     */
    public interface InitStatusListener {

        /**
         * Called when an initialization task updates its status.
         * @param event task name
         * @return new status
         */
        Object updateStatus(String event);

        /**
         * Called when an initialization task completes.
         * @param status final status
         */
        void finish(Object status);
    }

    /**
     * Sets initialization task listener.
     * @param listener initialization task listener
     */
    public static void setInitStatusListener(InitStatusListener listener) {
        CheckParameterUtil.ensureParameterNotNull(listener);
        initListener = listener;
    }

    /**
     * Constructs new {@code Main} object.
     * @see #initialize()
     */
    protected Main() {
        setInstance(this);
    }

    private static void setInstance(Main instance) {
        main = instance;
    }

    /**
     * Initializes the main object. A lot of global variables are initialized here.
     * @since 10340
     */
    public void initialize() {
        fileWatcher.start();

        new InitializationTask(tr("Executing platform startup hook"), platform::startupHook).call();

        new InitializationTask(tr("Building main menu"), this::initializeMainWindow).call();

        undoRedo.addCommandQueueListener(redoUndoListener);

        // creating toolbar
        GuiHelper.runInEDTAndWait(() -> contentPanePrivate.add(toolbar.control, BorderLayout.NORTH));

        registerActionShortcut(menu.help, Shortcut.registerShortcut("system:help", tr("Help"),
                KeyEvent.VK_F1, Shortcut.DIRECT));

        // This needs to be done before RightAndLefthandTraffic::initialize is called
        try {
            new InitializationTask(tr("Initializing internal boundaries data"), Territories::initialize).call();
        } catch (JosmRuntimeException e) {
            // Can happen if the current projection needs NTV2 grid which is not available
            // In this case we want the user be able to change his projection
            BugReport.intercept(e).warn();
        }

        // contains several initialization tasks to be executed (in parallel) by a ExecutorService
        List<Callable<Void>> tasks = new ArrayList<>();

        tasks.add(new InitializationTask(tr("Initializing OSM API"), () -> {
                // We try to establish an API connection early, so that any API
                // capabilities are already known to the editor instance. However
                // if it goes wrong that's not critical at this stage.
                try {
                    OsmApi.getOsmApi().initialize(null, true);
                } catch (OsmTransferCanceledException | OsmApiInitializationException e) {
                    Main.warn(getErrorMessage(Utils.getRootCause(e)));
                }
            }));

        tasks.add(new InitializationTask(tr("Initializing internal traffic data"), RightAndLefthandTraffic::initialize));

        tasks.add(new InitializationTask(tr("Initializing validator"), OsmValidator::initialize));

        tasks.add(new InitializationTask(tr("Initializing presets"), TaggingPresets::initialize));

        tasks.add(new InitializationTask(tr("Initializing map styles"), MapPaintPreference::initialize));

        tasks.add(new InitializationTask(tr("Loading imagery preferences"), ImageryPreference::initialize));

        try {
            ExecutorService service = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(), Utils.newThreadFactory("main-init-%d", Thread.NORM_PRIORITY));
            for (Future<Void> i : service.invokeAll(tasks)) {
                i.get();
            }
            // asynchronous initializations to be completed eventually
            service.submit((Runnable) TMSLayer::getCache);
            service.submit((Runnable) OsmValidator::initializeTests);
            service.submit(OverpassTurboQueryWizard::getInstance);
            service.shutdown();
        } catch (InterruptedException | ExecutionException ex) {
            throw new JosmRuntimeException(ex);
        }

        // hooks for the jmapviewer component
        FeatureAdapter.registerBrowserAdapter(OpenBrowser::displayUrl);
        FeatureAdapter.registerTranslationAdapter(I18n.getTranslationAdapter());
        FeatureAdapter.registerLoggingAdapter(name -> Logging.getLogger());

        new InitializationTask(tr("Updating user interface"), () -> GuiHelper.runInEDTAndWait(() -> {
            toolbar.refreshToolbarControl();
            toolbar.control.updateUI();
            contentPanePrivate.updateUI();
        })).call();
    }

    /**
     * Called once at startup to initialize the main window content.
     * Should set {@link #menu} and {@link #panel}
     */
    protected abstract void initializeMainWindow();

    static final class InitializationTask implements Callable<Void> {

        private final String name;
        private final Runnable task;

        protected InitializationTask(String name, Runnable task) {
            this.name = name;
            this.task = task;
        }

        @Override
        public Void call() {
            Object status = null;
            if (initListener != null) {
                status = initListener.updateStatus(name);
            }
            task.run();
            if (initListener != null) {
                initListener.finish(status);
            }
            return null;
        }
    }

    /**
     * Returns the main layer manager that is used by the map view.
     * @return The layer manager. The value returned will never change.
     * @since 10279
     */
    public static MainLayerManager getLayerManager() {
        return layerManager;
    }

    /**
     * Replies the current selected primitives, from a end-user point of view.
     * It is not always technically the same collection of primitives than {@link DataSet#getSelected()}.
     * Indeed, if the user is currently in drawing mode, only the way currently being drawn is returned,
     * see {@link DrawAction#getInProgressSelection()}.
     *
     * @return The current selected primitives, from a end-user point of view. Can be {@code null}.
     * @since 6546
     */
    public Collection<OsmPrimitive> getInProgressSelection() {
        if (map != null && map.mapMode instanceof DrawAction) {
            return ((DrawAction) map.mapMode).getInProgressSelection();
        } else {
            DataSet ds = getLayerManager().getEditDataSet();
            if (ds == null) return null;
            return ds.getSelected();
        }
    }

    public static void redirectToMainContentPane(JComponent source) {
        RedirectInputMap.redirect(source, contentPanePrivate);
    }

    /**
     * Registers a {@code JosmAction} and its shortcut.
     * @param action action defining its own shortcut
     */
    public static void registerActionShortcut(JosmAction action) {
        registerActionShortcut(action, action.getShortcut());
    }

    /**
     * Registers an action and its shortcut.
     * @param action action to register
     * @param shortcut shortcut to associate to {@code action}
     */
    public static void registerActionShortcut(Action action, Shortcut shortcut) {
        KeyStroke keyStroke = shortcut.getKeyStroke();
        if (keyStroke == null)
            return;

        InputMap inputMap = contentPanePrivate.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        Object existing = inputMap.get(keyStroke);
        if (existing != null && !existing.equals(action)) {
            info(String.format("Keystroke %s is already assigned to %s, will be overridden by %s", keyStroke, existing, action));
        }
        inputMap.put(keyStroke, action);

        contentPanePrivate.getActionMap().put(action, action);
    }

    /**
     * Unregisters a shortcut.
     * @param shortcut shortcut to unregister
     */
    public static void unregisterShortcut(Shortcut shortcut) {
        contentPanePrivate.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(shortcut.getKeyStroke());
    }

    /**
     * Unregisters a {@code JosmAction} and its shortcut.
     * @param action action to unregister
     */
    public static void unregisterActionShortcut(JosmAction action) {
        unregisterActionShortcut(action, action.getShortcut());
    }

    /**
     * Unregisters an action and its shortcut.
     * @param action action to unregister
     * @param shortcut shortcut to unregister
     */
    public static void unregisterActionShortcut(Action action, Shortcut shortcut) {
        unregisterShortcut(shortcut);
        contentPanePrivate.getActionMap().remove(action);
    }

    /**
     * Replies the registered action for the given shortcut
     * @param shortcut The shortcut to look for
     * @return the registered action for the given shortcut
     * @since 5696
     */
    public static Action getRegisteredActionShortcut(Shortcut shortcut) {
        KeyStroke keyStroke = shortcut.getKeyStroke();
        if (keyStroke == null)
            return null;
        Object action = contentPanePrivate.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(keyStroke);
        if (action instanceof Action)
            return (Action) action;
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////
    //  Implementation part
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Listener that sets the enabled state of undo/redo menu entries.
     */
    protected final CommandQueueListener redoUndoListener = (queueSize, redoSize) -> {
            menu.undo.setEnabled(queueSize > 0);
            menu.redo.setEnabled(redoSize > 0);
        };

    /**
     * Should be called before the main constructor to setup some parameter stuff
     */
    public static void preConstructorInit() {
        ProjectionPreference.setProjection();

        String defaultlaf = platform.getDefaultStyle();
        String laf = LafPreference.LAF.get();
        try {
            UIManager.setLookAndFeel(laf);
        } catch (final NoClassDefFoundError | ClassNotFoundException e) {
            // Try to find look and feel in plugin classloaders
            Main.trace(e);
            Class<?> klass = null;
            for (ClassLoader cl : PluginHandler.getResourceClassLoaders()) {
                try {
                    klass = cl.loadClass(laf);
                    break;
                } catch (ClassNotFoundException ex) {
                    Main.trace(ex);
                }
            }
            if (klass != null && LookAndFeel.class.isAssignableFrom(klass)) {
                try {
                    UIManager.setLookAndFeel((LookAndFeel) klass.getConstructor().newInstance());
                } catch (ReflectiveOperationException ex) {
                    warn(ex, "Cannot set Look and Feel: " + laf + ": "+ex.getMessage());
                } catch (UnsupportedLookAndFeelException ex) {
                    info("Look and Feel not supported: " + laf);
                    LafPreference.LAF.put(defaultlaf);
                    trace(ex);
                }
            } else {
                info("Look and Feel not found: " + laf);
                LafPreference.LAF.put(defaultlaf);
            }
        } catch (UnsupportedLookAndFeelException e) {
            info("Look and Feel not supported: " + laf);
            LafPreference.LAF.put(defaultlaf);
            trace(e);
        } catch (InstantiationException | IllegalAccessException e) {
            error(e);
        }
        toolbar = new ToolbarPreferences();

        UIManager.put("OptionPane.okIcon", ImageProvider.get("ok"));
        UIManager.put("OptionPane.yesIcon", UIManager.get("OptionPane.okIcon"));
        UIManager.put("OptionPane.cancelIcon", ImageProvider.get("cancel"));
        UIManager.put("OptionPane.noIcon", UIManager.get("OptionPane.cancelIcon"));
        // Ensures caret color is the same than text foreground color, see #12257
        // See http://docs.oracle.com/javase/8/docs/api/javax/swing/plaf/synth/doc-files/componentProperties.html
        for (String p : Arrays.asList(
                "EditorPane", "FormattedTextField", "PasswordField", "TextArea", "TextField", "TextPane")) {
            UIManager.put(p+".caretForeground", UIManager.getColor(p+".foreground"));
        }

        I18n.translateJavaInternalMessages();

        // init default coordinate format
        //
        try {
            CoordinateFormat.setCoordinateFormat(CoordinateFormat.valueOf(Main.pref.get("coordinates")));
        } catch (IllegalArgumentException iae) {
            Main.trace(iae);
            CoordinateFormat.setCoordinateFormat(CoordinateFormat.DECIMAL_DEGREES);
        }
    }

    /**
     * Handle command line instructions after GUI has been initialized.
     * @param args program arguments
     * @return the list of submitted tasks
     */
    protected static List<Future<?>> postConstructorProcessCmdLine(ProgramArguments args) {
        List<Future<?>> tasks = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        for (String s : args.get(Option.DOWNLOAD)) {
            tasks.addAll(DownloadParamType.paramType(s).download(s, fileList));
        }
        if (!fileList.isEmpty()) {
            tasks.add(OpenFileAction.openFiles(fileList, true));
        }
        for (String s : args.get(Option.DOWNLOADGPS)) {
            tasks.addAll(DownloadParamType.paramType(s).downloadGps(s));
        }
        final Collection<String> selectionArguments = args.get(Option.SELECTION);
        if (!selectionArguments.isEmpty()) {
            tasks.add(Main.worker.submit(() -> {
                for (String s : selectionArguments) {
                    SearchAction.search(s, SearchAction.SearchMode.add);
                }
            }));
        }
        return tasks;
    }

    /**
     * Closes JOSM and optionally terminates the Java Virtual Machine (JVM).
     * If there are some unsaved data layers, asks first for user confirmation.
     * @param exit If {@code true}, the JVM is terminated by running {@link System#exit} with a given return code.
     * @param exitCode The return code
     * @param reason the reason for exiting
     * @return {@code true} if JOSM has been closed, {@code false} if the user has cancelled the operation.
     * @since 11093 (3378 with a different function signature)
     */
    public static boolean exitJosm(boolean exit, int exitCode, SaveLayersDialog.Reason reason) {
        final boolean proceed = Boolean.TRUE.equals(GuiHelper.runInEDTAndWaitAndReturn(() ->
                SaveLayersDialog.saveUnsavedModifications(getLayerManager().getLayers(),
                        reason != null ? reason : SaveLayersDialog.Reason.EXIT)));
        if (proceed) {
            if (Main.main != null) {
                Main.main.shutdown();
            }

            if (exit) {
                System.exit(exitCode);
            }
            return true;
        }
        return false;
    }

    /**
     * Shutdown JOSM.
     */
    protected void shutdown() {
        if (!GraphicsEnvironment.isHeadless()) {
            worker.shutdown();
            ImageProvider.shutdown(false);
            JCSCacheManager.shutdown();
        }
        if (map != null) {
            map.rememberToggleDialogWidth();
        }
        // Remove all layers because somebody may rely on layerRemoved events (like AutosaveTask)
        getLayerManager().resetState();
        try {
            pref.saveDefaults();
        } catch (IOException ex) {
            Main.warn(ex, tr("Failed to save default preferences."));
        }
        if (!GraphicsEnvironment.isHeadless()) {
            worker.shutdownNow();
            ImageProvider.shutdown(true);
        }
    }

    /**
     * The type of a command line parameter, to be used in switch statements.
     * @see #paramType
     */
    enum DownloadParamType {
        httpUrl {
            @Override
            List<Future<?>> download(String s, Collection<File> fileList) {
                return new OpenLocationAction().openUrl(false, s);
            }

            @Override
            List<Future<?>> downloadGps(String s) {
                final Bounds b = OsmUrlToBounds.parse(s);
                if (b == null) {
                    JOptionPane.showMessageDialog(
                            Main.parent,
                            tr("Ignoring malformed URL: \"{0}\"", s),
                            tr("Warning"),
                            JOptionPane.WARNING_MESSAGE
                    );
                    return Collections.emptyList();
                }
                return downloadFromParamBounds(true, b);
            }
        }, fileUrl {
            @Override
            List<Future<?>> download(String s, Collection<File> fileList) {
                File f = null;
                try {
                    f = new File(new URI(s));
                } catch (URISyntaxException e) {
                    Main.warn(e);
                    JOptionPane.showMessageDialog(
                            Main.parent,
                            tr("Ignoring malformed file URL: \"{0}\"", s),
                            tr("Warning"),
                            JOptionPane.WARNING_MESSAGE
                    );
                }
                if (f != null) {
                    fileList.add(f);
                }
                return Collections.emptyList();
            }
        }, bounds {

            /**
             * Download area specified on the command line as bounds string.
             * @param rawGps Flag to download raw GPS tracks
             * @param s The bounds parameter
             * @return the complete download task (including post-download handler), or {@code null}
             */
            private List<Future<?>> downloadFromParamBounds(final boolean rawGps, String s) {
                final StringTokenizer st = new StringTokenizer(s, ",");
                if (st.countTokens() == 4) {
                    return Main.downloadFromParamBounds(rawGps, new Bounds(
                            new LatLon(Double.parseDouble(st.nextToken()), Double.parseDouble(st.nextToken())),
                            new LatLon(Double.parseDouble(st.nextToken()), Double.parseDouble(st.nextToken()))
                    ));
                }
                return Collections.emptyList();
            }

            @Override
            List<Future<?>> download(String param, Collection<File> fileList) {
                return downloadFromParamBounds(false, param);
            }

            @Override
            List<Future<?>> downloadGps(String param) {
                return downloadFromParamBounds(true, param);
            }
        }, fileName {
            @Override
            List<Future<?>> download(String s, Collection<File> fileList) {
                fileList.add(new File(s));
                return Collections.emptyList();
            }
        };

        /**
         * Performs the download
         * @param param represents the object to be downloaded
         * @param fileList files which shall be opened, should be added to this collection
         * @return the download task, or {@code null}
         */
        abstract List<Future<?>> download(String param, Collection<File> fileList);

        /**
         * Performs the GPS download
         * @param param represents the object to be downloaded
         * @return the download task, or {@code null}
         */
        List<Future<?>> downloadGps(String param) {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(
                        Main.parent,
                        tr("Parameter \"downloadgps\" does not accept file names or file URLs"),
                        tr("Warning"),
                        JOptionPane.WARNING_MESSAGE
                );
            }
            return Collections.emptyList();
        }

        /**
         * Guess the type of a parameter string specified on the command line with --download= or --downloadgps.
         *
         * @param s A parameter string
         * @return The guessed parameter type
         */
        static DownloadParamType paramType(String s) {
            if (s.startsWith("http:") || s.startsWith("https:")) return DownloadParamType.httpUrl;
            if (s.startsWith("file:")) return DownloadParamType.fileUrl;
            String coorPattern = "\\s*[+-]?[0-9]+(\\.[0-9]+)?\\s*";
            if (s.matches(coorPattern + "(," + coorPattern + "){3}")) return DownloadParamType.bounds;
            // everything else must be a file name
            return DownloadParamType.fileName;
        }
    }

    /**
     * Download area specified as Bounds value.
     * @param rawGps Flag to download raw GPS tracks
     * @param b The bounds value
     * @return the complete download task (including post-download handler)
     */
    private static List<Future<?>> downloadFromParamBounds(final boolean rawGps, Bounds b) {
        DownloadTask task = rawGps ? new DownloadGpsTask() : new DownloadOsmTask();
        // asynchronously launch the download task ...
        Future<?> future = task.download(true, b, null);
        // ... and the continuation when the download is finished (this will wait for the download to finish)
        return Collections.singletonList(Main.worker.submit(new PostDownloadHandler(task, future)));
    }

    /**
     * Identifies the current operating system family and initializes the platform hook accordingly.
     * @since 1849
     */
    public static void determinePlatformHook() {
        String os = System.getProperty("os.name");
        if (os == null) {
            warn("Your operating system has no name, so I'm guessing its some kind of *nix.");
            platform = new PlatformHookUnixoid();
        } else if (os.toLowerCase(Locale.ENGLISH).startsWith("windows")) {
            platform = new PlatformHookWindows();
        } else if ("Linux".equals(os) || "Solaris".equals(os) ||
                "SunOS".equals(os) || "AIX".equals(os) ||
                "FreeBSD".equals(os) || "NetBSD".equals(os) || "OpenBSD".equals(os)) {
            platform = new PlatformHookUnixoid();
        } else if (os.toLowerCase(Locale.ENGLISH).startsWith("mac os x")) {
            platform = new PlatformHookOsx();
        } else {
            warn("I don't know your operating system '"+os+"', so I'm guessing its some kind of *nix.");
            platform = new PlatformHookUnixoid();
        }
    }

    /* ----------------------------------------------------------------------------------------- */
    /* projection handling  - Main is a registry for a single, global projection instance        */
    /*                                                                                           */
    /* TODO: For historical reasons the registry is implemented by Main. An alternative approach */
    /* would be a singleton org.openstreetmap.josm.data.projection.ProjectionRegistry class.     */
    /* ----------------------------------------------------------------------------------------- */
    /**
     * The projection method used.
     * use {@link #getProjection()} and {@link #setProjection(Projection)} for access.
     * Use {@link #setProjection(Projection)} in order to trigger a projection change event.
     */
    private static volatile Projection proj;

    /**
     * Replies the current projection.
     *
     * @return the currently active projection
     */
    public static Projection getProjection() {
        return proj;
    }

    /**
     * Sets the current projection
     *
     * @param p the projection
     */
    public static void setProjection(Projection p) {
        CheckParameterUtil.ensureParameterNotNull(p);
        Projection oldValue = proj;
        Bounds b = isDisplayingMapView() ? map.mapView.getRealBounds() : null;
        proj = p;
        fireProjectionChanged(oldValue, proj, b);
    }

    /*
     * Keep WeakReferences to the listeners. This relieves clients from the burden of
     * explicitly removing the listeners and allows us to transparently register every
     * created dataset as projection change listener.
     */
    private static final List<WeakReference<ProjectionChangeListener>> listeners = new ArrayList<>();

    private static void fireProjectionChanged(Projection oldValue, Projection newValue, Bounds oldBounds) {
        if ((newValue == null ^ oldValue == null)
                || (newValue != null && oldValue != null && !Objects.equals(newValue.toCode(), oldValue.toCode()))) {
            synchronized (Main.class) {
                Iterator<WeakReference<ProjectionChangeListener>> it = listeners.iterator();
                while (it.hasNext()) {
                    WeakReference<ProjectionChangeListener> wr = it.next();
                    ProjectionChangeListener listener = wr.get();
                    if (listener == null) {
                        it.remove();
                        continue;
                    }
                    listener.projectionChanged(oldValue, newValue);
                }
            }
            if (newValue != null && oldBounds != null) {
                Main.map.mapView.zoomTo(oldBounds);
            }
            /* TODO - remove layers with fixed projection */
        }
    }

    /**
     * Register a projection change listener.
     * The listener is registered to be weak, so keep a reference of it if you want it to be preserved.
     *
     * @param listener the listener. Ignored if <code>null</code>.
     */
    public static void addProjectionChangeListener(ProjectionChangeListener listener) {
        if (listener == null) return;
        synchronized (Main.class) {
            for (WeakReference<ProjectionChangeListener> wr : listeners) {
                // already registered ? => abort
                if (wr.get() == listener) return;
            }
            listeners.add(new WeakReference<>(listener));
        }
    }

    /**
     * Removes a projection change listener.
     *
     * @param listener the listener. Ignored if <code>null</code>.
     */
    public static void removeProjectionChangeListener(ProjectionChangeListener listener) {
        if (listener == null) return;
        synchronized (Main.class) {
            // remove the listener - and any other listener which got garbage
            // collected in the meantime
            listeners.removeIf(wr -> wr.get() == null || wr.get() == listener);
        }
    }

    /**
     * Listener for window switch events.
     *
     * These are events, when the user activates a window of another application
     * or comes back to JOSM. Window switches from one JOSM window to another
     * are not reported.
     */
    public interface WindowSwitchListener {
        /**
         * Called when the user activates a window of another application.
         */
        void toOtherApplication();

        /**
         * Called when the user comes from a window of another application back to JOSM.
         */
        void fromOtherApplication();
    }

    /**
     * Registers a new {@code MapFrameListener} that will be notified of MapFrame changes.
     * <p>
     * It will fire an initial mapFrameInitialized event when the MapFrame is present.
     * Otherwise will only fire when the MapFrame is created or destroyed.
     * @param listener The MapFrameListener
     * @return {@code true} if the listeners collection changed as a result of the call
     * @see #addMapFrameListener
     * @since 11904
     */
    public static boolean addAndFireMapFrameListener(MapFrameListener listener) {
        return mainPanel != null && mainPanel.addAndFireMapFrameListener(listener);
    }

    /**
     * Registers a new {@code MapFrameListener} that will be notified of MapFrame changes
     * @param listener The MapFrameListener
     * @return {@code true} if the listeners collection changed as a result of the call
     * @see #addAndFireMapFrameListener
     * @since 5957
     */
    public static boolean addMapFrameListener(MapFrameListener listener) {
        return mainPanel != null && mainPanel.addMapFrameListener(listener);
    }

    /**
     * Unregisters the given {@code MapFrameListener} from MapFrame changes
     * @param listener The MapFrameListener
     * @return {@code true} if the listeners collection changed as a result of the call
     * @since 5957
     */
    public static boolean removeMapFrameListener(MapFrameListener listener) {
        return mainPanel != null && mainPanel.removeMapFrameListener(listener);
    }

    /**
     * Adds a new network error that occur to give a hint about broken Internet connection.
     * Do not use this method for errors known for sure thrown because of a bad proxy configuration.
     *
     * @param url The accessed URL that caused the error
     * @param t The network error
     * @return The previous error associated to the given resource, if any. Can be {@code null}
     * @since 6642
     */
    public static Throwable addNetworkError(URL url, Throwable t) {
        if (url != null && t != null) {
            Throwable old = addNetworkError(url.toExternalForm(), t);
            if (old != null) {
                Main.warn("Already here "+old);
            }
            return old;
        }
        return null;
    }

    /**
     * Adds a new network error that occur to give a hint about broken Internet connection.
     * Do not use this method for errors known for sure thrown because of a bad proxy configuration.
     *
     * @param url The accessed URL that caused the error
     * @param t The network error
     * @return The previous error associated to the given resource, if any. Can be {@code null}
     * @since 6642
     */
    public static Throwable addNetworkError(String url, Throwable t) {
        if (url != null && t != null) {
            return NETWORK_ERRORS.put(url, t);
        }
        return null;
    }

    /**
     * Returns the network errors that occured until now.
     * @return the network errors that occured until now, indexed by URL
     * @since 6639
     */
    public static Map<String, Throwable> getNetworkErrors() {
        return new HashMap<>(NETWORK_ERRORS);
    }

    /**
     * Clears the network errors cache.
     * @since 12011
     */
    public static void clearNetworkErrors() {
        NETWORK_ERRORS.clear();
    }

    /**
     * Returns the JOSM website URL.
     * @return the josm website URL
     * @since 6897
     */
    public static String getJOSMWebsite() {
        if (Main.pref != null)
            return Main.pref.get("josm.url", JOSM_WEBSITE);
        return JOSM_WEBSITE;
    }

    /**
     * Returns the JOSM XML URL.
     * @return the josm XML URL
     * @since 6897
     */
    public static String getXMLBase() {
        // Always return HTTP (issues reported with HTTPS)
        return "http://josm.openstreetmap.de";
    }

    /**
     * Returns the OSM website URL.
     * @return the OSM website URL
     * @since 6897
     */
    public static String getOSMWebsite() {
        if (Main.pref != null)
            return Main.pref.get("osm.url", OSM_WEBSITE);
        return OSM_WEBSITE;
    }

    /**
     * Returns the OSM website URL depending on the selected {@link OsmApi}.
     * @return the OSM website URL depending on the selected {@link OsmApi}
     */
    private static String getOSMWebsiteDependingOnSelectedApi() {
        final String api = OsmApi.getOsmApi().getServerUrl();
        if (OsmApi.DEFAULT_API_URL.equals(api)) {
            return getOSMWebsite();
        } else {
            return api.replaceAll("/api$", "");
        }
    }

    /**
     * Replies the base URL for browsing information about a primitive.
     * @return the base URL, i.e. https://www.openstreetmap.org
     * @since 7678
     */
    public static String getBaseBrowseUrl() {
        if (Main.pref != null)
            return Main.pref.get("osm-browse.url", getOSMWebsiteDependingOnSelectedApi());
        return getOSMWebsiteDependingOnSelectedApi();
    }

    /**
     * Replies the base URL for browsing information about a user.
     * @return the base URL, i.e. https://www.openstreetmap.org/user
     * @since 7678
     */
    public static String getBaseUserUrl() {
        if (Main.pref != null)
            return Main.pref.get("osm-user.url", getOSMWebsiteDependingOnSelectedApi() + "/user");
        return getOSMWebsiteDependingOnSelectedApi() + "/user";
    }

    /**
     * Determines if we are currently running on OSX.
     * @return {@code true} if we are currently running on OSX
     * @since 6957
     */
    public static boolean isPlatformOsx() {
        return Main.platform instanceof PlatformHookOsx;
    }

    /**
     * Determines if we are currently running on Windows.
     * @return {@code true} if we are currently running on Windows
     * @since 7335
     */
    public static boolean isPlatformWindows() {
        return Main.platform instanceof PlatformHookWindows;
    }

    /**
     * Determines if the given online resource is currently offline.
     * @param r the online resource
     * @return {@code true} if {@code r} is offline and should not be accessed
     * @since 7434
     */
    public static boolean isOffline(OnlineResource r) {
        return OFFLINE_RESOURCES.contains(r) || OFFLINE_RESOURCES.contains(OnlineResource.ALL);
    }

    /**
     * Sets the given online resource to offline state.
     * @param r the online resource
     * @return {@code true} if {@code r} was not already offline
     * @since 7434
     */
    public static boolean setOffline(OnlineResource r) {
        return OFFLINE_RESOURCES.add(r);
    }

    /**
     * Sets the given online resource to online state.
     * @param r the online resource
     * @return {@code true} if {@code r} was offline
     * @since 8506
     */
    public static boolean setOnline(OnlineResource r) {
        return OFFLINE_RESOURCES.remove(r);
    }

    /**
     * Replies the set of online resources currently offline.
     * @return the set of online resources currently offline
     * @since 7434
     */
    public static Set<OnlineResource> getOfflineResources() {
        return EnumSet.copyOf(OFFLINE_RESOURCES);
    }
}
