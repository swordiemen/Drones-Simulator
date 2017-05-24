package org.inaetics.dronessimulator.gameengine;

import org.apache.log4j.Logger;
import org.inaetics.dronessimulator.common.D3Vector;
import org.inaetics.dronessimulator.common.protocol.MessageTopic;
import org.inaetics.dronessimulator.discovery.api.Discoverer;
import org.inaetics.dronessimulator.discovery.api.DuplicateName;
import org.inaetics.dronessimulator.discovery.api.Instance;
import org.inaetics.dronessimulator.discovery.api.discoverynode.DiscoveryNode;
import org.inaetics.dronessimulator.discovery.api.discoverynode.DiscoveryPath;
import org.inaetics.dronessimulator.discovery.api.discoverynode.NodeEventHandler;
import org.inaetics.dronessimulator.discovery.api.discoverynode.discoveryevent.AddedNode;
import org.inaetics.dronessimulator.discovery.api.discoverynode.discoveryevent.RemovedNode;
import org.inaetics.dronessimulator.gameengine.common.state.Drone;
import org.inaetics.dronessimulator.gameengine.gamestatemanager.IGameStateManager;
import org.inaetics.dronessimulator.gameengine.identifiermapper.IdentifierMapper;
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
 * Set up are: physicsengine, incoming command messages, queue between physicsengine and ruleprocessors,
 * discovery handler
 * and ruleprocessors
 */
public class GameEngine {
    /**
     * Physicsengine which is used in game engine
     */
    private volatile IPhysicsEngineDriver m_physicsEngineDriver;

    /**
     * Manager of state outside of physicsengine
     */
    private volatile IGameStateManager m_stateManager;
    /**
     * Ruleprocessors to handle any outgoing messages. Last ruleprocessor SendMessages send all messages off
     */
    private volatile IRuleProcessors m_ruleProcessors;

    private volatile Subscriber m_subscriber;

    private volatile IdentifierMapper m_id_mapper;

    private volatile Discoverer m_discoverer;

    /**
     * Message handler to handle any newly discovered or removed drones
     */
    private DiscoveryHandler discoveryHandler;

    /**
     * Message handler to handle incoming commands from drones
     */
    private SubscriberMessageHandler incomingHandler;

    /**
     * Start the wrapper. Setup all handlers, queues and engines. Connects everything if needed.
     */
    public void start() throws DuplicateName, IOException {
        Logger.getLogger(GameEngine.class).info("Starting Game Engine...");
        this.incomingHandler = new SubscriberMessageHandler(this.m_physicsEngineDriver, this.m_id_mapper, this.m_stateManager);
        this.discoveryHandler = new DiscoveryHandler(this.m_physicsEngineDriver, this.m_id_mapper);

        // Setup subscriber
        try {
            this.m_subscriber.addTopic(MessageTopic.MOVEMENTS);
            this.m_subscriber.addTopic(MessageTopic.STATEUPDATES);
        } catch(IOException e) {
            Logger.getLogger(GameEngine.class).fatal("Could not subscribe to topic " + MessageTopic.MOVEMENTS + ".");
        }

        this.m_subscriber.addHandler(Message.class, this.incomingHandler);


        // Setup discoverer
        m_discoverer.register(new Instance("service", "services", "gameengine" , new HashMap<>(), false));

        List<NodeEventHandler<AddedNode>> addHandlers = new ArrayList<>();

        addHandlers.add((AddedNode addedNodeEvent) -> {
            DiscoveryNode node = addedNodeEvent.getNode();
            DiscoveryPath path = node.getPath();

            if( path.startsWith(DiscoveryPath.type(DiscoveryPath.DRONES)) && path.isConfigPath()) {
                String protocolId = node.getId();
                int gameengineId = m_id_mapper.getNewGameEngineId();
                D3Vector position = new D3Vector();

                this.m_physicsEngineDriver.addNewEntity(new Drone(gameengineId, Drone.DRONE_MAX_HEALTH, position, new D3Vector(), new D3Vector()), protocolId);

            }

        });

        List<NodeEventHandler<RemovedNode>> removeHandlers = new ArrayList<>();

        removeHandlers.add((RemovedNode removedNodeEvent) -> {
            DiscoveryNode node = removedNodeEvent.getNode();
            DiscoveryPath path = node.getPath();

            if( path.startsWith(DiscoveryPath.type(DiscoveryPath.DRONES)) && path.isConfigPath()) {
                String protcolId = node.getId();

                this.m_physicsEngineDriver.removeEntity(protcolId);
            }
        });


        m_discoverer.addHandlers(true, addHandlers, Collections.emptyList(), removeHandlers);


        Logger.getLogger(GameEngine.class).info("Started Game Engine!");
    }

    /**
     * Stops the wrapper. Kills the engine and ruleprocessor threads
     * @throws Exception - Any exception which might happen during shutting down the wrapper
     */
    public void stop() throws Exception {
        Logger.getLogger(GameEngine.class).info("Stopped Game Engine!");
    }
}
