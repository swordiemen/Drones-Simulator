package org.inaetics.dronessimulator.gameengine.test;

import org.inaetics.dronessimulator.common.vector.D3Vector;
import org.inaetics.dronessimulator.gameengine.common.Size;
import org.inaetics.dronessimulator.physicsengine.Entity;
import org.inaetics.dronessimulator.physicsengine.PhysicsEngine;
import org.inaetics.dronessimulator.physicsengine.PhysicsEngineEventObserver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class TestGameCollisionAndBroadcast {
    private MockObserver observer;
    private PhysicsEngine physicsEngine;

    private boolean hasStarted = false;
    private boolean hasEnded = false;
    private boolean hasBroadcast = false;

    @Before
    public void init() {
        observer = new MockObserver();
        physicsEngine = new PhysicsEngine();

        physicsEngine.setObserver(observer);
    }

    @Test
    public void collisionTest() {
        Entity e1 = new Entity(1, new Size(0.1, 0.1, 0.1), new D3Vector(-1, 0, 0), new D3Vector(1, 0, 0));
        Entity e2 = new Entity(2, new Size(0.1, 0.1, 0.1), new D3Vector(1, 0, 0), new D3Vector(-1, 0, 0));

        physicsEngine.addInsert(e1);
        physicsEngine.addInsert(e2);

        physicsEngine.start();
        physicsEngine.startEngine();

        try {
            Thread.sleep(1100);
            physicsEngine.stopEngine();
            physicsEngine.interrupt();
            physicsEngine.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assert hasStarted;
        assert hasEnded;
        assert hasBroadcast;
    }

    public class MockObserver implements PhysicsEngineEventObserver {

        @Override
        public void collisionStartHandler(Entity e1, Entity e2) {
            // Only expect it once
            Assert.assertEquals(false, hasStarted);
            if (e1.getEntityId() == 1) {
                Assert.assertEquals(2, e2.getEntityId());
                hasStarted = true;
            } else {
                Assert.assertEquals(2, e1.getEntityId());
                Assert.assertEquals(1, e2.getEntityId());
                hasStarted = true;
            }
        }

        @Override
        public void collisionStopHandler(Entity e1, Entity e2) {
            // Only expect it once
            Assert.assertEquals(false, hasEnded);

            if (e1.getEntityId() == 1) {
                Assert.assertEquals(2, e2.getEntityId());
                hasEnded = true;
            } else {
                Assert.assertEquals(2, e1.getEntityId());
                Assert.assertEquals(1, e2.getEntityId());
                hasEnded = true;
            }

        }

        @Override
        public void broadcastStateHandler(List<Entity> currentState) {
            Assert.assertEquals(2, currentState.size());

            hasBroadcast = true;
        }
    }
}
