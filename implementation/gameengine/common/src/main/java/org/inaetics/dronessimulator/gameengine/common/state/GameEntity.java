package org.inaetics.dronessimulator.gameengine.common.state;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.inaetics.dronessimulator.common.D3Vector;
import org.inaetics.dronessimulator.common.protocol.EntityType;

/**
 * An entity in the physicsengine with some added game state
 */
@AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
public abstract class GameEntity<C extends GameEntity<C>> {
    /**
     * Id of the game entity. Should match with an entity id in the engine
     */
    private final int entityId;
    /**
     * Position of the entity in the engine
     */
    private volatile D3Vector position;
    /**
     * Velocity of the entity in the engine
     */
    private volatile D3Vector velocity;
    /**
     * Acceleration of the entity in the engine
     */
    private volatile D3Vector acceleration;

    /**
     * Get the type of the game entity in terms of the shared protocol
     * @return Type of entity according to protocol
     */
    public abstract EntityType getType();

    public int getEntityId() {
        return entityId;
    }

    public D3Vector getPosition() {
        return position;
    }

    public void setPosition(D3Vector position) {
        this.position = position;
    }

    public D3Vector getVelocity() {
        return velocity;
    }

    public void setVelocity(D3Vector velocity) {
        this.velocity = velocity;
    }

    public D3Vector getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(D3Vector acceleration) {
        this.acceleration = acceleration;
    }

    public abstract C deepCopy();
}
