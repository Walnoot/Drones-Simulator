package org.inaetics.dronessimulator.physicsenginewrapper.state;

import lombok.Getter;
import org.inaetics.dronessimulator.common.D3Vector;
import org.inaetics.dronessimulator.common.protocol.EntityType;

/**
 * A bullet game entity
 */
@Getter
public class Bullet extends GameEntity {
    /**
     * How much damage this bullet will do upon impact
     */
    private final int dmg;

    /**
     * Construction of a bullet entity
     * @param id The id of the bullet entity
     * @param dmg The damage of the bullet upon impact
     * @param position The starting position of the bullet
     * @param velocity The velocity of the bullet
     * @param acceleration The acceleration of the bullet
     */
    public Bullet(int id, int dmg, D3Vector position, D3Vector velocity, D3Vector acceleration) {
        super(id, position, velocity, acceleration);

        this.dmg = dmg;
    }

    /**
     * Return the protocol entity type of the game entity
     * @return Which type the game entity is in terms of the protocol
     */
    @Override
    public EntityType getType() {
        return EntityType.DRONE;
    }
}
