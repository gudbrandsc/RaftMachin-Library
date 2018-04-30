# project-5-gudbrandsc
project-5-gudbrandsc created by GitHub Classroom

For project 5 I will a Raft library that I can use for my user service(project 3/4). The reason that I want to make it a library is 
because it will make it easier to test different features as I develop it. For my demos before the deadline I will try to include my library
to multible simple servers to demonstrate the different parts of raft(defined below). Since Raft aims at having a high level of understandability, I can 
use most of my time implementing features instead of getting stuck on complex parts that I don't fully understand(like parts of Dynamo). 

To "force" myself to work with this project incrementally throughout the time period I will have multiple benchmarks. These benchmarks might change depending on their difficulties and 
if I find out that some features require more work than previously assumed. This will be my first outline of my project and will not change before I talk too you.


<b>05/01</b><br>
First thing I want to do is too set up the architecture for a server. This is as far as I can see an essential piece to make 
to be able to test other features like RPC, election. 

<b>This includes:</b>
  - Consensus module
  - log
  - State Machine 
  
<i> Possible challenges - store state on node failure</i> 
  
  
<b>05/03</b><br>
 Implement RPC messages. This implementation could change as I'm working on election and replication. There are two RPC messages that need to be implemented:
 - RequestVotes is used by candidates during elections
 - AppendEntries used by leaders for replicating log entries and also as a heartbeat 
    
<b>05/08</b><br>
There are two thing I want to have working on this benchmark. First is the election algorithm, and detection of leader failure.
 - Finish election algorithm on leader failure, and send RPC election requests. 
 
I also want to have a working demo of all the features above. This will be done by having a 4 services(1 leader, and 3 secondaries). 


<b>05/10</b><br>
Log replication - leader will send RPC AppendEntries to sencondaries. 
- Secondaries should append AppendEntries to their log.
- Log entries should be committed(added to state machine) based on RPC from leader. 

For this project I will try to put a lot of effort on my test script to make sure that my demo is working correctly. 
I have still not 100% decided on having a demo using my userservice, or if I'm going to have a simple service. This will depend on the 
amount of time i have left



