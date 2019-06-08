package org.xmpp.docs.simplemuc;

import rocks.xmpp.addr.Jid;

import java.util.ArrayList;
import java.util.Collection;

public class RoomState {

    private final Collection<Participant> participants = new ArrayList<>();

    public String join(Jid client, String desiredNick) throws NickAlreadyInUseException {
        synchronized (participants) {
            final Participant participant = Participant.find(participants, client);
            if (participant != null) {
                participant.add(client);
                return participant.getNick();
            } else {
                if (Participant.isNickInUse(participants, desiredNick)) {
                    throw new NickAlreadyInUseException();
                } else {
                    this.participants.add(new Participant(client, desiredNick));
                    return desiredNick;
                }
            }
        }
    }

    public boolean leave(Jid client, String nick) {
        synchronized (participants) {
            final Participant participant = Participant.find(participants, client);
            if (participant != null) {
                if (participant.getNick().equals(nick) && participant.remove(client)) {
                    this.participants.remove(participant);
                    return true;
                }
            }
        }
        return false;
    }

    public Participant getParticipant(Jid client) throws ClientNotJoinedException {
        Participant participant = Participant.find(participants, client);
        if (participant == null || !participant.getJoinedClients().contains(client)) {
            throw new ClientNotJoinedException();
        }
        return participant;
    }

    public Collection<Participant> getParticipants() {
        return participants;
    }

    public static class NickAlreadyInUseException extends Exception {

    }

    public static class ClientNotJoinedException extends Exception {

    }
}
