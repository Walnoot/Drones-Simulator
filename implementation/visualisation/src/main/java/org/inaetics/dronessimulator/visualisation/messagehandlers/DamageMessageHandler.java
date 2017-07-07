package org.inaetics.dronessimulator.visualisation.messagehandlers;

import org.inaetics.dronessimulator.pubsub.api.Message;
import org.inaetics.dronessimulator.pubsub.api.MessageHandler;

/**
 * The damage message handler class. Implements what to do when damage is inflicted.
 */
public class DamageMessageHandler implements MessageHandler {
    /**
     * Empty method
     * @param message The received message.
     */
    @Override
    public void handleMessage(Message message) {
        // Do nothing on damage
    }
}
