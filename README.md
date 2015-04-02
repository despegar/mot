Message-Oriented Transport
==========================

The Message-Oriented Transport (Mot) is an experiment to speed and simplify communications inside the data center, adding delimitation and request-response association to TCP streams. Mot is an application-layer general-purpose protocol (and implementation) for transporting independent and relatively small messages (and potentially their responses).

Introduction
------------

Communications inside the data center are almost universally done using the Transmission Control Protocol (TCP). As TCP provides a bidirectional, unstructured stream, usually something must be added at the application level to delimit "messages", associate responses to requests and provide some form of typing.

Perhaps because of its universal deployment in the Internet and abundant and prolific tooling community, the Hypertext Transfer Protocol (HTTP) is commonly used as a transport inside the data center. This has some drawbacks:

* Single request per connection. Because HTTP can only send one message at a time (pipelining might help, but still enforces only a FIFO queue), any server delay prevents reuse of the TCP channel for additional requests. This problem is usually worked around by the use of multiple connections, which in turn must be pooled to avoid the overhead of creation. Moreover, as HTTP is actually half-duplex (the response cannot be sent before the request is completely received) the TCP channel is never fully used.

* Text based request and response headers. Reducing the data in headers could directly improve the latency.

* Redundant headers. Several headers are repeatedly sent across requests on the same channel. However, headers such as the User-Agent, Host, and Accept* are generally static and do not need to be resent.

