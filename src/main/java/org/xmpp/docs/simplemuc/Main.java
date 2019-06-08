package org.xmpp.docs.simplemuc;

import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppSessionConfiguration;
import rocks.xmpp.core.session.debug.ConsoleDebugger;
import rocks.xmpp.core.stanza.model.Message;
import rocks.xmpp.core.stanza.model.Presence;
import rocks.xmpp.core.stanza.model.StanzaError;
import rocks.xmpp.core.stanza.model.errors.Condition;
import rocks.xmpp.extensions.caps.EntityCapabilitiesManager;
import rocks.xmpp.extensions.component.accept.ExternalComponent;
import rocks.xmpp.extensions.disco.ServiceDiscoveryManager;
import rocks.xmpp.extensions.disco.model.info.Identity;
import rocks.xmpp.extensions.muc.model.Affiliation;
import rocks.xmpp.extensions.muc.model.Muc;
import rocks.xmpp.extensions.muc.model.Role;
import rocks.xmpp.extensions.muc.model.user.MucUser;
import rocks.xmpp.extensions.muc.model.user.Status;
import rocks.xmpp.im.subscription.PresenceManager;

import java.util.HashMap;

public class Main {

    private static final HashMap<Jid, RoomState> ROOMS = new HashMap<>();

    public static void main(String... args) {
        final XmppSessionConfiguration.Builder builder = XmppSessionConfiguration.builder();

        if (Configuration.isDebug()) {
            builder.debugger(ConsoleDebugger.class);
        }

        ExternalComponent externalComponent = ExternalComponent.create(
                Configuration.getComponentName(),
                Configuration.getSharedSecret(),
                Configuration.getHostname(),
                Configuration.getPort());

        externalComponent.addInboundMessageListener(messageEvent -> {
            final Message message = messageEvent.getMessage();
            if (message.getTo().isDomainJid()) {
                if (message.getType() == Message.Type.ERROR) {
                    return;
                }
                externalComponent.sendMessage(message.createError(Condition.SERVICE_UNAVAILABLE));
            }

            if (message.getType() == Message.Type.GROUPCHAT && message.getTo().isBareJid()) {
                final Jid roomJid = message.getTo().asBareJid();
                final RoomState roomState = ROOMS.get(roomJid);
                if (roomState == null) {
                    final Message error = message.createError(new StanzaError(StanzaError.Type.CANCEL, Condition.ITEM_NOT_FOUND, "group chat does not exists"));
                    externalComponent.sendMessage(error);
                    return;
                }
                Participant sender;
                try {
                    sender = roomState.getParticipant(message.getFrom());
                } catch (RoomState.ClientNotJoinedException e) {
                    final Message error = message.createError(new StanzaError(StanzaError.Type.AUTH, Condition.FORBIDDEN, "You are not a participant of this room"));
                    externalComponent.sendMessage(error);
                    return;
                }
                Message echo = new Message();
                echo.setType(Message.Type.GROUPCHAT);
                echo.setFrom(roomJid.withResource(sender.getNick()));
                echo.setId(message.getId());
                echo.setBody(message.getBody());
                echo.addExtensions(message.getExtensions());
                for (Participant participant : roomState.getParticipants()) {
                    for (Jid client : participant.getJoinedClients()) {
                        echo.setTo(client);
                        externalComponent.sendMessage(echo);
                    }
                }
            }
        });

        ServiceDiscoveryManager serviceDiscoveryManager = externalComponent.getManager(ServiceDiscoveryManager.class);
        serviceDiscoveryManager.addIdentity(Identity.conferenceText());
        serviceDiscoveryManager.addFeature(Muc.NAMESPACE);

        externalComponent.getManager(EntityCapabilitiesManager.class).setEnabled(false);
        externalComponent.getManager(PresenceManager.class).setEnabled(false);

        externalComponent.addInboundPresenceListener(presenceEvent -> {
            final Presence presence = presenceEvent.getPresence();
            Muc muc = presence.getExtension(Muc.class);
            Jid roomJid = presence.getTo().asBareJid();
            String desiredNick = presence.getTo().getResource();
            Jid client = presence.getFrom();
            if (roomJid.getLocal() == null) {
                System.out.println("presence to service received");
                return;
            }
            RoomState roomState = ROOMS.computeIfAbsent(roomJid, r -> new RoomState());
            if (muc != null && presence.getType() == null) {
                System.out.println(client + " joins room " + roomJid + " as " + desiredNick);
                try {
                    final String nick = roomState.join(client, desiredNick);
                    Presence selfPresence = new Presence(client);
                    selfPresence.setFrom(roomJid.withResource(nick));
                    selfPresence.addExtension(MucUser.withItem(Affiliation.NONE, Role.PARTICIPANT, Jid.of(client.asBareJid()), Status.SELF_PRESENCE, Status.SERVICE_HAS_ASSIGNED_OR_MODIFIED_NICK));

                    for (Participant participant : roomState.getParticipants()) {
                        if (participant.getJid().equals(client.asBareJid())) {
                            continue;
                        }
                        Presence participantPresence = new Presence(client);
                        participantPresence.setFrom(roomJid.withResource(participant.getNick()));
                        participantPresence.addExtension(MucUser.withItem(Affiliation.NONE, Role.PARTICIPANT, Jid.of(participant.getJid())));
                        externalComponent.sendPresence(participantPresence);
                        for (Jid joinedClient : participant.getJoinedClients()) {
                            Presence newUserPresence = new Presence(joinedClient);
                            newUserPresence.setFrom(roomJid.withResource(nick));
                            newUserPresence.addExtension(MucUser.withItem(Affiliation.NONE, Role.PARTICIPANT, Jid.of(client.asBareJid())));
                            externalComponent.sendPresence(newUserPresence);
                        }
                    }

                    externalComponent.sendPresence(selfPresence);
                } catch (RoomState.NickAlreadyInUseException e) {
                    Presence errorPresence = presence.createError(Condition.CONFLICT);
                    externalComponent.sendPresence(errorPresence);
                }
            }
            if (presence.getType() == Presence.Type.UNAVAILABLE) {
                System.out.println(client + " leaves room " + roomJid);
                if (roomState.leave(client, desiredNick)) {
                    for (Participant participant : roomState.getParticipants()) {
                        for (Jid joinedClient : participant.getJoinedClients()) {
                            Presence leaveNotificationPresence = new Presence(joinedClient);
                            leaveNotificationPresence.setType(Presence.Type.UNAVAILABLE);
                            leaveNotificationPresence.setFrom(roomJid.withResource(desiredNick));
                            leaveNotificationPresence.addExtension(MucUser.withItem(Affiliation.NONE, Role.NONE, Jid.of(client.asBareJid())));
                            externalComponent.sendPresence(leaveNotificationPresence);
                        }
                    }
                }
                Presence selfPresence = new Presence(client);
                selfPresence.setType(Presence.Type.UNAVAILABLE);
                selfPresence.setFrom(roomJid.withResource(desiredNick));
                selfPresence.addExtension(MucUser.withItem(Affiliation.NONE, Role.PARTICIPANT, Jid.of(client.asBareJid()), Status.SELF_PRESENCE));
            }
        });

        connectAndKeepRetrying(externalComponent);
    }

    private static void connectAndKeepRetrying(final ExternalComponent component) {
        while (true) {
            try {
                component.connect();
                while (component.isConnected()) {
                    sleep(500);
                }
            } catch (XmppException e) {
                System.err.println(e.getMessage());
            }
            sleep(5000);
        }
    }

    private static void sleep(long interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {

        }
    }


}
