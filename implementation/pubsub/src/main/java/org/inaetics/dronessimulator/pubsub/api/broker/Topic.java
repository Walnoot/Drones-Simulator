package org.inaetics.dronessimulator.pubsub.api.broker;

/**
 * Interface for a topic which categorizes messages.
 */
public interface Topic {
    /**
     * Returns the name of this topic.
     * @return The name of this topic.
     */
    String getName();
}