* Messy relation between the protocol and its transport. Originally, HTTP did not do any provision for reusing connections. Although in HTTP 1.1 connections are reused by default, some problems remain, as servers can (and do) unilaterally close connections. The Apache Web Server, for example, [closes idle connections after only 5 seconds](https://httpd.apache.org/docs/2.4/mod/core.html#keepalivetimeout).

* Streaming complexity. There are three distinct modes of "transfer encodings" for request and response bodies.

* General complexity. It is perhaps not widely known that implementing HTTP correctly can be difficult. The protocol has evolved for a long time and has some exotic features that must be supported, at least partially, such as pipelining or "trailers" in chunked transfer encoding.

Other approaches
----------------

* Plain sockets -- it is always possible to use the TCP streams directly (and it is indeed done by a lot of applications); this, however, puts the burden of doing all the repetitive tasks (delimitation, response association, connection lifecycle management) on the application programmer.

* [HTTP/2](https://tools.ietf.org/html/draft-ietf-httpbis-http2) (previously known as SPDY) -- a protocol than maintains HTTP/1.x semantics, but encodes the information in binary form; it also modifies the way the data is sent over the TCP connection (TLS actually), allowing connection multiplexing. Its goal is primarily to serve as a replacement for HTTP in the web.

* [ZeroMQ](http://zeromq.org/) (ØMQ) -- an attempt to re-signify the Berkeley sockets API, defining several types of interactions using delimited messages over (among others) a TCP transport.

* [Stream Control Transmission Protocol](http://tools.ietf.org/html/rfc4960) (SCTP) -- a transport-layer protocol to replace TCP, which provides multiplexed streams and stream-aware congestion control. SCTP solves the "idle connection" problem and also provides message delimitation. It does not provide, however, the mapping of requests to responses, which should be done at the application level. In spite of that, SCTP could be a good fit as a transport for Mot.

Mot's approach
--------------

There are two types of things that can be sent over Mot: "messages", which are not responded, and "requests", which expect "responses" from the counterpart. The roles of the parties are well-defined and fixed: the "client" sends messages and requests to the "server", that sends "responses" back.

As HTTP actually hijacks a TCP connection during the request-response cycle, it is in practice free to stream requests or responses -- the connection would have been idle otherwise. Assuming that messages are small enough to be kept in memory, the request-response pattern can be implemented using only one connection per pair of participating processes. Taking advantage of that design restriction, Mot maintains just one connection regardless of the number of pending responses. Connections are initiated by clients and maintained until an idle period expires. Connections that fail in any way are automatically re-established if needed. This re-establishment policy also makes the protocol multi-homed.

A key feature of Mot is that the only mapping it provides is between requests and responses. Clients and servers cannot "dialog", at least not at the protocol level. Obviously they can do that in the upper layers, just like cookies implement a session over HTTP, which is a stateless protocol. This makes the protocol, as HTTP, a good fit for load balancing.

Messages and requests can be send intermixed between the same parties. They share the same structure on the wire, the difference being that requests leave information (and a timer) in the client, to map the response when it arrives (or report the timeout if it does not).

Keeping messages relatively small (the actual size is actually configurable) also prevents head-of-line blocking from being an issue. It also simplifies flow control significantly.

Messages
--------

From a user's point of view, Mot messages consist of a delimited byte array. The maximum size of the array that can be received is communicated by each party in the connection handshake. Messages also support attributes: a sequence of name-value associations that can be used to pass meta-data. The names are short ASCII string and the values are short byte arrays. Mot does not interpret the contents of the attributes in any way, they exist solely for the user's convenience. Attributes can be used to pass the header when encapsulating HTTP requests and responses.

Flow control
------------

The fact that the protocol makes shared use of a TCP connection introduces the need of flow control for proxy servers. These servers operate on behalf of others and receive selective back-pressure. Regarding requests, upon the case of a slow back-end, the server can respond with some application-level message indicating that the other side is unavailable or overloaded (akin to HTTP 503 responses). However, in the case of responses, if the front-end slows down (i.e. stops accepting data at the TCP level) the only way to avoid buffering (or discarding) is to tell the back-end to stop sending responses.

Mot uses a very minimalistic flow control mechanism. Each request carries a "flow identification", a opaque integer. If flow control is not desired, this value is just zero, which is reserved to the "main flow". The proxy server should maintain some mapping between front-end connections and flows. If a front-end client slows down, the proxy server "closes" the corresponding flow, sending a specific frame. The back-end server must then stop sending responses, until the flow is opened again.

It is worth noting that this mechanism does not operate on bytes, but in whole messages. Being a small-message protocol makes it easy to require that all parties be prepared to buffer whole messages.

Wire Format
-----------

The protocol wire format is specified as a handful of binary frames. Simplicity was an explicit design goal, and there are currently just eight frame types, which are documented [here](http://static.javadoc.io/com.github.marianobarrios/mot_2.11/0.8-RC9/index.html#mot.protocol.package).

Implementation
--------------

The present implementation uses blocking IO, with one thread reading and other writing, per each TCP socket. This results in two threads per counterpart, which in a typical data center environment effectively caps the number of threads in the low thousands.

Netty's implementation of the hashed wheel timer is used to keep track of request expirations. Tests showed it is quite more scalable than the JDK-provided ScheduledThreadPoolExecutor, which uses a heap internally. The hashed wheel timer scales well into the hundreds of thousands of requests per second. Its trick is to trade speed for some resolution, which can be acceptable in the case of IO timeouts. This is the only external dependency.

As it is commonly done with HTTP and other protocols, when the target is specified using a domain name (not an IP address), the implementation will try to establish a connection with all the A and AAAA records associated with the name, until one eventually succeeds.

With respect to concurrency, all the interface is thread-safe. In particular, messages and responses can be sent by several threads safely. Internally, the access to TCP connections is serialized using concurrent queues. There is one queue per each connection side (each pair of distinct Mot parties uses one queue for sending messages from their side). The queue used is a variant of the [LinkedBlockingQueue](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/LinkedBlockingQueue.html) that manages priorities and supports sub-queues. It is called [LinkedBlockingMultiQueue](src/main/scala/mot/queue/LinkedBlockingMultiQueue.scala).

Regarding performance, a single client-server pair can easily reach a throughput in the order of hundreds of thousands of request-response round-trips, using two quad-core instances. The latency in idle hardware of a request-response round-trip is in the order of the single millisecond.

API
---

* Browse [API documentation](http://www.javadoc.io/doc/com.github.marianobarrios/mot_2.11/) for the most recent release.

Monitoring
----------

While monitoring is an important feature in general, it is more important in this case, as Mot uses a binary protocol, and also because of the connection model (a single TCP connection can transport unrelated data). These circumstances can make the typical inspection using tcpdump or netstat somewhat inconvenient.

There are two monitoring tools available. Both work through TCP connections to the loopback interface:

### motstat

[motstat](motstat) is a command-line application that provides information about current connections (much like netstat) and also live monitoring of internal counters.

### motdump

[motdump](motdump) is a command-line application that dumps the frames as they are received or transmitted, much like as tcpdump does with the protocol segments. It offers a full set of filters that allow the command to be useful even in high-traffic environments.

Tools
-----

A frequent reason to keep using legacy protocols is simply that useful tools do exist. In order to make the testing of Mot servers more convenient, simple command-line application are provided. 

### murl

[murl](murl) (short for "Mot Curl") implements the client-side Mot functionality and allows to use the protocol from the command line.

### monch

[monch](monch-project) (short for "MOt BeNCHmark") is a simple benchmarking utility, inspired in the Apache Bench (ab) tool.

