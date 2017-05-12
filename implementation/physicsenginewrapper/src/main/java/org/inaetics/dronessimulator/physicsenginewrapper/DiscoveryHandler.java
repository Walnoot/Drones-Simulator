package org.inaetics.dronessimulator.physicsenginewrapper;

import lombok.AllArgsConstructor;
import org.inaetics.dronessimulator.common.D3Vector;
import org.inaetics.dronessimulator.physicsengine.Entity;
import org.inaetics.dronessimulator.physicsengine.EntityCreation;
import org.inaetics.dronessimulator.physicsengine.PhysicsEngine;
import org.inaetics.dronessimulator.physicsengine.Size;
import org.inaetics.dronessimulator.physicsenginewrapper.state.Drone;
import org.inaetics.dronessimulator.physicsenginewrapper.state.PhysicsEngineStateManager;

@AllArgsConstructor
public class DiscoveryHandler {
    private final PhysicsEngine physicsEngine;
    private final PhysicsEngineStateManager stateManager;

    public void newDrone(int id, D3Vector position) {
        this.stateManager.addEntityState(new Drone(1, 100, position, new D3Vector(), new D3Vector()));
        this.physicsEngine.addInsert(new EntityCreation(new Entity(id, new Size(10,10,10), position)));
    }
}