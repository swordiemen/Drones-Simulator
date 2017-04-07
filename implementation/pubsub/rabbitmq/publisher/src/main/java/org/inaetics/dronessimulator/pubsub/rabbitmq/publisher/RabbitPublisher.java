package org.inaetics.dronessimulator.pubsub.rabbitmq.publisher;

import com.rabbitmq.client.ConnectionFactory;
import org.inaetics.dronessimulator.pubsub.api.Message;
import org.inaetics.dronessimulator.pubsub.api.publisher.Publisher;
import org.inaetics.dronessimulator.pubsub.api.serializer.Serializer;
import org.inaetics.dronessimulator.pubsub.api.Topic;
import org.inaetics.dronessimulator.pubsub.rabbitmq.common.RabbitConnection;

import java.io.IOException;

/**
 * A RabbitMQ implementation of a publisher.
 */
public class RabbitPublisher extends RabbitConnection implements Publisher {
    /**
     * Instantiates a new RabbitMQ publisher.
     * @param connectionFactory The RabbitMQ connection factory to use when starting a new connection.
     * @param serializer The serializer to use.
     */
    public RabbitPublisher(ConnectionFactory connectionFactory, Serializer serializer) {
        super(connectionFactory, serializer);
    }

    /**
     * Instantiates a new RabbitMQ publisher for use with OSGi. This constructor assumes the serializer is injected
     * later on.
     * @param connectionFactory The RabbitMQ connection factory to use when starting a new connection.
     */
    public RabbitPublisher(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    /**
     * Sends the given message to subscribers on the topic of this publisher.
     * @param topic The topic to publish the message to.
     * @param message The message to send.
     */
    public void send(Topic topic, Message message) {
        try {
            // Automatically (re)connect if needed
            if (!this.isConnected()) {
                this.connect();
            }

            // Declare topic, declares exchange on message broker if needed
            this.declareTopic(topic);

            // Drop null messages and when a serializer is absent
            Serializer serializer = this.serializer;

            if (message != null && serializer != null) {
                byte[] serializedMessage = serializer.serialize(message);
                this.channel.basicPublish(topic.getName(), "", null, serializedMessage);
            }
        } catch (IOException ignore) {
            // Just drop the message if there is no good connection
        }
    }
}