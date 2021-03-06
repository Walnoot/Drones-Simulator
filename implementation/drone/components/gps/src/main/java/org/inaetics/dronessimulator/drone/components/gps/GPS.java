package org.inaetics.dronessimulator.drone.components.gps;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import org.inaetics.dronessimulator.common.Settings;
import org.inaetics.dronessimulator.common.protocol.MessageTopic;
import org.inaetics.dronessimulator.common.protocol.StateMessage;
import org.inaetics.dronessimulator.common.vector.D3PolarCoordinate;
import org.inaetics.dronessimulator.common.vector.D3Vector;
import org.inaetics.dronessimulator.drone.droneinit.DroneInit;
import org.inaetics.dronessimulator.pubsub.api.MessageHandler;
import org.inaetics.dronessimulator.pubsub.api.subscriber.Subscriber;

import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The GPS drone component
 */
@Log4j
@NoArgsConstructor //OSGi constructor
@AllArgsConstructor //Testing constructor
public class GPS implements MessageHandler<StateMessage> {
    private final Set<GPSCallback> callbacks = new HashSet<>();
    /** The Subscriber to use for receiving messages */
    private volatile Subscriber subscriber;
    /** The drone instance that can be used to get information about the current drone */
    private volatile DroneInit drone;
    private StateMessage previousMessage;

    /**
     * Last known position of this drone in the architecture
     */
    @Getter
    @Setter
    private volatile D3Vector position = new D3Vector();
    /**
     * Last known velocity of this drone in the architecture
     */
    @Getter
    @Setter
    private volatile D3Vector velocity = new D3Vector();
    /**
     * Last known acceleration of this drone in the architecture
     */
    @Getter
    @Setter
    private volatile D3Vector acceleration = new D3Vector();
    /**
     * Last known direction of this drone in the architecture
     */
    @Getter
    @Setter
    private volatile D3PolarCoordinate direction = new D3PolarCoordinate();


    /**
     * Start the GPS (called from Apache Felix). This initializes to what messages the subscriber should listen.
     */
    public void start() {
        try {
            this.subscriber.addTopic(MessageTopic.STATEUPDATES);
        } catch (IOException e) {
            log.fatal(e);
        }
        this.subscriber.addHandler(StateMessage.class, this);
    }

    public void handleMessage(StateMessage message) {
        if (message != null && message.getIdentifier().equals(this.drone.getIdentifier())) {
            //Prepare some variables
            double deltaNow = ChronoUnit.MILLIS.between(message.getTimestamp(), LocalTime.now());
            Optional<D3Vector> optionalPosition = message.getPosition();
            Optional<D3Vector> optionalVelocity = message.getVelocity();
            Optional<D3Vector> optionalAccelration = message.getAcceleration();
            Optional<D3PolarCoordinate> optionalDirection = message.getDirection();

            //Check if the message is recent
            if (previousMessage != null && deltaNow > Settings.getTickTime(ChronoUnit.MILLIS)) {
                double deltaMessages = ChronoUnit.MILLIS.between(previousMessage.getTimestamp(), message.getTimestamp());
                if (deltaMessages <= 0) {
                    //We cannot use two messages that were send at the same time since this will create a NaN. To avoid getting this multiple times, we do not update the last message.
                    return;
                }
                interpolateMessages(deltaNow, deltaMessages, message);
            } else {
                optionalPosition.ifPresent(this::setPosition);
                optionalAccelration.ifPresent(this::setAcceleration);
                optionalVelocity.ifPresent(this::setVelocity);
                optionalDirection.ifPresent(this::setDirection);
            }
            previousMessage = message;
            //Run callbacks
            callbacks.forEach(callback -> callback.run(message));
        }
    }

