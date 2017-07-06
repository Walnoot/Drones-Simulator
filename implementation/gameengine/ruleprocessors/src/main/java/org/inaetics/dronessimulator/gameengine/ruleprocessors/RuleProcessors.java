package org.inaetics.dronessimulator.gameengine.ruleprocessors;


import org.apache.log4j.Logger;
import org.inaetics.dronessimulator.architectureevents.ArchitectureEventController;
import org.inaetics.dronessimulator.common.architecture.SimulationAction;
import org.inaetics.dronessimulator.common.architecture.SimulationState;
import org.inaetics.dronessimulator.gameengine.common.gameevent.GameEngineEvent;
import org.inaetics.dronessimulator.gameengine.identifiermapper.IdentifierMapper;
import org.inaetics.dronessimulator.gameengine.physicsenginedriver.IPhysicsEngineDriver;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.Processor;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.RemoveStaleStateData;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.RemoveStrayBullets;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.SendMessages;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.deathmatch.CollisionRule;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.deathmatch.KillEntitiesRule;
import org.inaetics.dronessimulator.pubsub.api.publisher.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Rule processors service. The rule processors listen on events and act on them based on predefined rules.
 */
public class RuleProcessors extends Thread implements IRuleProcessors {
    private ArchitectureEventController m_architectureEventController;

    /** The physics engine driver to get events from. */
    private IPhysicsEngineDriver m_driver;

    /** The publisher to send messages to. */
    private Publisher m_publisher;

    /** The identifier mapper. */
    private IdentifierMapper m_id_mapper;

    /** Queue of the events to process. */
    private LinkedBlockingQueue<GameEngineEvent> incomingEvents;

    /** Active rules. Should end SendMessages to broadcast the messages to other subsystems. */
    private Processor[] rules;

    @Override
    public void start() {
        Logger.getLogger(RuleProcessors.class).info("Starting Rule Processors...");

        assert m_driver != null;

        this.incomingEvents = this.m_driver.getOutgoingQueue();

        this.rules = new Processor[]{ new CollisionRule()
                                    , new KillEntitiesRule()
                                    , new RemoveStrayBullets()
                                    , new RemoveStaleStateData()
                                    , new SendMessages(this.m_publisher, this.m_id_mapper)
                                    };

        m_architectureEventController.addHandler(SimulationState.INIT, SimulationAction.CONFIG, SimulationState.CONFIG,
                (SimulationState from, SimulationAction action, SimulationState to) -> {
                    configRules();
                }
        );

        super.start();
    }

    @Override
    public void run() {
        Logger.getLogger(RuleProcessors.class).info("Started RuleProcessors");
        while(!this.isInterrupted()) {
            GameEngineEvent msg;
            try {
                msg = incomingEvents.take();
            } catch (InterruptedException e) {
                Logger.getLogger(RuleProcessors.class).error("Interrupted while waiting for incoming event");
                this.interrupt();
                break;
            }

            if(msg != null) {
                this.processEventsForRules(Collections.singletonList(msg));
            } else {
                Logger.getLogger(RuleProcessors.class).error("Received event on incoming queue but was null!");
            }
        }

        Logger.getLogger(RuleProcessors.class).info("Ruleprocessors is shut down!");
    }

    /**
     * Processes the given events in each of the defined rules, in order.
     * @param events The events to process.
     */
    public void processEventsForRules(List<GameEngineEvent> events) {
        List<GameEngineEvent> allEvents = events;
        for(Processor rule : this.rules) {
            allEvents = this.processEventsForRule(allEvents, rule);
        }
    }

    /**
     * Processes the given events for the given rule.
     * @param events The events to process.
     * @param rule The rule to apply.
     * @return The list of events to pass to the next rule.
     */
    public List<GameEngineEvent> processEventsForRule(List<GameEngineEvent> events, Processor rule) {
        List<GameEngineEvent> result = new ArrayList<>(events.size() * 2);

        for(GameEngineEvent event : events) {
            List<GameEngineEvent> newEvents = rule.process(event);

            result.add(event);
            result.addAll(newEvents);
        }

        return result;
    }

    public void configRules() {
        for(Processor rule : rules) {
            rule.configRule();
        }
    }

    /**
     * Stops the rule processors.
     */
    public void quit() {
        Logger.getLogger(RuleProcessors.class).info("Shutting down ruleprocessors...");
        this.interrupt();
    }

    @Override
    @Deprecated
    public void destroy() {
        // Override destroy from thread to do nothing. Will be called as callback by Activator upon destroy of the bundle
    }
}