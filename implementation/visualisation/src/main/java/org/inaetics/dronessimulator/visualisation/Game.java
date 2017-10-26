package org.inaetics.dronessimulator.visualisation;

import com.rabbitmq.client.ConnectionFactory;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.extern.log4j.Log4j;
import org.apache.log4j.Logger;
import org.inaetics.dronessimulator.architectureevents.ArchitectureEventControllerService;
import org.inaetics.dronessimulator.common.architecture.SimulationAction;
import org.inaetics.dronessimulator.common.architecture.SimulationState;
import org.inaetics.dronessimulator.common.protocol.*;
import org.inaetics.dronessimulator.common.vector.D3PolarCoordinate;
import org.inaetics.dronessimulator.common.vector.D3Vector;
import org.inaetics.dronessimulator.discovery.api.DiscoveryPath;
import org.inaetics.dronessimulator.discovery.api.Instance;
import org.inaetics.dronessimulator.discovery.api.discoverynode.DiscoveryNode;
import org.inaetics.dronessimulator.discovery.api.discoverynode.NodeEventHandler;
import org.inaetics.dronessimulator.discovery.api.discoverynode.Type;
import org.inaetics.dronessimulator.discovery.api.discoverynode.discoveryevent.AddedNode;
import org.inaetics.dronessimulator.discovery.api.discoverynode.discoveryevent.ChangedValue;
import org.inaetics.dronessimulator.discovery.api.discoverynode.discoveryevent.RemovedNode;
import org.inaetics.dronessimulator.discovery.etcd.EtcdDiscovererService;
import org.inaetics.dronessimulator.pubsub.javaserializer.JavaSerializer;
import org.inaetics.dronessimulator.pubsub.rabbitmq.publisher.RabbitPublisher;
import org.inaetics.dronessimulator.pubsub.rabbitmq.subscriber.RabbitSubscriber;
import org.inaetics.dronessimulator.visualisation.controls.PannableCanvas;
import org.inaetics.dronessimulator.visualisation.controls.SceneGestures;
import org.inaetics.dronessimulator.visualisation.messagehandlers.*;
import org.inaetics.dronessimulator.visualisation.uiupdates.UIUpdate;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Instances of the game class create a new javafx application. This class provides a connection with etcd, rabbitmq and
 * contain all the game elements.
 */
@Log4j
public class Game extends Application {
    /**
     * All the entities in the game
     */
    private final ConcurrentMap<String, BaseEntity> entities = new ConcurrentHashMap<>();
    /**
     * All the available entities from the discoverer
     */
    private final ConcurrentMap<String, DiscoveryNode> availableEntities = new ConcurrentHashMap<>();
    /**
     * Rabbitmq configuration
     */
    private final Map<String, String> rabbitConfig = new HashMap<>();
    /**
     * UI updates
     */
    private final BlockingQueue<UIUpdate> uiUpdates;
    /**
     * Subscriber for rabbitmq
     */
    private RabbitSubscriber subscriber;
    /**
     * Publisher for rabbitmq
     */
    private RabbitPublisher publisher;
    /**
     * Discoverer for etcd
     */
    private EtcdDiscovererService discoverer;
    /**
     * The pannable and zommable canvas
     */
    private PannableCanvas canvas;
    /**
     * Group for all entities
     */
    private Group root;
    /**
     * check to see if the method onRabbitConnect is executed
     */
    private AtomicBoolean onRabbitConnectExecuted = new AtomicBoolean(false);
    /**
     * counter for the logger to output once every 100 times
     */
    private int i = 0;
    /**
     * Time is ms of the last log
     */
    private long lastLog = -1;
    /**
     * Close event handler
     * When the window closes, rabbitmq and the discoverer disconnect
     */
    private EventHandler onCloseEventHandler = new EventHandler<WindowEvent>() {
        boolean isClosed = false;

        @Override
        public void handle(WindowEvent t) {
            if (!isClosed) {
                log.info("Closing the application gracefully");
                try {
                    if (subscriber != null)
                        subscriber.disconnect();
                    if (publisher != null)
                        publisher.disconnect();
                    if (discoverer != null)
                        discoverer.stop();
                    isClosed = true;
                } catch (IOException e) {
                    log.fatal(e);
                }
                Platform.exit();
            }
        }
    };
    private Instance visualisationInstance = new Instance(Type.SERVICE, org.inaetics.dronessimulator.discovery.api.discoverynode.Group.SERVICES, "visualisation", new HashMap<>());

