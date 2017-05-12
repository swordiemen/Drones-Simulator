package org.inaetics.dronessimulator.physicsenginewrapper.ruleprocessors.message;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.inaetics.dronessimulator.common.protocol.KillMessage;
import org.inaetics.dronessimulator.common.protocol.ProtocolMessage;
import org.inaetics.dronessimulator.physicsenginewrapper.state.PhysicsEngineStateManager;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Getter
@ToString
public class DestroyDrone extends RuleMessage {
    private final int id;

    @Override
    public List<ProtocolMessage> getProtocolMessage(PhysicsEngineStateManager stateManager) {
        KillMessage msg = new KillMessage();

        msg.setEntityId(this.id);
        msg.setEntityType(stateManager.getTypeFor(id));

        return Collections.singletonList(msg);
    }
}