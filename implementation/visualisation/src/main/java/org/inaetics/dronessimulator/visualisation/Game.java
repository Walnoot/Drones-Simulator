package org.inaetics.dronessimulator.visualisation;

import com.rabbitmq.client.ConnectionFactory;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
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
import org.apache.log4j.Logger;
import org.inaetics.dronessimulator.architectureevents.ArchitectureEventControllerService;
import org.inaetics.dronessimulator.common.architecture.SimulationAction;
import org.inaetics.dronessimulator.common.architecture.SimulationState;
import org.inaetics.dronessimulator.common.protocol.*;
import org.inaetics.dronessimulator.discovery.api.DiscoveryPath;
import org.inaetics.dronessimulator.discovery.api.discoverynode.DiscoveryNode;
import org.inaetics.dronessimulator.discovery.api.discoverynode.NodeEventHandler;
import org.inaetics.dronessimulator.discovery.api.discoverynode.Type;
import org.inaetics.dronessimulator.discovery.api.discoverynode.discoveryevent.ChangedValue;
import org.inaetics.dronessimulator.discovery.etcd.EtcdDiscovererService;
import org.inaetics.dronessimulator.pubsub.javaserializer.JavaSerializer;
import org.inaetics.dronessimulator.pubsub.rabbitmq.publisher.RabbitPublisher;
import org.inaetics.dronessimulator.pubsub.rabbitmq.subscriber.RabbitSubscriber;
import org.inaetics.dronessimulator.visualisation.controls.PannableCanvas;
import org.inaetics.dronessimulator.visualisation.controls.SceneGestures;
import org.inaetics.dronessimulator.visualisation.messagehandlers.*;
import org.inaetics.dronessimulator.visualisation.uiupdates.UIUpdate;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

public class Game extends Application {
    private RabbitSubscriber subscriber;
    private RabbitPublisher publisher;
    private EtcdDiscovererService discoverer;
    private ArchitectureEventControllerService architectureEventController;

    private static final Logger logger = Logger.getLogger(Game.class);

    private final ConcurrentMap<String, BaseEntity> entities = new ConcurrentHashMap<>();

    private final Map<String, String> rabbitConfig = new HashMap<>();

    private PannableCanvas canvas;
    private Group root;

    private final BlockingQueue<UIUpdate> uiUpdates;

    public Game() {
        this.uiUpdates = new LinkedBlockingQueue<>();
    }

    private int i = 0;
    private long lastLog = -1;

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
        setupArchitectureManagementVisuals();
        setupArchitectureManagement();

        lastLog = System.currentTimeMillis();
        AnimationTimer gameLoop = new AnimationTimer() {

            @Override
            public void handle(long now) {
                i++;
                if (i == 100) {
                    long current = System.currentTimeMillis();
                    float durationAverageMs = ((float) (current - lastLog)) / 100f;
                    float fps = 1000f / durationAverageMs;
                    lastLog = current;

                    logger.info("Average: " + durationAverageMs);
                    logger.info("FPS: " + fps);
                    i = 0;
                }

                while(!uiUpdates.isEmpty()) {
                    try {
                        UIUpdate uiUpdate = uiUpdates.take();
                        uiUpdate.execute(canvas);
                    } catch (InterruptedException e) {
                        logger.fatal(e);
                        Thread.currentThread().interrupt();
                    }
                }

                // update sprites in scene
                entities.forEach((id, entity) -> entity.updateUI());
            }

        };
        gameLoop.start();
    }

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

            if(path.startsWith(DiscoveryPath.config(Type.RABBITMQ, org.inaetics.dronessimulator.discovery.api.discoverynode.Group.BROKER, "default"))) {
                if(node.getValue("username") != null) {
                    rabbitConfig.put("username", node.getValue("username"));
                }

                if(node.getValue("password") != null) {
                    rabbitConfig.put("password", node.getValue("password"));
                }

                if(node.getValue("uri") != null) {
                    rabbitConfig.put("uri", node.getValue("uri"));
                }

                if(rabbitConfig.size() == 3) {
                    connectRabbit();
                }
            }
        });

        this.discoverer.addHandlers(true, Collections.emptyList(), changedValueHandlers, Collections.emptyList());


    }

    private void connectRabbit() {
        if (this.subscriber == null) {
            logger.info("Connecting RabbitMQ...");
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUsername(rabbitConfig.get("username"));
            connectionFactory.setPassword(rabbitConfig.get("password"));
            connectionFactory.setHost(rabbitConfig.get("uri"));

            // We can connect to localhost, since the visualization does not run within Docker
            this.subscriber = new RabbitSubscriber(connectionFactory, "visualisation", new JavaSerializer());
            this.publisher = new RabbitPublisher(connectionFactory, new JavaSerializer());

            try {
                this.subscriber.connect();
                this.publisher.connect();
                logger.info("Connected RabbitMQ!");
            } catch (IOException e) {
                logger.fatal(e);
            }
        }

        this.subscriber.addHandler(CollisionMessage.class, new CollisionMessageHandler());
        this.subscriber.addHandler(DamageMessage.class, new DamageMessageHandler());
        this.subscriber.addHandler(FireBulletMessage.class, new FireBulletMessageHandler());
        this.subscriber.addHandler(KillMessage.class, new KillMessageHandler(this.entities));
        this.subscriber.addHandler(StateMessage.class, new StateMessageHandler(uiUpdates, this.canvas, this.entities));

        try {
            this.subscriber.addTopic(MessageTopic.STATEUPDATES);
        } catch (IOException e) {
            logger.fatal(e);
        }
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

        // create canvas
        canvas = new PannableCanvas(Settings.CANVAS_WIDTH, Settings.CANVAS_HEIGHT);
        canvas.setId("pane");
        canvas.setTranslateX(0);
        canvas.setTranslateY(0);

        root.getChildren().add(canvas);

        double width = Settings.SCENE_WIDTH > Settings.CANVAS_WIDTH ? Settings.CANVAS_WIDTH : Settings.SCENE_WIDTH;
        double height = Settings.SCENE_HEIGHT > Settings.CANVAS_HEIGHT ? Settings.CANVAS_HEIGHT : Settings.SCENE_HEIGHT;

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

    private void setupArchitectureManagement() {
        this.architectureEventController = new ArchitectureEventControllerService(this.discoverer);
        this.architectureEventController.start();

        this.architectureEventController.addHandler(SimulationState.INIT, SimulationAction.CONFIG, SimulationState.CONFIG,
                (SimulationState fromState, SimulationAction action, SimulationState toState) -> {
                    for(BaseEntity e : this.entities.values()) {
                        e.delete();
                    }
                    this.entities.clear();
                }
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}