    /**
     * Interpolate the position, velocity and acceleration from the previous message and the current message. The estimated values are also set to the corresponding
     * fields. This method makes use of the other interpolate methods to achieve this.
     *
     * @param deltaNow      the difference between the moment the message was send and the current time.
     * @param deltaMessages the difference between the moment the previous message was send and the current message was send.
     * @param message       The current message
     */
    private void interpolateMessages(double deltaNow, double deltaMessages, StateMessage message) {
        Optional<D3Vector> optionalPosition = message.getPosition();
        Optional<D3Vector> optionalVelocity = message.getVelocity();
        Optional<D3Vector> optionalAccelration = message.getAcceleration();
        Optional<D3Vector> optionalPreviousPosition = previousMessage.getPosition();
        Optional<D3Vector> optionalPreviousVelocity = previousMessage.getVelocity();
        Optional<D3Vector> optionalPreviousAcceleration = previousMessage.getAcceleration();

        //Use the previous message to make a better guess of the location the drone probably is.
        if (optionalAccelration.isPresent() && optionalPreviousAcceleration.isPresent()) {
            D3Vector estimatedAcceleration = interpolateAcceleration(deltaNow, deltaMessages, optionalAccelration.get(), optionalPreviousAcceleration.get());

            if (optionalVelocity.isPresent() && optionalPreviousVelocity.isPresent()) {
                D3Vector estimatedVelocity = interpolateVelocity(deltaNow, deltaMessages, optionalVelocity.get(), optionalAccelration.get(), optionalPreviousVelocity.get(),
                        estimatedAcceleration);

                if (optionalPosition.isPresent() && optionalPreviousPosition.isPresent()) {
                    interpolatePosition(deltaNow, deltaMessages, optionalPosition.get(), optionalAccelration.get(), optionalPreviousPosition.get(), estimatedVelocity);
                }
            }
        }
    }

    private void interpolatePosition(double deltaNow, double deltaMessages, D3Vector optionalPosition, D3Vector optionalAccelration, D3Vector optionalPreviousPosition, D3Vector estimatedVelocity) {
        D3Vector deltaPosition;
        if (optionalAccelration.equals(D3Vector.ZERO)) {
            deltaPosition = D3Vector.ZERO;
        } else {
            deltaPosition = optionalPosition.normalize().scale(optionalPosition.sub(optionalPreviousPosition).length() / deltaMessages);
        }
        D3Vector estimatedPosition = optionalPosition.add(deltaPosition.scale(deltaNow / 1000)).add(estimatedVelocity.scale(Settings.getTickTime(ChronoUnit.SECONDS)));
        setPosition(estimatedPosition);
    }

    private D3Vector interpolateVelocity(double deltaNow, double deltaMessages, D3Vector optionalVelocity, D3Vector optionalAccelration, D3Vector optionalPreviousVelocity, D3Vector estimatedAcceleration) {
        D3Vector deltaVelocity;
        if (optionalAccelration.equals(D3Vector.ZERO)) {
            deltaVelocity = D3Vector.ZERO;
        } else {
            deltaVelocity = optionalVelocity.normalize().scale(optionalVelocity.sub(optionalPreviousVelocity).length() / deltaMessages);
        }
        D3Vector estimatedVelocity = optionalVelocity.add(deltaVelocity.scale(deltaNow / 1000)).add(estimatedAcceleration.scale(Settings.getTickTime(ChronoUnit.SECONDS)));
        setVelocity(estimatedVelocity);
        return estimatedVelocity;
    }

    private D3Vector interpolateAcceleration(double deltaNow, double deltaMessages, D3Vector acceleration, D3Vector previousAcceleration) {
        D3Vector deltaAcceleration;
        if (acceleration.equals(D3Vector.ZERO)) {
            deltaAcceleration = D3Vector.ZERO;
        } else {
            deltaAcceleration = acceleration.normalize().scale(acceleration.sub(previousAcceleration).length() / deltaMessages);
        }
        D3Vector estimatedAcceleration = acceleration.add(deltaAcceleration.scale(deltaNow / 1000));
        setAcceleration(estimatedAcceleration);
        return estimatedAcceleration;
    }

    /**
     * Submit a callback-function that is called after each state update is received. The StateMessage is a parameter for this callback.
     *
     * @param callback the function to be called
     */
    public final void registerCallback(GPSCallback callback) {
        callbacks.add(callback);
    }

    @FunctionalInterface
    public interface GPSCallback {
        void run(StateMessage newState);
    }
}
