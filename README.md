---
title: Developing A Simple Multi-User Chat Component with Java
author: Daniel Gultsch
---
# Introduction

In this tutorial we are going to implement a simple group chat service from scratch. While not very useful by itself it has great potential to teach you a bit about the inner workings of XMPP and serve as a starting off point into component development.

Components in general and group chat components in particular are also a building block for gateways (transports into other (instant messaging) networks). A hypothetical RocketChat or Mattermost gateway would probably act as a group chat service. Therefor the knowledge gained by working through this tutorial can be very beneficial to anyone attempting to develop such a gateway.

# Components vs Bots

You might already be familiar with the concepts of chat bots. From a protocol perspective chats bots are nothing more than regular XMPP clients. Each will use an account on an XMPP server and be available under the Jabber ID of that account. They are quite easy to setup since you don’t need to run your own XMPP server. Communicating with a chat bot happens by optionally adding the JID of the bot to your roster and then sending simple text commands or even (when your client supports that) something like [Ad-Hoc Commands](https://xmpp.org/extensions/xep-0050.html). Bots can be great tools for providing simple functionality like translation, home automation or other kinds of task delegation.

Components on the other hand extend server functionality. Components on a server are usually discoverable to its users by means of [Service Discovery](https://xmpp.org/extensions/xep-0030.html). Each component will get an entry in the [Items Node](https://xmpp.org/extensions/xep-0030.html#items-nodes) of a server and users will make an (automated) attempt at discovering that additional functionality provided by their server. Components are available on their own domain (usually but not necessarily a subdomain of the server). Most servers implementations come with a set of additional services out of the box. Popular examples can include [HTTP File Upload](https://xmpp.org/extensions/xep-0363.html) (usually available under share.domain.tld or files.domain.tld), a [SOCKS5 Bytestreams Proxy](https://xmpp.org/extensions/xep-0065.html) (proxy.domain.tld) or a [Multi-User Chat](https://xmpp.org/extensions/xep-0045.html) (conference.domain.tld, muc.domain.tld, …).

The [Jabber Component Protocol](https://xmpp.org/extensions/xep-0114.html) provides an easy way to create an external service without modifying the server itself. Components can be written in almost any language (that has an XMPP library) and are basically stand alone programs that connect to an XMPP server. Requests to and from that component are proxied through the XMPP server. The TCP connection between server and component is not encrypted. Therefor the component has to run on the same machine as the XMPP server. (Also for security purposes the component port is usually only exposed on localhost.) The XMPP server will route all stanzas (Messages, IQ and Presences) where the domain part of the to attribute matches the components domain to that component. Subsequently the component can not only be addressed by its own domain but also with any Jabber ID under that domain (muc.domain.tld, a@muc.domain.tld, b@muc.domain.tld, …) including full JIDs like c@muc.domain.tld/some-resource or muc.domain.tld/some-other-resource.
When responding the component can also use any of those Jabber IDs in the from attribute.

In summary components…

* Give you great flexibility and control over all localparts under a domain including the domain itself
* Are automatically discoverable by clients/users
* Require you to run your own server

# Component Setup

Explaining how to setup your own XMPP server is out of scope for this tutorial. However for your convenience here are a few words specifically regarding configuring components on the server side: On most servers the amount of configuration necessary is limited to providing a domain under which the component is going to be available and a *secret* which the external component will use to authenticate itself towards the server.

Most servers will automatically add components that are a subdomain of a configured virtual host to the items node of said virtual host. If you want to make the component available on something that is not a subdomain while still making it automatically discoverable you might have to add the component domain manually to the items (Most servers have a way of adding additional items.)

## Prosody

For Prosody you can add something along those lines to your configuration file.

```
Component "mymuc.domain.tld"
         component_secret = "mysecret"
```
The Prosody documentation on external components is available [here](https://prosody.im/doc/components).

## Ejabberd

In the listen section of `ejabberd.yml` add something like that
```
listen:
  -
    port: 5347
    ip: "127.0.0.1"
    module: ejabberd_service
    hosts:
      "mymuc.domain.tld":
        password: "mysecret"
```
The Ejabberd documentation for external components is available [here](https://docs.ejabberd.im/admin/configuration/#listening-module). Hint: Also search that documentation for the keyword *ejabberd_service* to find additional examples.

# Getting Started

## Build enviroment and dependencies

## Creating the component

Creating a component in Babbler is fairly straight forward. The static helper method `ExternalComponent.create()` will take the domain, the secret, as well as the hostname and port of the XMPP server as paramaters and return an instance of `ExternalComponent`.

We quickly write another small convenience method `connectAndKeepRetrying()` that will attempt to connect the component to the server and automatically reconnect it in case the XMPP server gets restarted.

```java
public static void main(String... args) {
    ExternalComponent externalComponent = ExternalComponent.create(
        "mymuc.domain.tld",
        "mysecret",
        "localhost",
         5347);
    connectAndKeepRetrying(externalComponent);
}
private static void connectAndKeepRetrying(final ExternalComponent c) {
    while (true) {
        try {
            c.connect();
            while (c.isConnected()) {
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
```
If everything was setup correctly running that program will actually return no output. Only in case of failure it will complain about not being able to connect to the host or about not being able to authorize.

## Rejecting messages to the component itself

A group chat service should reject messages addressed to the service itself (mymuc.domain.tld). Doing that will also give you a nice opportunity to confirm that the component is working correctly and can be addressed from another XMPP account.

**Note:** To avoid nasty loops no XMPP entity (and that includes servers, clients and services) should ever automatically respond to other error messages.

```java
externalComponent.addInboundMessageListener(messageEvent -> {
    final Message message = messageEvent.getMessage();
    if (message.getTo().isDomainJid()) {
        if (message.getType() == Message.Type.ERROR) {
            return;
        }
        final Message error = message.createError(Condition.SERVICE_UNAVAILABLE);
        externalComponent.sendMessage(error);
    }
});
```
If you add *mymuc.domain.tld* to your roster and send a message to it, the component will respond with an error marking the sent message as *failed*.

# A simple MUC
## Introduction to MUC

Most MUC services will automatically create a chat room as soon as the first user attempts to join. Joining a MUC room is always tied to one particular XMPP session. This means that a user is only *in the room* for as long as the client is online. A user can also be in a room multiple times; once with every client. When joining simulataniously with multiple clients a user can also choose whether they want all clients to join with the same nick name or with different nick names. Joining with the same nick name will make that user appear as one to other participants; joining with different nicks will make them appear appear as multiple participants to others. It will not immedatly be obvious to others that those participants belong to the same account.

### Same nick merging

A MUC service has the ability to assign a different nick name on join (Instead of the requested one). This can come in handy for some types of transports where the other instant messaging network uses globel nick names instead of per room nick names. For simplicity purposes and also to demonstrate that concept our implementation will rewrite the nick to match the nick of the first client that joined. (The first client will be free to choose what ever nick name they wish as long as it isn’t used by another account.)

### Joining

The protocol for joining a MUC consists of the client sending a presence stanza to the MUC. The to-attribute of that presence contains the Jabber ID of the room (`room-one@mymuc.domain.tld`) as well as the desired nick as resource. (`room-one@mymuc.domain.tld/desired-nick`). In response the room will send presences back for each participant in the room. The presence will originate from full JIDs that have the participant’s nicks as resource. The last presence send from the room back to the client is a reflection of the presence the client used to join. This serves two purposes:

1. It indicates to the client that all other participants’ presences have been sent.
2. In case of nick name reassignment it informs the client of the new nick.

#### Presence from client to room

```xml
<presence from="user@domain.tld/pc" to="room-one@mymuc.domain.tld/desired-nick">
   <x xmlns="http://jabber.org/protocol/muc"/>
</presence>
```
The presence must have a special `x` element as child to indicate that this is an attempt to join a room.

#### Presences from room to client

```xml
<presence from="room-one@mymuc.domain.tld/user-a" to="user@domain.tld/pc">
  <x xmlns="http://jabber.org/protocol/muc#user">
    <item affiliation="none" role="participant"/>
  </x>
</presence>
<presence from="room-one@mymuc.domain.tld/user-b" to="user@domain.tld/pc">
  <x xmlns="http://jabber.org/protocol/muc#user">
    <item affiliation="none" role="participant"/>
  </x>
</presence>
<presence from="room-one@mymuc.domain.tld/reassigned-nick" to="user@domain.tld/pc">
  <x xmlns="http://jabber.org/protocol/muc#user">
    <item affiliation="member" role="participant"/>
    <status code="110"/>
  </x>
</presence>
```
Presences from the room to the client are garnished with an `x` element. This is used to convey meta information like role and affilation (admin, owner, …). The clients own presences is additionally annotated with a `<status code="110"/>`. This helps the client to understand that this is their own presence and is the mechanism that makes nick reassignment work.

*Sidenote: Some older clients might not look at the status and just attempt to recognize their own presence by looking at resource part and wait that one matches the nick they used on join. However this is a bug in the client and should be reported when encountered.*

## Start coding
