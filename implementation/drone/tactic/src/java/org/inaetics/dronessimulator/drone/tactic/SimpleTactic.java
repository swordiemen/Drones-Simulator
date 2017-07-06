package org.inaetics.dronessimulator.drone.tactic;

import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.ServiceDependency;
import org.inaetics.dronessimulator.common.D3Vector;
import org.inaetics.dronessimulator.drone.components.engine.Engine;
import org.inaetics.dronessimulator.drone.components.gps.GPS;
import org.inaetics.dronessimulator.drone.components.gun.Gun;
import org.inaetics.dronessimulator.drone.components.radar.Radar;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by mart on 17-5-17.
 */
public class SimpleTactic extends Tactic {
    protected volatile Radar m_radar;
    protected volatile GPS m_gps;
    protected volatile Engine m_engine;
    protected volatile Gun m_gun;

    private static final int MAX_DEVIATION_POSTION = 1024;
    private static final int MAX_Z_DEVIATION_POSTION = 100;
    /**
     *  -- IMPLEMENT FUNCTIONS
     */
    public List<ServiceDependency> getComponents(DependencyManager dm){
        List<ServiceDependency> components = new ArrayList<ServiceDependency>()   ;
        components.add(dm.createServiceDependency()
                        .setService(Radar.class)
                        .setRequired(true)
                );
        components.add(dm.createServiceDependency()
                        .setService(GPS.class)
                        .setRequired(true)
                );
        components.add(dm.createServiceDependency()
                        .setService(Engine.class)
                        .setRequired(true)
                );
        components.add(dm.createServiceDependency()
                        .setService(Gun.class)
                        .setRequired(true)
                );
        return components;
    }

    void calculateTactics(){
        this.calculateAcceleration();
        this.calculateGun();
    }

    /**
     *  -- FUNCTIONS
     */

    /**
     *
     * @param input_acceleration
     * @return
     */
    private D3Vector accelerateByNoMovement(D3Vector input_acceleration){
        D3Vector output_acceleration = input_acceleration;
        if (m_gps.getAcceleration().length() == 0 && m_gps.getVelocity().length() == 0){
            double x = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());
            double y = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());
            double z = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());
            output_acceleration =  new D3Vector(x, y, z);
        }
        return output_acceleration;
    }

    /**
     *
     * @param input_acceleration
     * @return
     */
    private D3Vector accelerateForWall(D3Vector input_acceleration){
        D3Vector output_acceleration = input_acceleration;
        double aantal_seconden_tot_nul = m_gps.getVelocity().length() / m_engine.getMaxAcceleration();
        D3Vector berekende_position = m_gps.getVelocity().scale(0.5).scale(aantal_seconden_tot_nul).add(m_gps.getPosition());

        if(berekende_position.getX() >= MAX_DEVIATION_POSTION ||
                berekende_position.getX() <= 0 ||
                berekende_position.getY() >= MAX_DEVIATION_POSTION ||
                berekende_position.getY() <= 0 ||
                berekende_position.getZ() >= MAX_Z_DEVIATION_POSTION ||
                berekende_position.getZ() <= 0){
            output_acceleration = m_engine.maximize_acceleration(m_engine.limit_acceleration(m_gps.getVelocity().scale(-1)));
        }
        return output_acceleration;
    }

    /**
     *
     * @param input_acceleration
     * @return
     */
    private D3Vector accelerateAfterWall(D3Vector input_acceleration){
        D3Vector output_acceleration = input_acceleration;
        // Check positions | if maximum deviation is archieved then change acceleration in opposite direction
        double x = output_acceleration.getX();
        double y = output_acceleration.getY();
        double z = output_acceleration.getZ();

        if(m_gps.getPosition().getX() >= MAX_DEVIATION_POSTION ||
                m_gps.getPosition().getX() <= 0){
            y = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());
            z = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());

            if(m_gps.getPosition().getX() >= MAX_DEVIATION_POSTION){
                x = - m_engine.getMaxAcceleration();
            }
            else if(m_gps.getPosition().getX() <= 0){
                x = m_engine.getMaxAcceleration();
            }
        }

        if(m_gps.getPosition().getY() >= MAX_DEVIATION_POSTION || m_gps.getPosition().getY() <= 0){
            x = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());
            z = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());

            if(m_gps.getPosition().getY() >= MAX_DEVIATION_POSTION){
                y = - m_engine.getMaxAcceleration();
            }
            else if(m_gps.getPosition().getY() <= 0){
                y = m_engine.getMaxAcceleration();
            }
        }

        if(m_gps.getPosition().getZ() >= MAX_Z_DEVIATION_POSTION || m_gps.getPosition().getZ() <= 0){
            x = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());
            y = ThreadLocalRandom.current().nextDouble(-m_engine.getMaxAcceleration(), m_engine.getMaxAcceleration());

            if(m_gps.getPosition().getZ() >= MAX_Z_DEVIATION_POSTION){
                z = - m_engine.getMaxAcceleration();
            }
            else if(m_gps.getPosition().getZ() <= 0){
                z = m_engine.getMaxAcceleration();
            }
        }

        output_acceleration = new D3Vector(x,y,z);
        output_acceleration = m_engine.maximize_acceleration(output_acceleration);
        return output_acceleration;
    }

    private void calculateAcceleration(){
        D3Vector output_acceleration = m_engine.maximize_acceleration(m_gps.getAcceleration());
        output_acceleration = this.accelerateByNoMovement(output_acceleration);
        output_acceleration = this.accelerateByEngine(output_acceleration);
        output_acceleration = this.accelerateForWall(output_acceleration);
        output_acceleration = this.accelerateAfterWall(output_acceleration);
        output_acceleration = m_engine.limit_acceleration(output_acceleration);
        m_engine.changeAcceleration(output_acceleration);
    }

    private void calculateGun(){
        Optional<D3Vector> target = m_radar.getNearestTarget();
        if(target.isPresent()){
            if(target.get().distance_between(m_gps.getPosition()) <= m_gun.getMaxDistance()){
                m_gun.fireBullet(target.get().sub(m_gps.getPosition()).toPoolCoordinate());
            }
        }
    }


}
