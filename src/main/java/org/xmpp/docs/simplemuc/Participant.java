package org.xmpp.docs.simplemuc;

import rocks.xmpp.addr.Jid;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Participant {

    private final Jid jid;

    private final String nick;

    private final Set<Jid> joinedClients;

    public Set<Jid> getJoinedClients() {
        return joinedClients;
    }

    public Participant(Jid jid, String nick) {
        this.jid = jid.asBareJid();
        this.nick = nick;
        this.joinedClients = new HashSet<>();
        this.joinedClients.add(jid);
    }

    public void add(Jid client) {
        this.joinedClients.add(client);
    }

    public boolean remove(Jid client) {
        this.joinedClients.remove(client);
        return this.joinedClients.size() == 0;
    }

    public static boolean isNickInUse(Collection<Participant> participants, String nick) {
        for(Participant participant : participants) {
            if (participant.nick.equals(nick)) {
                return true;
            }
        }
        return false;
    }

    public static Participant find(Collection<Participant> participants, Jid jid) {
        for(Participant participant : participants) {
            if (participant.jid.equals(jid.asBareJid())) {
                return participant;
            }
        }
        return null;
    }

    public String getNick() {
        return nick;
    }

    public Jid getJid() {
        return jid;
    }
}
