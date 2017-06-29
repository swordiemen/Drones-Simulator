package org.inaetics.dronessimulator.physicsengine;

import org.apache.log4j.Logger;
import org.inaetics.dronessimulator.common.D3Vector;
import org.inaetics.dronessimulator.physicsengine.entityupdate.EntityUpdate;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A very simple physics engine where gravity holds, all entities are 1kg and without other interacting forces (e.g.
 * collision- and air friction forces). Collisions are detected by the simple hitboxes computed by the size of the
 * entity. On a fixed time interval the current state is broadcast to the observer. The start and end of every collision
 * are also broadcast to the observer.
 *
 * @threadsafe
 */
public class PhysicsEngine extends Thread implements IPhysicsEngine {
    /** Gravity in meters/second^2. */
    public static final D3Vector GRAVITY = new D3Vector(0, 0, 0); // TODO: Fix gravity (-9.81)

    /** Time the current time step started. In milliseconds. */
    private long current_step_started_at_ms;

    /** Time the last broadcast was sent. In milliseconds. */
    private long last_state_broadcast_at_ms;

    /** Time between broadcasts of the current state. In milliseconds. */
    private long broadcast_state_every_ms;

    /** Whether the physics engine has quit. (Only true if the engine has started and then quit.) */
    private volatile boolean quit;

    /** Whether the physics engine is started. */
    private final AtomicBoolean started;

    /** The entity manager which manages all changes and state of entities. */
    private final EntityManager entityManager;

    /** A map containing all collisions between entity ids which have started. */
    private final HashMap<Integer, Set<Integer>> currentCollisions;

    /** The observer to which any events are sent. */
    private PhysicsEngineEventObserver observer;

    /**
     * Creates the physics engine object.
     * Before you start the engine, you MUST set an observer using the setObserver method.
     */
    public PhysicsEngine() {
        this.current_step_started_at_ms = System.currentTimeMillis();
        this.last_state_broadcast_at_ms = this.current_step_started_at_ms;
        this.broadcast_state_every_ms = -1;

        this.quit = false;
        this.started = new AtomicBoolean(false);

        this.currentCollisions = new HashMap<>();
        this.entityManager = new EntityManager(this.currentCollisions);

        this.observer = null;
    }

    /**
     * Returns the entity manager used by the physics engine.
     * @return The entity manager.
     */
    public EntityManager getEntityManager() {
        return this.entityManager;
    }

    @Override
    public void setTimeBetweenBroadcastms(long broadcast_state_every_ms) {
        this.broadcast_state_every_ms = broadcast_state_every_ms;
    }

    @Override
    public void setObserver(PhysicsEngineEventObserver observer) {
        this.observer = observer;
    }

    /**
     * Calculates the environment forces that act on the entity. Currently only gravity is supported.
     * @param entity The entity on which environment forces act
     * @return The total resulting vector of all environment forces.
     */
    private D3Vector environmentForces(Entity entity) {
        return GRAVITY;
    }

    /**
     * Determine the time step for the current loop.
     * @return The time since the last loop in seconds.
     */
    private double stageTimeStep() {
        long current_ms = System.currentTimeMillis();
        long timestep_ms = current_ms - current_step_started_at_ms;
        double timestep_s = ((float) timestep_ms) / 1000;
        this.current_step_started_at_ms = current_ms;

        return timestep_s;
    }

