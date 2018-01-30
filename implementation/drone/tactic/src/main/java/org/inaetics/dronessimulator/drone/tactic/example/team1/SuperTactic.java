package org.inaetics.dronessimulator.drone.tactic.example.team1;

import lombok.extern.log4j.Log4j;
import org.inaetics.dronessimulator.common.Settings;
import org.inaetics.dronessimulator.common.protocol.TacticMessage;
import org.inaetics.dronessimulator.common.vector.D3Vector;
import org.inaetics.dronessimulator.drone.tactic.Tactic;
import org.inaetics.dronessimulator.drone.tactic.example.basic.BasicTacticCommunication;
import org.inaetics.dronessimulator.drone.tactic.example.basic.BasicTacticHeartbeat;
import org.inaetics.dronessimulator.drone.tactic.example.basic.ProtocolTags;
import org.inaetics.dronessimulator.pubsub.api.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.Float.NaN;

@Log4j
public class SuperTactic extends Tactic {

    private final UUID id = UUID.randomUUID();
    private volatile boolean isRadar = false;

    private final Map<UUID, D3Vector> friendsAsCommunicated = new HashMap<>(); //TODO add time validity

    private volatile Thread updateThread;

    private volatile D3Vector priorityTarget = null;

    private volatile int count = 0;

    @Override
    protected void initializeTactics() {
        log.info("Initializing tactics..");
        // determine whether i'm radarDrone or gunDrone
        if (radar != null) {
            isRadar = true;
        }

        updateThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    updateMessages();
                    sendUpdate();
                    Thread.sleep(10);
                    //Thread.yield();
                } catch (InterruptedException ie) {
                    //Thread.currentThread().interrupt();
                }
            }
        });
        updateThread.start();
    }

    public void sendUpdate() {
        //send own pos
        TacticMessage msg = new TacticMessage();
        msg.put("type", "position");
        msg.put("pos", gps.getPosition().toString());
        msg.put("uuid", this.id.toString());
        //TODO time

        //log.info("gogo pos");
        radio.send(msg);
    }

    public void updateMessages() {
        Message msg = radio.getMessages().poll();
        while (msg != null) {
            //log.info("got msg!!!");
            if (msg instanceof TacticMessage) {
                TacticMessage tm = (TacticMessage) msg;
                String type = tm.get("type");
                if ("position".equals(type)) {
                    UUID id = UUID.fromString(tm.get("uuid"));
                    D3Vector pos = D3Vector.fromString(tm.get("pos"));
                    synchronized (this) {
                        friendsAsCommunicated.put(id, pos);
                    }
                } else if ("target".equals(type)) {
                    priorityTarget = D3Vector.fromString(tm.get("pos"));
                } else {
                    log.warn("Unknown msg type " + type);
                }
            }
            msg = radio.getMessages().poll();
        }
    }

    @Override
    protected void calculateTactics() {
        long now = System.currentTimeMillis();

        if (isRadar) {
            D3Vector own = gps.getPosition();

            //remove possible friends from foes
            List<D3Vector> foes = radar.getRadar();
            synchronized (this) {
                for (D3Vector pos : this.friendsAsCommunicated.values()) {
                    if (count % 100 == 0) {
                        log.info("checking friend pos "  + pos.toString());
                    }
                    foes.removeIf((m) -> couldBeFriend(pos, now, m));
                }
            }

            //select foe as main target
            double minRange = Double.MAX_VALUE;
            D3Vector minPos = null;
            for (D3Vector p : foes) {
                double d = p.distance_between(own);
                if (d < minRange) {
                    minRange = d;
                    minPos = p;
                }
            }

            //communicate prio target
            if (minPos != null) {
                TacticMessage msg = new TacticMessage();
                msg.put("type", "target");
                msg.put("pos", minPos.toString());
                radio.send(msg);
            }


            if (count % 100 == 0) {
                int size;
                synchronized (this) {
                    size = this.friendsAsCommunicated.size();
                }
                log.info("nr of friends: " + size);
                log.info("nr of foes: " + foes.size());
                if (minPos != null) {
                    log.info("prio target at " + minPos.toString());
                }
            }

        }

        if (this.gun != null) {
            if (priorityTarget != null) {
                if (count % 100 == 0) {
                    log.info("FIRE !! at " + priorityTarget.toString());
                }
                this.gun.fireBullet(priorityTarget.sub(gps.getPosition()).toPoolCoordinate());
                moveTo(priorityTarget);
            } else {
                //moveRandom();
                engine.changeVelocity(new D3Vector());
                if (count % 100 == 0) {
                    log.info("no target no shooting");
                }
            }
        } else {
            //radar
            moveRandom();
        }

        count++;
    }

    private void moveRandom() {
        D3Vector m = new D3Vector(Math.random() * Settings.ARENA_WIDTH, Math.random()  * Settings.ARENA_DEPTH, Math.random() * Settings.ARENA_HEIGHT);
        this.engine.changeVelocity(m);
    }

    private void moveTo(D3Vector pos) {
        if (pos.sub(gps.getPosition()).length() < 2) {
            engine.changeVelocity(new D3Vector());
        } else {
            engine.changeVelocity(pos.sub(gps.getPosition()));
        }
    }

    private boolean couldBeFriend(D3Vector pos, long measurementTime, D3Vector measurement) {
        boolean proprablyFriends = false;
        //ignore time for now
        double dx = pos.getX() - measurement.getX();
        dx = Math.abs(dx);
        double dy = pos.getY() - measurement.getY();
        dy = Math.abs(dy);
        double dz = pos.getZ() - measurement.getZ();
        dz = Math.abs(dz);

        double allowedDiff = 50;
        if (dx < allowedDiff && dy < allowedDiff && dz < allowedDiff) {
            proprablyFriends = true;
        }
        return proprablyFriends;
    }

    @Override
    protected void finalizeTactics() {
        updateThread.interrupt();
    }

}
