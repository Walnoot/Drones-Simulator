package org.inaetics.dronessimulator.gameengine.ruleprocessors;


import org.apache.log4j.Logger;
import org.inaetics.dronessimulator.gameengine.common.gameevent.GameEngineEvent;
import org.inaetics.dronessimulator.gameengine.identifiermapper.IdentifierMapper;
import org.inaetics.dronessimulator.gameengine.physicsenginedriver.IPhysicsEngineDriver;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.Processor;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.RemoveStrayBullets;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.SendMessages;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.deathmatch.CollisionRule;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.deathmatch.KillEntitiesRule;
import org.inaetics.dronessimulator.pubsub.api.publisher.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class RuleProcessors extends Thread implements IRuleProcessors {
    private IPhysicsEngineDriver m_driver;
    private Publisher m_publisher;
    private IdentifierMapper m_id_mapper;

    private LinkedBlockingQueue<GameEngineEvent> incomingEvents;

    private Processor[] rules;

    @Override
    public void start() {
        Logger.getLogger(RuleProcessors.class).info("Starting Rule Processors...");

        assert m_driver != null;

        this.incomingEvents = this.m_driver.getOutgoingQueue();

        this.rules = new Processor[]{ new CollisionRule()
                                    , new KillEntitiesRule()
                                    , new RemoveStrayBullets()
                                    , new SendMessages(this.m_publisher, this.m_id_mapper)
                                    };
        super.start();
    }

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

    public void processEventsForRules(List<GameEngineEvent> events) {
        for(Processor rule : this.rules) {
            events = this.processEventsForRule(events, rule);
        }
    }

    public List<GameEngineEvent> processEventsForRule(List<GameEngineEvent> events, Processor rule) {
        List<GameEngineEvent> result = new ArrayList<>(events.size() * 2);

        for(GameEngineEvent event : events) {
            List<GameEngineEvent> newEvents = rule.process(event);

            result.add(event);
            result.addAll(newEvents);
        }

        return result;
    }

    public void quit() {
        Logger.getLogger(RuleProcessors.class).info("Shutting down ruleprocessors...");
        this.interrupt();
    }

    @Override
    @Deprecated
    public void destroy() {
    }
}
