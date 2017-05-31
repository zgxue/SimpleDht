## SimpleDHT

### Introduction

Designed a simple DHT based on Chord. Although the design is based on Chord, it is a simplified version of Chord; finger tables and finger-based routing is in the future plans. There are three things implemented:
- ID space partitioning/re-partitioning
- Ring-based routing,
- Node joins.

The content provider implements all DHT functionalities and support insert and query operations. When run multiple instances of app, all content provider instances form a Chord ring and serve insert/query requests in a distributed fashion according to the Chord protocol.

Concurrent node joins and Node leaves/failures are ignored.

### References

Here are two references for the Chord design:

- [Lecture slides on Chord](http://www.cse.buffalo.edu/~stevko/courses/cse486/spring17/lectures/14-dht.pdf)

- [Chord paper](http://www.cse.buffalo.edu/~stevko/courses/cse486/spring17/files/chord_sigcomm.pdf)
