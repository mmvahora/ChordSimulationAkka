# Course Project: Chord Protocol Simulator using Akka/HTTP

**Jesse Coultas (Group Leader)**

**Anjana Anand**

**Eunice Daphne John Kanagaraj**

**Moin Vahora**

**Shreya Shrivastava**

# Prerequisites:
**Docker image jesseuic/chordsim:latest**

**Java JDK 1.8 + SBT (SimpleBuildTools) Required**



# How to Run:

Docker image is available at jesseuic/chordsim:latest.  The docker image uses port 8080 which can be mapped to a different port when launching the docker image.

To run a job, build JSON Body with the following params and run using HTTP POST port 8080 to path /submitJob:

* numUsers  Int -> Ex: 10

* numComputers Int -> Ex: 15

* fingerSize Int -> Ex: 20

* perActorMinReq Int -> Ex: 1

* perActorMaxReq Int -> Ex: 10

* simulationDuration(seconds) Int -> Ex: 20

* timeMarks(seconds) Array of Ints -> Ex: [10, 15, 20]

* fileID String -> Ex: Filename manually added to upload directory (As specified in config file) or the ID provide back if upload performed using the submitFile service

* readWriteRatio Float -> Ex:1.0

Upload Filename
POST file using standard HTTP Multiform Post to port 8080 with path /submitFile with field name of submitFile.


# Design and Implementation: 

Project Structure was built upon using the published paper ["Chord: A Scalable Peer-to-peer Lookup Protocol
for Internet Applications"](https://pdos.csail.mit.edu/papers/ton:chord/paper-ton.pdf) 

Further details of structure built upon using the following slides/presentations from Tristan Penman:

* [Chord: A Scalable Peer-to-peer Lookup Protocol for Internet Applications](https://www.slideshare.net/tristanpenman/chord-a-scalable-peertopeer-lookup-protocol-for-internet-applications) (Penman takes the verbose and more complicated paper and breaks it down into easy-to-digest slides. Great for building a better base understanding of the structure and specific behavior of the Chord Protocol)

* [Implementing a Distributed Hash Table with Scala and Akka](https://www.slideshare.net/tristanpenman/implementing-a-distributed-hash-table-with-scala-and-akka) (Here Penman goes in depth to explain the various aspects, specifically the implementation described in the original paper, of the Chord Protocol. He gives examples of good/correct practices using Akka actors. Listed are eight algorithms/functions which define the Chord protocol. The functions are defined as (slide 15):
    * CheckPredecessor
    * ClosestPrecedingNode
    * FindPredecessor 
    * FindSuccessor
    * FixFingers
    * Join
    * Notify
    * Stabilize 

* [Chord: A Scalable Peer-to-peer Lookup
Service for Internet Applications](https://pdfs.semanticscholar.org/e7c7/f5b60d162db3ae5a0556010124df9f2e01a0.pdf) (Along with the previous functions, which are used to build and manage the ChordSpace itself, we need functions to allow for key lookup. In the linked slides from Jacobs University Bremen, we can see three functions defined and used for key lookup)
   * ClosestPrecedingFinger
   * FindPredecessor
   * FindSuccessor 

Together these functions (some overlap) are used to build the ChordSpace, manage it, and implement key lookup.


These functions (with slight modifications and differences) will be the basis for our implementation of the Chord protocol simulation.


When the node enters the chord ring, it invokes the function join and finds the predecessor and sets the predecessor. Similarly, it finds the successor and sets the successor. If the incoming node is the first node to join the network, it sets itself as successor and predecessor, and points to itself in the finger table. 

In the search predecessor function, if that node is set as a predecessor to itself else we find the closet preceding neighbour before that node of that node and sets it as the predecessor. Similarly, the search successor function finds its closest neighbour after that node and sets it as the successor. 

After setting the node's successor and predecessor, it calls this fixfinger function to set the entries of the finger table. The following formula is applied to make entries into the finger table.( N + 2 pow i ) mod (2 pow finger size). The update function will update the reference in other predecessors of the current node who can possibly point to this node.


The keys are generated based on the number of requests. Every entry in the data set is mapped to a unique key.  When the key approaches the node, it checks for the successor and gets linked to that node. If it doesn't finds a matching successor,  it checks for the successor of the predecessor until it finds a match or gets the successor of the closest preceding finger and matches itself with that node.  

Sample Finger Table generated and tested for the finger size 6 and number of nodes 3 (46, 14, 58). 

** Finger Table of 46 **
Predecessor: 14 Successor: 58

|   Finger  |  Entry   | 	
| --------- | -------- |
|     0     |    58    | 
|     1     |    58    | 
|     2	    |    58    | 
|     3	    |    58    |             
|     4     |    14    |
|     5     |    14    | 

** Finger Table of 14 **
Predecessor: 58 Successor: 46

|   Finger    |  Entry   | 	
| ----------- | -------- |
|    0        |    46    | 
|    1        |    46    | 
|    2	      |    46    | 
|    3	      |    46    |             
|    4        |    46    |
|    5        |    46    |

** Finger Table of 58 **
Predecessor: 46 ## Successor: 14

|   Finger    |  Entry   | 	
| ----------- | -------- |
|      0      |    14    | 
|      1      |    14    | 
|      2      |    14    | 
|      3      |    14    |             
|      4      |    14    |
|      5      |    46    | 

#Simulation Results Analysis:
We ran 3 separate simulations with different parameters. The parameters are the number of nodes in the chordSpace, the finger Space and the number of requests per user. Here are our results:

**Simulation 1**

Time Taken 83 ms

Node : 439303793 Hop Count : 10

Node : 158771893 Hop Count : 24

.
.
.
Node : 729019316 Hop Count : 853

Node : 566422674 Hop Count : 902

Number of Nodes: 500

Number of Requests: 10

Keys Searched: 4980

Total hops: 276007

Avg. number of hops:55.2014

**Simulation 2**

Time Taken 58 ms

Node : 433373942 Hop Count : 38

Node : 44319321 Hop Count : 59
.
.
.
Node : 428991422 Hop Count : 47

Node : 526259603 Hop Count : 12

Number of Nodes: 100

Number of Requests: 100

Keys Searched: 9971

Total hops: 101918

Avg. number of hops:10.1918

**Simulation 3**

Time Taken 110 ms
Node : 158771893 Hop Count : 19

Node : 196040290 Hop Count : 17
.
.
.
Node : 664415054 Hop Count : 1830

Node : 126072343 Hop Count : 989

Number of Nodes: 1000

Number of Requests: 10

Keys Searched: 9963

Total hops: 912595

Avg. number of hops:91.2595

* Based on the total runtime for each simulation, the number of total nodes seemingly has more of an affect than the number of requests per user. Simulation 2 has 10x the number of requests of the other 2, but has the lowest runtime. Keeping with that, Simulation 3 has a higher runtime than Simulation 2, which is because the increased ChordSpace size (total nodes).

* The amount of keys searched seems to be related to both input values, as the total # of keys search being approximately numNodes*numRequests (500*10=5000≈4980, 100*100=10000≈9971, 1000*10=1000≈9963). 

* The total number of hops also seems to correlate to the numNodes and numRequests parameters, though the exact correlation is murky. In Simulations 1 and 3, we have almost 4x more hops in Simulation 1 than 3 even though 3 only has double the number of total nodes. Simulation has the least hops at ≈100,000, which reaffirms that the total chordSpace size (numNodes) is what affects this number most.
