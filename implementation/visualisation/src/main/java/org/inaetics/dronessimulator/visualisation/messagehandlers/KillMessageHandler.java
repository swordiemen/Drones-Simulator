package org.inaetics.dronessimulator.visualisation.messagehandlers;

import org.inaetics.dronessimulator.common.protocol.KillMessage;
import org.inaetics.dronessimulator.pubsub.api.Message;
import org.inaetics.dronessimulator.pubsub.api.MessageHandler;
import org.inaetics.dronessimulator.visualisation.BaseEntity;

import java.util.concurrent.ConcurrentMap;


public class KillMessageHandler implements MessageHandler {
    private final ConcurrentMap<String, BaseEntity> entities;

    public KillMessageHandler(ConcurrentMap<String, BaseEntity> entities) {
        this.entities = entities;
    }

    @Override
    public void handleMessage(Message message) {
        KillMessage killMessage = (KillMessage) message;
        BaseEntity baseEntity = entities.get(killMessage.getIdentifier());

        if(baseEntity != null) {
            baseEntity.delete();
            entities.remove(killMessage.getIdentifier());
        }
    }
}
