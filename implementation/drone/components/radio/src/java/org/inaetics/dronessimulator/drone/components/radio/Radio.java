package org.inaetics.dronessimulator.drone.components.radio;

import org.apache.log4j.Logger;
import org.inaetics.dronessimulator.common.protocol.TeamTopic;
import org.inaetics.dronessimulator.common.protocol.TextMessage;
import org.inaetics.dronessimulator.common.protocol.MessageTopic;
import org.inaetics.dronessimulator.common.protocol.StateMessage;
import org.inaetics.dronessimulator.drone.droneinit.DroneInit;
import org.inaetics.dronessimulator.pubsub.api.Message;
import org.inaetics.dronessimulator.pubsub.api.MessageHandler;
import org.inaetics.dronessimulator.pubsub.api.Topic;
import org.inaetics.dronessimulator.pubsub.api.subscriber.Subscriber;
import org.inaetics.dronessimulator.pubsub.api.publisher.Publisher;


import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Radio component wich makes drone to drone communication possible.
 */
public class Radio implements MessageHandler {
    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(Radio.class);

    /** Reference to Subscriber bundle */
    private volatile Subscriber m_subscriber;
    /** Reference to Publisher bundle */
    private volatile Publisher m_publisher;
    /** Reference to Drone Init bundle */
    private volatile DroneInit m_drone;
    /** Queue with received strings */
    private ConcurrentLinkedQueue<String> received_queue = new ConcurrentLinkedQueue<String>();

    private Topic topic;

    /**
     * FELIX CALLBACKS
     */
    public void start() {
        topic = new TeamTopic(m_drone.getTeamname());
        try {
            this.m_subscriber.addTopic(topic);
        } catch (IOException e) {
            logger.fatal(e);
        }
        this.m_subscriber.addHandler(StateMessage.class, this);
        this.m_subscriber.addHandler(TextMessage.class, this);
    }

    /**
     * Sends a text to other drones through the radio
     * @param text the text to send.
     */

    public void sendText(String text){
        logger.debug("Topic: " + topic.getName() + "Message sent: " + text);
        TextMessage msg = new TextMessage();
        msg.setText(text);
        try{
            m_publisher.send(topic, msg);
        } catch(IOException e){
            logger.fatal(e);
        }
    }

    /**
     * Retrieves the queue with received messages
     * @return queue with messages
     */
    public ConcurrentLinkedQueue<String> getMessages(){
        logger.debug("getmessages queue length: " + received_queue.size());
        ConcurrentLinkedQueue<String> r = received_queue;
        received_queue = new ConcurrentLinkedQueue<>();
        return r;
    }

    /**
     * -- MESSAGEHANDLER
     */
    public void handleMessage(Message message) {
        if (message instanceof TextMessage){
            TextMessage textMessage = (TextMessage) message;
            received_queue.add(textMessage.getText());

        }
    }

}
