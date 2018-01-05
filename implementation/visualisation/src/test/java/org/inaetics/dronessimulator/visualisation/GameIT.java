package org.inaetics.dronessimulator.visualisation;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j;
import org.awaitility.Duration;
import org.awaitility.core.ConditionTimeoutException;
import org.inaetics.dronessimulator.common.Tuple;
import org.inaetics.dronessimulator.common.protocol.GameFinishedMessage;
import org.inaetics.dronessimulator.common.protocol.KillMessage;
import org.inaetics.dronessimulator.common.protocol.StateMessage;
import org.inaetics.dronessimulator.discovery.api.instances.DroneInstance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

@Log4j
public class GameIT extends ApplicationTest {
    public static final Duration TIMEOUT_GAME = new Duration(5, TimeUnit.MINUTES);
    private Game game;

    @Override
    public void start(Stage stage) throws Exception {
        game = new Game();
        game.start(stage);
    }

    @Before
    public void setUp() throws Exception {
        await().atMost(30, TimeUnit.SECONDS).until(() -> !getButtons().isEmpty());
        await().until(() -> game.getSubscriber() != null && !game.getSubscriber().getHandlers().isEmpty());
        Assert.assertFalse(game.getSubscriber().getHandlers().get(GameFinishedMessage.class).isEmpty());
        Assert.assertFalse(game.getSubscriber().getHandlers().get(KillMessage.class).isEmpty());
        Assert.assertFalse(game.getSubscriber().getHandlers().get(StateMessage.class).isEmpty());
    }

    @Test
    public void startTest() throws Exception {
        Map<String, Button> buttons = getButtons();
        WaitForAsyncUtils.waitForFxEvents();
        clickOn(buttons.get("Config"));
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        clickOn(buttons.get("Start"));
        WaitForAsyncUtils.waitForFxEvents();
        //Wait for the pop-up alert that the game has finished
        try {
            await().atMost(TIMEOUT_GAME).until(() -> !lookup((n -> n instanceof Label && ((Label) n).getText() != null && ((Label) n).getText().startsWith("The game is finished"))).queryAll().isEmpty());
        } catch (ConditionTimeoutException e) {
            Map<String, Long> dronesPerTeam = game.getEntities().entrySet().parallelStream()
                    .filter(gameEntity -> gameEntity.getValue() instanceof BasicDrone)
                    .map(gameEntity -> new Tuple<>(gameEntity.getKey(), (BasicDrone) gameEntity))
                    .filter(drone -> drone.getRight().getCurrentHP() > 0)
                    .collect(Collectors.groupingBy((s) -> game.getDiscoverer().getNode(new DroneInstance(s.getLeft())).getValues().get("teamname"), Collectors.counting()));
            log.error("Game timed out, the following drones are still available: " + dronesPerTeam);
            log.error("entities available: " + game.getEntities());
            throw e;
        }
        WaitForAsyncUtils.waitForFxEvents();
        clickOn("OK");
        WaitForAsyncUtils.waitForFxEvents();
        clickOn(buttons.get("Stop"));
    }

    private Map<String, Button> getButtons() {
        return lookup((p) -> p.getClass().equals(Button.class)).queryAll().parallelStream().map(n -> (Button) n).collect(Collectors.toMap(Labeled::getText, item -> item));
    }

}
