Message-Oriented Transport
==========================

The Message-Oriented Transport (Mot) is an experiment to speed and simplify communications inside the data center, which are nowadays mostly done using HTTP. Mot is an application-layer protocol (and implementation) for transporting independent and relatively small messages (and potentially their responses).

Introduction
------------

Communications inside the data center are almost universally done using the Transmission Control Protocol (TCP). As TCP provides a bidirectional, unstructured stream, usually something must be added at the application level to delimit "messages", associate responses to requests and provide some form of typing.

Perhaps because of its universal deployment in the Internet and abundant and prolific tooling community, the Hypertext Transfer Protocol (HTTP) is commonly used as a transport inside the data center. This has some drawbacks:

* Single request per connection. Because HTTP can only send one message at a time (pipelining might help, but still enforces only a FIFO queue), any server delay prevents reuse of the TCP channel for additional requests. This problem is usually worked around by the use of multiple connections, which in turn must be pooled to avoid the overhead of creation. Moreover, as HTTP is actually half-duplex (the response cannot be sent before the request is completely received) the TCP channel is never fully used.

* Text based request and response headers. Reducing the data in headers could directly improve the latency.

* Redundant headers. Several headers are repeatedly sent across requests on the same channel. However, headers such as the User-Agent, Host, and Accept* are generally static and do not need to be resent.

* Messy relation between the protocol and its transport. Originally, HTTP did not do any provision for reusing connections. Although in HTTP 1.1 connections are reused by default, some problems remain, as servers can (and do) unilaterally close connections. The Apache Web Server, for example, [closes idle connections after only 5 seconds](https://httpd.apache.org/docs/2.4/mod/core.html#keepalivetimeout).

* Streaming complexity. There are three distinct modes of "transfer encodings" for request and response bodies.

Other approaches
----------------

* Plain sockets -- it is always possible to use the TCP streams directly (and it is indeed done by a lot of applications); this, however, puts the burden of doing all the repetitive tasks (delimitation, response assocation, connection lifecycle management) on the applicacion programmer.

* [SPDY](http://www.chromium.org/spdy/spdy-whitepaper) -- a protocol than maintains HTTP semantics, but encodes the information in binary form; it also modifies the way the data is sent over the TCP connection (TLS actually); its goal is primarily to serve as a replacement for HTTP in the web.

* [ZeroMQ](http://zeromq.org/) (ØMQ) -- an attempt to re-signify the Berkely sockets API, defining several types of interactions using delimited messages over (among others) a TCP transport.

* [Stream Control Transmission Protocol](http://tools.ietf.org/html/rfc4960) (SCTP) -- a transport-layer protocol to replace TCP, which provides multiplexed streams and stream-aware congestion control. SCTP solves the "idle connection" problem and also provides message delimitation. It does not provide, however, the mapping of requests to responses, which should be done at the application level. In spite of that, SCTP could be a good fit as a transport for Mot.

Mot's approach
--------------

There are two types of things that can be sent over Mot: "messages", which are not responded, and "requests", which expect "responses" from the counterpart. The roles of the parties are well-defined and fixed: the "client" sends messages and requests to the "server", that sends "responses" back.

As HTTP actually hijacks a TCP connection during the request-response cycle, it is in practice free to stream requests or responses -- the connection would have been idle otherwise. Assuming that messages are small enough to be kept in memory, the request-response pattern can be implemented using only one connection per pair of participating processes. Taking advantage of that design restriction, Mot maintains just one connection regardless of the number of pending responses. Connections are initiated by clients and maintained until an idle period expires. Connections that fail in any way are automatically re-established if needed. This re-establishment policy also makes the protocol multi-homed.

A key feature of Mot is that the only mapping it provides is between requests and responses. Clients and servers cannot "dialog", at least not at the protocol level. Obviously they can do that in the upper layers, just like cookies implement a session over HTTP, which is a stateless protocol. This makes the protocol, as HTTP, a good fit for load balancing.

Messages and requests can be send intermixed between the same parties. They share the same structure on the wire, the difference being that requests leave information (and a timer) in the client, to map the response when it arrives (or report the timeout if it does not).

Keeping messages relatively small (the actual size is actually configurable) also prevents head-of-line blocking from being an issue.

Messages
--------

From a user's point of view, Mot messages consist of a delimited byte array. The maximum size of the array that can be received is communicated by each party in the connection handshake. Messages also support attributes: a sequence of name-value associations that can be used to pass metadata. The names are short ASCII string and the values are short byte arrays. Mot does not interpret the contents of the attributes in any way, they exist solely for the user's convenience. Attributes can be used to pass the header when encapsulating HTTP requests and responses.

Wire Format
-----------

The protocol wire format is specified as a handful of frames. There are currently eight frame types, which are documented [here](src/main/scala/mot/protocol).

Implementation
--------------

The present implementation uses blocking IO, with one thread reading and other writing, per each TCP socket. This results in two threads per counterpart, which in a typical data center environment effectively caps the number of threads in the low thousands.

Netty's implementation of the hashed wheel timer is used to keep track of request expirations. Tests showed it is quite more scalable than the JDK-provided ScheduledThreadPoolExecutor, which uses a heap internally. The hashed wheel timer scales well into the hundreds of thousands of requests per second. Its trick is to trade speed for some resolution, which can be acceptable in the case of IO timeouts. This is the only external dependency.

As it is commonly done with HTTP and other protocols, when the target is specified using a domain nane (not an IP address), the implementation will try to establish a connection with all the A and AAAA records associated with the name, until one eventually succeeds.

Regarding performance, a single client-server pair can easily reach a throughput in the order of hundreds of thousands of request-response roundtrips, using two quad-core instances. The latency in idle hardware of a request-response roundtrip is in the order of the single millisecond.

Known limitations
-----------------

* Transport Layer Security (TLS) is not currently supported.