    /**
     * Instantiates a new game object
     */
    public Game() {
        this.uiUpdates = new LinkedBlockingQueue<>();
    }

    /**
     * Main method of the visualisation
     *
     * @param args - args
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Main entry point for a JavaFX application
     *
     * @param primaryStage - the primary stage for this application
     */
    @Override
    public void start(Stage primaryStage) {
        setupInterface(primaryStage);
        setupDiscovery();
        setupRabbit();
        setupGameEventListener();

        lastLog = System.currentTimeMillis();
        AnimationTimer gameLoop = new AnimationTimer() {

            @Override
            public void handle(long now) {
                long current_step_started_at_ms = System.currentTimeMillis();

                if (!isRabbitConnected()) {
                    log.info("RabbitMQ is not (yet) connected.");
                } else if (!onRabbitConnectExecuted.get()) {
                    onRabbitConnect();
                    onRabbitConnectExecuted.set(true);
                }

                i++;
                if (i == 100) {
                    long current = System.currentTimeMillis();
                    float durationAverageMs = ((float) (current - lastLog)) / 100f;
                    float fps = 1000f / durationAverageMs;
                    lastLog = current;

                    log.info("Average: " + durationAverageMs);
                    log.info("FPS: " + fps);
                    i = 0;
                }

                while (!uiUpdates.isEmpty()) {
                    try {
                        UIUpdate uiUpdate = uiUpdates.take();
                        uiUpdate.execute(canvas);
                    } catch (InterruptedException e) {
                        log.fatal(e);
                        Thread.currentThread().interrupt();
                    }
                }

                // update sprites in scene
                entities.forEach((id, entity) -> entity.updateUI());


                long current_step_ended_at_ms = System.currentTimeMillis();
                long current_step_took_ms = current_step_ended_at_ms - current_step_started_at_ms;
                long diff = 10 - current_step_took_ms;

                if (diff > 0) {
                    try {
                        Thread.sleep(diff);
                    } catch (InterruptedException e) {
                        log.fatal(e);
                        Thread.currentThread().interrupt();
                    }
                }
            }

        };
        gameLoop.start();
    }

