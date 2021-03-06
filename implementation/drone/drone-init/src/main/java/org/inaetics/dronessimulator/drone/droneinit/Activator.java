package org.inaetics.dronessimulator.drone.droneinit;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.inaetics.dronessimulator.discovery.api.Discoverer;
import org.osgi.framework.BundleContext;

public class Activator extends DependencyActivatorBase {
    @Override
    public void init(BundleContext bundleContext, DependencyManager dependencyManager) throws Exception {
        dependencyManager.add(createComponent()
                .setInterface(DroneInit.class.getName(), null)
                .setImplementation(DroneInit.class)
                .add(createServiceDependency()
                        .setService(Discoverer.class)
                        .setRequired(true)
                )
                .setCallbacks("init", "start", "stop", "destroy")
        );
    }
}