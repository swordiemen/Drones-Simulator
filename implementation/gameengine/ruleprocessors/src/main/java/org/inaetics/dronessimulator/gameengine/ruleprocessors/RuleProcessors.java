package org.inaetics.dronessimulator.gameengine.ruleprocessors;


import lombok.extern.log4j.Log4j;
import org.inaetics.dronessimulator.architectureevents.ArchitectureEventController;
import org.inaetics.dronessimulator.common.Settings;
import org.inaetics.dronessimulator.common.TimeoutTimer;
import org.inaetics.dronessimulator.common.architecture.SimulationAction;
import org.inaetics.dronessimulator.common.architecture.SimulationState;
import org.inaetics.dronessimulator.gameengine.common.gameevent.GameEngineEvent;
import org.inaetics.dronessimulator.gameengine.identifiermapper.IdentifierMapper;
import org.inaetics.dronessimulator.gameengine.physicsenginedriver.IPhysicsEngineDriver;
import org.inaetics.dronessimulator.gameengine.ruleprocessors.rules.Rule;
import org.inaetics.dronessimulator.pubsub.api.publisher.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Rule processors service. The rule processors listen on events and act on them based on predefined rules.
 */
@Log4j
public class RuleProcessors extends Thread implements IRuleProcessors {
    public static final TimeoutTimer INTERVAL_RULES_TIMEOUT = new TimeoutTimer(Settings.TICK_TIME * 10); //Run interval rules once every 10 iterations.
    private ArchitectureEventController m_architectureEventController;

    /**
     * The physics engine driver to get events from.
     */
    private volatile IPhysicsEngineDriver m_driver;
    private volatile Publisher m_publisher;
    private volatile IdentifierMapper m_id_mapper;

    /**
     * Queue of the events to process.
     */
    private LinkedBlockingQueue<GameEngineEvent> incomingEvents;

    /**
     * Active rules. Should end SendMessages to broadcast the messages to other subsystems.
     */
    private List<Rule> rules;
    private List<Rule> intervalRules;

    @Override
    public void start() {
        log.info("Starting Rule Processors...");

        assert m_driver != null;

        this.incomingEvents = this.m_driver.getOutgoingQueue();

        this.rules = RuleSets.getRulesForGameMode(Settings.GAME_MODE, this.m_publisher, this.m_id_mapper);
        this.intervalRules = RuleSets.getIntervalRulesForGameMode(Settings.GAME_MODE, this.m_publisher, this
                .m_id_mapper);

        m_architectureEventController.addHandler(SimulationState.INIT, SimulationAction.CONFIG, SimulationState.CONFIG, (from, action, to) -> configRules());
        //When the user presses start, reset the Interval rules timeout
        m_architectureEventController.addHandler(SimulationState.CONFIG, SimulationAction.START, SimulationState.RUNNING, (f, a, t) -> INTERVAL_RULES_TIMEOUT.reset());

        super.start();
    }

    @Override
    public void run() {
        log.info("Started RuleProcessors");
        while (!this.isInterrupted()) {
            GameEngineEvent msg;
            try {
                msg = incomingEvents.take();
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for incoming event");
                this.interrupt();
                break;
            }

            if (msg != null) {
                this.processEventsForRules(this.rules, Collections.singletonList(msg));
                if (INTERVAL_RULES_TIMEOUT.timeIsExceeded()) {
                    INTERVAL_RULES_TIMEOUT.reset();
                    log.info("Run inteval rules");
                    processEventsForRules(this.intervalRules, Collections.singletonList(msg));
                }
            } else {
                log.error("Received event on incoming queue but was null!");
            }
        }

        log.info("Ruleprocessors is shut down!");
    }

    /**
     * Processes the given events in each of the defined rules, in order.
     *
     * @param events The events to process.
     */
    public void processEventsForRules(List<Rule> rulesToProcess, List<GameEngineEvent> events) {
        List<GameEngineEvent> allEvents = events;
        for (Rule rule : rulesToProcess) {
            allEvents = this.processEventsForRule(allEvents, rule);
        }
    }

    /**
     * Processes the given events for the given rule.
     *
     * @param events The events to process.
     * @param rule   The rule to apply.
     * @return The list of events to pass to the next rule.
     */
    public List<GameEngineEvent> processEventsForRule(List<GameEngineEvent> events, Rule rule) {
        List<GameEngineEvent> result = new ArrayList<>(events.size() * 2);

        for (GameEngineEvent event : events) {
            result.addAll(rule.process(event));
        }

        return result;
    }

    public void configRules() {
        for (Rule rule : rules) {
            rule.configRule();
        }
        for (Rule rule : intervalRules) {
            rule.configRule();
        }
    }

    /**
     * Stops the rule processors.
     */
    public void quit() {
        log.info("Shutting down ruleprocessors...");
        this.interrupt();
    }

    @Override
    public void destroy() {
        // Override destroy from thread to do nothing. Will be called as callback by Activator upon destroy of the bundle
    }
}
