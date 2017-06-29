package org.inaetics.dronessimulator.gameengine;

import org.apache.log4j.Logger;
import org.inaetics.dronessimulator.common.D3Vector;
import org.inaetics.dronessimulator.common.protocol.*;
import org.inaetics.dronessimulator.discovery.api.Discoverer;
import org.inaetics.dronessimulator.discovery.api.DuplicateName;
import org.inaetics.dronessimulator.discovery.api.Instance;
import org.inaetics.dronessimulator.discovery.api.discoverynode.DiscoveryNode;
import org.inaetics.dronessimulator.discovery.api.DiscoveryPath;
import org.inaetics.dronessimulator.discovery.api.discoverynode.Group;
import org.inaetics.dronessimulator.discovery.api.discoverynode.NodeEventHandler;
import org.inaetics.dronessimulator.discovery.api.discoverynode.Type;
import org.inaetics.dronessimulator.discovery.api.discoverynode.discoveryevent.AddedNode;
import org.inaetics.dronessimulator.discovery.api.discoverynode.discoveryevent.RemovedNode;
import org.inaetics.dronessimulator.gameengine.common.state.Drone;
import org.inaetics.dronessimulator.gameengine.gamestatemanager.IGameStateManager;
import org.inaetics.dronessimulator.gameengine.identifiermapper.IdentifierMapper;
import org.inaetics.dronessimulator.gameengine.messagehandlers.*;
import org.inaetics.dronessimulator.gameengine.physicsenginedriver.IPhysicsEngineDriver;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.IRuleProcessors;
import org.inaetics.dronessimulator.pubsub.api.Message;
import org.inaetics.dronessimulator.pubsub.api.subscriber.Subscriber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Wrapper around PhysicsEngine. Sets up and connects all handlers with each other.
 * Set up are: physics engine, incoming command messages, queue between physics engine and rule processors,
 * discovery handler.
 */
public class GameEngine {
    /** Physics engine used in the game engine. */
    private volatile IPhysicsEngineDriver m_physicsEngineDriver;

    /** Manager of state outside of physicsengine. */
    private volatile IGameStateManager m_stateManager;

    /** Rule processors to handle any outgoing messages. The last rule processor SendMessages sends all messages off. */
    private volatile IRuleProcessors m_ruleProcessors;

    /** The subscriber to use. */
    private volatile Subscriber m_subscriber;

    /** The identifier mapper to use. */
    private volatile IdentifierMapper m_id_mapper;

    /** The discoverer to use. */
    private volatile Discoverer m_discoverer;

    /** Concrete message handlers. */
    private CollisionMessageHandler collisionMessageHandler;
    private DamageMessageHandler damageMessageHandler;
    private FireBulletMessageHandler fireBulletMessageHandler;
    private KillMessageHandler killMessageHandler;
    private MovementMessageHandler movementMessageHandler;
    private StateMessageHandler stateMessageHandler;

    /** The game engine instance to register. */
    private Instance discoveryInstance;

    /**
     * Starts the wrapper. Sets up all handlers, queues and engines. Connects everything if needed.
     */
    public void start() throws DuplicateName, IOException {
        Logger.getLogger(GameEngine.class).info("Starting Game Engine...");
        this.collisionMessageHandler = new CollisionMessageHandler(this.m_physicsEngineDriver, this.m_id_mapper, this.m_stateManager);
        this.damageMessageHandler = new DamageMessageHandler(this.m_physicsEngineDriver, this.m_id_mapper, this.m_stateManager);
        this.fireBulletMessageHandler = new FireBulletMessageHandler(this.m_physicsEngineDriver, this.m_id_mapper, this.m_stateManager);
        this.killMessageHandler = new KillMessageHandler(this.m_physicsEngineDriver, this.m_id_mapper, this.m_stateManager);
        this.movementMessageHandler = new MovementMessageHandler(this.m_physicsEngineDriver, this.m_id_mapper, this.m_stateManager);
        this.stateMessageHandler = new StateMessageHandler(this.m_physicsEngineDriver, this.m_id_mapper, this.m_stateManager);

        // Setup subscriber
        try {
            this.m_subscriber.addTopic(MessageTopic.MOVEMENTS);
            this.m_subscriber.addTopic(MessageTopic.STATEUPDATES);
        } catch(IOException e) {
            Logger.getLogger(GameEngine.class).fatal("Could not subscribe to topic " + MessageTopic.MOVEMENTS + ".");
        }

        this.m_subscriber.addHandler(CollisionMessage.class, this.collisionMessageHandler);
        this.m_subscriber.addHandler(DamageMessage.class, this.damageMessageHandler);
        this.m_subscriber.addHandler(FireBulletMessage.class, this.fireBulletMessageHandler);
        this.m_subscriber.addHandler(KillMessage.class, this.killMessageHandler);
        this.m_subscriber.addHandler(MovementMessage.class, this.movementMessageHandler);
        this.m_subscriber.addHandler(StateMessage.class, this.stateMessageHandler);

        // Setup discoverer
        discoveryInstance = new Instance(Type.SERVICE, Group.SERVICES, "gameengine" , new HashMap<>());
        m_discoverer.register(discoveryInstance);

        List<NodeEventHandler<AddedNode>> addHandlers = new ArrayList<>();

        addHandlers.add((AddedNode addedNodeEvent) -> {
            DiscoveryNode node = addedNodeEvent.getNode();
            DiscoveryPath path = node.getPath();

            if( path.startsWith(DiscoveryPath.type(Type.DRONE)) && path.isConfigPath()) {
                String protocolId = node.getId();
                int gameengineId = m_id_mapper.getNewGameEngineId();
                D3Vector position = new D3Vector(100, 100, 50);

                this.m_physicsEngineDriver.addNewEntity(new Drone(gameengineId, Drone.DRONE_MAX_HEALTH, position, new D3Vector(), new D3Vector()), protocolId);
                Logger.getLogger(GameEngine.class).info("Added new drone " + protocolId + " as " + gameengineId);
            }

        });

        List<NodeEventHandler<RemovedNode>> removeHandlers = new ArrayList<>();

        removeHandlers.add((RemovedNode removedNodeEvent) -> {
            DiscoveryNode node = removedNodeEvent.getNode();
            DiscoveryPath path = node.getPath();

            if( path.startsWith(DiscoveryPath.type(Type.DRONE)) && path.isConfigPath()) {
                String protocolId = node.getId();

                this.m_physicsEngineDriver.removeEntity(protocolId);
                Logger.getLogger(GameEngine.class).info("Removed drone " + protocolId);
            }
        });

        m_discoverer.addHandlers(true, addHandlers, Collections.emptyList(), removeHandlers);

        Logger.getLogger(GameEngine.class).info("Started Game Engine!");
    }

    /**
     * Stops the wrapper. Kills the engine and rule processor threads.
     * @throws Exception - Any exception which might happen during shutting down the wrapper.
     */
    public void stop() throws Exception {
        this.m_discoverer.unregister(discoveryInstance);
        Logger.getLogger(GameEngine.class).info("Stopped Game Engine!");
    }
}