    /**
     * Creates the canvas for scrolling and panning.
     *
     * @param primaryStage - Stage as given by the start method
     */
    private void setupInterface(Stage primaryStage) {
        root = new Group();

        primaryStage.setTitle("Drone simulator");
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(onCloseEventHandler);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> onCloseEventHandler.handle(null)));

        // create canvas
        canvas = new PannableCanvas(Settings.CANVAS_WIDTH, Settings.CANVAS_HEIGHT);
        canvas.setId("pane");
        canvas.setTranslateX(0);
        canvas.setTranslateY(0);

        root.getChildren().add(canvas);

        double width = Math.max(Settings.CANVAS_WIDTH, Settings.SCENE_WIDTH);
        double height = Math.max(Settings.CANVAS_HEIGHT, Settings.SCENE_HEIGHT);

        // create scene which can be dragged and zoomed
        Scene scene = new Scene(root, width, height);
        SceneGestures sceneGestures = new SceneGestures(canvas);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, sceneGestures.getOnMousePressedEventHandler());
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, sceneGestures.getOnMouseDraggedEventHandler());
        scene.addEventFilter(ScrollEvent.ANY, sceneGestures.getOnScrollEventHandler());
        scene.getStylesheets().addAll(this.getClass().getResource("/style.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();
        canvas.addGrid();
    }

    /**
     * Setup discovery
     * Create new discoverer object and start
     */
    private void setupDiscovery() {
        this.discoverer = new EtcdDiscovererService();
        this.discoverer.start();
    }

    /**
     * Sets up the connection to the message broker and subscribes to the necessary channels and sets the required handlers
     */
    private void setupRabbit() {
        List<NodeEventHandler<ChangedValue>> changedValueHandlers = new ArrayList<>();

        changedValueHandlers.add((ChangedValue e) -> {
            DiscoveryNode node = e.getNode();
            DiscoveryPath path = node.getPath();

            if (path.equals(DiscoveryPath.config(Type.RABBITMQ, org.inaetics.dronessimulator.discovery.api.discoverynode.Group.BROKER, "default"))) {
                if (node.getValue("username") != null) {
                    rabbitConfig.put("username", node.getValue("username"));
                }

                if (node.getValue("password") != null) {
                    rabbitConfig.put("password", node.getValue("password"));
                }

                if (node.getValue("uri") != null) {
                    rabbitConfig.put("uri", node.getValue("uri"));
                }

                if (rabbitConfig.size() == 3) {
                    connectRabbit();
                }
            }
        });

        this.discoverer.addHandlers(true, Collections.emptyList(), changedValueHandlers, Collections.emptyList());
    }

    private boolean isRabbitConnected() {
        return subscriber != null && subscriber.isConnected() && publisher != null && publisher.isConnected();
    }

    /**
     * Connect to rabbitmq using the rabbitconfig, then connect the publisher and subscriber
     * Adds the handlers for listening to incoming game messages
     */
    private void connectRabbit() {
        if (!isRabbitConnected()) {
            log.info("Connecting RabbitMQ...");
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUsername(rabbitConfig.get("username"));
            connectionFactory.setPassword(rabbitConfig.get("password"));
            try {
                connectionFactory.setUri(rabbitConfig.get("uri"));
            } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
                log.fatal(e);
            }

            // We can connect to localhost, since the visualization does not run within Docker
            this.subscriber = new RabbitSubscriber(connectionFactory, "visualisation", new JavaSerializer(), discoverer);
            this.publisher = new RabbitPublisher(connectionFactory, new JavaSerializer(), discoverer);

            try {
                this.subscriber.connect();
                this.publisher.connect();
                log.info("Connected RabbitMQ!");
            } catch (IOException e) {
                log.fatal(e);
            }

            this.subscriber.addHandler(CollisionMessage.class, new CollisionMessageHandler());
            this.subscriber.addHandler(DamageMessage.class, new DamageMessageHandler());
            this.subscriber.addHandler(FireBulletMessage.class, new FireBulletMessageHandler());
            this.subscriber.addHandler(KillMessage.class, new KillMessageHandler(this.entities));
            this.subscriber.addHandler(StateMessage.class, new StateMessageHandler(uiUpdates, this.entities));

            try {
                this.subscriber.addTopic(MessageTopic.STATEUPDATES);
            } catch (IOException e) {
                log.fatal(e);
            }
        }
    }

    /**
     * Setup the architecture management buttons when rabbit is connected
     */
    private void onRabbitConnect() {
        setupArchitectureManagementVisuals();
        setupArchitectureManagement();
    }

    /**
     * Sets up the connection to the message broker and subscribes to the necessary channels and sets the required handlers
     */
    private void setupGameEventListener() {
        List<NodeEventHandler<RemovedNode>> removeHandlers = new ArrayList<>();
        List<NodeEventHandler<AddedNode>> addHandlers = new ArrayList<>();

        addHandlers.add((AddedNode addedNodeEvent) -> {
            DiscoveryNode node = addedNodeEvent.getNode();
            DiscoveryPath path = node.getPath();

            if (path.startsWith(DiscoveryPath.group(Type.DRONE, org.inaetics.dronessimulator.discovery.api.discoverynode.Group.DRONE)) && path.isConfigPath()) {
                String protocolId = node.getId();
                availableEntities.put(protocolId, node);
                createDroneIfNotExists(protocolId);
                log.info("Added drone " + protocolId + " to visualisation");
            }
        });

        removeHandlers.add((RemovedNode removedNodeEvent) -> {
            DiscoveryNode node = removedNodeEvent.getNode();
            DiscoveryPath path = node.getPath();

            if (path.startsWith(DiscoveryPath.group(Type.DRONE, org.inaetics.dronessimulator.discovery.api.discoverynode.Group.DRONE)) && path.isConfigPath()) {
                String protocolId = node.getId();
                availableEntities.remove(protocolId);
                BaseEntity baseEntity = entities.get(protocolId);

                if (baseEntity != null) {
                    baseEntity.delete();
                    entities.remove(protocolId);
                }
                log.info("Removed drone " + protocolId + " from visualisation");
            }
        });

        this.discoverer.addHandlers(true, addHandlers, Collections.emptyList(), removeHandlers);
    }

    /**
     * Create the different buttons for the architecture management and add the desired actions to them
     */
    private void setupArchitectureManagementVisuals() {
        HBox buttons = new HBox();

        Button configButton = new Button("Config");
        Button startButton = new Button("Start");
        Button pauseButton = new Button("Pause");
        Button resumeButton = new Button("Resume");
        Button stopButton = new Button("Stop");

        buttons.getChildren().addAll(configButton, startButton, pauseButton, resumeButton, stopButton);

        BorderPane borderPane = new BorderPane();
        borderPane.setPrefHeight(canvas.getScene().getHeight());
        borderPane.setPrefWidth(canvas.getScene().getWidth());

        Pane space = new Pane();
        space.setMinSize(1, 1);
        HBox.setHgrow(space, Priority.ALWAYS);

        HBox container = new HBox();
        container.setPrefWidth(canvas.getScene().getWidth());

        container.getChildren().addAll(space, buttons);
        borderPane.setBottom(container);
        root.getChildren().add(borderPane);

        configButton.setOnMouseClicked(new ArchitectureButtonEventHandler(SimulationAction.CONFIG, publisher));
        startButton.setOnMouseClicked(new ArchitectureButtonEventHandler(SimulationAction.START, publisher));
        stopButton.setOnMouseClicked(new ArchitectureButtonEventHandler(SimulationAction.STOP, publisher));
        pauseButton.setOnMouseClicked(new ArchitectureButtonEventHandler(SimulationAction.PAUSE, publisher));
        resumeButton.setOnMouseClicked(new ArchitectureButtonEventHandler(SimulationAction.RESUME, publisher));
    }

    /**
     * Responds to incoming architecture lifecycle messages
     */
    private void setupArchitectureManagement() {
        ArchitectureEventControllerService architectureEventController;
        architectureEventController = new ArchitectureEventControllerService(this.discoverer);
        architectureEventController.start();

        architectureEventController.addHandler(SimulationState.INIT, SimulationAction.CONFIG, SimulationState.CONFIG,
                (SimulationState fromState, SimulationAction action, SimulationState toState) -> {
                    for (BaseEntity e : this.entities.values()) {
                        e.delete();
                    }
                    this.entities.clear();
                    //Create all drones that are currently available
                    getAvailableDrones().forEach(entry -> createDrone(entry.getKey()));
                }
        );
    }

    private List<Map.Entry<String, DiscoveryNode>> getAvailableDrones() {
        //TODO maybe perform a check with etcd for synchronisation
        List<Map.Entry<String, DiscoveryNode>> availableDrones = availableEntities.entrySet().stream().filter((entry) -> entry.getValue().getPath().startsWith(DiscoveryPath.group(Type.DRONE, org.inaetics.dronessimulator.discovery.api.discoverynode.Group.DRONE))).collect(Collectors.toList());
        visualisationInstance.getProperties().put("availableDrones", availableDrones.toString());
        visualisationInstance.getProperties().put("numberOfAvailableDrones", String.valueOf(availableDrones.size()));
        try {
            discoverer.updateProperties(visualisationInstance, visualisationInstance.getProperties());
        } catch (IOException e) {
            log.error(e);
        }
        return availableDrones;
    }

    private void createDroneIfNotExists(String protocolId) {
        BaseEntity baseEntity = entities.get(protocolId);

        if (baseEntity == null) {
            createDrone(protocolId);
        }
    }

    /**
     * Creates a new drone and returns it
     *
     * @param id String - Identifier of the new drone
     */
    private void createDrone(String id) {
        BasicDrone drone = new BasicDrone(uiUpdates);
        drone.setPosition(new D3Vector(-9999, -9999, -9999));
        drone.setDirection(new D3PolarCoordinate(-9999, -9999, -9999));
        entities.putIfAbsent(id, drone);
    }
}
