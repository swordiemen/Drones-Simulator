package org.inaetics.dronessimulator.discovery.etcd;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdErrorCode;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.inaetics.dronessimulator.discovery.api.DiscoveredConfig;
import org.inaetics.dronessimulator.discovery.api.Discoverer;
import org.inaetics.dronessimulator.discovery.api.DuplicateName;
import org.inaetics.dronessimulator.discovery.api.Instance;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Discoverer implementation which uses etcd.
 */
public class EtcdDiscoverer implements Discoverer {
    /** Prefix for all etcd paths. */
    private static final String PATH_PREFIX = "/";

    /** Location where discoverable configs can be found. */
    private static final String DISCOVERABLE_CONFIG_DIR = "discoverable_config";

    /** The instances registered through this discoverer. */
    Map<Instance, String> myInstances;

    /** The etcd client instance. */
    EtcdClient client;

    /**
     * Instantiates a new etcd discoverer and connects to etcd using the given URI.
     * @param uri The URI to connect to etcd.
     */
    public EtcdDiscoverer(URI uri) {
        this.myInstances = new HashMap<>();
        this.client = new EtcdClient(uri);

        // Create discoverable config directory
        this.client.putDir(buildPath(DISCOVERABLE_CONFIG_DIR));
    }

    @Override
    public void register(Instance instance) throws DuplicateName, IOException {
        String path = buildInstancePath(instance);

        EtcdResponsePromise<EtcdKeysResponse> promise = this.client.putDir(path).prevExist(false).send();
        Throwable exception = promise.getException();

        // Check if this instance already exists
        if (exception instanceof EtcdException && ((EtcdException) exception).isErrorCode(EtcdErrorCode.NodeExist)) {
            throw new DuplicateName(exception);
        } else if (exception != null) {
            throw new IOException(exception);
        } else {
            // Set properties
            instance.getProperties().forEach((key, value) -> this.client.put(buildPath(path, key), value));

            // Set discoverable config if needed
            String discoverablePath = null;

            if (instance.isConfigDiscoverable()) {
                discoverablePath = this.registerDiscoverableConfig(instance);
            }

            // Register instance
            this.myInstances.put(instance, discoverablePath);
        }
    }

    /**
     * Registers the instance as a discoverable config. Places a reference to the instance in a special etcd directory.
     * @param instance The instance to register.
     * @return The path to the key.
     * @throws IOException An error occured.
     */
    private String registerDiscoverableConfig(Instance instance) throws IOException {
        if (!this.myInstances.containsKey(instance)) {
            String instancePath = buildInstancePath(instance);
            String path = buildPath(DISCOVERABLE_CONFIG_DIR);

            EtcdResponsePromise<EtcdKeysResponse> promise = this.client.post(path, instancePath).send();
            EtcdKeysResponse keys = promise.getNow();
            return keys.node.key;
        } else {
            return this.myInstances.get(instance);
        }
    }

    @Override
    public void unregister(Instance instance) throws IOException {
        String path = buildInstancePath(instance);

        String discoverablePath = this.myInstances.getOrDefault(instance, null);

        if (instance.isConfigDiscoverable() && discoverablePath != null) {
            this.client.delete(discoverablePath).send();
        }

        this.client.deleteDir(path).recursive().send();
        this.myInstances.remove(instance);
    }

    @Override
    public Map<String, Collection<String>> find(String type) {
        Map<String, Collection<String>> forType = new HashMap<>();

        String path = buildPath(type);

        try {
            EtcdResponsePromise<EtcdKeysResponse> promise = this.client.getDir(path).recursive().send();
            EtcdKeysResponse keys = promise.getNow();

            if (keys != null) {
                keys.node.nodes.forEach(groupNode -> {
                    Collection<String> forGroup = new HashSet<>();
                    forType.put(getDirName(groupNode.key), forGroup);
                    groupNode.nodes.forEach(node -> {
                        forGroup.add(getDirName(node.key));
                    });
                });
            }
        } catch (IOException ignored) {
            // Just return an empty map
        }

        return forType;
    }

    @Override
    public Collection<String> find(String type, String group) {
        Collection<String> forGroup = new HashSet<>();

        String path = buildPath(type, group);

        try {
            EtcdResponsePromise<EtcdKeysResponse> promise = this.client.getDir(path).recursive().send();
            EtcdKeysResponse keys = promise.getNow();

            if (keys != null) {
                keys.node.nodes.forEach(node -> forGroup.add(getDirName(node.key)));
            }
        } catch (IOException ignored) {
            // Just return an empty collection
        }

        return forGroup;
    }

    @Override
    public Map<String, String> getProperties(String type, String group, String name) {
        Map<String, String> properties = new HashMap<>();

        String path = buildPath(type, group, name);

        try {
            EtcdResponsePromise<EtcdKeysResponse> promise = this.client.getDir(path).send();
            EtcdKeysResponse keys = promise.getNow();

            if (keys != null) {
                keys.node.nodes.forEach(node -> properties.put(node.key, node.value));
            }
        } catch (IOException ignored) {
            // Just return an empty map
        }

        return properties;
    }

    /**
     * Builds an etcd path from a number of strings.
     * @param segments The segments of the path.
     * @return The constructed path.
     */
    private static String buildPath(String ... segments) {
        return PATH_PREFIX + String.join("/", segments);
    }

    /**
     * Builds an etcd path for the given instance.
     * @param instance The instance to build the path for.
     * @return The path for the instance.
     */
    private static String buildInstancePath(Instance instance) {
        return buildPath(instance.getType(), instance.getGroup(), instance.getName());
    }

    /**
     * Returns the last segment of the given path. If the given string is not a path or is a path with a single-level,
     * the input string is returned.
     * @param path The full path.
     * @return The last segment in the path.
     */
    private static String getDirName(String path) {
        return path.substring(Math.max(0, path.lastIndexOf("/")));
    }
}
