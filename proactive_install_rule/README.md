# ONOS_APP_proactive_flowrule
## This Lab is to proactive install the flow rules in only one packet-in.
As the first packet-in send to the controller, the controller will find the path between the source and destination.
Then, the controller will proactively install the 2 flow rule(src->dst + dst->src) on each switch which is on the path. 
Also, the flow rule is installed from the switch that closest to the dst switch, because it can prevent the packet in again!