    /**
     * Move all entities respecting collisions. Entities can move through each other, but collisions do spawn events
     * which are sent to the observer.
     * @param timestep_s The time since the last move in seconds.
     */
    private void stageMove(double timestep_s) {
        Map<Integer, Entity> entities = this.entityManager.getEntities();

        for(Map.Entry<Integer, Entity> e1 : entities.entrySet()) {
            Entity entity = e1.getValue();
            int e1Id = entity.getId();

            // Set the next place the entity will move to with new velocity
            D3Vector nextAcceleration = entity.getAcceleration();
            D3Vector nextVelocity = entity.nextVelocity(environmentForces(entity).add(nextAcceleration), timestep_s);
            D3Vector nextPosition = entity.nextPosition(nextVelocity, timestep_s);

            entity.setAcceleration(nextAcceleration);
            entity.setVelocity(nextVelocity);
            entity.setPosition(nextPosition);

            // Check for collisions for this entity
            for(Map.Entry<Integer, Entity> e2 : entities.entrySet()) {
                Entity otherEntity = e2.getValue();
                int e2Id = otherEntity.getId();

                if(entity.getId() != otherEntity.getId()) {
                    // If the entity is colliding with another entity
                    Set<Integer> collisionsE1 = currentCollisions.get(e1Id);
                    Set<Integer> collisionsE2 = currentCollisions.get(e2Id);

                    if(entity.collides(otherEntity)) {
                        boolean startedE1WithE2 = false;
                        boolean startedE2WithE1 = false;

                        // Only add to hashmap if the collision was not present yet
                        if(!collisionsE1.contains(e2Id)) {
                            collisionsE1.add(e2Id);
                            startedE1WithE2 = true;
                        }

                        if(!collisionsE2.contains(e1Id)) {
                            collisionsE2.add(e1Id);
                            startedE2WithE1 = true;
                        }

                        // If the collision wasn't happening yet
                        if(observer != null && (startedE1WithE2 || startedE2WithE1)) {
                            //This collision is new and has just started
                            observer.collisionStartHandler(Entity.deepcopy(entity), Entity.deepcopy(otherEntity));
                        }
                    } else {
                        //These entities are not colliding, so remove any collisions if there were any
                        boolean e1CollidedWithe2 = collisionsE1.remove(e2Id);
                        boolean e2CollidedWithe1 = collisionsE2.remove(e1Id);

                        if(observer != null && (e1CollidedWithe2 || e2CollidedWithe1)) {
                            //This collision has just ended
                            observer.collisionStopHandler(Entity.deepcopy(entity), Entity.deepcopy(otherEntity));
                        }
                    }
                }
            }


        }
    }

    /**
     * Broadcasts the state if necessary for this loop.
     */
    private void stageBroadcastState() {
        long last_broadcast_ms = this.current_step_started_at_ms - this.last_state_broadcast_at_ms;

        if(this.broadcast_state_every_ms >= 0 && last_broadcast_ms >= this.broadcast_state_every_ms) {
            observer.broadcastStateHandler(this.entityManager.copyState());
            this.last_state_broadcast_at_ms = this.current_step_started_at_ms;
        }

    }

    /**
     * Starts the current physics engine. If it is already started, no action is taken.
     * @threadsafe
     */
    private void runServer() {
        Thread t = Thread.currentThread();

        if(started.compareAndSet(false, true)) {
            Logger.getLogger(PhysicsEngine.class).info("Started PhysicsEngine!");

            quit = false;

            this.current_step_started_at_ms = System.currentTimeMillis();
            this.last_state_broadcast_at_ms = this.current_step_started_at_ms;
            
            while(!t.isInterrupted()) {
                double timestep_s = this.stageTimeStep();
                this.entityManager.processChanges();
                this.stageMove(timestep_s);
                this.stageBroadcastState();
            }

            started.set(false);
            quit = true;

            Logger.getLogger(PhysicsEngine.class).info("PhysicsEngine has shutdown");
        }
    }

    /**
     * Starts the physics engine thread.
     */
    public void start() {
        Logger.getLogger(PhysicsEngine.class).info("Starting PhysicsEngine...");

        super.start();
    }

    /**
     * Method to start the thread. Do not call directly, use PhysicsEngine.start().
     * @requires this.getObserver() != null
     * @threadsafe
     */
    public void run() {
        assert this.observer != null;

        this.runServer();
    }

    @Override
    public void addInserts(Collection<Entity> creations) {
        this.entityManager.addInserts(creations);
    }

    @Override
    public void addInsert(Entity creation) {
        this.entityManager.addInsert(creation);
    }

    @Override
    public void addUpdates(Integer entityId, Collection<EntityUpdate> updates) {
        this.entityManager.addUpdates(entityId, updates);
    }

    @Override
    public void addUpdate(Integer entityId, EntityUpdate update) {
       this.entityManager.addUpdate(entityId, update);
    }

    @Override
    public void addRemovals(Collection<Integer> removals) {
        this.entityManager.addRemovals(removals);
     }

    @Override
    public void addRemoval(Integer removal) {
        this.entityManager.addRemoval(removal);
    }

    /**
     * Tell the engine thread to quit.
     * @threadsafe
     */
    public void quit() {
        Logger.getLogger(PhysicsEngine.class).info("Turning off physics engine...");
        this.interrupt();
    }

    /**
     * Returns whether the physics engine has started.
     * @threadsafe
     * @return Whether the physics engine is currently running.
     */
    public boolean hasStarted() {
        return this.started.get();
    }

    /**
     * Returns whether the physics engine has started and then quit.
     * @threadsafe
     * @return Whether the physics engine has quit.
     */
    public boolean hasQuit() {
        return this.quit;
    }

    @Override
    @Deprecated
    public void destroy() {
    }
}