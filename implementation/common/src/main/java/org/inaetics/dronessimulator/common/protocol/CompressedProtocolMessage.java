package org.inaetics.dronessimulator.common.protocol;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@AllArgsConstructor
@NoArgsConstructor
public class CompressedProtocolMessage extends ProtocolMessage {

    private List<ProtocolMessage> msgs = new ArrayList<>();

    public void add(ProtocolMessage msg) {
        msgs.add(msg);
    }

    public void add(List<ProtocolMessage> msgsArg) {
        msgs.addAll(msgsArg);
    }

    public void remove(ProtocolMessage msg) {
        msgs.remove(msg);
    }

    public void remove(List<ProtocolMessage> msgsArg) {
        msgs.removeAll(msgsArg);
    }

    public List<ProtocolMessage> getAll() {
        return msgs;
    }

    public ProtocolMessage poll() {
        if (msgs.size() != 0) {
            ProtocolMessage m = msgs.get(0);
            msgs.remove(0);
            return m;
        }
        return null;
    }

    public Stream<ProtocolMessage> stream() {
        return msgs.stream();
    }

    @Override
    public List<MessageTopic> getTopics() {
        return Collections.singletonList(MessageTopic.STATEUPDATES);
    }

    @Override
    public String toString() {
        return "(CompressedProtocolMessage{" +
                String.join(",", msgs.stream().map(Object::toString).collect(Collectors.toList())) +
                "}";
    }
}
