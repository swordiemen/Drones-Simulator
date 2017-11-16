package org.inaetics.dronessimulator.drone.components.engine;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j;
import org.inaetics.dronessimulator.common.protocol.MessageTopic;
import org.inaetics.dronessimulator.common.protocol.MovementMessage;
import org.inaetics.dronessimulator.drone.components.gps.GPS;
import org.inaetics.dronessimulator.common.vector.D3Vector;
import org.inaetics.dronessimulator.drone.droneinit.DroneInit;
import org.inaetics.dronessimulator.pubsub.api.publisher.Publisher;

import java.io.IOException;

/**
 * The engine component in a drone
 */
@Log4j
@NoArgsConstructor //This is the constructor for OSGi
@AllArgsConstructor //This is a constructor for test purposes.
public class Engine {
    /**
     * The max acceleration of this engine in m/s^2
     */
    public static final int MAX_ACCELERATION = 10;
    /**
     * The max velocity of this engine in m/s
     */
    private static final int MAX_VELOCITY = 20;
    /**
     * The Publisher bundle
     */
    private volatile Publisher m_publisher;
    /**
     * The Drone Init bundle
     */
    private volatile DroneInit m_drone;
    private volatile GPS m_gps;

    /**
     * Limit the acceleration
     *
     * @param input The acceleration to limit
     * @return The limited acceleration
     */
    public D3Vector limit_acceleration(D3Vector input) {
        D3Vector output = input;
        // Prevent that the acceleration exceeds te maximum acceleration
        if (input.length() > MAX_ACCELERATION) {
            double correctionFactor = MAX_ACCELERATION / input.length();
            output = input.scale(correctionFactor);
        }
        return output;
    }

    /**
     * Maximizes the acceleration in the same direction
     *
     * @param input The vector to scale to the maximal acceleration value
     * @return The vector in the same direction as input but length == max acceleration value
     */
    public D3Vector maximize_acceleration(D3Vector input) {
        D3Vector output = input;
        if (input.length() < MAX_ACCELERATION && input.length() != 0) {
            double correctionFactor = MAX_ACCELERATION / input.length();
            output = input.scale(correctionFactor);
        }
        return output;
    }


    /**
     * Limits the velocity when the maximum velocity is archieved.
     *
     * @param input acceleration as a D3Vector
     * @return optimized acceleration as a D3Vector
     */
    private D3Vector limit_velocity(D3Vector input) {
        D3Vector output = input;
        // Check velocity
        if (m_gps.getVelocity().length() >= MAX_VELOCITY && m_gps.getVelocity().add(input).length() >= m_gps.getVelocity().length()) {
            output = new D3Vector();
        }
        return output;
    }

    /**
     * Stagnate the acceleration when the velocity is at 90% of the maximum velocity.
     *
     * @param input acceleration as a D3Vector
     * @return optimized acceleration as a D3Vector
     */
    public D3Vector stagnate_acceleration(D3Vector input) {
        D3Vector output = input;
        // Change acceleration if velocity is close to the maximum velocity
        if (m_gps.getVelocity().length() >= (MAX_VELOCITY - (MAX_VELOCITY * 0.1))) {
            double factor = 0.25;
            D3Vector test_acceleration = m_gps.getAcceleration().scale(factor);
            if (m_gps.getVelocity().add(test_acceleration).length() <= m_gps.getVelocity().add(input).length()) {
                output = test_acceleration;
            }
        }
        return output;
    }

    /**
     * Send the new desired acceleration to the game-engine
     *
     * @param input_acceleration The new acceleration for the drone using this component
     */
    public void changeAcceleration(D3Vector input_acceleration) {
        D3Vector acceleration = input_acceleration;

        acceleration = this.limit_acceleration(acceleration);
        acceleration = this.limit_velocity(acceleration);
        acceleration = this.stagnate_acceleration(acceleration);

        MovementMessage msg = new MovementMessage();
        msg.setAcceleration(acceleration);
        msg.setIdentifier(m_drone.getIdentifier());

        try {
            m_publisher.send(MessageTopic.MOVEMENTS, msg);
        } catch (IOException e) {
            log.fatal(e);
        }
    }
}